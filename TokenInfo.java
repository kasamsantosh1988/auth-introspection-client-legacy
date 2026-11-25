package com.example.authclient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the auth server said about a token. {@link #INACTIVE} is the answer for anything not valid for this API.
 * Accessor names (active(), subject(), ...) match the Java 21 edition of this library.
 */
public final class TokenInfo {

    public static final TokenInfo INACTIVE = new TokenInfo(false, null, null, null, null, null, null, null, null);

    private final boolean active;
    private final String subject;
    private final String userId;
    private final String directory;
    private final Set<String> roles;
    private final List<String> scopes;
    private final List<String> audience;
    private final String clientId;
    private final Instant expiresAt;

    public TokenInfo(boolean active, String subject, String userId, String directory, Set<String> roles,
            List<String> scopes, List<String> audience, String clientId, Instant expiresAt) {
        this.active = active;
        this.subject = subject;
        this.userId = userId;
        this.directory = directory;
        this.roles = (roles == null) ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(roles));
        this.scopes = (scopes == null) ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(scopes));
        this.audience = (audience == null) ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(audience));
        this.clientId = clientId;
        this.expiresAt = expiresAt;
    }

    public boolean active() { return this.active; }
    public String subject() { return this.subject; }
    public String userId() { return this.userId; }
    public String directory() { return this.directory; }
    public Set<String> roles() { return this.roles; }
    public List<String> scopes() { return this.scopes; }
    public List<String> audience() { return this.audience; }
    public String clientId() { return this.clientId; }
    public Instant expiresAt() { return this.expiresAt; }
}
