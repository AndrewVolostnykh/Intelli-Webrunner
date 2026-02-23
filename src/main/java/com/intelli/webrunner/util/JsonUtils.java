package com.intelli.webrunner.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String prettyPrint(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            Object parsed = MAPPER.readValue(body, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception ignored) {
            return body;
        }
    }
}
