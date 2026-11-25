package com.example.authclient;

/** Asks the auth server about one token. */
public interface TokenIntrospector {

    /** Returns {@link TokenInfo#INACTIVE} for unknown/expired/revoked tokens. Throws if the auth server cannot answer. */
    TokenInfo introspect(String token);
}
