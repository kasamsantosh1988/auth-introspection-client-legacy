package com.example.authclient;

/** Access to the caller of the current request from anywhere on the request thread. */
public final class CurrentUser {

    static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<AuthenticatedUser>();

    private CurrentUser() {
    }

    /** The caller, or null when the request was not authenticated (excluded path). */
    public static AuthenticatedUser get() {
        return HOLDER.get();
    }

    /** The caller, or an IllegalStateException when the request was not authenticated (excluded path). */
    public static AuthenticatedUser require() {
        AuthenticatedUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("No authenticated user on this request");
        }
        return user;
    }

    static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }
}
