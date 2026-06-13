package com.intelli.webrunner.io;

import com.intelli.webrunner.state.HeaderEntryState;

import java.util.List;

/**
 * A single request parsed from an IntelliJ {@code .http} file.
 */
public final class HttpFileRequest {

	public String name;
	public String method;
	public String url;
	public List<HeaderEntryState> headers;
	public String body;
}
