package com.intelli.webrunner.script;

import com.intelli.webrunner.execution.HttpExecutionResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptRuntimeTest {

	private final ScriptRuntime runtime = new ScriptRuntime();

	@Test
	void emptyScriptDoesNothing() {
		ScriptRequest request = request("body");
		VarsStore vars = new VarsStore();

		runtime.runScript("   ", context(vars, request, request("body"), null, new ArrayList<>()));

		assertEquals("body", request.getBody());
		assertTrue(vars.entries().isEmpty());
	}

	@Test
	void varsSupportGetSetAddAndAll() {
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();

		runtime.runScript(
			"""
				vars.set('token', 'abc');
				vars.add('count', 3);
				log(vars.get('token'), vars.get('count'), stringify(vars.all()));
				""",
			context(vars, request(""), request(""), null, logs)
		);

		assertEquals("abc", vars.get("token"));
		assertEquals(3.0, vars.get("count"));
		assertEquals(1, logs.size());
		assertTrue(logs.get(0).contains("abc 3"));
		assertTrue(logs.get(0).contains("\"token\":\"abc\""));
	}

	@Test
	void globalContextIsAvailableAndMutable() {
		VarsStore globalContext = new VarsStore();
		globalContext.add("baseUrl", "http://localhost");
		List<String> logs = new ArrayList<>();

		runtime.runScript(
			"""
				log(globalContext.get('baseUrl'));
				globalContext.add('port', 8080);
				context.globalContext.set('path', '/api');
				""",
			context(new VarsStore(), request(""), request(""), null, logs, globalContext)
		);

		assertEquals("http://localhost", logs.get(0));
		assertEquals(8080.0, globalContext.get("port"));
		assertEquals("/api", globalContext.get("path"));
	}

	@Test
	void beforeScriptCanMutateJsonBodyHeadersParamsFormDataAndBinaryPath() {
		ScriptRequest request = request("{\"name\":\"old\"}");

		runtime.runScript(
			"""
				request.body.name = 'new';
				request.body.count = 2;
				request.headers = [{ name: 'Authorization', value: 'Bearer token', enabled: true }];
				request.params = [{ name: 'q', value: 42, enabled: false }];
				request.formData = [{ name: 'file', value: 'C:/tmp/a.txt', enabled: true, file: true }];
				request.binaryFilePath = 'C:/tmp/body.bin';
				""",
			context(new VarsStore(), request, request("{\"name\":\"old\"}"), null, new ArrayList<>())
		);

		assertEquals("{\"name\":\"new\",\"count\":2}", request.getBody());
		assertEquals(1, request.getHeaders().size());
		assertEquals("Authorization", request.getHeaders().get(0).name);
		assertEquals("Bearer token", request.getHeaders().get(0).value);
		assertTrue(request.getHeaders().get(0).enabled);
		assertEquals("q", request.getParams().get(0).name);
		assertEquals("42.0", request.getParams().get(0).value);
		assertFalse(request.getParams().get(0).enabled);
		assertEquals("file", request.getFormData().get(0).name);
		assertEquals("C:/tmp/a.txt", request.getFormData().get(0).value);
		assertTrue(request.getFormData().get(0).file);
		assertEquals("C:/tmp/body.bin", request.getBinaryFilePath());
	}

	@Test
	void invalidJsonBodyStaysStringAndPreservesBareTemplatePlaceholders() {
		ScriptRequest request = request("{\"someVar\":{{someValue}}}");
		VarsStore vars = new VarsStore();

		runtime.runScript(
			"vars.add('someValue', 'AAA'); log(request.body);",
			context(vars, request, request(request.getBody()), null, new ArrayList<>())
		);

		assertEquals("{\"someVar\":{{someValue}}}", request.getBody());
		assertEquals("AAA", vars.get("someValue"));
	}

	@Test
	void rawRequestKeepsOriginalRequestSnapshot() {
		ScriptRequest request = request("{\"name\":\"changed\"}");
		ScriptRequest rawRequest = request("{\"name\":\"original\"}");
		List<String> logs = new ArrayList<>();

		runtime.runScript("request.body.name = 'runtime'; log(rawRequest.body.name);", context(new VarsStore(), request, rawRequest, null, logs));

		assertEquals("original", logs.get(0));
		assertEquals("{\"name\":\"runtime\"}", request.getBody());
	}

	@Test
	void responseObjectExposesHttpResponseFieldsAndParsedJsonBody() {
		List<String> logs = new ArrayList<>();
		HttpExecutionResponse response = new HttpExecutionResponse(
			201,
			Map.of("content-type", List.of("application/json")),
			"{\"id\":7,\"ok\":true}"
		);

		runtime.runScript(
			"log(response.statusCode, response.headers['content-type'][0], response.body.id, response.body.ok);",
			context(new VarsStore(), request(""), request(""), response, logs)
		);

		assertEquals("201.0 application/json 7.0 true", logs.get(0));
	}

	@Test
	void helpersLogAssertStringifyJsonifyAndUuidAreAvailable() {
		List<String> logs = new ArrayList<>();

		runtime.runScript(
			"""
				var parsed = jsonify('{"a":1}');
				log(stringify({ a: parsed.a, hasUuid: uuid().length > 0 }));
				assert(false, true, 'expected failure');
				context.log('via context');
				""",
			context(new VarsStore(), request(""), request(""), null, logs)
		);

		assertTrue(logs.get(0).contains("\"a\":1"));
		assertTrue(logs.get(0).contains("\"hasUuid\":true"));
		assertEquals("Assertion failed: expected failure expected true received false", logs.get(1));
		assertEquals("via context", logs.get(2));
	}

	@Test
	void assertComparesJsonObjectsStructurally() {
		List<String> logs = new ArrayList<>();

		runtime.runScript(
			"""
				assert({ id: 7, nested: { ok: true }, roles: ['admin'] }, { id: 7, nested: { ok: true }, roles: ['admin'] }, 'objects should match');
				assert({ id: 7 }, { id: 8 }, 'objects should differ');
				""",
			context(new VarsStore(), request(""), request(""), null, logs)
		);

		assertEquals(1, logs.size());
		assertEquals("Assertion failed: objects should differ $.id expected 8.0 received 7.0", logs.get(0));
	}

	@Test
	void predefinedRandomAndDateFunctionsAreAvailable() {
		List<String> logs = new ArrayList<>();

		runtime.runScript(
			"""
				var value = {
					randomStringLength: randomString(12).length,
					randomEmail: randomEmail(),
					randomIsoDate: randomIsoDate(),
					randomRfcDate: randomRfcDate(),
					randomDateTime: randomDateTime(),
					randomDate: randomDate(),
					randomTime: randomTime(),
					randomMillilsDate: randomMillilsDate(),
					randomEpochSecondsDate: randomEpochSecondsDate(),
					currentIsoDate: currentIsoDate(),
					currentRfcDate: currentRfcDate(),
					currentDateTime: currentDateTime(),
					currentDate: currentDate(),
					currentTime: currentTime(),
					currentMillilsDate: currentMillilsDate(),
					currentEpochSecondsDate: currentEpochSecondsDate(),
					randomNumber: randomNumber(3, 5),
					contextRandomNumber: context.randomNumber(3, 5),
					randomDoubleDefault: randomDouble(1.5, 1.5),
					randomDoubleCustom: randomDouble(1.5, 1.5, 3),
					contextRandomDoubleCustom: context.randomDouble(1.5, 1.5, 2)
				};
				log(stringify(value));
				""",
			context(new VarsStore(), request(""), request(""), null, logs)
		);

		String result = logs.get(0);
		assertTrue(result.contains("\"randomStringLength\":12"));
		assertTrue(result.matches(".*\"randomEmail\":\"[a-z0-9]{10}@[a-z0-9]{8}\\.com\".*"));
		assertTrue(result.matches(".*\"randomIsoDate\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\".*"));
		assertTrue(result.matches(".*\"randomRfcDate\":\"[^\"]+ GMT\".*"));
		assertTrue(result.matches(".*\"randomDateTime\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\".*"));
		assertTrue(result.matches(".*\"randomDate\":\"\\d{4}-\\d{2}-\\d{2}\".*"));
		assertTrue(result.matches(".*\"randomTime\":\"\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\".*"));
		assertTrue(result.matches(".*\"randomMillilsDate\":\\d+.*"));
		assertTrue(result.matches(".*\"randomEpochSecondsDate\":\\d+.*"));
		assertTrue(result.matches(".*\"currentIsoDate\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\".*"));
		assertTrue(result.matches(".*\"currentRfcDate\":\"[^\"]+ GMT\".*"));
		assertTrue(result.matches(".*\"currentDateTime\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\".*"));
		assertTrue(result.matches(".*\"currentDate\":\"\\d{4}-\\d{2}-\\d{2}\".*"));
		assertTrue(result.matches(".*\"currentTime\":\"\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\".*"));
		assertTrue(result.matches(".*\"currentMillilsDate\":\\d+.*"));
		assertTrue(result.matches(".*\"currentEpochSecondsDate\":\\d+.*"));
		assertTrue(result.matches(".*\"randomNumber\":[3-5].*"));
		assertTrue(result.matches(".*\"contextRandomNumber\":[3-5].*"));
		assertTrue(result.contains("\"randomDoubleDefault\":\"1.5000000000\""));
		assertTrue(result.contains("\"randomDoubleCustom\":\"1.500\""));
		assertTrue(result.contains("\"contextRandomDoubleCustom\":\"1.50\""));
	}

	@Test
	void logAndReturnLogsValueAndReturnsOnlyValue() {
		List<String> logs = new ArrayList<>();
		VarsStore vars = new VarsStore();

		runtime.runScript(
			"""
				vars.set('first', logAndReturn('abc'));
				vars.set('second', logAndReturn('token', 42));
				vars.set('third', context.logAndReturn('payload', { ok: true }));
				""",
			context(vars, request(""), request(""), null, logs)
		);

		assertEquals("abc", vars.get("first"));
		assertEquals(42.0, vars.get("second"));
		assertEquals(Map.of("ok", true), vars.get("third"));
		assertEquals("abc", logs.get(0));
		assertEquals("token 42.0", logs.get(1));
		assertEquals("payload {\"ok\":true}", logs.get(2));
	}

	@Test
	void undefinedHeaderParamAndFormValuesNormalizeToEmptyStrings() {
		ScriptRequest request = request("");

		runtime.runScript(
			"""
				request.headers = [{ name: 'X-Test', enabled: true }];
				request.params = [{ name: 'p', enabled: true }];
				request.formData = [{ name: 'f', enabled: true }];
				""",
			context(new VarsStore(), request, request(""), null, new ArrayList<>())
		);

		assertEquals("", request.getHeaders().get(0).value);
		assertEquals("", request.getParams().get(0).value);
		assertEquals("", request.getFormData().get(0).value);
	}

	private ScriptContext context(
		VarsStore vars,
		ScriptRequest request,
		ScriptRequest rawRequest,
		Object response,
		List<String> logs
	) {
		return context(vars, request, rawRequest, response, logs, new VarsStore());
	}

	private ScriptContext context(
		VarsStore vars,
		ScriptRequest request,
		ScriptRequest rawRequest,
		Object response,
		List<String> logs,
		VarsStore globalContext
	) {
		ScriptLogger logger = logs::add;
		return new ScriptContext(vars, logger, new ScriptHelpers(logger), request, rawRequest, response, globalContext);
	}

	private ScriptRequest request(String body) {
		ScriptRequest request = new ScriptRequest(body, List.of(), List.of());
		request.setFormData(List.of());
		request.setBinaryFilePath("");
		assertNotNull(request.getHeaders());
		return request;
	}
}
