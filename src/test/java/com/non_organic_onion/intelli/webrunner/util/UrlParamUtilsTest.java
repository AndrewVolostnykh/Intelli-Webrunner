package com.non_organic_onion.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlParamUtilsTest {

	@Test
	void leavesUrlWithProtocolUnchanged() {
		assertEquals("http://localhost:8080/api", UrlParamUtils.applyDefaultProtocol("http://localhost:8080/api"));
		assertEquals("https://example.com/api", UrlParamUtils.applyDefaultProtocol("https://example.com/api"));
	}

	@Test
	void addsHttpForLocalhost() {
		assertEquals("http://localhost:8080/api", UrlParamUtils.applyDefaultProtocol("localhost:8080/api"));
		assertEquals("http://localhost/api", UrlParamUtils.applyDefaultProtocol("localhost/api"));
		assertEquals("http://localhost", UrlParamUtils.applyDefaultProtocol("localhost"));
	}

	@Test
	void addsHttpsForNonLocalhost() {
		assertEquals("https://example.com/api", UrlParamUtils.applyDefaultProtocol("example.com/api"));
		assertEquals("https://api.example.com:8443/users", UrlParamUtils.applyDefaultProtocol("api.example.com:8443/users"));
	}
}
