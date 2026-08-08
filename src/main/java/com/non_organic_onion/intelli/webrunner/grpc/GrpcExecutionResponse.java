package com.non_organic_onion.intelli.webrunner.grpc;

import java.util.List;
import java.util.Map;

public class GrpcExecutionResponse {
    public final int statusCode;
    public final String statusMessage;
    public final Map<String, List<String>> headers;
    public final String body;
    public final boolean serverStreaming;

    public GrpcExecutionResponse(int statusCode, String statusMessage, Map<String, List<String>> headers, String body) {
        this(statusCode, statusMessage, headers, body, false);
    }

    public GrpcExecutionResponse(
        int statusCode,
        String statusMessage,
        Map<String, List<String>> headers,
        String body,
        boolean serverStreaming
    ) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
        this.body = body;
        this.serverStreaming = serverStreaming;
    }
}
