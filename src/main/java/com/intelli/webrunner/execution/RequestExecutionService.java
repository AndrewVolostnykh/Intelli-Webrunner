package com.intelli.webrunner.execution;

import com.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.intelli.webrunner.grpc.GrpcExecutor;
import com.intelli.webrunner.script.ScriptContext;
import com.intelli.webrunner.script.ScriptHelpers;
import com.intelli.webrunner.script.ScriptLogger;
import com.intelli.webrunner.script.ScriptRequest;
import com.intelli.webrunner.script.ScriptRuntime;
import com.intelli.webrunner.script.VarsStore;
import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.util.JsonUtils;
import com.intelli.webrunner.util.PayloadTypes;
import com.intelli.webrunner.util.StateCopyUtils;
import com.intelli.webrunner.util.TemplateEngine;
import com.intelli.webrunner.util.UrlParamUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs a request through the full pipeline: before-script, templating, transport (HTTP or gRPC),
 * then after-script. Contains no Swing, project, or persistence dependencies, so it can be reused
 * by the request editor, the chain runner, and the step debugger.
 */
public final class RequestExecutionService {

	private final TemplateEngine templateEngine;
	private final ScriptRuntime scriptRuntime;
	private final HttpExecutor httpExecutor;
	private final GrpcExecutor grpcExecutor;

	public RequestExecutionService(
		TemplateEngine templateEngine,
		ScriptRuntime scriptRuntime,
		HttpExecutor httpExecutor,
		GrpcExecutor grpcExecutor
	) {
		this.templateEngine = templateEngine;
		this.scriptRuntime = scriptRuntime;
		this.httpExecutor = httpExecutor;
		this.grpcExecutor = grpcExecutor;
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
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		rawRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		scriptRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
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
		String templatedUrl = UrlParamUtils.applyQueryParams(templatedUrlBase, templatedParams);

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
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = JsonUtils.toJson(response.headers);
			return new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			return ExecutionResult.failure(logs);
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
		VarsStore vars = new VarsStore();
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = logs::add;
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		rawRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		rawRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		scriptRequest.setFormData(StateCopyUtils.cloneFormData(formData));
		scriptRequest.setBinaryFilePath(binaryFilePath == null ? "" : binaryFilePath);

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return DownloadResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
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
		String templatedUrl = UrlParamUtils.applyQueryParams(templatedUrlBase, templatedParams);

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
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = JsonUtils.toJson(response.headers);
			ExecutionResult result = new ExecutionResult(
				response.statusCode,
				"",
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
			return new DownloadResult(result, response.bodyBytes, response.headers);
		} catch (Exception error) {
			logs.add("Request failed: " + error.getMessage());
			return DownloadResult.failure(logs);
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
		VarsStore vars = sharedVars == null ? new VarsStore() : sharedVars;
		List<String> logs = new ArrayList<>();
		ScriptLogger logger = message -> logs.add(message);
		ScriptHelpers helpers = new ScriptHelpers(logger);
		ScriptRequest rawRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));
		ScriptRequest scriptRequest = new ScriptRequest(body, StateCopyUtils.cloneHeaders(headers), StateCopyUtils.cloneHeaders(params));

		try {
			scriptRuntime.runScript(
				before,
				new ScriptContext(vars, logger, helpers, scriptRequest, rawRequest, null)
			);
		} catch (Exception error) {
			logs.add("Before request error: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}

		Map<String, Object> varsSnapshot = vars.entries();
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
						response
					)
				);
			} catch (Exception error) {
				logs.add("After request error: " + error.getMessage());
			}
			String responseHeaders = JsonUtils.toJson(response.headers);
			return new ExecutionResult(
				response.statusCode,
				response.statusMessage,
				JsonUtils.prettyPrint(response.body),
				responseHeaders,
				String.join("\n", logs)
			);
		} catch (Exception error) {
			logs.add("gRPC request failed: " + error.getMessage());
			return ExecutionResult.failure(logs);
		}
	}
}
