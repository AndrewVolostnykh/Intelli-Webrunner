package com.intelli.webrunner.execution;

import com.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.intelli.webrunner.grpc.GrpcExecutor;
import com.intelli.webrunner.kafka.KafkaMessageProducer;
import com.intelli.webrunner.kafka.KafkaSendRequest;
import com.intelli.webrunner.kafka.KafkaSendResult;
import com.intelli.webrunner.script.GlobalContextRuntime;
import com.intelli.webrunner.script.ScriptContext;
import com.intelli.webrunner.script.ScriptHelpers;
import com.intelli.webrunner.script.ScriptLogger;
import com.intelli.webrunner.script.ScriptRequest;
import com.intelli.webrunner.script.ScriptRuntime;
import com.intelli.webrunner.script.VarsStore;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.util.JsonUtils;
import com.intelli.webrunner.util.PayloadTypes;
import com.intelli.webrunner.util.StateCopyUtils;
import com.intelli.webrunner.util.TemplateEngine;
import com.intelli.webrunner.util.UrlParamUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a request through the full pipeline: before-script, templating, transport (HTTP or gRPC),
 * then after-script. Contains no Swing or project dependencies, so it can be reused by the request
 * editor and the chain runner while still resolving the shared persisted global context.
 */
public final class RequestExecutionService {

	private final TemplateEngine templateEngine;
	private final ScriptRuntime scriptRuntime;
	private final HttpExecutor httpExecutor;
	private final GrpcExecutor grpcExecutor;
	private final KafkaMessageProducer kafkaMessageProducer;
	private final GlobalContextRuntime globalContextRuntime;

	public RequestExecutionService(
		TemplateEngine templateEngine,
		ScriptRuntime scriptRuntime,
		HttpExecutor httpExecutor,
		GrpcExecutor grpcExecutor,
		KafkaMessageProducer kafkaMessageProducer,
		GlobalWebrunnerStateService stateService
	) {
		this.templateEngine = templateEngine;
		this.scriptRuntime = scriptRuntime;
		this.httpExecutor = httpExecutor;
		this.grpcExecutor = grpcExecutor;
		this.kafkaMessageProducer = kafkaMessageProducer;
		this.globalContextRuntime = new GlobalContextRuntime(stateService, scriptRuntime);
	}

	public ExecutionResult executeWithScripts(
		String method,
		String url,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		boolean forChain,
		VarsStore sharedVars,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		VarsStore globalContext = loadGlobalContext(logger, logs);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		rawRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		scriptRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null, globalContext)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, vars);
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);
		List<FormEntryState> templatedFormData = templateEngine.applyToFormData(
			scriptRequest.getFormData(),
			varsSnapshot
		);
		String templatedBinaryPath = templateEngine.applyToText(
			scriptRequest.getBinaryFilePath(),
			varsSnapshot
		);
		String templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
		String templatedUrl = UrlParamUtils.applyDefaultProtocol(
			UrlParamUtils.applyQueryParams(templatedUrlBase, templatedParams)
		);

		try {
			HttpExecutionResponse response =
				httpExecutor.execute(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					templatedFormData,
					templatedBinaryPath,
					PayloadTypes.resolveType(payloadType)
				);
			try {
				ScriptRequest afterRequest = new ScriptRequest(
					templatedBody,
					templatedHeaders,
					templatedParams
				);
				afterRequest.setFormData(StateCopyUtils.cloneFormData(templatedFormData));
				afterRequest.setBinaryFilePath(templatedBinaryPath);
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						afterRequest,
						rawRequest,
						response,
						globalContext
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			String responseHeaders = JsonUtils.toJson(response.headers);
			return withDuration(new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			), startedAt);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}
	}

	public DownloadResult executeWithScriptsDownload(
		String method,
		String url,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = logs::add;
		ScriptHelpers helpers = new ScriptHelpers(logger);
		VarsStore globalContext = loadGlobalContext(logger, logs);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		rawRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		scriptRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null, globalContext)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return new DownloadResult(withDuration(ExecutionResult.failure(logs), startedAt), null, Map.of());
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, vars);
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);
		List<FormEntryState> templatedFormData = templateEngine.applyToFormData(
			scriptRequest.getFormData(),
			varsSnapshot
		);
		String templatedBinaryPath = templateEngine.applyToText(
			scriptRequest.getBinaryFilePath(),
			varsSnapshot
		);
		String templatedUrlBase = templateEngine.applyToText(url, varsSnapshot);
		String templatedUrl = UrlParamUtils.applyDefaultProtocol(
			UrlParamUtils.applyQueryParams(templatedUrlBase, templatedParams)
		);

		try {
			HttpExecutionResponse response =
				httpExecutor.executeBinary(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					templatedFormData,
					templatedBinaryPath,
					PayloadTypes.resolveType(payloadType)
				);
			try {
				ScriptRequest afterRequest = new ScriptRequest(
					templatedBody,
					templatedHeaders,
					templatedParams
				);
				afterRequest.setFormData(StateCopyUtils.cloneFormData(templatedFormData));
				afterRequest.setBinaryFilePath(templatedBinaryPath);
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						afterRequest,
						rawRequest,
						response,
						globalContext
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			String responseHeaders = JsonUtils.toJson(response.headers);
			ExecutionResult result = withDuration(new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			), startedAt);
			return new DownloadResult(result, response.bodyBytes, response.headers);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return new DownloadResult(withDuration(ExecutionResult.failure(logs), startedAt), null, Map.of());
		}
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		VarsStore globalContext = loadGlobalContext(logger, logs);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null, globalContext)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, vars);
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);

		try {
			GrpcExecutionResponse response = grpcExecutor.execute(details.target,
																  details.service,
																  details.grpcMethod,
																  templatedBody,
																  templatedHeaders
			);
			try {
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						new ScriptRequest(
							templatedBody,
							templatedHeaders,
							templatedParams
						),
						rawRequest,
						response,
						globalContext
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			String responseHeaders = JsonUtils.toJson(response.headers);
			return withDuration(new ExecutionResult(
				response.statusCode,
				response.statusMessage,
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			), startedAt);
		} catch (Exception error) {
			logs.add("gRPC request failed: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}
	}

	public ExecutionResult executeKafkaWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		String body,
		String before,
		String after,
		String keyType,
		String bodyType,
		String partition,
		VarsStore sharedVars
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = logs::add;
		ScriptHelpers helpers = new ScriptHelpers(logger);
		VarsStore globalContext = loadGlobalContext(logger, logs);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), List.of());
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), List.of());

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null, globalContext)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, vars);
		String templatedBootstrapServers = templateEngine.applyToText(details.kafkaBootstrapServers, varsSnapshot);
		String templatedTopic = templateEngine.applyToText(details.kafkaTopic, varsSnapshot);
		String templatedKey = templateEngine.applyToText(details.kafkaKey, varsSnapshot);
		String templatedPartition = templateEngine.applyToText(partition, varsSnapshot);
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);

		try {
			KafkaSendRequest request = new KafkaSendRequest();
			request.bootstrapServers = templatedBootstrapServers;
			request.topic = templatedTopic;
			request.key = templatedKey;
			request.keyType = keyType;
			request.body = templatedBody;
			request.bodyType = bodyType;
			request.partition = templatedPartition;
			request.headers = templatedHeaders;
			KafkaSendResult response = kafkaMessageProducer.send(request);
			try {
				scriptRuntime.runScript(
					after,
					new ScriptContext(
						vars,
						logger,
						helpers,
						new ScriptRequest(templatedBody, templatedHeaders, List.of()),
						rawRequest,
						response,
						globalContext
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			return withDuration(new ExecutionResult(
				200,
				"OK",
				JsonUtils.toJson(buildKafkaResponseBody(
					response,
					templatedKey,
					keyType,
					templatedHeaders,
					templatedBody,
					bodyType,
					templatedPartition
				)),
				"{}",
				String.join("\n", logs)
			), startedAt);
		} catch (Exception error) {
			logs.add("Kafka request failed: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}
	}

	private Map<String, Object> buildKafkaResponseBody(
		KafkaSendResult result,
		String key,
		String keyType,
		List<HeaderEntryState> headers,
		String body,
		String bodyType,
		String requestedPartition
	) {
		Map<String, Object> response = new LinkedHashMap<>();
		Map<String, Object> sent = new LinkedHashMap<>();
		sent.put("key", key == null ? "" : key);
		sent.put("keyType", keyType == null ? "" : keyType);
		sent.put("headers", kafkaHeadersToMaps(headers));
		sent.put("body", body == null ? "" : body);
		sent.put("bodyType", bodyType == null ? "" : bodyType);
		sent.put("partition", requestedPartition == null ? "" : requestedPartition);
		response.put("sent", sent);

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("topic", result.topic);
		metadata.put("partition", result.partition);
		metadata.put("offset", result.offset);
		metadata.put("timestamp", result.timestamp);
		metadata.put("keyBytes", result.keyBytes);
		metadata.put("valueBytes", result.valueBytes);
		metadata.put("headers", result.headers);
		response.put("metadata", metadata);
		return response;
	}

	private List<Map<String, Object>> kafkaHeadersToMaps(List<HeaderEntryState> headers) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (headers == null) {
			return result;
		}
		for (HeaderEntryState header : headers) {
			if (header == null || !header.enabled || header.name == null || header.name.isBlank()) {
				continue;
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("name", header.name.trim());
			entry.put("value", header.value == null ? "" : header.value);
			result.add(entry);
		}
		return result;
	}

	private ExecutionResult withDuration(
		ExecutionResult result,
		long startedAt
	) {
		long durationMillis = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
		return new ExecutionResult(
			result.statusCode,
			result.statusMessage,
			result.responseBody,
			result.responseHeaders,
			result.logs,
			durationMillis
		);
	}

	private VarsStore loadGlobalContext(
		ScriptLogger logger,
		List<String> logs
	) {
		try {
			return globalContextRuntime.loadAndRun(logger);
		} catch (Exception error) {
			logs.add("Global context error: " + error.getMessage());
			return new VarsStore();
		}
	}
}
