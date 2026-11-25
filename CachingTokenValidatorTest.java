package com.example.authclient;

import static org.junit.Assert.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class CachingTokenValidatorTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return this.now; }
    }

    private static final class Fake implements TokenIntrospector {
        final AtomicInteger calls = new AtomicInteger();
        TokenInfo answer;
        RuntimeException failure;

        @Override
        public TokenInfo introspect(String token) {
            this.calls.incrementAndGet();
            if (this.failure != null) {
                throw this.failure;
            }
            return this.answer;
        }
    }

    private static TokenInfo active(Instant exp) {
        return new TokenInfo(true, "app:1", "1", "app", Collections.singleton("USER"), Collections.<String>emptyList(),
                Collections.singletonList("api"), "app", exp);
    }

    private final MutableClock clock = new MutableClock();
    private final Fake fake = new Fake();

    private CachingTokenValidator validator(int maxEntries) {
        return new CachingTokenValidator(this.fake, this.clock, Duration.ofSeconds(60), Duration.ofSeconds(5), maxEntries);
    }

    @Test
    public void positiveAnswerIsReusedForMaxTtl() {
        this.fake.answer = active(this.clock.now.plusSeconds(1800));
        CachingTokenValidator v = validator(100);
        assertTrue(v.validate("t").active());
        this.clock.now = this.clock.now.plusSeconds(59);
        assertTrue(v.validate("t").active());
        assertEquals(1, this.fake.calls.get());
        this.clock.now = this.clock.now.plusSeconds(2);
        v.validate("t");
        assertEquals(2, this.fake.calls.get());
    }

    @Test
    public void neverKeptPastTheTokensOwnExpiry() {
        this.fake.answer = active(this.clock.now.plusSeconds(10));
        CachingTokenValidator v = validator(100);
        assertTrue(v.validate("t").active());
        this.clock.now = this.clock.now.plusSeconds(11);
        this.fake.answer = TokenInfo.INACTIVE;
        assertFalse(v.validate("t").active());
        assertEquals(2, this.fake.calls.get());
    }

    @Test
    public void expiredTokenIsNotServedEvenIfAuthServerSaidActive() {
        this.fake.answer = active(this.clock.now.minusSeconds(1));
        assertFalse(validator(100).validate("t").active());
    }

    @Test
    public void negativeAnswerIsRememberedBriefly() {
        this.fake.answer = TokenInfo.INACTIVE;
        CachingTokenValidator v = validator(100);
        assertFalse(v.validate("bad").active());
        assertFalse(v.validate("bad").active());
        assertEquals(1, this.fake.calls.get());
        this.clock.now = this.clock.now.plusSeconds(6);
        v.validate("bad");
        assertEquals(2, this.fake.calls.get());
    }

    @Test
    public void failsClosedAndDoesNotCacheFailures() {
        this.fake.failure = new AuthServerUnavailableException("down", null);
        CachingTokenValidator v = validator(100);
        for (int i = 0; i < 2; i++) {
            try {
                v.validate("t");
                fail();
            }
            catch (AuthServerUnavailableException expected) {
                // ok
            }
        }
        assertEquals(2, this.fake.calls.get());
        assertEquals(0, v.size());
    }

    @Test
    public void cacheSizeIsBounded() {
        this.fake.answer = active(this.clock.now.plusSeconds(1800));
        CachingTokenValidator v = validator(3);
        for (int i = 0; i < 10; i++) {
            v.validate("token-" + i);
            assertTrue(v.size() <= 3);
        }
    }
}
