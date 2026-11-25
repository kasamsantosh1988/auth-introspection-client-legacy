package com.example.authclient;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public class MiniJsonTest {

    @Test
    public void readsTheIntrospectionShape() {
        Map<String, Object> m = MiniJson.parseObject("{\"active\":true,\"sub\":\"demo-ui:1001\",\"aud\":[\"demo-api\"],"
                + "\"roles\":[\"USER\",\"ADMIN\"],\"exp\":1761001800,\"scope\":\"app auth.x\",\"n\":null,\"f\":1.5}");
        assertEquals(Boolean.TRUE, m.get("active"));
        assertEquals("demo-ui:1001", m.get("sub"));
        assertEquals(Arrays.<Object>asList("demo-api"), m.get("aud"));
        assertEquals(Arrays.<Object>asList("USER", "ADMIN"), m.get("roles"));
        assertEquals(Long.valueOf(1761001800L), m.get("exp"));
        assertNull(m.get("n"));
        assertEquals(Double.valueOf(1.5), m.get("f"));
    }

    @Test
    public void readsEscapes() {
        Map<String, Object> m = MiniJson.parseObject("{\"a\":\"x\\\"y\\\\z\\n\\u00e9\"}");
        assertEquals("x\"y\\z\né", m.get("a"));
    }

    @Test
    public void readsEmptyContainersAndWhitespace() {
        Map<String, Object> m = MiniJson.parseObject(" { \"a\" : [ ] , \"b\" : { } } ");
        assertTrue(((java.util.List<?>) m.get("a")).isEmpty());
        assertTrue(((Map<?, ?>) m.get("b")).isEmpty());
    }

    @Test
    public void refusesBadInput() {
        String[] bad = {"", "[]", "{", "{\"a\":}", "{\"a\":1,}", "{\"a\":tru}", "{\"a\":\"x}", "{\"a\":1} x", "nul", "{a:1}"};
        for (String json : bad) {
            try {
                MiniJson.parseObject(json);
                fail("should refuse: " + json);
            }
            catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void refusesDeepNesting() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("{\"a\":");
        }
        sb.append("1");
        for (int i = 0; i < 50; i++) {
            sb.append("}");
        }
        try {
            MiniJson.parseObject(sb.toString());
            fail();
        }
        catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
