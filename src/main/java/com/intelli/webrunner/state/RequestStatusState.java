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
    public String responseCookies;
    public String logs;
    public String beforeScript;
    public String afterScript;
    public String kafkaKeyType;
    public String kafkaBodyType;
    public String kafkaPartitions;
    public String kafkaOffsetStrategy;
    public boolean stressEnabled;
    public String stressRequestsPerSec;
    public String stressTotalDuration;
    public String stressTotalDurationUnit;
    public String stressNumberOfRequests;
    public String stressParallelWorkers;
    public String stressRampUpTime;
    public String stressRampUpTimeUnit;
    public String stressDelayBetweenRequests;
    public String stressDelayBetweenRequestsUnit;
    public String stressJitterFrom;
    public String stressJitterTo;
}
