package com.non_organic_onion.intelli.webrunner.execution;

import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.script.VarsStore;

import java.util.List;
import java.util.Map;

public record HttpStressRequest(
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
	VarsStore chainContext,
	Map<String, Object> chainRequests
) {
	public HttpStressRequest(
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
		VarsStore chainContext
	) {
		this(method, url, headers, params, body, before, after, payloadType, formData, binaryFilePath, chainContext, Map.of());
	}

	public HttpStressRequest(
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
		this(method, url, headers, params, body, before, after, payloadType, formData, binaryFilePath, null, Map.of());
	}
}
