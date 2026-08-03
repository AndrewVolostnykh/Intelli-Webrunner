package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.execution.HttpPayloadType;
import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;

import java.util.List;

/**
 * Builds a POSIX-shell compatible cURL command from an HTTP request.
 */
public final class CurlCommandBuilder {

	private CurlCommandBuilder() {
	}

	public static String build(
		String method,
		String url,
		List<HeaderEntryState> headers,
		List<HeaderEntryState> params,
		String body,
		String payloadType,
		List<FormEntryState> formData,
		String binaryFilePath
	) {
		String resolvedUrl = UrlParamUtils.applyDefaultProtocol(
			UrlParamUtils.applyQueryParams(url, params)
		);
		HttpPayloadType resolvedPayloadType = PayloadTypes.resolveType(payloadType);
		StringBuilder command = new StringBuilder("curl");
		command.append(" -X ").append(shellQuote(normalizeMethod(method)));

		boolean hasContentType = false;
		if (headers != null) {
			for (HeaderEntryState header : headers) {
				if (header == null || !header.enabled || header.name == null || header.name.isBlank()) {
					continue;
				}
				String name = header.name.trim();
				if ("Content-Type".equalsIgnoreCase(name)) {
					hasContentType = true;
				}
				command.append(" -H ").append(shellQuote(name + ": " + safe(header.value)));
			}
		}

		if (resolvedPayloadType == HttpPayloadType.FORM_DATA) {
			appendFormData(command, formData);
		} else if (resolvedPayloadType == HttpPayloadType.BINARY) {
			if (!hasContentType) {
				command.append(" -H ").append(shellQuote("Content-Type: application/octet-stream"));
			}
			if (binaryFilePath != null && !binaryFilePath.isBlank()) {
				command.append(" --data-binary ").append(shellQuote("@" + binaryFilePath));
			}
		} else if (body != null && !body.isBlank()) {
			command.append(" --data-raw ").append(shellQuote(body));
		}

		command.append(' ').append(shellQuote(resolvedUrl));
		return command.toString();
	}

	static String shellQuote(String value) {
		return "'" + safe(value).replace("'", "'\"'\"'") + "'";
	}

	private static void appendFormData(StringBuilder command, List<FormEntryState> formData) {
		if (formData == null) {
			return;
		}
		for (FormEntryState entry : formData) {
			if (entry == null || !entry.enabled || entry.name == null || entry.name.isBlank()) {
				continue;
			}
			String value = safe(entry.value);
			String formValue = entry.name.trim() + "=" + (entry.file ? "@" : "") + value;
			command.append(" -F ").append(shellQuote(formValue));
		}
	}

	private static String normalizeMethod(String method) {
		return method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
