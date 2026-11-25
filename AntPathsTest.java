package com.example.authclient;

import static org.junit.Assert.*;

import org.junit.Test;

public class AntPathsTest {

    @Test
    public void matches() {
        assertTrue(AntPaths.match("/actuator/health/**", "/actuator/health"));
        assertTrue(AntPaths.match("/actuator/health/**", "/actuator/health/liveness"));
        assertTrue(AntPaths.match("/actuator/health/**", "/actuator/health/a/b"));
        assertTrue(AntPaths.match("/error", "/error"));
        assertTrue(AntPaths.match("/public/*", "/public/x"));
        assertTrue(AntPaths.match("/p?blic", "/public"));
        assertTrue(AntPaths.match("/**/ping", "/a/b/ping"));
        assertTrue(AntPaths.match("/static/*.css", "/static/site.css"));
    }

    @Test
    public void doesNotMatch() {
        assertFalse(AntPaths.match("/actuator/health/**", "/actuator/info"));
        assertFalse(AntPaths.match("/actuator/health/**", "/actuator/healthy"));
        assertFalse(AntPaths.match("/error", "/error/x"));
        assertFalse(AntPaths.match("/public/*", "/public/a/b"));
        assertFalse(AntPaths.match("/public/*", "/public"));
        assertFalse(AntPaths.match("/static/*.css", "/static/site.js"));
        assertFalse(AntPaths.match("/orders", "/"));
    }
}
