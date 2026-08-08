package com.non_organic_onion.intelli.webrunner.execution;

import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMessageProducer;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaSendRequest;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaSendResult;
import com.non_organic_onion.intelli.webrunner.script.GlobalContextRuntime;
import com.non_organic_onion.intelli.webrunner.script.ScriptContext;
import com.non_organic_onion.intelli.webrunner.script.ScriptFlowControl;
import com.non_organic_onion.intelli.webrunner.script.ScriptHelpers;
import com.non_organic_onion.intelli.webrunner.script.ScriptLogger;
import com.non_organic_onion.intelli.webrunner.script.ScriptRequest;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.util.JsonUtils;
import com.non_organic_onion.intelli.webrunner.util.HttpStatusReasons;
import com.non_organic_onion.intelli.webrunner.util.PayloadTypes;
import com.non_organic_onion.intelli.webrunner.util.ResponseCookieUtils;
import com.non_organic_onion.intelli.webrunner.util.StateCopyUtils;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import com.non_organic_onion.intelli.webrunner.util.UrlParamUtils;

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
	private static final int DEFAULT_TIMEOUT_MILLIS = 0;

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
		return executeWithScripts(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			forChain,
			sharedVars,
			null,
			payloadType,
			formData,
			binaryFilePath,
			DEFAULT_TIMEOUT_MILLIS
		);
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
		String binaryFilePath,
		int timeoutMillis
	) {
		return executeWithScripts(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			forChain,
			sharedVars,
			null,
			payloadType,
			formData,
			binaryFilePath,
			timeoutMillis
		);
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
		VarsStore chainContext,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		return executeWithScripts(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			forChain,
			sharedVars,
			chainContext,
			Map.of(),
			payloadType,
			formData,
			binaryFilePath,
			DEFAULT_TIMEOUT_MILLIS
		);
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
		VarsStore chainContext,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath,
		int timeoutMillis
	) {
		return executeWithScripts(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			forChain,
			sharedVars,
			chainContext,
			Map.of(),
			payloadType,
			formData,
			binaryFilePath,
			timeoutMillis
		);
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
		VarsStore chainContext,
		Map<String, Object> chainRequests,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		return executeWithScripts(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			forChain,
			sharedVars,
			chainContext,
			chainRequests,
			payloadType,
			formData,
			binaryFilePath,
			DEFAULT_TIMEOUT_MILLIS
		);
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
		VarsStore chainContext,
		Map<String, Object> chainRequests,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath,
		int timeoutMillis
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		VarsStore chainVars = chainContext == null ? new VarsStore() : chainContext;
		ScriptFlowControl flowControl = new ScriptFlowControl();
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
				new ScriptContext(
					vars,
					logger,
					helpers,
					scriptRequest,
					rawRequest,
					null,
					globalContext,
					chainVars,
					chainRequests,
					flowControl
				)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}
		ExecutionResult beforeControlResult = controlResult(flowControl, logs, startedAt, true);
		if (beforeControlResult != null) {
			globalContextRuntime.persist(globalContext);
			return beforeControlResult;
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, chainVars, vars);
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
		String rawRequestSnapshot = buildHttpRequestSnapshot(method, url, rawRequest);
		ScriptRequest sentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
		sentRequest.setFormData(StateCopyUtils.cloneFormData(templatedFormData));
		sentRequest.setBinaryFilePath(templatedBinaryPath);
		String sentRequestSnapshot = buildHttpRequestSnapshot(method, templatedUrl, sentRequest);

		try {
			HttpExecutionResponse response =
				httpExecutor.execute(
					method,
					templatedUrl,
					templatedHeaders,
					templatedBody,
					templatedFormData,
					templatedBinaryPath,
					PayloadTypes.resolveType(payloadType),
					normalizeTimeout(timeoutMillis)
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
						globalContext,
						chainVars,
						chainRequests,
						flowControl
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			globalContextRuntime.persist(globalContext);
			String responseHeaders = JsonUtils.toJson(response.headers);
			String responseCookies = JsonUtils.toJson(ResponseCookieUtils.extractCookies(response.headers));
			String responseSnapshot = buildResponseSnapshot(
				response.statusCode,
				HttpStatusReasons.reason(response.statusCode),
				response.body,
				response.headers,
				ResponseCookieUtils.extractCookies(response.headers)
			);
			return withDuration(new ExecutionResult(
				response.statusCode,
				HttpStatusReasons.reason(response.statusCode),
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				responseCookies,
				String.join("\n", logs),
				-1,
				rawRequestSnapshot,
				sentRequestSnapshot,
				responseSnapshot,
				flowStatus(flowControl)
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
		return executeWithScriptsDownload(
			method,
			url,
			headers,
			params,
			body,
			before,
			after,
			payloadType,
			formData,
			binaryFilePath,
			DEFAULT_TIMEOUT_MILLIS
		);
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
		String binaryFilePath,
		int timeoutMillis
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
					PayloadTypes.resolveType(payloadType),
					normalizeTimeout(timeoutMillis)
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
			String responseCookies = JsonUtils.toJson(ResponseCookieUtils.extractCookies(response.headers));
			ExecutionResult result = withDuration(new ExecutionResult(
				response.statusCode,
				HttpStatusReasons.reason(response.statusCode),
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				responseCookies,
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
		return executeGrpcWithScripts(details, headers, params, body, before, after, sharedVars, null);
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars,
		int timeoutMillis
	) {
		return executeGrpcWithScripts(details, headers, params, body, before, after, sharedVars, null, Map.of(), timeoutMillis);
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars,
		VarsStore chainContext
	) {
		return executeGrpcWithScripts(details, headers, params, body, before, after, sharedVars, chainContext, Map.of());
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars,
		VarsStore chainContext,
		int timeoutMillis
	) {
		return executeGrpcWithScripts(details, headers, params, body, before, after, sharedVars, chainContext, Map.of(), timeoutMillis);
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars,
		VarsStore chainContext,
		Map<String, Object> chainRequests
	) {
		return executeGrpcWithScripts(
			details,
			headers,
			params,
			body,
			before,
			after,
			sharedVars,
			chainContext,
			chainRequests,
			DEFAULT_TIMEOUT_MILLIS
		);
	}

	public ExecutionResult executeGrpcWithScripts(
		RequestDetailsState details,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String before,
		String after,
		VarsStore sharedVars,
		VarsStore chainContext,
		Map<String, Object> chainRequests,
		int timeoutMillis
	) {
		long startedAt = System.nanoTime();
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		VarsStore chainVars = chainContext == null ? new VarsStore() : chainContext;
		ScriptFlowControl flowControl = new ScriptFlowControl();
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		VarsStore globalContext = loadGlobalContext(logger, logs);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(
					vars,
					logger,
					helpers,
					scriptRequest,
					rawRequest,
					null,
					globalContext,
					chainVars,
					chainRequests,
					flowControl
				)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			globalContextRuntime.persist(globalContext);
			return withDuration(ExecutionResult.failure(logs), startedAt);
		}
		ExecutionResult beforeControlResult = controlResult(flowControl, logs, startedAt, true);
		if (beforeControlResult != null) {
			globalContextRuntime.persist(globalContext);
			return beforeControlResult;
		}

		Map<String, Object> varsSnapshot = globalContextRuntime.mergeForTemplates(globalContext, chainVars, vars);
		String templatedBody = templateEngine.applyToBody(scriptRequest.getBody(), varsSnapshot);
		List<HeaderEntryState> templatedHeaders =
			templateEngine.applyToHeaders(scriptRequest.getHeaders(), varsSnapshot);
		List<HeaderEntryState> templatedParams = templateEngine.applyToParams(scriptRequest.getParams(), varsSnapshot);
		RequestDetailsState templatedDetails = templatedGrpcDetails(details, varsSnapshot);
		String rawRequestSnapshot = buildGrpcRequestSnapshot(details, rawRequest);
		ScriptRequest sentRequest = new ScriptRequest(templatedBody, templatedHeaders, templatedParams);
		String sentRequestSnapshot = buildGrpcRequestSnapshot(templatedDetails, sentRequest);

		try {
			GrpcExecutionResponse response = grpcExecutor.execute(templatedDetails.target,
																  templatedDetails.service,
																  templatedDetails.grpcMethod,
																  templatedBody,
																  templatedHeaders,
																  normalizeTimeout(timeoutMillis),
																  message -> {
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
																				  message,
																				  globalContext,
																				  chainVars,
																				  chainRequests,
																				  flowControl
																			  )
																		  );
																	  } catch (Exception error) {
																		  logs.add("On Message error: " + error.getMessage());
																	  }
																  }
			);
			if (!response.serverStreaming) {
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
							globalContext,
							chainVars,
							chainRequests,
							flowControl
						)
					);
				} catch (Exception error) {
					logs.add("After request error: " + error.getMessage());
				}
			}
			globalContextRuntime.persist(globalContext);
			String responseHeaders = JsonUtils.toJson(response.headers);
			String responseSnapshot = buildResponseSnapshot(
				response.statusCode,
				response.statusMessage,
				response.body,
				response.headers,
				List.of()
			);
			return withDuration(new ExecutionResult(
				response.statusCode,
				response.statusMessage,
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				"",
				String.join("\n", logs),
				-1,
				rawRequestSnapshot,
				sentRequestSnapshot,
				responseSnapshot,
				flowStatus(flowControl)
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
		return executeKafkaWithScripts(
			details,
			headers,
			body,
			before,
			after,
			keyType,
			bodyType,
			partition,
			sharedVars,
			DEFAULT_TIMEOUT_MILLIS
		);
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
		VarsStore sharedVars,
		int timeoutMillis
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
			request.timeoutMillis = normalizeTimeout(timeoutMillis);
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

	private String buildHttpRequestSnapshot(
		String method,
		String url,
		ScriptRequest request
	) {
		Map<String, Object> snapshot = buildRequestSnapshot(request);
		snapshot.put("method", method == null ? "GET" : method);
		snapshot.put("url", url == null ? "" : url);
		return JsonUtils.toJson(snapshot);
	}

	private String buildGrpcRequestSnapshot(
		RequestDetailsState details,
		ScriptRequest request
	) {
		Map<String, Object> snapshot = buildRequestSnapshot(request);
		snapshot.put("target", details == null || details.target == null ? "" : details.target);
		snapshot.put("service", details == null || details.service == null ? "" : details.service);
		snapshot.put("method", details == null || details.grpcMethod == null ? "" : details.grpcMethod);
		return JsonUtils.toJson(snapshot);
	}

	private RequestDetailsState templatedGrpcDetails(
		RequestDetailsState details,
		Map<String, Object> varsSnapshot
	) {
		RequestDetailsState templated = new RequestDetailsState();
		if (details == null) {
			return templated;
		}
		templated.requestId = details.requestId;
		templated.type = details.type;
		templated.method = details.method;
		templated.payloadType = details.payloadType;
		templated.url = details.url;
		templated.target = templateEngine.applyToText(details.target, varsSnapshot);
		templated.service = templateEngine.applyToText(details.service, varsSnapshot);
		templated.grpcMethod = templateEngine.applyToText(details.grpcMethod, varsSnapshot);
		templated.kafkaBootstrapServers = details.kafkaBootstrapServers;
		templated.kafkaTopic = details.kafkaTopic;
		templated.kafkaKey = details.kafkaKey;
		templated.kafkaGroupId = details.kafkaGroupId;
		return templated;
	}

	private Map<String, Object> buildRequestSnapshot(ScriptRequest request) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		if (request == null) {
			snapshot.put("body", "");
			snapshot.put("params", List.of());
			snapshot.put("headers", List.of());
			snapshot.put("formData", List.of());
			snapshot.put("binaryFilePath", "");
			return snapshot;
		}
		snapshot.put("body", request.getBody() == null ? "" : request.getBody());
		snapshot.put("params", request.getParams() == null ? List.of() : request.getParams());
		snapshot.put("headers", request.getHeaders() == null ? List.of() : request.getHeaders());
		snapshot.put("formData", request.getFormData() == null ? List.of() : request.getFormData());
		snapshot.put("binaryFilePath", request.getBinaryFilePath() == null ? "" : request.getBinaryFilePath());
		return snapshot;
	}

	private String buildResponseSnapshot(
		int statusCode,
		String statusMessage,
		String body,
		Object headers,
		Object cookies
	) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("statusCode", statusCode);
		snapshot.put("statusMessage", statusMessage == null ? "" : statusMessage);
		snapshot.put("body", JsonUtils.prettyPrint(body));
		snapshot.put("headers", headers == null ? Map.of() : headers);
		snapshot.put("cookies", cookies == null ? List.of() : cookies);
		return JsonUtils.toJson(snapshot);
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
			result.responseCookies,
			result.logs,
			durationMillis,
			result.rawRequestSnapshot,
			result.sentRequestSnapshot,
			result.responseSnapshot,
			result.flowStatus
		);
	}

	private int normalizeTimeout(int timeoutMillis) {
		return Math.max(0, timeoutMillis);
	}

	private ExecutionResult controlResult(
		ScriptFlowControl flowControl,
		List<String> logs,
		long startedAt,
		boolean beforeSend
	) {
		if (flowControl == null || flowControl.action() == ScriptFlowControl.Action.PROCEED) {
			return null;
		}
		String status = flowStatus(flowControl);
		String message = beforeSend
			? status + " before request send."
			: status + " after request send.";
		logs.add(message);
		return withDuration(new ExecutionResult(
			0,
			status,
			"",
			"{}",
			"",
			String.join("\n", logs),
			-1,
			"",
			"",
			"",
			status
		), startedAt);
	}

	private String flowStatus(ScriptFlowControl flowControl) {
		if (flowControl == null) {
			return "";
		}
		if (flowControl.interrupted()) {
			return "Interrupted";
		}
		if (flowControl.skipped()) {
			return "Skipped";
		}
		return "";
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
