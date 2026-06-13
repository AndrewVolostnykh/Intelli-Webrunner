package com.intelli.webrunner.util;

import com.intelli.webrunner.execution.HttpPayloadType;

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
		if ("Binary".equals(label)) {
			return "BINARY";
		}
		return "RAW";
	}

	public static HttpPayloadType resolveType(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "FORM_DATA" -> HttpPayloadType.FORM_DATA;
			case "BINARY" -> HttpPayloadType.BINARY;
			default -> HttpPayloadType.RAW;
		};
	}
}
