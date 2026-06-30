package com.intelli.webrunner.util;

import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;

import java.util.ArrayList;
import java.util.List;

public final class CurlRequest {
	public String method = "GET";
	public String url = "";
	public String payloadType = "RAW";
	public String body = "";
	public String binaryFilePath = "";
	public List<HeaderEntryState> headers = new ArrayList<>();
	public List<HeaderEntryState> params = new ArrayList<>();
	public List<FormEntryState> formData = new ArrayList<>();
}
