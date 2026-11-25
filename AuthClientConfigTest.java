package com.example.authclient;

import static org.junit.Assert.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class AuthClientConfigTest {

    private static AuthClientConfig of(final Map<String, String> values) {
        return AuthClientConfig.from(new AuthClientConfig.Source() {
            @Override
            public String get(String name) {
                return values.get(name);
            }
        });
    }

    @Test
    public void defaultsMatchTheJava21Edition() {
        AuthClientConfig c = new AuthClientConfig();
        assertEquals(Duration.ofSeconds(2), c.getConnectTimeout());
        assertEquals(Duration.ofSeconds(3), c.getReadTimeout());
        assertEquals(Duration.ofSeconds(60), c.getMaxCacheTtl());
        assertEquals(Duration.ofSeconds(5), c.getNegativeCacheTtl());
        assertEquals(10_000, c.getMaxCacheEntries());
        assertEquals(java.util.Arrays.asList("/actuator/health/**", "/error"), c.getExcludePaths());
        assertTrue(c.isEnabled());
    }

    @Test
    public void readsEverySetting() {
        Map<String, String> v = new HashMap<String, String>();
        v.put("introspection-uri", "https://auth/oauth2/introspect");
        v.put("client-id", "ekyc-api");
        v.put("client-secret", "s3cret");
        v.put("required-audience", "ekyc-api");
        v.put("connect-timeout", "500ms");
        v.put("read-timeout", "4s");
        v.put("max-cache-ttl", "2m");
        v.put("negative-cache-ttl", "PT10S");
        v.put("max-cache-entries", "50");
        v.put("exclude-paths", " /health/** , /ping ");
        AuthClientConfig c = of(v).validate();
        assertEquals("ekyc-api", c.getClientId());
        assertEquals("ekyc-api", c.getRequiredAudience());
        assertEquals(Duration.ofMillis(500), c.getConnectTimeout());
        assertEquals(Duration.ofSeconds(4), c.getReadTimeout());
        assertEquals(Duration.ofMinutes(2), c.getMaxCacheTtl());
        assertEquals(Duration.ofSeconds(10), c.getNegativeCacheTtl());
        assertEquals(50, c.getMaxCacheEntries());
        assertEquals(java.util.Arrays.asList("/health/**", "/ping"), c.getExcludePaths());
    }

    @Test
    public void requiredSettingsMustBePresent() {
        Map<String, String> v = new HashMap<String, String>();
        v.put("introspection-uri", "https://auth/oauth2/introspect");
        v.put("client-id", "api");
        try {
            of(v).validate();
            fail();
        }
        catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("client-secret"));
        }
    }

    @Test
    public void disabledNeedsNothing() {
        Map<String, String> v = new HashMap<String, String>();
        v.put("enabled", "false");
        assertFalse(of(v).validate().isEnabled());
    }

    @Test
    public void badDurationIsRefused() {
        try {
            AuthClientConfig.parseDuration("soon");
            fail();
        }
        catch (IllegalStateException expected) {
            // ok
        }
    }
}
