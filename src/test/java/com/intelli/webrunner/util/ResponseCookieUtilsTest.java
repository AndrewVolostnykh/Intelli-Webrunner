package com.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseCookieUtilsTest {

	@Test
	void extractsSetCookieHeadersCaseInsensitively() {
		List<Map<String, String>> cookies = ResponseCookieUtils.extractCookies(Map.of(
			"set-cookie",
			List.of(
				"session=abc123; Path=/; HttpOnly; SameSite=Lax",
				"theme=dark; Max-Age=3600"
			)
		));

		assertEquals(2, cookies.size());
		assertEquals("session", cookies.get(0).get("name"));
		assertEquals("abc123", cookies.get(0).get("value"));
		assertEquals("/", cookies.get(0).get("Path"));
		assertEquals("true", cookies.get(0).get("HttpOnly"));
		assertEquals("Lax", cookies.get(0).get("SameSite"));
		assertEquals("theme", cookies.get(1).get("name"));
		assertEquals("dark", cookies.get(1).get("value"));
		assertEquals("3600", cookies.get(1).get("Max-Age"));
	}

	@Test
	void ignoresMissingCookies() {
		assertTrue(ResponseCookieUtils.extractCookies(Map.of("Content-Type", List.of("application/json"))).isEmpty());
		assertTrue(ResponseCookieUtils.extractCookies(null).isEmpty());
	}
}
