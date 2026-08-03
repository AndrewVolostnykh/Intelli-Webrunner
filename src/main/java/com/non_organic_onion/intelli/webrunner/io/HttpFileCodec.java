package com.non_organic_onion.intelli.webrunner.io;

import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.util.FileNameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes IntelliJ {@code .http} request files. Pure parsing/serialization with no UI
 * or persistence dependencies.
 */
public final class HttpFileCodec {

	private HttpFileCodec() {
	}

	public static List<HttpFileRequest> parse(File file) throws IOException {
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		List<HttpFileRequest> requests = new ArrayList<>();
		List<String> block = new ArrayList<>();
		String pendingName = null;
		boolean hadDelimiter = false;
		for (String line : lines) {
			String trimmed = line == null ? "" : line.trim();
			if (trimmed.startsWith("###")) {
				hadDelimiter = true;
				if (!block.isEmpty()) {
					HttpFileRequest parsed = parseBlock(pendingName, block);
					if (parsed != null) {
						requests.add(parsed);
					}
					block.clear();
				}
				String name = trimmed.substring(3).trim();
				pendingName = name.isEmpty() ? null : name;
				continue;
			}
			block.add(line);
		}
		if (!block.isEmpty() || !hadDelimiter) {
			HttpFileRequest parsed = parseBlock(pendingName, block);
			if (parsed != null) {
				requests.add(parsed);
			}
		}
		return requests;
	}

	public static HttpFileRequest parseBlock(
		String name,
		List<String> lines
	) {
		int index = 0;
		while (index < lines.size()) {
			String line = lines.get(index);
			if (line == null || line.trim().isEmpty() || FileNameUtils.isHttpComment(line)) {
				index++;
				continue;
			}
			break;
		}
		if (index >= lines.size()) {
			return null;
		}
		String requestLine = lines.get(index).trim();
		String[] parts = requestLine.split("\\s+");
		if (parts.length < 2) {
			return null;
		}
		String method = parts[0].trim();
		String url = parts[1].trim();
		index++;
		List<HeaderEntryState> headers = new ArrayList<>();
		while (index < lines.size()) {
			String line = lines.get(index);
			if (line == null || line.trim().isEmpty()) {
				index++;
				break;
			}
			if (FileNameUtils.isHttpComment(line)) {
				index++;
				continue;
			}
			int colon = line.indexOf(':');
			if (colon > 0) {
				String headerName = line.substring(0, colon).trim();
				String headerValue = line.substring(colon + 1).trim();
				if (!headerName.isEmpty()) {
					HeaderEntryState header = new HeaderEntryState();
					header.id = UUID.randomUUID().toString();
					header.name = headerName;
					header.value = headerValue;
					header.enabled = true;
					headers.add(header);
				}
			}
			index++;
		}
		String body = "";
		if (index < lines.size()) {
			body = String.join("\n", lines.subList(index, lines.size()));
		}
		HttpFileRequest request = new HttpFileRequest();
		request.name = name;
		request.method = method;
		request.url = url;
		request.headers = headers;
		request.body = body;
		return request;
	}

	/**
	 * Serializes one request into a {@code .http} block, including the {@code ### name} delimiter
	 * when a name is present. {@code url} should already include any query parameters.
	 */
	public static String buildBlock(
		String name,
		String method,
		String url,
		String body,
		List<HeaderEntryState> headers
	) {
		StringBuilder builder = new StringBuilder();
		String safeName = name == null ? "" : name;
		if (!safeName.isBlank()) {
			builder.append("### ").append(safeName).append("\n");
		}
		builder.append(method).append(" ").append(url).append("\n");
		if (headers != null) {
			for (HeaderEntryState header : headers) {
				if (header == null || !header.enabled) {
					continue;
				}
				String headerName = header.name == null ? "" : header.name.trim();
				if (headerName.isEmpty()) {
					continue;
				}
				String headerValue = header.value == null ? "" : header.value;
				builder.append(headerName).append(": ").append(headerValue).append("\n");
			}
		}
		if (body != null && !body.isBlank()) {
			builder.append("\n");
			builder.append(body);
			if (!body.endsWith("\n")) {
				builder.append("\n");
			}
		}
		return builder.toString();
	}
}
