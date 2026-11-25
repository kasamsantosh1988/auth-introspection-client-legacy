package com.example.authclient;

/** The auth server could not be reached or answered with something unusable. The request must be refused (fail closed). */
public class AuthServerUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
