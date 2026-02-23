package com.intelli.webrunner.execution;

import com.intelli.webrunner.state.HeaderEntryState;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpExecutor {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpExecutionResponse execute(String method, String url, List<HeaderEntryState> headers, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        HttpRequest.BodyPublisher publisher = (body == null || body.isBlank())
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        builder.method(method.toUpperCase(), publisher);

        if (headers != null) {
            for (HeaderEntryState header : headers) {
                if (header == null || !header.enabled) {
                    continue;
                }
                if (header.name == null || header.name.isBlank()) {
                    continue;
                }
                builder.header(header.name, header.value == null ? "" : header.value);
            }
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, List<String>> responseHeaders = new HashMap<>(response.headers().map());
        return new HttpExecutionResponse(response.statusCode(), responseHeaders, response.body() == null ? "" : response.body());
    }
}
