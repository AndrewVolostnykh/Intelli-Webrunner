package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure helpers for working with URL query parameters and {@link HeaderEntryState} param lists.
 */
public final class UrlParamUtils {

	private UrlParamUtils() {
	}

	public static String applyQueryParams(
		String url,
		List<HeaderEntryState> params
	) {
		if (url == null) {
			return "";
		}
		if (params == null || params.isEmpty()) {
			return url;
		}
		String base = url;
		String fragment = "";
		int hashIndex = url.indexOf('#');
		if (hashIndex >= 0) {
			base = url.substring(0, hashIndex);
			fragment = url.substring(hashIndex);
		}
		StringBuilder builder = new StringBuilder(base);
		boolean hasQuery = base.contains("?");
		boolean needsSeparator = hasQuery && !base.endsWith("?") && !base.endsWith("&");
		Set<String> existingPairs = collectQueryPairs(base);

		for (HeaderEntryState param : params) {
			if (param == null || !param.enabled) {
				continue;
			}
			String name = param.name == null ? "" : param.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			String value = param.value == null ? "" : param.value;
			String dedupeKey = name + ((char) 0) + value;
			if (existingPairs.contains(dedupeKey)) {
				continue;
			}
			if (!hasQuery) {
				builder.append('?');
				hasQuery = true;
				needsSeparator = false;
			} else if (needsSeparator) {
				builder.append('&');
			}
			builder.append(encodeParam(name));
			builder.append('=');
			builder.append(encodeParam(value));
			needsSeparator = true;
		}

		return builder.append(fragment).toString();
	}

	public static String applyDefaultProtocol(String url) {
		if (url == null) {
			return "";
		}
		String trimmed = url.trim();
		if (trimmed.isEmpty() || hasProtocol(trimmed)) {
			return trimmed;
		}
		if (isLocalhost(trimmed)) {
			return "http://" + trimmed;
		}
		return "https://" + trimmed;
	}

	public static String replaceQueryParams(
		String url,
		List<HeaderEntryState> params
	) {
		if (url == null) {
			return "";
		}
		String base = url;
		String fragment = "";
		int hashIndex = url.indexOf('#');
		if (hashIndex >= 0) {
			base = url.substring(0, hashIndex);
			fragment = url.substring(hashIndex);
		}
		int queryIndex = base.indexOf('?');
		if (queryIndex >= 0) {
			base = base.substring(0, queryIndex);
		}
		if (params == null || params.isEmpty()) {
			return base + fragment;
		}
		StringBuilder queryBuilder = new StringBuilder();
		boolean first = true;
		for (HeaderEntryState param : params) {
			if (param == null || !param.enabled) {
				continue;
			}
			String name = param.name == null ? "" : param.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			String value = param.value == null ? "" : param.value;
			if (!first) {
				queryBuilder.append('&');
			}
			queryBuilder.append(encodeParam(name));
			queryBuilder.append('=');
			queryBuilder.append(encodeParam(value));
			first = false;
		}
		if (queryBuilder.length() == 0) {
			return base + fragment;
		}
		return base + "?" + queryBuilder + fragment;
	}

	public static String encodeParam(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	public static String decodeParam(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return value;
		}
	}

	public static List<HeaderEntryState> parseQueryParams(String url) {
		List<HeaderEntryState> result = new ArrayList<>();
		if (url == null || url.isBlank()) {
			return result;
		}
		int queryIndex = url.indexOf('?');
		if (queryIndex < 0) {
			return result;
		}
		int hashIndex = url.indexOf('#', queryIndex);
		String query = hashIndex >= 0 ? url.substring(queryIndex + 1, hashIndex) : url.substring(queryIndex + 1);
		if (query.isBlank()) {
			return result;
		}
		String[] parts = query.split("&");
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			String name;
			String value;
			int eq = part.indexOf('=');
			if (eq >= 0) {
				name = decodeParam(part.substring(0, eq));
				value = decodeParam(part.substring(eq + 1));
			} else {
				name = decodeParam(part);
				value = "";
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			HeaderEntryState entry = new HeaderEntryState();
			entry.id = UUID.randomUUID().toString();
			entry.name = name;
			entry.value = value;
			entry.enabled = true;
			result.add(entry);
		}
		return result;
	}

	public static Set<String> collectQueryPairs(String url) {
		Set<String> pairs = new HashSet<>();
		for (HeaderEntryState entry : parseQueryParams(url)) {
			String name = entry.name == null ? "" : entry.name;
			String value = entry.value == null ? "" : entry.value;
			pairs.add(name + ((char) 0) + value);
		}
		return pairs;
	}

	public static List<HeaderEntryState> mergeParamsWithUrl(
		List<HeaderEntryState> params,
		String url
	) {
		List<HeaderEntryState> fromUrl = parseQueryParams(url);
		if (fromUrl.isEmpty()) {
			return params == null ? List.of() : params;
		}
		List<HeaderEntryState> merged = new ArrayList<>();
		if (params != null) {
			for (HeaderEntryState entry : params) {
				if (entry == null) {
					continue;
				}
				HeaderEntryState clone = new HeaderEntryState();
				clone.id = entry.id;
				clone.name = entry.name;
				clone.value = entry.value;
				clone.enabled = entry.enabled;
				merged.add(clone);
			}
		}
		for (HeaderEntryState entry : fromUrl) {
			HeaderEntryState existing = findParamByName(merged, entry.name);
			if (existing == null) {
				merged.add(entry);
			} else {
				existing.value = entry.value;
				existing.enabled = true;
			}
		}
		return merged;
	}

	public static HeaderEntryState findParamByName(
		List<HeaderEntryState> params,
		String name
	) {
		if (name == null) {
			return null;
		}
		for (HeaderEntryState entry : params) {
			if (entry != null && name.equals(entry.name)) {
				return entry;
			}
		}
		return null;
	}

	private static boolean hasProtocol(String url) {
		return url.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*");
	}

	private static boolean isLocalhost(String url) {
		if (url.length() < "localhost".length()) {
			return false;
		}
		if (!url.regionMatches(true, 0, "localhost", 0, "localhost".length())) {
			return false;
		}
		if (url.length() == "localhost".length()) {
			return true;
		}
		char next = url.charAt("localhost".length());
		return next == ':' || next == '/' || next == '?' || next == '#';
	}
}
