package com.non_organic_onion.intelli.webrunner.util;

public final class JsonTextOperations {

	private JsonTextOperations() {
	}

	public static String remove(String json, String value) {
		return replace(json, value, "");
	}

	public static String replace(String json, String target, String replacement) {
		String source = json == null ? "" : json;
		if (target == null || target.isEmpty()) {
			return source;
		}
		return source.replace(target, replacement == null ? "" : replacement);
	}
}
