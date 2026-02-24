package com.intelli.webrunner.execution;

import com.intelli.webrunner.state.FormEntryState;
import com.intelli.webrunner.state.HeaderEntryState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HttpExecutor {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpExecutionResponse execute(String method, String url, List<HeaderEntryState> headers, String body)
            throws IOException, InterruptedException {
        return execute(method, url, headers, body, List.of(), null, HttpPayloadType.RAW);
    }

    public HttpExecutionResponse execute(
            String method,
            String url,
            List<HeaderEntryState> headers,
            String body,
            List<FormEntryState> formData,
            String binaryFilePath,
            HttpPayloadType payloadType
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        HttpRequest.BodyPublisher publisher;
        HttpPayloadType resolved = payloadType == null ? HttpPayloadType.RAW : payloadType;
        List<HeaderEntryState> mutableHeaders = headers == null ? new ArrayList<>() : new ArrayList<>(headers);

        if (resolved == HttpPayloadType.FORM_DATA) {
            String boundary = "WebrunnerBoundary" + UUID.randomUUID();
            publisher = buildMultipartPublisher(formData, boundary);
            ensureHeader(mutableHeaders, "Content-Type", "multipart/form-data; boundary=" + boundary);
        } else if (resolved == HttpPayloadType.BINARY) {
            if (binaryFilePath == null || binaryFilePath.isBlank()) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else {
                Path path = Path.of(binaryFilePath);
                publisher = HttpRequest.BodyPublishers.ofFile(path);
            }
            ensureHeader(mutableHeaders, "Content-Type", "application/octet-stream");
        } else {
            publisher = (body == null || body.isBlank())
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
        }

        builder.method(method.toUpperCase(), publisher);

        for (HeaderEntryState header : mutableHeaders) {
            if (header == null || !header.enabled) {
                continue;
            }
            if (header.name == null || header.name.isBlank()) {
                continue;
            }
            builder.header(header.name, header.value == null ? "" : header.value);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, List<String>> responseHeaders = new HashMap<>(response.headers().map());
        return new HttpExecutionResponse(response.statusCode(), responseHeaders, response.body() == null ? "" : response.body());
    }

    public HttpExecutionResponse executeBinary(
            String method,
            String url,
            List<HeaderEntryState> headers,
            String body,
            List<FormEntryState> formData,
            String binaryFilePath,
            HttpPayloadType payloadType
    ) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        HttpRequest.BodyPublisher publisher;
        HttpPayloadType resolved = payloadType == null ? HttpPayloadType.RAW : payloadType;
        List<HeaderEntryState> mutableHeaders = headers == null ? new ArrayList<>() : new ArrayList<>(headers);

        if (resolved == HttpPayloadType.FORM_DATA) {
            String boundary = "WebrunnerBoundary" + UUID.randomUUID();
            publisher = buildMultipartPublisher(formData, boundary);
            ensureHeader(mutableHeaders, "Content-Type", "multipart/form-data; boundary=" + boundary);
        } else if (resolved == HttpPayloadType.BINARY) {
            if (binaryFilePath == null || binaryFilePath.isBlank()) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else {
                Path path = Path.of(binaryFilePath);
                publisher = HttpRequest.BodyPublishers.ofFile(path);
            }
            ensureHeader(mutableHeaders, "Content-Type", "application/octet-stream");
        } else {
            publisher = (body == null || body.isBlank())
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body);
        }

        builder.method(method.toUpperCase(), publisher);

        for (HeaderEntryState header : mutableHeaders) {
            if (header == null || !header.enabled) {
                continue;
            }
            if (header.name == null || header.name.isBlank()) {
                continue;
            }
            builder.header(header.name, header.value == null ? "" : header.value);
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Map<String, List<String>> responseHeaders = new HashMap<>(response.headers().map());
        byte[] bytes = response.body();
        String textBody = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        return new HttpExecutionResponse(response.statusCode(), responseHeaders, textBody, bytes);
    }

    private void ensureHeader(List<HeaderEntryState> headers, String name, String value) {
        for (HeaderEntryState header : headers) {
            if (header == null || header.name == null) {
                continue;
            }
            if (header.name.equalsIgnoreCase(name)) {
                return;
            }
        }
        HeaderEntryState entry = new HeaderEntryState();
        entry.name = name;
        entry.value = value;
        entry.enabled = true;
        headers.add(entry);
    }

    private HttpRequest.BodyPublisher buildMultipartPublisher(
            List<FormEntryState> entries,
            String boundary
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8);
        if (entries != null) {
            for (FormEntryState entry : entries) {
                if (entry == null || !entry.enabled) {
                    continue;
                }
                String name = entry.name == null ? "" : entry.name;
                if (name.isBlank()) {
                    continue;
                }
                output.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
                output.write(lineBreak);
                if (entry.file) {
                    String pathValue = entry.value == null ? "" : entry.value;
                    if (pathValue.isBlank()) {
                        continue;
                    }
                    Path path = Path.of(pathValue);
                    String filename = path.getFileName() == null ? "file" : path.getFileName().toString();
                    output.write(
                            ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"")
                                    .getBytes(StandardCharsets.UTF_8)
                    );
                    output.write(lineBreak);
                    String contentType = Files.probeContentType(path);
                    if (contentType == null || contentType.isBlank()) {
                        contentType = "application/octet-stream";
                    }
                    output.write(("Content-Type: " + contentType).getBytes(StandardCharsets.UTF_8));
                    output.write(lineBreak);
                    output.write(lineBreak);
                    output.write(Files.readAllBytes(path));
                    output.write(lineBreak);
                } else {
                    String value = entry.value == null ? "" : entry.value;
                    output.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes(StandardCharsets.UTF_8));
                    output.write(lineBreak);
                    output.write(lineBreak);
                    output.write(value.getBytes(StandardCharsets.UTF_8));
                    output.write(lineBreak);
                }
            }
        }
        output.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        output.write(lineBreak);
        return HttpRequest.BodyPublishers.ofByteArray(output.toByteArray());
    }
}
