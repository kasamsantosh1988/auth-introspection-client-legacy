package com.example.authclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** POST /oauth2/introspect with the API's client credentials (RFC 7662), using only the JDK. */
public class HttpTokenIntrospector implements TokenIntrospector {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final String uri;
    private final String basicAuth;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HttpTokenIntrospector(AuthClientConfig config) {
        this.uri = config.getIntrospectionUri();
        // RFC 6749 section 2.3.1: id and secret are form-encoded, then Base64. The auth server decodes them the same way.
        this.basicAuth = "Basic " + Base64.getEncoder()
            .encodeToString((formEncode(config.getClientId()) + ":" + formEncode(config.getClientSecret())).getBytes(UTF_8));
        this.connectTimeoutMillis = (int) config.getConnectTimeout().toMillis();
        this.readTimeoutMillis = (int) config.getReadTimeout().toMillis();
    }

    @Override
    public TokenInfo introspect(String token) {
        Map<String, Object> body;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(this.uri).openConnection();
            connection.setInstanceFollowRedirects(false);       // never send the credentials anywhere else
            connection.setConnectTimeout(this.connectTimeoutMillis);
            connection.setReadTimeout(this.readTimeoutMillis);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", this.basicAuth);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");
            byte[] form = ("token=" + formEncode(token)).getBytes(UTF_8);
            connection.setFixedLengthStreamingMode(form.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(form);
            }
            finally {
                out.close();
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                // Includes 401 (wrong API credentials) and 5xx: none of them mean "token invalid".
                throw new AuthServerUnavailableException("Introspection call failed: HTTP " + status, null);
            }
            body = MiniJson.parseObject(new String(readLimited(connection.getInputStream()), UTF_8));
        }
        catch (AuthServerUnavailableException ex) {
            throw ex;
        }
        catch (IOException ex) {
            throw new AuthServerUnavailableException("Introspection call failed: " + ex.getClass().getSimpleName(), ex);
        }
        catch (RuntimeException ex) {
            throw new AuthServerUnavailableException("Introspection answer not usable: " + ex.getClass().getSimpleName(), ex);
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return toInfo(body);
    }

    static TokenInfo toInfo(Map<String, Object> body) {
        if (!Boolean.TRUE.equals(body.get("active"))) {
            return TokenInfo.INACTIVE;
        }
        Object scope = body.get("scope");
        List<String> scopes = (scope instanceof String) ? splitOnSpaces((String) scope) : strings(scope);
        return new TokenInfo(true, text(body.get("sub")), text(body.get("uid")), text(body.get("dir")),
                new LinkedHashSet<String>(strings(body.get("roles"))), scopes, strings(body.get("aud")),
                text(body.get("client_id")), instant(body.get("exp")));
    }

    private static List<String> splitOnSpaces(String value) {
        List<String> out = new ArrayList<String>();
        for (String part : value.split(" ")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_RESPONSE_BYTES) {
                    throw new IOException("Introspection answer too large");
                }
            }
            return buffer.toByteArray();
        }
        finally {
            in.close();
        }
    }

    private static String formEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(Object value) {
        return (value == null) ? null : value.toString();
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<String>();
        if (value == null) {
            return out;
        }
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        out.add(value.toString());
        return out;
    }

    private static Instant instant(Object value) {
        if (value instanceof Number) {
            return Instant.ofEpochSecond(((Number) value).longValue());
        }
        if (value instanceof String) {
            try {
                return Instant.ofEpochSecond(Long.parseLong((String) value));
            }
            catch (NumberFormatException ex) {
                return Instant.parse((String) value);
            }
        }
        return null;
    }
}
