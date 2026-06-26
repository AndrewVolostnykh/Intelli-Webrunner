package com.intelli.webrunner.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateEngineTest {

	private final TemplateEngine engine = new TemplateEngine();
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void replacesBareJsonPlaceholderWithString() {
		String result = engine.applyToBody("{\"someVar\":{{someValue}}}", Map.of("someValue", "AAA"));

		assertJsonEquals("{\"someVar\":\"AAA\"}", result);
	}

	@Test
	void replacesBareJsonPlaceholderWithNumberBooleanObjectAndArray() {
		String result = engine.applyToBody(
			"{\"num\":{{num}},\"flag\":{{flag}},\"obj\":{{obj}},\"arr\":{{arr}}}",
			Map.of(
				"num", 12,
				"flag", true,
				"obj", Map.of("nested", "value"),
				"arr", List.of(1, 2)
			)
		);

		assertJsonEquals(
			"{\"num\":12,\"flag\":true,\"obj\":{\"nested\":\"value\"},\"arr\":[1,2]}",
			result
		);
	}

	@Test
	void quotedWholePlaceholderPreservesJsonType() {
		String result = engine.applyToBody("{\"someVar\":\"{{someValue}}\"}", Map.of("someValue", 42));

		assertJsonEquals("{\"someVar\":42}", result);
	}

	@Test
	void partialQuotedPlaceholderInterpolatesAsText() {
		String result = engine.applyToBody("{\"url\":\"/users/{{id}}\"}", Map.of("id", 42));

		assertJsonEquals("{\"url\":\"/users/42\"}", result);
	}

	@Test
	void missingBareJsonPlaceholderBecomesNull() {
		String result = engine.applyToBody("{\"missing\":{{value}}}", Map.of());

		assertJsonEquals("{\"missing\":null}", result);
	}

	@Test
	void missingQuotedWholePlaceholderIsPreserved() {
		String result = engine.applyToBody("{\"missing\":\"{{value}}\"}", Map.of());

		assertJsonEquals("{\"missing\":\"{{value}}\"}", result);
	}

	@Test
	void invalidJsonFallsBackToTextInterpolation() {
		String result = engine.applyToBody("hello {{name}}", Map.of("name", "world"));

		assertEquals("hello world", result);
	}

	@Test
	void bareTemplateQuotingIgnoresTemplatesInsideJsonStrings() {
		String result = engine.applyToBody(
			"{\"text\":\"literal {{value}}\",\"bare\":{{value}}}",
			Map.of("value", "AAA")
		);

		assertJsonEquals("{\"text\":\"literal AAA\",\"bare\":\"AAA\"}", result);
	}

	@Test
	void appliesTemplatesToHeadersParamsFormDataAndText() {
		HeaderEntryState header = header("X-{{suffix}}", "Bearer {{token}}");
		HeaderEntryState param = header("q", "{{query}}");
		FormEntryState formEntry = formEntry("file-{{id}}", "C:/tmp/{{name}}.txt");

		engine.applyToHeaders(new ArrayList<>(List.of(header)), Map.of("suffix", "Auth", "token", "abc"));
		engine.applyToParams(new ArrayList<>(List.of(param)), Map.of("query", "search"));
		engine.applyToFormData(new ArrayList<>(List.of(formEntry)), Map.of("id", 7, "name", "report"));

		assertEquals("X-Auth", header.name);
		assertEquals("Bearer abc", header.value);
		assertEquals("search", param.value);
		assertEquals("file-7", formEntry.name);
		assertEquals("C:/tmp/report.txt", formEntry.value);
		assertEquals("path/abc", engine.applyToText("path/{{token}}", Map.of("token", "abc")));
	}

	@Test
	void supportsPredefinedFunctionsInJsonPayloadPlaceholders() {
		String result = engine.applyToBody(
			"{\"id\":\"{{uuid()}}\",\"name\":{{randomString(8)}},\"count\":{{randomNumber(3,5)}},\"price\":{{randomDouble(1.5,1.5,3)}}}",
			Map.of()
		);

		try {
			JsonNode json = mapper.readTree(result);
			assertTrue(json.get("id").asText().matches("[0-9a-f-]{36}"));
			assertTrue(json.get("name").asText().matches("[A-Za-z0-9]{8}"));
			assertTrue(json.get("count").asInt() >= 3);
			assertTrue(json.get("count").asInt() <= 5);
			assertEquals("1.500", json.get("price").asText());
		} catch (Exception error) {
			throw new AssertionError(error);
		}
	}

	@Test
	void supportsPredefinedFunctionsInTextInterpolation() {
		String result = engine.applyToText(
			"request-{{randomString(6)}}-{{randomDouble(2.0,2.0)}}",
			Map.of()
		);

		assertTrue(result.matches("request-[A-Za-z0-9]{6}-2\\.0000000000"));
	}

	private HeaderEntryState header(String name, String value) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = true;
		return entry;
	}

	private FormEntryState formEntry(String name, String value) {
		FormEntryState entry = new FormEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = true;
		return entry;
	}

	private void assertJsonEquals(String expected, String actual) {
		try {
			JsonNode expectedJson = mapper.readTree(expected);
			JsonNode actualJson = mapper.readTree(actual);
			assertEquals(expectedJson, actualJson);
		} catch (Exception error) {
			throw new AssertionError(error);
		}
	}
}
