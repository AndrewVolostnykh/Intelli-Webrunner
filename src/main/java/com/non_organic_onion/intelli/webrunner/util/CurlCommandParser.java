package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Parses common cURL command variants copied from browsers and API clients.
 */
public final class CurlCommandParser {

	private CurlCommandParser() {
	}

	public static CurlRequest parse(String command) {
		List<String> tokens = tokenize(command);
		if (tokens.isEmpty() || !"curl".equalsIgnoreCase(tokens.get(0))) {
			throw new IllegalArgumentException("Command must start with curl.");
		}

		CurlRequest request = new CurlRequest();
		String explicitMethod = null;
		boolean useGet = false;
		List<String> dataParts = new ArrayList<>();

		for (int index = 1; index < tokens.size(); index++) {
			String token = tokens.get(index);
			if ("-X".equals(token) || "--request".equals(token)) {
				explicitMethod = requireValue(tokens, ++index, token);
			} else if (token.startsWith("--request=")) {
				explicitMethod = token.substring("--request=".length());
			} else if (token.startsWith("-X") && token.length() > 2) {
				explicitMethod = token.substring(2);
			} else if ("-H".equals(token) || "--header".equals(token)) {
				request.headers.add(parseHeader(requireValue(tokens, ++index, token)));
			} else if (token.startsWith("--header=")) {
				request.headers.add(parseHeader(token.substring("--header=".length())));
			} else if (token.startsWith("-H") && token.length() > 2) {
				request.headers.add(parseHeader(token.substring(2)));
			} else if (isDataOption(token)) {
				String value = requireValue(tokens, ++index, token);
				appendData(request, dataParts, token, value);
			} else if (token.startsWith("-d") && token.length() > 2) {
				appendData(request, dataParts, "-d", token.substring(2));
			} else if (isDataOptionWithValue(token)) {
				int separator = token.indexOf('=');
				appendData(request, dataParts, token.substring(0, separator), token.substring(separator + 1));
			} else if ("-F".equals(token) || "--form".equals(token) || "--form-string".equals(token)) {
				request.formData.add(parseForm(requireValue(tokens, ++index, token), "--form-string".equals(token)));
			} else if (token.startsWith("--form=")) {
				request.formData.add(parseForm(token.substring("--form=".length()), false));
			} else if (token.startsWith("--form-string=")) {
				request.formData.add(parseForm(token.substring("--form-string=".length()), true));
			} else if (token.startsWith("-F") && token.length() > 2) {
				request.formData.add(parseForm(token.substring(2), false));
			} else if ("--url".equals(token)) {
				request.url = requireValue(tokens, ++index, token);
			} else if (token.startsWith("--url=")) {
				request.url = token.substring("--url=".length());
			} else if ("-G".equals(token) || "--get".equals(token)) {
				useGet = true;
			} else if (takesIgnoredValue(token)) {
				index++;
			} else if (!token.startsWith("-") && request.url.isBlank()) {
				request.url = token;
			}
		}

		if (request.url.isBlank()) {
			throw new IllegalArgumentException("cURL command does not contain a URL.");
		}

		if (!request.formData.isEmpty()) {
			request.payloadType = "FORM_DATA";
		}
		if (!dataParts.isEmpty()) {
			String data = String.join("&", dataParts);
			if (useGet) {
				request.params.addAll(UrlParamUtils.parseQueryParams("?" + data));
			} else if (!"BINARY".equals(request.payloadType)) {
				request.body = data;
			}
		}
		request.params = UrlParamUtils.mergeParamsWithUrl(request.params, request.url);
		request.method = resolveMethod(explicitMethod, useGet, request);
		return request;
	}

	static List<String> tokenize(String command) {
		List<String> tokens = new ArrayList<>();
		if (command == null) {
			return tokens;
		}
		StringBuilder current = new StringBuilder();
		char quote = 0;
		boolean tokenStarted = false;
		for (int index = 0; index < command.length(); index++) {
			char ch = command.charAt(index);
			if (quote != 0) {
				if (ch == quote) {
					quote = 0;
					tokenStarted = true;
				} else if (ch == '\\' && quote == '"' && index + 1 < command.length()) {
					current.append(command.charAt(++index));
					tokenStarted = true;
				} else {
					current.append(ch);
					tokenStarted = true;
				}
				continue;
			}
			if (ch == '\'' || ch == '"') {
				quote = ch;
				tokenStarted = true;
			} else if (ch == '\\' && index + 1 < command.length()) {
				char next = command.charAt(index + 1);
				if (next == '\r' || next == '\n') {
					index++;
					if (next == '\r' && index + 1 < command.length() && command.charAt(index + 1) == '\n') {
						index++;
					}
				} else {
					current.append(next);
					index++;
					tokenStarted = true;
				}
			} else if (ch == '^' && index + 1 < command.length()
				&& (command.charAt(index + 1) == '\r' || command.charAt(index + 1) == '\n')) {
				index++;
				if (command.charAt(index) == '\r' && index + 1 < command.length()
					&& command.charAt(index + 1) == '\n') {
					index++;
				}
			} else if (Character.isWhitespace(ch)) {
				if (tokenStarted) {
					tokens.add(current.toString());
					current.setLength(0);
					tokenStarted = false;
				}
			} else {
				current.append(ch);
				tokenStarted = true;
			}
		}
		if (quote != 0) {
			throw new IllegalArgumentException("Unclosed quote in cURL command.");
		}
		if (tokenStarted) {
			tokens.add(current.toString());
		}
		return tokens;
	}

	private static void appendData(CurlRequest request, List<String> dataParts, String option, String value) {
		if ("--data-binary".equals(option) && value.startsWith("@") && value.length() > 1) {
			request.payloadType = "BINARY";
			request.binaryFilePath = value.substring(1);
			return;
		}
		dataParts.add(value);
	}

	private static HeaderEntryState parseHeader(String value) {
		HeaderEntryState header = new HeaderEntryState();
		header.id = UUID.randomUUID().toString();
		int separator = value.indexOf(':');
		if (separator < 0) {
			header.name = value.trim();
			header.value = "";
		} else {
			header.name = value.substring(0, separator).trim();
			header.value = value.substring(separator + 1).trim();
		}
		header.enabled = true;
		return header;
	}

	private static FormEntryState parseForm(String value, boolean forceString) {
		int separator = value.indexOf('=');
		if (separator <= 0) {
			throw new IllegalArgumentException("Invalid form value: " + value);
		}
		FormEntryState entry = new FormEntryState();
		entry.id = UUID.randomUUID().toString();
		entry.name = value.substring(0, separator);
		entry.value = value.substring(separator + 1);
		entry.file = !forceString && entry.value.startsWith("@");
		if (entry.file) {
			entry.value = entry.value.substring(1);
		}
		entry.enabled = true;
		return entry;
	}

	private static String resolveMethod(String explicitMethod, boolean useGet, CurlRequest request) {
		if (explicitMethod != null && !explicitMethod.isBlank()) {
			return explicitMethod.trim().toUpperCase(Locale.ROOT);
		}
		if (useGet) {
			return "GET";
		}
		if (!request.formData.isEmpty() || !request.body.isEmpty() || !request.binaryFilePath.isEmpty()) {
			return "POST";
		}
		return "GET";
	}

	private static boolean isDataOption(String token) {
		return "-d".equals(token) || "--data".equals(token) || "--data-raw".equals(token)
			|| "--data-binary".equals(token) || "--data-ascii".equals(token)
			|| "--data-urlencode".equals(token);
	}

	private static boolean isDataOptionWithValue(String token) {
		return token.startsWith("--data=") || token.startsWith("--data-raw=")
			|| token.startsWith("--data-binary=") || token.startsWith("--data-ascii=")
			|| token.startsWith("--data-urlencode=");
	}

	private static boolean takesIgnoredValue(String token) {
		return "-u".equals(token) || "--user".equals(token) || "-A".equals(token)
			|| "--user-agent".equals(token) || "-e".equals(token) || "--referer".equals(token)
			|| "-b".equals(token) || "--cookie".equals(token) || "-o".equals(token)
			|| "--output".equals(token) || "--connect-timeout".equals(token)
			|| "--max-time".equals(token) || "--proxy".equals(token);
	}

	private static String requireValue(List<String> tokens, int index, String option) {
		if (index >= tokens.size()) {
			throw new IllegalArgumentException("Missing value for " + option + ".");
		}
		return tokens.get(index);
	}
}
