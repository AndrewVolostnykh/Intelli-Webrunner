package com.intelli.webrunner.execution;

import java.util.List;
import java.util.Map;

/**
 * An {@link ExecutionResult} paired with the raw response bytes, used by "Send and Download".
 */
public class DownloadResult {

	public final ExecutionResult result;
	public final byte[] bodyBytes;
	public final Map<String, List<String>> headers;

	public DownloadResult(ExecutionResult result, byte[] bodyBytes, Map<String, List<String>> headers) {
		this.result = result;
		this.bodyBytes = bodyBytes;
		this.headers = headers;
	}

	public static DownloadResult failure(List<String> logs) {
		return new DownloadResult(ExecutionResult.failure(logs), null, Map.of());
	}
}
