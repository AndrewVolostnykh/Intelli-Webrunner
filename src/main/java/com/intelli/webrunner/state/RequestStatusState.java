package com.intelli.webrunner.state;

import java.util.ArrayList;
import java.util.List;

public class RequestStatusState {
    public String requestId;
    public String requestBody;
    public List<HeaderEntryState> requestHeaders = new ArrayList<>();
    public List<HeaderEntryState> requestParams = new ArrayList<>();
    public List<FormEntryState> formData = new ArrayList<>();
    public String binaryFilePath;
    public String responseBody;
    public String responseHeaders;
    public String logs;
    public String beforeScript;
    public String afterScript;
}
