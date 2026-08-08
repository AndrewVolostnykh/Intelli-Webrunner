package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import org.junit.jupiter.api.Test;

import java.util.List;

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

	@Test
	void replaceQueryParamsReplacesExistingQueryValues() {
		assertEquals(
			"https://api.test/users?a=blablabla",
			UrlParamUtils.replaceQueryParams(
				"https://api.test/users?a=random",
				List.of(param("a", "blablabla"))
			)
		);
	}

	private static HeaderEntryState param(String name, String value) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = true;
		return entry;
	}
}
