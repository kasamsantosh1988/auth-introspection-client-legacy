# auth-introspection-client-legacy

Bearer-token validation for **Java 8** APIs on **`javax.servlet`** (Servlet 3.0+), using the Auth Server's introspection endpoint.
Same behaviour as the Java 21 edition (`auth-introspection-client`), with **no Spring and no runtime dependency** except the servlet API your server already provides.

Use this edition when the API cannot use the Java 21 library: it runs on Java 8 or newer, in Tomcat 7+ / WebLogic / JBoss / WebSphere style containers, and in old Spring or Spring Boot 1.x/2.x applications.
**Never put both editions on the same classpath**: they share the package `com.example.authclient`, so moving an application to Java 21 later means replacing the dependency only.

## What it does

| Request | Result |
|---|---|
| No or malformed `Authorization: Bearer ...` | `401`, `WWW-Authenticate: Bearer error="invalid_request"` |
| Token inactive, expired, revoked, not meant for this API | `401`, `error="invalid_token"` |
| Auth Server unreachable (and token not cached) | `503` with `Retry-After: 5` (fails closed) |
| Valid token | The request continues. `CurrentUser` holds the caller on the request thread. |
| Excluded path (`/actuator/health/**`, `/error` by default) or CORS preflight | Not checked |
| Path with `..`, `;`, `%`, `\`, `//` or `/./` | Never treated as excluded: the token is required |

Caching: a positive answer is reused for at most `max-cache-ttl` (60 s) and never past the token's own expiry; a negative answer for 5 s. The cache key is a SHA-256 of the token. A logout or revoke therefore reaches this API after at most `max-cache-ttl`.

## Install

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>auth-introspection-client-legacy</artifactId>
  <version>1.0.0</version>
</dependency>
```

Publish the jar to your internal Maven feed (Azure Artifacts) from the pipeline: `mvn deploy`, or `./build.sh` if Maven cannot download its plugins.

## Settings

| Setting | Meaning | Default |
|---|---|---|
| `introspection-uri` | `https://<auth server>/oauth2/introspect` (**required**) | |
| `client-id` | The API's own client id from the resource-server registration (**required**) | |
| `client-secret` | The API's secret (**required**) | |
| `required-audience` | Optional second check of the token's `aud` | none |
| `connect-timeout` / `read-timeout` | `500ms`, `2s`, `1m`, `PT3S` | `2s` / `3s` |
| `max-cache-ttl` / `negative-cache-ttl` | | `60s` / `5s` |
| `max-cache-entries` | | `10000` |
| `exclude-paths` | Comma separated Ant patterns | `/actuator/health/**,/error` |
| `enabled` | `false` only for tests | `true` |

Each value is read from, in this order: the JVM system property `auth.client.<name>`, the environment variable `AUTH_CLIENT_<NAME>` (upper case, `-` and `.` become `_`, for example `AUTH_CLIENT_CLIENT_SECRET`), then the filter `init-param` `<name>`.
**Put the secret in the environment or a system property, never in `web.xml` or the repository.**
A missing required setting stops the filter at startup with a clear message.

## Register the filter

**web.xml** (works everywhere). Put it **first** among the filters:

```xml
<filter>
  <filter-name>bearerToken</filter-name>
  <filter-class>com.example.authclient.BearerTokenFilter</filter-class>
  <init-param><param-name>introspection-uri</param-name><param-value>https://auth.example.com/oauth2/introspect</param-value></init-param>
  <init-param><param-name>client-id</param-name><param-value>ekyc-api</param-value></init-param>
  <init-param><param-name>required-audience</param-name><param-value>ekyc-api</param-value></init-param>
  <!-- client-secret comes from the environment: AUTH_CLIENT_CLIENT_SECRET -->
</filter>
<filter-mapping>
  <filter-name>bearerToken</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

**Spring Boot 1.x or 2.x** (still `javax.servlet`):

```java
@Bean
public FilterRegistrationBean<BearerTokenFilter> bearerTokenFilter(
        @Value("${auth.client.introspection-uri}") String uri,
        @Value("${auth.client.client-id}") String clientId,
        @Value("${auth.client.client-secret}") String secret) {
    AuthClientConfig config = new AuthClientConfig()
            .setIntrospectionUri(uri).setClientId(clientId).setClientSecret(secret)
            .setRequiredAudience("ekyc-api");
    FilterRegistrationBean<BearerTokenFilter> bean = new FilterRegistrationBean<>(new BearerTokenFilter(config));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    bean.addUrlPatterns("/*");
    return bean;
}
```

(`FilterRegistrationBean<>` is generic from Spring Boot 2; in 1.x use the raw type.)

## Use the caller in your code

```java
AuthenticatedUser user = CurrentUser.require();      // IllegalStateException on an excluded path
String userId = user.userId();                       // the application's own user id; null for a service token
if (user.isService()) { /* client_credentials token: check user.hasScope("ekyc.internal") */ }
else if (user.hasRole("ADMIN")) { ... }
user.directory();                                    // application whose users these are (for a hand-off: the source application)
```

`CurrentUser.get()` returns `null` when there is no caller (the Java 21 edition returns an `Optional`; the only API difference).
The token tells you **who** the caller is. Whether this user may see **this record** is still decided in your API.

## Build and test

```bash
mvn verify                  # needs Maven Central (junit, servlet API)
# or, with the jars already on disk:
SERVLET_JAR=... JUNIT_JAR=... HAMCREST_JAR=... ./build.sh
```

`maven.compiler.release=8` makes the compiler reject any API newer than Java 8; the class files are version 52. 39 unit tests cover the cache rules, the HTTP call (against a local test server), the filter responses, the settings, and the path tricks.

## Java 8 checklist

- The JVM trust store must contain the CA of the Auth Server's certificate (old JVMs often lack internal CAs).
- Use a recent Java 8 update (TLS 1.2 to the Auth Server).
- Run the filter before any other filter that touches the request.
- The introspection call blocks the request thread for at most `connect-timeout + read-timeout` on a cache miss.
- A proxy: set the standard `https.proxyHost` and `https.proxyPort` system properties.
