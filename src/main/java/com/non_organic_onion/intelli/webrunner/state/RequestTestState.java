package com.non_organic_onion.intelli.webrunner.state;

import java.util.ArrayList;
import java.util.List;

public class RequestTestState {
    public String id;
    public String name;
    public boolean disabled;
    public String resultStatus;
    public String resultDetails;
    public String requestBody;
    public List<HeaderEntryState> requestHeaders = new ArrayList<>();
    public List<HeaderEntryState> requestParams = new ArrayList<>();
    public List<FormEntryState> formData = new ArrayList<>();
    public String binaryFilePath;
    public String beforeScript;
    public String afterScript;
    public String responseBody;
    public String responseHeaders;
    public String responseCookies;
    public String logs;
}
