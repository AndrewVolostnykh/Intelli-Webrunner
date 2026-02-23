package com.intelli.webrunner.script;

import java.util.Objects;
import java.util.UUID;

public class ScriptHelpers {
    private final ScriptLogger logger;

    public ScriptHelpers(ScriptLogger logger) {
        this.logger = logger;
    }

    public void assertValue(Object actual, Object expected, String message) {
        boolean match;
        if (expected == null) {
            match = actual != null && !(actual instanceof Boolean && Objects.equals(actual, Boolean.FALSE));
        } else {
            match = Objects.equals(actual, expected);
        }
        if (match) {
            return;
        }
        if (expected == null) {
            logger.log("Assertion failed" + (message == null ? "" : ": " + message));
        } else {
            logger.log("Assertion failed" + (message == null ? "" : ": " + message) + " expected " + expected + " received " + actual);
        }
    }

    public String uuid() {
        return UUID.randomUUID().toString();
    }
}
