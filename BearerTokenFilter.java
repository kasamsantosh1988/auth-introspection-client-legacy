package com.example.authclient;

import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Requires "Authorization: Bearer ..." on every request except the excluded paths and CORS preflights.
 * 401 = missing/invalid/expired/revoked token or not meant for this API; 503 = the auth server could not be asked.
 *
 * Register it first in the filter chain, either in web.xml with the no-argument constructor (settings are read as
 * described in {@link AuthClientConfig}) or from code with {@link #BearerTokenFilter(AuthClientConfig)}.
 */
public class BearerTokenFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(BearerTokenFilter.class.getName());
    private static final String BEARER = "Bearer ";

    private volatile CachingTokenValidator validator;
    private volatile List<String> excludePaths = Collections.emptyList();
    private volatile String requiredAudience;
    private volatile boolean enabled = true;

    /** For web.xml: configured in {@link #init(FilterConfig)}. */
    public BearerTokenFilter() {
    }

    public BearerTokenFilter(AuthClientConfig config) {
        configure(config);
    }

    /** For tests and custom wiring. */
    public BearerTokenFilter(CachingTokenValidator validator, List<String> excludePaths, String requiredAudience) {
        this.validator = validator;
        this.excludePaths = excludePaths;
        this.requiredAudience = requiredAudience;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (this.validator == null && this.enabled) {
            try {
                configure(AuthClientConfig.load(filterConfig));
            }
            catch (RuntimeException ex) {
                throw new ServletException("Auth client is not configured: " + ex.getMessage(), ex);
            }
        }
    }

    private void configure(AuthClientConfig config) {
        config.validate();
        this.enabled = config.isEnabled();
        if (!this.enabled) {
            LOG.warning("auth.client.enabled=false: bearer tokens are NOT checked. Use this only in tests.");
            return;
        }
        String uri = config.getIntrospectionUri().toLowerCase();
        if (!uri.startsWith("https://") && !uri.startsWith("http://localhost") && !uri.startsWith("http://127.0.0.1")) {
            LOG.warning("The introspection URI is not https. The API's credentials and the tokens travel unprotected.");
        }
        this.excludePaths = config.getExcludePaths();
        this.requiredAudience = config.getRequiredAudience();
        this.validator = new CachingTokenValidator(new HttpTokenIntrospector(config), Clock.systemUTC(),
                config.getMaxCacheTtl(), config.getNegativeCacheTtl(), config.getMaxCacheEntries());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest) || !(servletResponse instanceof HttpServletResponse)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (!this.enabled || shouldNotFilter(request)) {
            chain.doFilter(request, response);
            return;
        }
        CachingTokenValidator active = this.validator;
        if (active == null) {
            LOG.severe("Bearer token filter has no configuration. Refusing the request.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            unauthorized(response, "invalid_request", "Bearer token required");
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            unauthorized(response, "invalid_request", "Bearer token required");
            return;
        }

        TokenInfo info;
        try {
            info = active.validate(token);
        }
        catch (AuthServerUnavailableException ex) {
            LOG.log(Level.WARNING, "Cannot validate token: {0}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.setHeader("Cache-Control", "no-store");
            return;
        }
        if (!info.active() || (this.requiredAudience != null && !info.audience().contains(this.requiredAudience))) {
            unauthorized(response, "invalid_token", "The access token is invalid or expired");
            return;
        }

        AuthenticatedUser user = new AuthenticatedUser(info.subject(), info.userId(), info.directory(), info.roles(),
                new LinkedHashSet<String>(info.scopes()));
        request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, user);
        CurrentUser.set(user);
        try {
            chain.doFilter(request, response);
        }
        finally {
            CurrentUser.clear();
        }
    }

    @Override
    public void destroy() {
    }

    boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null) {
            return true;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = (context != null && !context.isEmpty() && uri.startsWith(context)) ? uri.substring(context.length()) : uri;
        if (isSuspicious(path)) {
            return false;       // never skip authentication for a path that tries tricks
        }
        for (String pattern : this.excludePaths) {
            if (AntPaths.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** Dot segments, path parameters, encoded characters, backslashes and doubled slashes can make a path look excluded. */
    private static boolean isSuspicious(String path) {
        return path.contains("..") || path.contains(";") || path.contains("%") || path.contains("\\")
                || path.contains("//") || path.contains("/./");
    }

    private static void unauthorized(HttpServletResponse response, String error, String description) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer error=\"" + error + "\", error_description=\"" + description + "\"");
        response.setHeader("Cache-Control", "no-store");
    }
}
