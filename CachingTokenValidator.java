package com.example.authclient;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers what the auth server said about a token, so the same token is checked with the auth server once, not on
 * every request. A positive answer is reused until the token's expiry or maxCacheTtl, whichever is sooner; a negative
 * answer for negativeCacheTtl. The cache is keyed by a hash of the token (the token itself is never kept as a key).
 * If the auth server is unreachable and the token is not cached, the exception propagates (fail closed).
 */
public class CachingTokenValidator {

    private static final class Entry {
        final TokenInfo info;
        final Instant validUntil;

        Entry(TokenInfo info, Instant validUntil) {
            this.info = info;
            this.validUntil = validUntil;
        }
    }

    private final TokenIntrospector introspector;
    private final Clock clock;
    private final Duration maxTtl;
    private final Duration negativeTtl;
    private final int maxEntries;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<String, Entry>();

    public CachingTokenValidator(TokenIntrospector introspector, Clock clock, Duration maxTtl, Duration negativeTtl,
            int maxEntries) {
        this.introspector = introspector;
        this.clock = clock;
        this.maxTtl = maxTtl;
        this.negativeTtl = negativeTtl;
        this.maxEntries = maxEntries;
    }

    public TokenInfo validate(String token) {
        String key = sha256(token);
        Instant now = this.clock.instant();
        Entry cached = this.cache.get(key);
        if (cached != null && now.isBefore(cached.validUntil)) {
            return cached.info;
        }

        TokenInfo info = this.introspector.introspect(token);
        Instant until;
        if (info.active()) {
            until = now.plus(this.maxTtl);
            if (info.expiresAt() != null && info.expiresAt().isBefore(until)) {
                until = info.expiresAt();
            }
        }
        else {
            until = now.plus(this.negativeTtl);
        }
        store(key, new Entry(info, until), now);
        // A token whose expiry passed between caching and now must not be served.
        return (info.active() && info.expiresAt() != null && !now.isBefore(info.expiresAt())) ? TokenInfo.INACTIVE : info;
    }

    int size() {
        return this.cache.size();
    }

    private void store(String key, Entry entry, Instant now) {
        if (this.cache.size() >= this.maxEntries) {
            for (Iterator<Map.Entry<String, Entry>> it = this.cache.entrySet().iterator(); it.hasNext();) {
                if (!now.isBefore(it.next().getValue().validUntil)) {
                    it.remove();
                }
            }
            if (this.cache.size() >= this.maxEntries) {
                this.cache.clear();      // simple and safe: the next requests just ask the auth server again
            }
        }
        this.cache.put(key, entry);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
