package com.non_organic_onion.intelli.webrunner.execution;

import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMessageProducer;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaSendRequest;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaSendResult;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.GlobalContextState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestExecutionServiceTest {

	@ParameterizedTest
	@ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"})
	void executeWithScriptsSupportsCommonHttpMethods(String method) {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			method,
			"https://example.test/resource",
			List.of(),
			List.of(),
			"",
			"",
			"",
			false,
			null,
			"RAW",
			List.of(),
			""
		);

		assertEquals(1, httpExecutor.executeCalls);
		assertEquals(method, httpExecutor.method);
		assertEquals("https://example.test/resource", httpExecutor.url);
		assertEquals(200, result.statusCode);
		assertEquals("OK", result.statusMessage);
	}

	@Test
	void executeWithScriptsAppliesRawPayloadTemplatesScriptsCookiesAndSnapshots() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(
			variable("host", "api.test", true),
			variable("user", "Ada", true),
			variable("disabled", "ignored", false)
		));
		stateService.saveGlobalContext(globalContext);
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		httpExecutor.response = new HttpExecutionResponse(
			201,
			orderedHeaders(
				"Content-Type",
				List.of("application/json"),
				"Set-Cookie",
				List.of("sid=abc; Path=/; HttpOnly", "theme=dark; Max-Age=60")
			),
			"{\"ok\":true,\"id\":7}"
		);
		RequestExecutionService service = httpService(httpExecutor, stateService);
		String before = """
			vars.set('token', 'secret');
			request.body.name = 'Grace';
			request.headers = [
				{name: 'X-User', value: '{{user}}', enabled: true},
				{name: 'X-Token', value: '{{token}}', enabled: true}
			];
			request.params = [
				{name: 'q', value: '{{user}}', enabled: true},
				{name: 'fromScript', value: '{{user}}', enabled: true}
			];
			log('before', stringify(request.body));
			""";
		String after = """
			log('after', response.statusCode, response.body.ok);
			globalContext.set('lastStatus', response.statusCode);
			""";

		ExecutionResult result = service.executeWithScripts(
			"POST",
			"{{host}}/users?existing=1",
			List.of(header("X-User", "{{user}}", true), header("X-Disabled", "{{disabled}}", false)),
			List.of(header("q", "{{user}}", true)),
			"{\"name\":\"old\"}",
			before,
			after,
			false,
			null,
			"RAW",
			List.of(),
			""
		);

		assertEquals(1, httpExecutor.executeCalls);
		assertEquals("POST", httpExecutor.method);
		assertEquals("https://api.test/users?existing=1&q=Ada&fromScript=Ada", httpExecutor.url);
		assertTrue(httpExecutor.body.contains("\"name\" : \"Grace\""));
		assertEquals(HttpPayloadType.RAW, httpExecutor.payloadType);
		assertEquals("Ada", headerValue(httpExecutor.headers, "X-User"));
		assertEquals("secret", headerValue(httpExecutor.headers, "X-Token"));
		assertEquals(201, result.statusCode);
		assertEquals("Created", result.statusMessage);
		assertTrue(result.responseBody.contains("\"ok\" : true"));
		assertTrue(result.responseHeaders.contains("\"Content-Type\""));
		assertTrue(result.responseCookies.contains("\"sid\""));
		assertTrue(result.responseCookies.contains("\"theme\""));
		assertTrue(result.logs.contains("before {\"name\":\"Grace\""));
		assertTrue(result.logs.contains("after 201.0 true"));
		assertTrue(result.rawRequestSnapshot.contains("{{host}}/users?existing=1"));
		assertTrue(result.sentRequestSnapshot.contains("https://api.test/users?existing=1&q=Ada&fromScript=Ada"));
		assertTrue(result.responseSnapshot.contains("\"statusCode\" : 201"));
		assertEquals("201.0", stateService.getGlobalContext().variables.stream()
			.filter(variable -> "lastStatus".equals(variable.name))
			.findFirst()
			.orElseThrow()
			.value);
	}

	@Test
	void executeWithScriptsPassesTemplatedFormDataPayload() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(variable("fileName", "avatar.png", true)));
		stateService.saveGlobalContext(globalContext);
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, stateService);

		ExecutionResult result = service.executeWithScripts(
			"POST",
			"upload.test/files",
			List.of(),
			List.of(),
			"",
			"request.formData.push({name: 'dynamic', value: 'yes', enabled: true, file: false});",
			"",
			false,
			null,
			"FORM_DATA",
			List.of(
				form("name", "Ada", true, false),
				form("file", "C:/tmp/{{fileName}}", true, true),
				form("disabled", "ignored", false, false)
			),
			""
		);

		assertEquals(200, result.statusCode);
		assertEquals(HttpPayloadType.FORM_DATA, httpExecutor.payloadType);
		assertEquals("https://upload.test/files", httpExecutor.url);
		assertEquals("Ada", formValue(httpExecutor.formData, "name"));
		assertEquals("C:/tmp/avatar.png", formValue(httpExecutor.formData, "file"));
		assertEquals("yes", formValue(httpExecutor.formData, "dynamic"));
		assertFalse(httpExecutor.formData.stream().anyMatch(entry -> "disabled".equals(entry.name) && entry.enabled));
	}

	@Test
	void executeWithScriptsPassesTemplatedUrlEncodedPayload() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(variable("name", "Ada Lovelace", true)));
		stateService.saveGlobalContext(globalContext);
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, stateService);

		ExecutionResult result = service.executeWithScripts(
			"POST",
			"https://api.test/token",
			List.of(),
			List.of(),
			"",
			"request.formData.push({name: 'scope', value: 'read write', enabled: true, file: false});",
			"",
			false,
			null,
			"X_WWW_FORM_URLENCODED",
			List.of(
				form("grant_type", "password", true, false),
				form("username", "{{name}}", true, false),
				form("disabled", "ignored", false, false)
			),
			""
		);

		assertEquals(200, result.statusCode);
		assertEquals(HttpPayloadType.X_WWW_FORM_URLENCODED, httpExecutor.payloadType);
		assertEquals("password", formValue(httpExecutor.formData, "grant_type"));
		assertEquals("Ada Lovelace", formValue(httpExecutor.formData, "username"));
		assertEquals("read write", formValue(httpExecutor.formData, "scope"));
		assertFalse(httpExecutor.formData.stream().anyMatch(entry -> "disabled".equals(entry.name) && entry.enabled));
	}

	@Test
	void executeWithScriptsPassesTemplatedBinaryPayloadPath() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(variable("name", "payload.bin", true)));
		stateService.saveGlobalContext(globalContext);
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, stateService);

		ExecutionResult result = service.executeWithScripts(
			"PUT",
			"https://files.test/upload",
			List.of(header("Content-Type", "application/custom-binary", true)),
			List.of(),
			"",
			"request.binaryFilePath = request.binaryFilePath.replace('payload', 'final');",
			"",
			false,
			null,
			"BINARY",
			List.of(),
			"C:/tmp/payload-{{name}}"
		);

		assertEquals(200, result.statusCode);
		assertEquals(HttpPayloadType.BINARY, httpExecutor.payloadType);
		assertEquals("C:/tmp/final-payload.bin", httpExecutor.binaryFilePath);
		assertEquals("application/custom-binary", headerValue(httpExecutor.headers, "Content-Type"));
	}

	@Test
	void executeWithScriptsPassesTimeoutToHttpExecutor() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"",
			"",
			false,
			null,
			"RAW",
			List.of(),
			"",
			125
		);

		assertEquals(125, httpExecutor.timeoutMillis);
	}

	@Test
	void executeWithScriptsDoesNotSendRequestWhenBeforeScriptFails() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"throw new Error('boom before');",
			"",
			false,
			null,
			"RAW",
			List.of(),
			""
		);

		assertEquals(0, httpExecutor.executeCalls);
		assertEquals(0, result.statusCode);
		assertTrue(result.logs.contains("boom before"));
	}

	@Test
	void executeWithScriptsReturnsFailureWhenHttpExecutorThrows() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		httpExecutor.failure = new IOException("network down");
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"",
			"",
			false,
			null,
			"RAW",
			List.of(),
			""
		);

		assertEquals(1, httpExecutor.executeCalls);
		assertEquals(0, result.statusCode);
		assertTrue(result.logs.contains("Request failed: network down"));
	}

	@Test
	void executeWithScriptsKeepsResponseWhenAfterScriptFails() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"",
			"throw new Error('boom after');",
			false,
			null,
			"RAW",
			List.of(),
			""
		);

		assertEquals(1, httpExecutor.executeCalls);
		assertEquals(200, result.statusCode);
		assertTrue(result.logs.contains("boom after"));
	}

	@Test
	void executeWithScriptsHonorsSkipBeforeSendingChainRequest() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"skip('skip this request');",
			"",
			true,
			new VarsStore(),
			"RAW",
			List.of(),
			""
		);

		assertEquals(0, httpExecutor.executeCalls);
		assertEquals("Skipped", result.flowStatus);
		assertEquals("Skipped", result.statusMessage);
		assertTrue(result.logs.contains("[[WEBRUNNER_CHAIN_SKIP]]skip this request"));
		assertTrue(result.logs.contains("Skipped before request send."));
	}

	@Test
	void executeWithScriptsHonorsInterruptBeforeSendingChainRequest() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		ExecutionResult result = service.executeWithScripts(
			"GET",
			"https://api.test",
			List.of(),
			List.of(),
			"",
			"interrupt('stop chain');",
			"",
			true,
			new VarsStore(),
			"RAW",
			List.of(),
			""
		);

		assertEquals(0, httpExecutor.executeCalls);
		assertEquals("Interrupted", result.flowStatus);
		assertEquals("Interrupted", result.statusMessage);
		assertTrue(result.logs.contains("[[WEBRUNNER_CHAIN_INTERRUPT]]stop chain"));
		assertTrue(result.logs.contains("Interrupted before request send."));
	}

	@Test
	void executeWithScriptsDownloadReturnsBinaryBodyAndHeaders() {
		CapturingHttpExecutor httpExecutor = new CapturingHttpExecutor();
		byte[] bodyBytes = new byte[] {1, 2, 3, 4};
		httpExecutor.response = new HttpExecutionResponse(
			200,
			Map.of("Content-Type", List.of("application/octet-stream")),
			"binary",
			bodyBytes
		);
		RequestExecutionService service = httpService(httpExecutor, new GlobalWebrunnerStateService());

		DownloadResult download = service.executeWithScriptsDownload(
			"GET",
			"files.test/archive",
			List.of(),
			List.of(header("download", "true", true)),
			"",
			"",
			"log('downloaded', response.statusCode);",
			"RAW",
			List.of(),
			""
		);

		assertEquals(1, httpExecutor.executeBinaryCalls);
		assertEquals("https://files.test/archive?download=true", httpExecutor.url);
		assertEquals(200, download.result.statusCode);
		assertArrayEquals(bodyBytes, download.bodyBytes);
		assertEquals(List.of("application/octet-stream"), download.headers.get("Content-Type"));
		assertTrue(download.result.logs.contains("downloaded 200"));
	}

	@Test
	void executeGrpcWithScriptsResolvesGlobalContextInEndpointFields() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(
			variable("host", "localhost", true),
			variable("port", "9090", true),
			variable("service", "demo.EchoService", true),
			variable("method", "Echo", true)
		));
		stateService.saveGlobalContext(globalContext);
		CapturingGrpcExecutor grpcExecutor = new CapturingGrpcExecutor();
		RequestExecutionService service = new RequestExecutionService(
			new TemplateEngine(),
			new ScriptRuntime(),
			new HttpExecutor(),
			grpcExecutor,
			new KafkaMessageProducer(),
			stateService
		);
		RequestDetailsState details = new RequestDetailsState();
		details.type = RequestType.GRPC;
		details.target = "{{host}}:{{port}}";
		details.service = "{{service}}";
		details.grpcMethod = "{{method}}";

		service.executeGrpcWithScripts(details, List.of(), List.of(), "{}", "", "", null);

		assertEquals("localhost:9090", grpcExecutor.target);
		assertEquals("demo.EchoService", grpcExecutor.service);
		assertEquals("Echo", grpcExecutor.method);
	}

	@Test
	void executeGrpcWithScriptsPassesTimeoutToGrpcExecutor() {
		CapturingGrpcExecutor grpcExecutor = new CapturingGrpcExecutor();
		RequestExecutionService service = new RequestExecutionService(
			new TemplateEngine(),
			new ScriptRuntime(),
			new HttpExecutor(),
			grpcExecutor,
			new KafkaMessageProducer(),
			new GlobalWebrunnerStateService()
		);
		RequestDetailsState details = new RequestDetailsState();
		details.type = RequestType.GRPC;
		details.target = "localhost:9090";
		details.service = "demo.EchoService";
		details.grpcMethod = "Echo";

		service.executeGrpcWithScripts(details, List.of(), List.of(), "{}", "", "", null, 175);

		assertEquals(175, grpcExecutor.timeoutMillis);
	}

	@Test
	void executeKafkaWithScriptsPassesTimeoutToProducer() {
		CapturingKafkaMessageProducer kafkaProducer = new CapturingKafkaMessageProducer();
		RequestExecutionService service = new RequestExecutionService(
			new TemplateEngine(),
			new ScriptRuntime(),
			new HttpExecutor(),
			new CapturingGrpcExecutor(),
			kafkaProducer,
			new GlobalWebrunnerStateService()
		);
		RequestDetailsState details = new RequestDetailsState();
		details.type = RequestType.KAFKA;
		details.kafkaBootstrapServers = "localhost:9092";
		details.kafkaTopic = "events";

		service.executeKafkaWithScripts(
			details,
			List.of(),
			"{\"ok\":true}",
			"",
			"",
			"String",
			"JSON",
			"",
			null,
			225
		);

		assertEquals(225, kafkaProducer.request.timeoutMillis);
	}

	@Test
	void createRequestAppliesDefaultTimeoutExceptKafkaListen() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		stateService.saveDefaultTimeoutMillis(345);

		var http = stateService.createRequest("HTTP", RequestType.HTTP, null);
		var grpc = stateService.createRequest("gRPC", RequestType.GRPC, null);
		var kafka = stateService.createRequest("Kafka", RequestType.KAFKA, null);
		var listen = stateService.createRequest("Listen", RequestType.KAFKA_LISTEN, null);

		assertEquals(345, stateService.getRequestDetails(http.id).timeoutMillis);
		assertEquals(345, stateService.getRequestDetails(grpc.id).timeoutMillis);
		assertEquals(345, stateService.getRequestDetails(kafka.id).timeoutMillis);
		assertEquals(0, stateService.getRequestDetails(listen.id).timeoutMillis);
	}

	private RequestExecutionService httpService(CapturingHttpExecutor httpExecutor, GlobalWebrunnerStateService stateService) {
		return new RequestExecutionService(
			new TemplateEngine(),
			new ScriptRuntime(),
			httpExecutor,
			new CapturingGrpcExecutor(),
			new KafkaMessageProducer(),
			stateService
		);
	}

	private static Map<String, List<String>> orderedHeaders(
		String firstName,
		List<String> firstValue,
		String secondName,
		List<String> secondValue
	) {
		Map<String, List<String>> headers = new LinkedHashMap<>();
		headers.put(firstName, firstValue);
		headers.put(secondName, secondValue);
		return headers;
	}

	private static String headerValue(List<HeaderEntryState> headers, String name) {
		return headers.stream()
			.filter(header -> name.equals(header.name))
			.findFirst()
			.orElseThrow()
			.value;
	}

	private static String formValue(List<FormEntryState> formData, String name) {
		return formData.stream()
			.filter(entry -> name.equals(entry.name))
			.findFirst()
			.orElseThrow()
			.value;
	}

	private static HeaderEntryState variable(String name, String value, boolean enabled) {
		return header(name, value, enabled);
	}

	private static HeaderEntryState header(String name, String value, boolean enabled) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		return entry;
	}

	private static FormEntryState form(String name, String value, boolean enabled, boolean file) {
		FormEntryState entry = new FormEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		entry.file = file;
		return entry;
	}

	private static final class CapturingHttpExecutor extends HttpExecutor {
		private int executeCalls;
		private int executeBinaryCalls;
		private String method;
		private String url;
		private List<HeaderEntryState> headers = List.of();
		private String body;
		private List<FormEntryState> formData = List.of();
		private String binaryFilePath;
		private HttpPayloadType payloadType;
		private int timeoutMillis;
		private int binaryTimeoutMillis;
		private Exception failure;
		private HttpExecutionResponse response = new HttpExecutionResponse(
			200,
			Map.of("Content-Type", List.of("application/json")),
			"{\"ok\":true}"
		);

		@Override
		public HttpExecutionResponse execute(
			String method,
			String url,
			List<HeaderEntryState> headers,
			String body,
			List<FormEntryState> formData,
			String binaryFilePath,
			HttpPayloadType payloadType,
			int timeoutMillis
		) throws IOException, InterruptedException {
			executeCalls++;
			this.timeoutMillis = timeoutMillis;
			capture(method, url, headers, body, formData, binaryFilePath, payloadType);
			if (failure instanceof IOException ioException) {
				throw ioException;
			}
			if (failure instanceof InterruptedException interruptedException) {
				throw interruptedException;
			}
			return response;
		}

		@Override
		public HttpExecutionResponse executeBinary(
			String method,
			String url,
			List<HeaderEntryState> headers,
			String body,
			List<FormEntryState> formData,
			String binaryFilePath,
			HttpPayloadType payloadType,
			int timeoutMillis
		) throws IOException, InterruptedException {
			executeBinaryCalls++;
			binaryTimeoutMillis = timeoutMillis;
			capture(method, url, headers, body, formData, binaryFilePath, payloadType);
			if (failure instanceof IOException ioException) {
				throw ioException;
			}
			if (failure instanceof InterruptedException interruptedException) {
				throw interruptedException;
			}
			return response;
		}

		private void capture(
			String method,
			String url,
			List<HeaderEntryState> headers,
			String body,
			List<FormEntryState> formData,
			String binaryFilePath,
			HttpPayloadType payloadType
		) {
			this.method = method;
			this.url = url;
			this.headers = headers;
			this.body = body;
			this.formData = formData;
			this.binaryFilePath = binaryFilePath;
			this.payloadType = payloadType;
		}
	}

	private static final class CapturingGrpcExecutor extends GrpcExecutor {
		private String target;
		private String service;
		private String method;
		private int timeoutMillis;

		@Override
		public GrpcExecutionResponse execute(
			String target,
			String service,
			String method,
			String payload,
			List<HeaderEntryState> metadata,
			int timeoutMillis
		) {
			return execute(target, service, method, payload, metadata, timeoutMillis, null);
		}

		@Override
		public GrpcExecutionResponse execute(
			String target,
			String service,
			String method,
			String payload,
			List<HeaderEntryState> metadata,
			int timeoutMillis,
			Consumer<GrpcExecutionResponse> serverMessageConsumer
		) {
			this.target = target;
			this.service = service;
			this.method = method;
			this.timeoutMillis = timeoutMillis;
			return new GrpcExecutionResponse(0, "OK", Map.of(), "{}");
		}
	}

	private static final class CapturingKafkaMessageProducer extends KafkaMessageProducer {
		private KafkaSendRequest request;

		@Override
		public KafkaSendResult send(KafkaSendRequest request) {
			this.request = request;
			return new KafkaSendResult(request.topic, 0, 1, 2, 0, 0, 0);
		}
	}
}
