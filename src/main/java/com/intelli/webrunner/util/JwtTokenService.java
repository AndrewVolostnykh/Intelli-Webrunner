package com.intelli.webrunner.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Stateless JWT decoding, expiry inspection, and HMAC signing helpers. */
public final class JwtTokenService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtTokenService() {
    }

    public static String decode(String value) {
        String token = normalize(value);
        if (token.isBlank()) {
            return "";
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length < 2) {
            return "Invalid JWT: expected at least header and payload parts.";
        }
        try {
            var decoded = MAPPER.createObjectNode();
            decoded.set("header", decodePart(parts[0]));
            decoded.set("payload", decodePart(parts[1]));
            decoded.put("signature", parts.length > 2 ? parts[2] : "");
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(decoded);
        } catch (Exception e) {
            return "Invalid JWT: " + e.getMessage();
        }
    }

    public static String expiryStatus(String value) {
        try {
            String[] parts = normalize(value).split("\\.", -1);
            if (parts.length < 2) {
                return "Not exp field";
            }
            JsonNode exp = decodePart(parts[1]).get("exp");
            if (exp == null || !exp.canConvertToLong()) {
                return "Not exp field";
            }
            return exp.asLong() <= Instant.now().getEpochSecond() ? "Expired" : "Not Expired";
        } catch (Exception ignored) {
            return "Not exp field";
        }
    }

    public static String update(String decodedToken, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret is required.");
        }
        JsonNode root = MAPPER.readTree(decodedToken);
        JsonNode header = root.get("header");
        JsonNode payload = root.get("payload");
        if (header == null || !header.isObject() || payload == null) {
            throw new IllegalArgumentException("Decoded JSON must contain header and payload objects.");
        }
        JsonNode algorithm = header.get("alg");
        if (algorithm == null || !algorithm.isTextual()) {
            throw new IllegalArgumentException("JWT header does not contain a supported alg field.");
        }
        String macAlgorithm = switch (algorithm.asText()) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported alg '" + algorithm.asText()
                + "'. Updating with a secret supports HS256, HS384, and HS512.");
        };
        String signingInput = encodeJson(header) + "." + encodeJson(payload);
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII))
        );
    }

    private static String normalize(String value) {
        String token = value == null ? "" : value.trim();
        if (token.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            return token.substring("Bearer".length()).trim();
        }
        return token;
    }

    private static JsonNode decodePart(String part) throws Exception {
        return MAPPER.readTree(Base64.getUrlDecoder().decode(part));
    }

    private static String encodeJson(JsonNode node) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MAPPER.writeValueAsBytes(node));
    }
}
