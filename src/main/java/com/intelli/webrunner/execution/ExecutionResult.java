package com.intelli.webrunner.execution;

import java.util.List;

/**
 * Outcome of executing a single HTTP or gRPC request together with its before/after scripts.
 */
public class ExecutionResult {

	public final int statusCode;
	public final String statusMessage;
	public final String responseBody;
	public final String responseHeaders;
	public final String logs;

	public ExecutionResult(
		int statusCode,
		String statusMessage,
		String responseBody,
		String responseHeaders,
		String logs
	) {
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
		this.responseBody = responseBody;
		this.responseHeaders = responseHeaders;
		this.logs = logs;
	}

	public static ExecutionResult failure(List<String> logs) {
		return new ExecutionResult(0, "", "", "{}", String.join("\n", logs));
	}
}
