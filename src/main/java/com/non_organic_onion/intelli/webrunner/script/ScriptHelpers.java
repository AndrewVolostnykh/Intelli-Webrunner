package com.non_organic_onion.intelli.webrunner.script;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ScriptHelpers {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Instant RANDOM_START = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant RANDOM_END = Instant.parse("2100-01-01T00:00:00Z");

    private final ScriptLogger logger;

    public ScriptHelpers(ScriptLogger logger) {
        this.logger = logger;
    }

    public void assertValue(Object actual, Object expected, String message) {
        if (isJsonLike(actual) && isJsonLike(expected)) {
            List<String> mismatches = new ArrayList<>();
            collectJsonMismatches("$", actual, expected, mismatches);
            if (mismatches.isEmpty()) {
                return;
            }
            String prefix = "Assertion failed" + (message == null ? "" : ": " + message);
            for (String mismatch : mismatches) {
                logger.log(prefix + " " + mismatch);
            }
            return;
        }

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
        } else if (actual == null) {
            logger.log("Assertion failed" + (message == null ? "" : ": " + message) + " expected " + expected);
        } else {
            logger.log("Assertion failed" + (message == null ? "" : ": " + message) + " expected " + expected + " received " + actual);
        }
    }

    private boolean isJsonLike(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }

    private void collectJsonMismatches(
            String path,
            Object actual,
            Object expected,
            List<String> mismatches
    ) {
        if (actual instanceof Map<?, ?> actualMap && expected instanceof Map<?, ?> expectedMap) {
            for (Object expectedKey : expectedMap.keySet()) {
                String key = String.valueOf(expectedKey);
                if (!actualMap.containsKey(expectedKey)) {
                    mismatches.add(path + "." + key + " expected " + formatAssertValue(expectedMap.get(expectedKey)) + " received <missing>");
                    continue;
                }
                collectJsonMismatches(path + "." + key, actualMap.get(expectedKey), expectedMap.get(expectedKey), mismatches);
            }
            for (Object actualKey : actualMap.keySet()) {
                if (!expectedMap.containsKey(actualKey)) {
                    mismatches.add(path + "." + actualKey + " expected <missing> received " + formatAssertValue(actualMap.get(actualKey)));
                }
            }
            return;
        }

        if (actual instanceof List<?> actualList && expected instanceof List<?> expectedList) {
            int max = Math.max(actualList.size(), expectedList.size());
            for (int i = 0; i < max; i++) {
                if (i >= expectedList.size()) {
                    mismatches.add(path + "[" + i + "] expected <missing> received " + formatAssertValue(actualList.get(i)));
                    continue;
                }
                if (i >= actualList.size()) {
                    mismatches.add(path + "[" + i + "] expected " + formatAssertValue(expectedList.get(i)) + " received <missing>");
                    continue;
                }
                collectJsonMismatches(path + "[" + i + "]", actualList.get(i), expectedList.get(i), mismatches);
            }
            return;
        }

        if (!Objects.equals(actual, expected)) {
            mismatches.add(path + " expected " + formatAssertValue(expected) + " received " + formatAssertValue(actual));
        }
    }

    private String formatAssertValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (Exception ignored) {
            }
        }
        return String.valueOf(value);
    }

    public String uuid() {
        return UUID.randomUUID().toString();
    }

    public String randomString(int size) {
        if (size <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return result.toString();
    }

    public String randomEmail() {
        return randomString(10).toLowerCase() + "@" + randomString(8).toLowerCase() + ".com";
    }

    public String randomIsoDate() {
        return formatIso(randomInstant());
    }

    public String randomRfcDate() {
        return formatRfc(randomInstant());
    }

    public String randomDateTime() {
        return formatDateTime(randomInstant());
    }

    public String randomDate() {
        return formatDate(randomInstant());
    }

    public String randomTime() {
        return formatTime(randomInstant());
    }

    public long randomMillilsDate() {
        return randomInstant().toEpochMilli();
    }

    public long randomEpochSecondsDate() {
        return randomInstant().getEpochSecond();
    }

    public String currentIsoDate() {
        return formatIso(Instant.now());
    }

    public String currentRfcDate() {
        return formatRfc(Instant.now());
    }

    public String currentDateTime() {
        return formatDateTime(Instant.now());
    }

    public String currentDate() {
        return formatDate(Instant.now());
    }

    public String currentTime() {
        return formatTime(Instant.now());
    }

    public long currentMillilsDate() {
        return Instant.now().toEpochMilli();
    }

    public long currentEpochSecondsDate() {
        return Instant.now().getEpochSecond();
    }

    public int randomNumber(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from must be less than or equal to to");
        }
        return ThreadLocalRandom.current().nextInt(from, to + 1);
    }

    public String randomDouble(double from, double to) {
        return randomDouble(from, to, 10);
    }

    public String randomDouble(double from, double to, int afterComma) {
        if (from > to) {
            throw new IllegalArgumentException("from must be less than or equal to to");
        }
        if (afterComma < 0) {
            throw new IllegalArgumentException("afterComma must be greater than or equal to 0");
        }
        double value = from == to ? from : ThreadLocalRandom.current().nextDouble(from, to);
        return BigDecimal.valueOf(value)
                .setScale(afterComma, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private Instant randomInstant() {
        long from = RANDOM_START.toEpochMilli();
        long to = RANDOM_END.toEpochMilli();
        return Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(from, to));
    }

    private String formatIso(Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(atUtc(instant));
    }

    private String formatRfc(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(atUtc(instant));
    }

    private String formatDateTime(Instant instant) {
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private String formatDate(Instant instant) {
        return DATE_FORMATTER.format(LocalDate.ofInstant(instant, ZoneOffset.UTC));
    }

    private String formatTime(Instant instant) {
        return TIME_FORMATTER.format(LocalTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private ZonedDateTime atUtc(Instant instant) {
        return instant.atZone(ZoneOffset.UTC);
    }
}
