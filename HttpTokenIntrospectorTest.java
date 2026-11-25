package com.example.authclient;

import static org.junit.Assert.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpTokenIntrospectorTest {

    private HttpServer server;
    private int status = 200;
    private String body = "{\"active\":false}";
    private int delayMillis = 0;
    private final AtomicReference<String> authorization = new AtomicReference<String>();
    private final AtomicReference<String> requestBody = new AtomicReference<String>();
    private final AtomicReference<String> contentType = new AtomicReference<String>();

    @Before
    public void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/oauth2/introspect", new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                authorization.set(ex.getRequestHeaders().getFirst("Authorization"));
                contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
                InputStream in = ex.getRequestBody();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[512];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                requestBody.set(new String(buf.toByteArray(), "UTF-8"));
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    }
                    catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] out = body.getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(status, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            }
        });
        this.server.start();
    }

    @After
    public void stop() {
        this.server.stop(0);
    }

    private HttpTokenIntrospector introspector() {
        return new HttpTokenIntrospector(new AuthClientConfig()
            .setIntrospectionUri("http://127.0.0.1:" + this.server.getAddress().getPort() + "/oauth2/introspect")
            .setClientId("demo-api").setClientSecret("p@ss w+rd%")
            .setConnectTimeout(Duration.ofSeconds(1)).setReadTimeout(Duration.ofMillis(500)));
    }

    @Test
    public void mapsAnActiveUserToken() {
        this.body = "{\"active\":true,\"sub\":\"demo-ui:1001\",\"uid\":\"1001\",\"dir\":\"demo-ui\",\"aud\":[\"demo-api\"],"
                + "\"roles\":[\"USER\"],\"scope\":\"app extra\",\"client_id\":\"demo-ui\",\"exp\":1761001800}";
        TokenInfo info = introspector().introspect("abc");
        assertTrue(info.active());
        assertEquals("demo-ui:1001", info.subject());
        assertEquals("1001", info.userId());
        assertEquals("demo-ui", info.directory());
        assertEquals(java.util.Arrays.asList("demo-api"), info.audience());
        assertTrue(info.roles().contains("USER"));
        assertEquals(java.util.Arrays.asList("app", "extra"), info.scopes());
        assertEquals("demo-ui", info.clientId());
        assertEquals(Instant.ofEpochSecond(1761001800L), info.expiresAt());
    }

    @Test
    public void mapsAServiceTokenWithoutUser() {
        this.body = "{\"active\":true,\"sub\":\"svc\",\"aud\":\"demo-api\",\"scope\":\"x\",\"client_id\":\"svc\",\"exp\":1761001800}";
        TokenInfo info = introspector().introspect("abc");
        assertTrue(info.active());
        assertNull(info.userId());
        assertEquals(java.util.Arrays.asList("demo-api"), info.audience());
        assertTrue(info.roles().isEmpty());
    }

    @Test
    public void inactiveAnswer() {
        this.body = "{\"active\":false}";
        assertFalse(introspector().introspect("abc").active());
    }

    @Test
    public void sendsFormEncodedTokenAndEncodedBasicCredentials() {
        introspector().introspect("a+b/c=");
        assertEquals("token=a%2Bb%2Fc%3D", this.requestBody.get());
        assertEquals("application/x-www-form-urlencoded", this.contentType.get());
        String decoded = new String(Base64.getDecoder().decode(this.authorization.get().substring("Basic ".length())));
        assertEquals("demo-api:p%40ss+w%2Brd%25", decoded);
    }

    @Test
    public void anyNon200IsUnavailableNeverInactive() {
        int[] codes = {400, 401, 403, 404, 500, 503};
        for (int code : codes) {
            this.status = code;
            try {
                introspector().introspect("abc");
                fail("HTTP " + code);
            }
            catch (AuthServerUnavailableException expected) {
                // ok
            }
        }
    }

    @Test
    public void garbageAnswerIsUnavailable() {
        this.body = "<html>oops</html>";
        try {
            introspector().introspect("abc");
            fail();
        }
        catch (AuthServerUnavailableException expected) {
            // ok
        }
    }

    @Test
    public void timeoutIsUnavailable() {
        this.delayMillis = 1500;
        try {
            introspector().introspect("abc");
            fail();
        }
        catch (AuthServerUnavailableException expected) {
            // ok
        }
    }

    @Test
    public void unreachableServerIsUnavailable() throws IOException {
        java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        int port = probe.getLocalPort();
        probe.close();
        HttpTokenIntrospector gone = new HttpTokenIntrospector(new AuthClientConfig()
            .setIntrospectionUri("http://127.0.0.1:" + port + "/oauth2/introspect").setClientId("a").setClientSecret("b"));
        try {
            gone.introspect("abc");
            fail();
        }
        catch (AuthServerUnavailableException expected) {
            // ok
        }
    }
}
