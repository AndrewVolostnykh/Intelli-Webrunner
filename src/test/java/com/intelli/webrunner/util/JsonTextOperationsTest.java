package com.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTextOperationsTest {

	@Test
	void removesAllLiteralOccurrences() {
		String json = "{\"name\":\"test\",\"description\":\"test value\"}";

		assertEquals(
			"{\"name\":\"\",\"description\":\" value\"}",
			JsonTextOperations.remove(json, "test")
		);
	}

	@Test
	void replacesAllLiteralOccurrences() {
		String json = "{\"environment\":\"dev\",\"url\":\"https://dev.example.com\"}";

		assertEquals(
			"{\"environment\":\"prod\",\"url\":\"https://prod.example.com\"}",
			JsonTextOperations.replace(json, "dev", "prod")
		);
	}

	@Test
	void ignoresEmptyTargetAndHandlesNullReplacement() {
		String json = "{\"value\":\"keep\"}";

		assertEquals(json, JsonTextOperations.remove(json, ""));
		assertEquals("{\"value\":\"\"}", JsonTextOperations.replace(json, "keep", null));
	}
}
