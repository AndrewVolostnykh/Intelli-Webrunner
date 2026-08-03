package com.non_organic_onion.intelli.webrunner.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResponseCookieUtils {

	private ResponseCookieUtils() {
	}

	public static List<Map<String, String>> extractCookies(Map<String, List<String>> headers) {
		List<Map<String, String>> cookies = new ArrayList<>();
		if (headers == null || headers.isEmpty()) {
			return cookies;
		}
		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) {
				continue;
			}
			List<String> values = entry.getValue();
			if (values == null) {
				continue;
			}
			for (String value : values) {
				Map<String, String> cookie = parseSetCookie(value);
				if (!cookie.isEmpty()) {
					cookies.add(cookie);
				}
			}
		}
		return cookies;
	}

	private static Map<String, String> parseSetCookie(String headerValue) {
		Map<String, String> cookie = new LinkedHashMap<>();
		if (headerValue == null || headerValue.isBlank()) {
			return cookie;
		}
		String[] parts = headerValue.split(";", -1);
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			if (part.isEmpty()) {
				continue;
			}
			int equalsIndex = part.indexOf('=');
			if (equalsIndex < 0) {
				cookie.put(i == 0 ? "name" : part, i == 0 ? part : "true");
				continue;
			}
			String key = part.substring(0, equalsIndex).trim();
			String value = part.substring(equalsIndex + 1).trim();
			if (i == 0) {
				cookie.put("name", key);
				cookie.put("value", value);
			} else {
				cookie.put(key, value);
			}
		}
		return cookie;
	}
}
