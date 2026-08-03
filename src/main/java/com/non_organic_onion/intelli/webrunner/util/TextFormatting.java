package com.non_organic_onion.intelli.webrunner.util;

/** Formatting operations for the Dev Tools text editor. */
public final class TextFormatting {

	private TextFormatting() {
	}

	public static String minify(String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	public static String beautify(String value) {
		return value == null ? "" : value.replace(".", ".\n");
	}
}
