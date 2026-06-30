package com.intelli.webrunner.execution;

import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;

import java.util.List;

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
	String binaryFilePath
) {
}
