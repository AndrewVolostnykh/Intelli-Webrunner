package com.intelli.webrunner.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Hashing operations for the Dev Tools Hash utility. */
public final class HashingService {
	public static final List<String> ALGORITHMS = List.of("MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512");

	private HashingService() {
	}

	public static String hash(String value, String algorithm, String secret) {
		String normalizedAlgorithm = normalizeAlgorithm(algorithm);
		byte[] input = nullToEmpty(value).getBytes(StandardCharsets.UTF_8);
		byte[] result = hasSecret(secret)
			? hmac(input, normalizedAlgorithm, secret)
			: digest(input, normalizedAlgorithm);
		return toHex(result);
	}

	private static byte[] digest(byte[] input, String algorithm) {
		try {
			MessageDigest digest = MessageDigest.getInstance(algorithm);
			return digest.digest(input);
		} catch (Exception error) {
			throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithm, error);
		}
	}

	private static byte[] hmac(byte[] input, String algorithm, String secret) {
		try {
			String macAlgorithm = "Hmac" + algorithm.replace("-", "");
			Mac mac = Mac.getInstance(macAlgorithm);
			SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm);
			mac.init(key);
			return mac.doFinal(input);
		} catch (Exception error) {
			throw new IllegalArgumentException("Unsupported HMAC algorithm: HMAC-" + algorithm, error);
		}
	}

	private static String normalizeAlgorithm(String algorithm) {
		if (algorithm == null || algorithm.isBlank()) {
			return ALGORITHMS.get(0);
		}
		for (String supported : ALGORITHMS) {
			if (supported.equalsIgnoreCase(algorithm.trim())) {
				return supported;
			}
		}
		throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithm);
	}

	private static boolean hasSecret(String secret) {
		return secret != null && !secret.isEmpty();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String toHex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
