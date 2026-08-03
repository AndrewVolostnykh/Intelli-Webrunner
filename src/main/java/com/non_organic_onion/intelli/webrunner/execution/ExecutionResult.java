package com.non_organic_onion.intelli.webrunner.execution;

import java.util.List;

/**
 * Outcome of executing a single HTTP or gRPC request together with its before/after scripts.
 */
public class ExecutionResult {

	public final int statusCode;
	public final String statusMessage;
	public final String responseBody;
	public final String responseHeaders;
	public final String responseCookies;
	public final String logs;
	public final long durationMillis;
	public final String rawRequestSnapshot;
	public final String sentRequestSnapshot;
	public final String responseSnapshot;
	public final String flowStatus;

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String logs
	) {
		this(statusCode, statusMessage, responseBody, responseHeaders, "", logs, -1);
	}

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String responseCookies,
		String logs
	) {
		this(statusCode, statusMessage, responseBody, responseHeaders, responseCookies, logs, -1);
	}

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String logs,
		long durationMillis
	) {
		this(statusCode, statusMessage, responseBody, responseHeaders, "", logs, durationMillis);
	}

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String responseCookies,
		String logs,
		long durationMillis
	) {
		this(
			statusCode,
			statusMessage,
			responseBody,
			responseHeaders,
			responseCookies,
			logs,
			durationMillis,
			"",
			"",
			"",
			""
		);
	}

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String responseCookies,
		String logs,
		long durationMillis,
		String rawRequestSnapshot,
		String sentRequestSnapshot,
		String responseSnapshot
	) {
		this(
			statusCode,
			statusMessage,
			responseBody,
			responseHeaders,
			responseCookies,
			logs,
			durationMillis,
			rawRequestSnapshot,
			sentRequestSnapshot,
			responseSnapshot,
			""
		);
	}

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String responseCookies,
		String logs,
		long durationMillis,
		String rawRequestSnapshot,
		String sentRequestSnapshot,
		String responseSnapshot,
		String flowStatus
	) {
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
		this.responseBody = responseBody;
		this.responseHeaders = responseHeaders;
		this.responseCookies = responseCookies;
		this.logs = logs;
		this.durationMillis = durationMillis;
		this.rawRequestSnapshot = rawRequestSnapshot;
		this.sentRequestSnapshot = sentRequestSnapshot;
		this.responseSnapshot = responseSnapshot;
		this.flowStatus = flowStatus == null ? "" : flowStatus;
	}

	public static ExecutionResult failure(List<String> logs) {
		return new ExecutionResult(0, "", "", "{}", "", String.join("\n", logs));
	}
}
