package com.intelli.webrunner.grpc;

import java.util.List;
import java.util.Map;

public class GrpcExecutionResponse {
    public final int statusCode;
    public final String statusMessage;
    public final Map<String, List<String>> headers;
    public final String body;

    public GrpcExecutionResponse(int statusCode, String statusMessage, Map<String, List<String>> headers, String body) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
        this.body = body;
    }
}
