package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void parseQueryParamsKeepsEmptyValuesAndOrder() {
		List<HeaderEntryState> params = UrlParamUtils.queryParamsFromUrl("https://api.test/users?a=1&b=&c#top");

		assertEquals(3, params.size());
		assertEquals("a", params.get(0).name);
		assertEquals("1", params.get(0).value);
		assertEquals("b", params.get(1).name);
		assertEquals("", params.get(1).value);
		assertEquals("c", params.get(2).name);
		assertEquals("", params.get(2).value);
	}

	@Test
	void replaceQueryParamsPreservesFragment() {
		assertEquals(
			"https://api.test/users?a=1#top",
			UrlParamUtils.replaceQueryParams("https://api.test/users?old=value#top", List.of(param("a", "1")))
		);
	}

	@Test
	void detectsQueryBeforeFragmentOnly() {
		assertTrue(UrlParamUtils.hasQuery("https://api.test/users?a=1#top"));
		assertFalse(UrlParamUtils.hasQuery("https://api.test/users#top?notQuery=1"));
	}

	private static HeaderEntryState param(String name, String value) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = true;
		return entry;
	}
}
