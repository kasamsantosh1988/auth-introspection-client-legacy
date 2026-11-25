package com.example.authclient;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

public class BearerTokenFilterTest {

    private static final class Req implements InvocationHandler {
        String method = "GET";
        String uri = "/orders/1";
        String context = "";
        final Map<String, String> headers = new HashMap<String, String>();
        final Map<String, Object> attributes = new HashMap<String, Object>();

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) {
            switch (m.getName()) {
                case "getMethod": return this.method;
                case "getRequestURI": return this.uri;
                case "getContextPath": return this.context;
                case "getHeader": return this.headers.get(String.valueOf(a[0]));
                case "setAttribute": this.attributes.put((String) a[0], a[1]); return null;
                case "getAttribute": return this.attributes.get(String.valueOf(a[0]));
                default: throw new UnsupportedOperationException(m.getName());
            }
        }

        HttpServletRequest proxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, this);
        }
    }

    private static final class Resp implements InvocationHandler {
        int status = 200;
        final Map<String, String> headers = new HashMap<String, String>();

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) {
            switch (m.getName()) {
                case "setStatus": this.status = (Integer) a[0]; return null;
                case "setHeader": this.headers.put((String) a[0], (String) a[1]); return null;
                default: throw new UnsupportedOperationException(m.getName());
            }
        }

        HttpServletResponse proxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, this);
        }
    }

    private static final class Chain implements FilterChain {
        int calls;
        AuthenticatedUser seen;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            this.calls++;
            this.seen = CurrentUser.get();
        }
    }

    private TokenInfo answer = TokenInfo.INACTIVE;
    private RuntimeException failure;
    private int introspections;

    private BearerTokenFilter filter(String requiredAudience) {
        TokenIntrospector introspector = new TokenIntrospector() {
            @Override
            public TokenInfo introspect(String token) {
                introspections++;
                if (failure != null) {
                    throw failure;
                }
                return answer;
            }
        };
        CachingTokenValidator validator = new CachingTokenValidator(introspector, Clock.systemUTC(), Duration.ofSeconds(60),
                Duration.ofSeconds(5), 100);
        return new BearerTokenFilter(validator, Arrays.asList("/actuator/health/**", "/error"), requiredAudience);
    }

    private static TokenInfo user(String audience) {
        return new TokenInfo(true, "app:1001", "1001", "app", Collections.singleton("USER"),
                Collections.singletonList("app"), Collections.singletonList(audience), "app", Instant.now().plusSeconds(1800));
    }

    private int run(BearerTokenFilter filter, Req req, Resp resp, Chain chain) throws Exception {
        filter.doFilter(req.proxy(), resp.proxy(), chain);
        return resp.status;
    }

    @Test
    public void missingOrMalformedHeaderIs401() throws Exception {
        for (String header : new String[] {null, "", "Basic abc", "Bearer", "Bearer   ", "bearertoken"}) {
            Req req = new Req();
            if (header != null) {
                req.headers.put("Authorization", header);
            }
            Resp resp = new Resp();
            Chain chain = new Chain();
            assertEquals("header: " + header, 401, run(filter(null), req, resp, chain));
            assertEquals(0, chain.calls);
            assertTrue(resp.headers.get("WWW-Authenticate").startsWith("Bearer error=\"invalid_request\""));
            assertEquals("no-store", resp.headers.get("Cache-Control"));
        }
    }

    @Test
    public void inactiveTokenIs401() throws Exception {
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        Resp resp = new Resp();
        Chain chain = new Chain();
        assertEquals(401, run(filter(null), req, resp, chain));
        assertEquals(0, chain.calls);
        assertTrue(resp.headers.get("WWW-Authenticate").contains("invalid_token"));
    }

    @Test
    public void validTokenReachesTheApplicationWithTheUser() throws Exception {
        this.answer = user("api");
        Req req = new Req();
        req.headers.put("Authorization", "bearer abc");     // scheme is case-insensitive
        Resp resp = new Resp();
        Chain chain = new Chain();
        assertEquals(200, run(filter("api"), req, resp, chain));
        assertEquals(1, chain.calls);
        assertEquals("1001", chain.seen.userId());
        assertTrue(chain.seen.hasRole("USER"));
        assertTrue(chain.seen.hasScope("app"));
        assertFalse(chain.seen.isService());
        assertNull("cleared after the request", CurrentUser.get());
        assertSame(chain.seen, req.attributes.get(CurrentUser.class.getName()));
    }

    @Test
    public void userIsClearedEvenWhenTheApplicationFails() throws Exception {
        this.answer = user("api");
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        FilterChain boom = new FilterChain() {
            @Override
            public void doFilter(ServletRequest q, ServletResponse r) throws IOException, ServletException {
                throw new ServletException("boom");
            }
        };
        try {
            filter(null).doFilter(req.proxy(), new Resp().proxy(), boom);
            fail();
        }
        catch (ServletException expected) {
            // ok
        }
        assertNull(CurrentUser.get());
    }

    @Test
    public void tokenForAnotherAudienceIs401() throws Exception {
        this.answer = user("other-api");
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        Resp resp = new Resp();
        Chain chain = new Chain();
        assertEquals(401, run(filter("api"), req, resp, chain));
        assertEquals(0, chain.calls);
    }

    @Test
    public void serviceTokenIsRecognised() throws Exception {
        this.answer = new TokenInfo(true, "eapps-svc", null, null, null, Collections.singletonList("ekyc.internal"),
                Collections.singletonList("ekyc-api"), "eapps-svc", Instant.now().plusSeconds(1800));
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        Chain chain = new Chain();
        assertEquals(200, run(filter("ekyc-api"), req, new Resp(), chain));
        assertTrue(chain.seen.isService());
        assertTrue(chain.seen.hasScope("ekyc.internal"));
    }

    @Test
    public void authServerDownIs503WithRetryAfterAndNeverLetsTheRequestThrough() throws Exception {
        this.failure = new AuthServerUnavailableException("down", null);
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        Resp resp = new Resp();
        Chain chain = new Chain();
        assertEquals(503, run(filter(null), req, resp, chain));
        assertEquals("5", resp.headers.get("Retry-After"));
        assertEquals(0, chain.calls);
    }

    @Test
    public void excludedPathsAndPreflightSkipTheCheck() throws Exception {
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness", "/error"}) {
            Req req = new Req();
            req.uri = path;
            Chain chain = new Chain();
            assertEquals(path, 200, run(filter(null), req, new Resp(), chain));
            assertEquals(1, chain.calls);
        }
        Req preflight = new Req();
        preflight.method = "OPTIONS";
        preflight.headers.put("Access-Control-Request-Method", "POST");
        Chain chain = new Chain();
        run(filter(null), preflight, new Resp(), chain);
        assertEquals(1, chain.calls);
        assertEquals(0, this.introspections);
    }

    @Test
    public void contextPathIsIgnoredWhenMatching() throws Exception {
        Req req = new Req();
        req.context = "/ekyc";
        req.uri = "/ekyc/actuator/health";
        Chain chain = new Chain();
        run(filter(null), req, new Resp(), chain);
        assertEquals(1, chain.calls);
    }

    @Test
    public void pathTricksCannotUseAnExclusion() throws Exception {
        List<String> tricks = Arrays.asList("/actuator/health/../orders/1", "/actuator/health/%2e%2e/orders",
                "/error;x=/../orders", "/actuator/health//..\\orders", "/actuator/health/./../orders");
        for (String path : tricks) {
            Req req = new Req();
            req.uri = path;
            Chain chain = new Chain();
            assertEquals(path, 401, run(filter(null), req, new Resp(), chain));
            assertEquals(0, chain.calls);
        }
    }

    @Test
    public void optionsWithoutCorsHeaderIsStillChecked() throws Exception {
        Req req = new Req();
        req.method = "OPTIONS";
        Chain chain = new Chain();
        assertEquals(401, run(filter(null), req, new Resp(), chain));
    }

    @Test
    public void disabledConfigurationLetsEverythingThrough() throws Exception {
        BearerTokenFilter disabled = new BearerTokenFilter(new AuthClientConfig().setEnabled(false));
        Chain chain = new Chain();
        assertEquals(200, run(disabled, new Req(), new Resp(), chain));
        assertEquals(1, chain.calls);
    }

    @Test
    public void filterWithoutConfigurationRefusesInsteadOfOpening() throws Exception {
        BearerTokenFilter unconfigured = new BearerTokenFilter();
        Req req = new Req();
        req.headers.put("Authorization", "Bearer abc");
        Resp resp = new Resp();
        Chain chain = new Chain();
        assertEquals(503, run(unconfigured, req, resp, chain));
        assertEquals(0, chain.calls);
    }
}
