package com.example.authclient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.servlet.FilterConfig;

/**
 * Settings of the API that validates bearer tokens. Same names as the Java 21 edition (prefix "auth.client."):
 * <pre>
 *   introspection-uri   e.g. https://auth.example.com/oauth2/introspect          (required)
 *   client-id           the API's own client id (registered as a resource server)  (required)
 *   client-secret       the API's secret                                           (required)
 *   required-audience   optional second check of the token's "aud"
 *   connect-timeout     default 2s      read-timeout   default 3s
 *   max-cache-ttl       default 60s     negative-cache-ttl   default 5s     max-cache-entries   default 10000
 *   exclude-paths       comma separated Ant patterns, default /actuator/health/**,/error
 *   enabled             default true (false only for tests)
 * </pre>
 * Each value is looked up in this order: JVM system property "auth.client.&lt;name&gt;", environment variable
 * "AUTH_CLIENT_&lt;NAME&gt;" (dots and dashes become underscores, upper case), then the filter init-param "&lt;name&gt;".
 * Put the secret in the environment or a system property, not in web.xml. Durations: "500ms", "3s", "2m", "1h" or ISO "PT30S".
 */
public final class AuthClientConfig {

    /** Where a setting can come from. */
    public interface Source {
        /** The value for a short name such as "introspection-uri", or null. */
        String get(String name);
    }

    private boolean enabled = true;
    private String introspectionUri;
    private String clientId;
    private String clientSecret;
    private String requiredAudience;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Duration maxCacheTtl = Duration.ofSeconds(60);
    private Duration negativeCacheTtl = Duration.ofSeconds(5);
    private int maxCacheEntries = 10_000;
    private List<String> excludePaths = new ArrayList<String>(Arrays.asList("/actuator/health/**", "/error"));

    public AuthClientConfig() {
    }

    /** System property, then environment variable, then the filter's init-params. */
    public static AuthClientConfig load(final FilterConfig filterConfig) {
        return from(new Source() {
            @Override
            public String get(String name) {
                String value = System.getProperty("auth.client." + name);
                if (isBlank(value)) {
                    value = System.getenv("AUTH_CLIENT_" + name.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT));
                }
                if (isBlank(value) && filterConfig != null) {
                    value = filterConfig.getInitParameter(name);
                }
                return value;
            }
        });
    }

    public static AuthClientConfig from(Source source) {
        AuthClientConfig c = new AuthClientConfig();
        String v;
        if ((v = value(source, "enabled")) != null) {
            c.enabled = Boolean.parseBoolean(v);
        }
        c.introspectionUri = value(source, "introspection-uri");
        c.clientId = value(source, "client-id");
        c.clientSecret = value(source, "client-secret");
        c.requiredAudience = value(source, "required-audience");
        if ((v = value(source, "connect-timeout")) != null) {
            c.connectTimeout = parseDuration(v);
        }
        if ((v = value(source, "read-timeout")) != null) {
            c.readTimeout = parseDuration(v);
        }
        if ((v = value(source, "max-cache-ttl")) != null) {
            c.maxCacheTtl = parseDuration(v);
        }
        if ((v = value(source, "negative-cache-ttl")) != null) {
            c.negativeCacheTtl = parseDuration(v);
        }
        if ((v = value(source, "max-cache-entries")) != null) {
            c.maxCacheEntries = Integer.parseInt(v);
        }
        if ((v = value(source, "exclude-paths")) != null) {
            List<String> paths = new ArrayList<String>();
            for (String part : v.split(",")) {
                if (!part.trim().isEmpty()) {
                    paths.add(part.trim());
                }
            }
            c.excludePaths = paths;
        }
        return c;
    }

    /** Fails at startup, with a clear message, when something required is missing or unsafe. */
    public AuthClientConfig validate() {
        if (!this.enabled) {
            return this;
        }
        require(this.introspectionUri, "introspection-uri");
        require(this.clientId, "client-id");
        require(this.clientSecret, "client-secret");
        if (this.connectTimeout.isNegative() || this.connectTimeout.isZero() || this.readTimeout.isNegative()
                || this.readTimeout.isZero()) {
            throw new IllegalStateException("auth.client timeouts must be positive");
        }
        if (this.maxCacheTtl.isNegative() || this.negativeCacheTtl.isNegative() || this.maxCacheEntries < 1) {
            throw new IllegalStateException("auth.client cache settings must not be negative");
        }
        return this;
    }

    static Duration parseDuration(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.startsWith("p")) {
                return Duration.parse(text.trim());
            }
            if (t.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(t.substring(0, t.length() - 2).trim()));
            }
            if (t.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(t.substring(0, t.length() - 1).trim()));
            }
            if (t.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(t.substring(0, t.length() - 1).trim()));
            }
            if (t.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(t.substring(0, t.length() - 1).trim()));
            }
            return Duration.ofSeconds(Long.parseLong(t));
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("Cannot read duration '" + text + "' (use 500ms, 3s, 2m, 1h or PT30S)");
        }
    }

    private static String value(Source source, String name) {
        String v = source.get(name);
        return isBlank(v) ? null : v.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void require(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalStateException("auth.client." + name + " is required");
        }
    }

    public boolean isEnabled() { return this.enabled; }
    public String getIntrospectionUri() { return this.introspectionUri; }
    public String getClientId() { return this.clientId; }
    public String getClientSecret() { return this.clientSecret; }
    public String getRequiredAudience() { return this.requiredAudience; }
    public Duration getConnectTimeout() { return this.connectTimeout; }
    public Duration getReadTimeout() { return this.readTimeout; }
    public Duration getMaxCacheTtl() { return this.maxCacheTtl; }
    public Duration getNegativeCacheTtl() { return this.negativeCacheTtl; }
    public int getMaxCacheEntries() { return this.maxCacheEntries; }
    public List<String> getExcludePaths() { return this.excludePaths; }

    public AuthClientConfig setEnabled(boolean v) { this.enabled = v; return this; }
    public AuthClientConfig setIntrospectionUri(String v) { this.introspectionUri = v; return this; }
    public AuthClientConfig setClientId(String v) { this.clientId = v; return this; }
    public AuthClientConfig setClientSecret(String v) { this.clientSecret = v; return this; }
    public AuthClientConfig setRequiredAudience(String v) { this.requiredAudience = v; return this; }
    public AuthClientConfig setConnectTimeout(Duration v) { this.connectTimeout = v; return this; }
    public AuthClientConfig setReadTimeout(Duration v) { this.readTimeout = v; return this; }
    public AuthClientConfig setMaxCacheTtl(Duration v) { this.maxCacheTtl = v; return this; }
    public AuthClientConfig setNegativeCacheTtl(Duration v) { this.negativeCacheTtl = v; return this; }
    public AuthClientConfig setMaxCacheEntries(int v) { this.maxCacheEntries = v; return this; }
    public AuthClientConfig setExcludePaths(List<String> v) { this.excludePaths = v; return this; }
}
