package com.non_organic_onion.intelli.webrunner.execution;

import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpExecutorTest {

	@Test
	void buildsUrlEncodedBodyFromEnabledFormEntries() {
		String body = HttpExecutor.buildUrlEncodedBody(List.of(
			form("grant_type", "client credentials", true),
			form("redirect uri", "https://example.test/callback?a=1&b=two", true),
			form("empty", null, true),
			form("ignored", "value", false)
		));

		assertEquals(
			"grant_type=client+credentials&redirect+uri=https%3A%2F%2Fexample.test%2Fcallback%3Fa%3D1%26b%3Dtwo&empty=",
			body
		);
	}

	private static FormEntryState form(String name, String value, boolean enabled) {
		FormEntryState entry = new FormEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		return entry;
	}
}
