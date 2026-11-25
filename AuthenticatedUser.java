package com.example.authclient;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The caller of the current request, as established from the bearer token. */
public final class AuthenticatedUser {

    private final String subject;
    private final String userId;
    private final String directory;
    private final Set<String> roles;
    private final Set<String> scopes;

    public AuthenticatedUser(String subject, String userId, String directory, Set<String> roles, Set<String> scopes) {
        this.subject = subject;
        this.userId = userId;
        this.directory = directory;
        this.roles = (roles == null) ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(roles));
        this.scopes = (scopes == null) ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(scopes));
    }

    /** The token subject, "clientId:userId" for users, the client id for services. */
    public String subject() { return this.subject; }

    /** The application's own user id, or null for a service token. */
    public String userId() { return this.userId; }

    /** The application whose directory the user belongs to (for a hand-off user, the source application). */
    public String directory() { return this.directory; }

    public Set<String> roles() { return this.roles; }

    public Set<String> scopes() { return this.scopes; }

    public boolean hasRole(String role) {
        return this.roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String role : candidates) {
            if (this.roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasScope(String scope) {
        return this.scopes.contains(scope);
    }

    /** True for a service token (client_credentials): it has no user. */
    public boolean isService() {
        return this.userId == null;
    }
}
