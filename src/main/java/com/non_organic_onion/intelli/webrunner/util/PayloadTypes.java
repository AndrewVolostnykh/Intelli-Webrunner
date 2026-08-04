package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.execution.HttpPayloadType;

import java.util.Locale;

/**
 * Pure mapping between UI payload labels, persisted payload values, and {@link HttpPayloadType}.
 */
public final class PayloadTypes {

	private PayloadTypes() {
	}

	public static String resolveLabel(Object value) {
		String normalized = value == null ? "" : String.valueOf(value).trim();
		if (normalized.equalsIgnoreCase("FORM_DATA") || normalized.equalsIgnoreCase("Form Data")) {
			return "Form Data";
		}
		if (normalized.equalsIgnoreCase("X_WWW_FORM_URLENCODED")
			|| normalized.equalsIgnoreCase("x-www-form-urlencoded")
			|| normalized.equalsIgnoreCase("application/x-www-form-urlencoded")) {
			return "x-www-form-urlencoded";
		}
		if (normalized.equalsIgnoreCase("BINARY") || normalized.equalsIgnoreCase("Binary")) {
			return "Binary";
		}
		return "Raw";
	}

	public static String resolveValue(Object value) {
		String label = resolveLabel(value);
		if ("Form Data".equals(label)) {
			return "FORM_DATA";
		}
		if ("x-www-form-urlencoded".equals(label)) {
			return "X_WWW_FORM_URLENCODED";
		}
		if ("Binary".equals(label)) {
			return "BINARY";
		}
		return "RAW";
	}

	public static HttpPayloadType resolveType(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "FORM_DATA" -> HttpPayloadType.FORM_DATA;
			case "X_WWW_FORM_URLENCODED", "X-WWW-FORM-URLENCODED", "APPLICATION/X-WWW-FORM-URLENCODED" ->
				HttpPayloadType.X_WWW_FORM_URLENCODED;
			case "BINARY" -> HttpPayloadType.BINARY;
			default -> HttpPayloadType.RAW;
		};
	}
}
