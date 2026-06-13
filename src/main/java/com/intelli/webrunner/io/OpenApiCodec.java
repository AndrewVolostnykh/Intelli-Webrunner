package com.intelli.webrunner.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.NodeState;
import com.intelli.webrunner.state.NodeType;
import com.intelli.webrunner.state.RequestDetailsState;
import com.intelli.webrunner.state.RequestStatusState;
import com.intelli.webrunner.util.UrlParamUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Converts Webrunner HTTP requests to and from OpenAPI 3 documents (with a {@code x-webrunner}
 * vendor extension for loss-free round-trips). Pure mapping logic; the tool window keeps the
 * file IO and node creation/orchestration.
 */
public final class OpenApiCodec {

	private final ObjectMapper mapper;

	public OpenApiCodec(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	// ---- export ----

	public Map<String, Object> buildVendorExtension(
		NodeState node,
		RequestDetailsState details,
		RequestStatusState status
	) {
		Map<String, Object> vendor = new LinkedHashMap<>();
		vendor.put("id", node.id);
		vendor.put("name", safe(node.name));
		vendor.put("method", details.method == null ? "GET" : details.method);
		vendor.put("url", details.url == null ? "" : details.url);
		vendor.put("headers", headerEntriesToMaps(status != null ? status.requestHeaders : List.of()));
		vendor.put("params", headerEntriesToMaps(status != null ? status.requestParams : List.of()));
		vendor.put("body", status != null ? safe(status.requestBody) : "");
		vendor.put("beforeScript", status != null ? safe(status.beforeScript) : "");
		vendor.put("afterScript", status != null ? safe(status.afterScript) : "");
		return vendor;
	}

	public List<Map<String, Object>> buildOpenApiParameters(
		RequestStatusState status,
		RequestDetailsState details
	) {
		List<Map<String, Object>> params = new ArrayList<>();
		List<HeaderEntryState> query =
			status != null ? UrlParamUtils.mergeParamsWithUrl(status.requestParams, details.url) : List.of();
		List<HeaderEntryState> headers = status != null ? status.requestHeaders : List.of();
		for (HeaderEntryState entry : query) {
			if (entry == null || !entry.enabled) {
				continue;
			}
			String name = entry.name == null ? "" : entry.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			Map<String, Object> param = new LinkedHashMap<>();
			param.put("name", name);
			param.put("in", "query");
			param.put("schema", Map.of("type", "string"));
			if (entry.value != null && !entry.value.isEmpty()) {
				param.put("example", entry.value);
			}
			params.add(param);
		}
		for (HeaderEntryState entry : headers) {
			if (entry == null || !entry.enabled) {
				continue;
			}
			String name = entry.name == null ? "" : entry.name.trim();
			if (name.isEmpty()) {
				continue;
			}
			Map<String, Object> param = new LinkedHashMap<>();
			param.put("name", name);
			param.put("in", "header");
			param.put("schema", Map.of("type", "string"));
			if (entry.value != null && !entry.value.isEmpty()) {
				param.put("example", entry.value);
			}
			params.add(param);
		}
		return params;
	}

	public Object buildOpenApiRequestBody(RequestStatusState status) {
		if (status == null) {
			return null;
		}
		String body = safe(status.requestBody);
		if (body.isBlank()) {
			return null;
		}
		Map<String, Object> content = new LinkedHashMap<>();
		Map<String, Object> media = new LinkedHashMap<>();
		media.put("example", body);
		String contentType = findHeaderValue(status.requestHeaders, "Content-Type");
		content.put(contentType == null || contentType.isBlank() ? "text/plain" : contentType, media);
		Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("content", content);
		return requestBody;
	}

	public ParsedUrl parseUrl(String url) {
		String fallbackPath = "/";
		if (url == null || url.isBlank()) {
			return new ParsedUrl(fallbackPath, null);
		}
		try {
			URI uri = URI.create(url);
			String path = uri.getRawPath();
			if (path == null || path.isBlank()) {
				path = "/";
			}
			String server = null;
			if (uri.getScheme() != null && uri.getHost() != null) {
				StringBuilder builder = new StringBuilder();
				builder.append(uri.getScheme()).append("://").append(uri.getHost());
				if (uri.getPort() > 0) {
					builder.append(":").append(uri.getPort());
				}
				server = builder.toString();
			}
			return new ParsedUrl(path, server);
		} catch (Exception e) {
			return new ParsedUrl(fallbackPath, null);
		}
	}

	public String buildOperationId(NodeState node) {
		String base = safe(node.name).replaceAll("[^A-Za-z0-9_]", "_");
		if (base.isBlank()) {
			base = "operation";
		}
		return base + "_" + node.id.replaceAll("[^A-Za-z0-9_]", "");
	}

	public List<String> buildFolderTags(
		NodeState node,
		Map<String, NodeState> nodeById
	) {
		List<String> tags = new ArrayList<>();
		String parentId = node.parentId;
		while (parentId != null) {
			NodeState parent = nodeById.get(parentId);
			if (parent == null) {
				break;
			}
			if (parent.type == NodeType.FOLDER && parent.name != null && !parent.name.isBlank()) {
				tags.add(parent.name);
			}
			parentId = parent.parentId;
		}
		Collections.reverse(tags);
		return tags;
	}

	// ---- import ----

	public boolean isHttpMethod(String method) {
		return method.equals("GET") || method.equals("POST") || method.equals("PUT")
			|| method.equals("PATCH") || method.equals("DELETE")
			|| method.equals("HEAD") || method.equals("OPTIONS");
	}

	public String resolveOperationUrl(
		Map<String, Object> doc,
		Map<?, ?> pathItem,
		Map<?, ?> operation,
		String path
	) {
		String baseUrl = firstServerUrl(operation);
		if (baseUrl == null) {
			baseUrl = firstServerUrl(pathItem);
		}
		if (baseUrl == null) {
			baseUrl = firstServerUrl(doc);
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			return path.startsWith("/") ? path : "/" + path;
		}
		String normalizedPath = path.startsWith("/") ? path : "/" + path;
		if (baseUrl.endsWith("/")) {
			return baseUrl.substring(0, baseUrl.length() - 1) + normalizedPath;
		}
		return baseUrl + normalizedPath;
	}

	public RequestData readVendorRequestData(
		Map<?, ?> operation,
		Map<?, ?> pathItem,
		String method,
		String url
	) {
		RequestData data = new RequestData();
		data.method = method;
		data.url = url;
		data.name = method + " " + url;

		Object vendorObj = operation.get("x-webrunner");
		if (vendorObj instanceof Map<?, ?> vendor) {
			Object name = vendor.get("name");
			if (name != null && !String.valueOf(name).isBlank()) {
				data.name = String.valueOf(name);
			}
			Object vendorMethod = vendor.get("method");
			if (vendorMethod != null) {
				data.method = String.valueOf(vendorMethod).toUpperCase(Locale.ROOT);
			}
			Object vendorUrl = vendor.get("url");
			if (vendorUrl != null && !String.valueOf(vendorUrl).isBlank()) {
				data.url = String.valueOf(vendorUrl);
			}
			data.headers = parseHeaderEntries(vendor.get("headers"));
			data.params = parseHeaderEntries(vendor.get("params"));
			Object body = vendor.get("body");
			if (body != null) {
				data.body = String.valueOf(body);
			}
			Object before = vendor.get("beforeScript");
			if (before != null) {
				data.beforeScript = String.valueOf(before);
			}
			Object after = vendor.get("afterScript");
			if (after != null) {
				data.afterScript = String.valueOf(after);
			}
			return data;
		}

		data.headers = parseOpenApiParameters(pathItem, operation, "header");
		data.params = parseOpenApiParameters(pathItem, operation, "query");
		data.body = extractRequestBody(operation);
		Object summary = operation.get("summary");
		if (summary != null && !String.valueOf(summary).isBlank()) {
			data.name = String.valueOf(summary);
		}
		Object opId = operation.get("operationId");
		if (opId != null && !String.valueOf(opId).isBlank() && (summary == null || String.valueOf(summary).isBlank())) {
			data.name = String.valueOf(opId);
		}
		return data;
	}

	// ---- internals ----

	private List<Map<String, Object>> headerEntriesToMaps(List<HeaderEntryState> entries) {
		List<Map<String, Object>> list = new ArrayList<>();
		if (entries == null) {
			return list;
		}
		for (HeaderEntryState entry : entries) {
			if (entry == null) {
				continue;
			}
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("name", entry.name == null ? "" : entry.name);
			map.put("value", entry.value == null ? "" : entry.value);
			map.put("enabled", entry.enabled);
			list.add(map);
		}
		return list;
	}

	private String findHeaderValue(
		List<HeaderEntryState> headers,
		String name
	) {
		if (headers == null || name == null) {
			return null;
		}
		for (HeaderEntryState header : headers) {
			if (header == null || !header.enabled || header.name == null) {
				continue;
			}
			if (name.equalsIgnoreCase(header.name.trim())) {
				return header.value == null ? "" : header.value.trim();
			}
		}
		return null;
	}

	private String firstServerUrl(Map<?, ?> container) {
		if (container == null) {
			return null;
		}
		Object serversObj = container.get("servers");
		if (!(serversObj instanceof List<?> servers)) {
			return null;
		}
		if (servers.isEmpty()) {
			return null;
		}
		Object first = servers.get(0);
		if (!(first instanceof Map<?, ?> server)) {
			return null;
		}
		Object url = server.get("url");
		return url == null ? null : String.valueOf(url);
	}

	private List<HeaderEntryState> parseHeaderEntries(Object value) {
		List<HeaderEntryState> list = new ArrayList<>();
		if (!(value instanceof List<?> entries)) {
			return list;
		}
		for (Object entryObj : entries) {
			if (!(entryObj instanceof Map<?, ?> entry)) {
				continue;
			}
			Object name = entry.get("name");
			if (name == null || String.valueOf(name).isBlank()) {
				continue;
			}
			HeaderEntryState header = new HeaderEntryState();
			header.id = UUID.randomUUID().toString();
			header.name = String.valueOf(name);
			Object valueObj = entry.get("value");
			header.value = valueObj == null ? "" : String.valueOf(valueObj);
			Object enabledObj = entry.get("enabled");
			header.enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));
			list.add(header);
		}
		return list;
	}

	private List<HeaderEntryState> parseOpenApiParameters(
		Map<?, ?> pathItem,
		Map<?, ?> operation,
		String location
	) {
		List<HeaderEntryState> list = new ArrayList<>();
		List<Object> combined = new ArrayList<>();
		Object pathParamsObj = pathItem == null ? null : pathItem.get("parameters");
		if (pathParamsObj instanceof List<?> pathParams) {
			combined.addAll(pathParams);
		}
		Object opParamsObj = operation.get("parameters");
		if (opParamsObj instanceof List<?> opParams) {
			combined.addAll(opParams);
		}
		if (combined.isEmpty()) {
			return list;
		}
		for (Object paramObj : combined) {
			if (!(paramObj instanceof Map<?, ?> param)) {
				continue;
			}
			Object in = param.get("in");
			if (in == null || !location.equalsIgnoreCase(String.valueOf(in))) {
				continue;
			}
			Object nameObj = param.get("name");
			if (nameObj == null || String.valueOf(nameObj).isBlank()) {
				continue;
			}
			HeaderEntryState entry = new HeaderEntryState();
			entry.id = UUID.randomUUID().toString();
			entry.name = String.valueOf(nameObj);
			Object value = param.get("example");
			if (value == null) {
				value = extractFromSchema(param);
			}
			entry.value = value == null ? "" : stringifyExample(value);
			entry.enabled = true;
			list.add(entry);
		}
		return list;
	}

	private Object extractFromSchema(Map<?, ?> param) {
		Object schemaObj = param.get("schema");
		if (!(schemaObj instanceof Map<?, ?> schema)) {
			return param.get("default");
		}
		Object example = schema.get("example");
		if (example != null) {
			return example;
		}
		Object def = schema.get("default");
		if (def != null) {
			return def;
		}
		return param.get("default");
	}

	private String extractRequestBody(Map<?, ?> operation) {
		Object bodyObj = operation.get("requestBody");
		if (!(bodyObj instanceof Map<?, ?> body)) {
			return "";
		}
		Object contentObj = body.get("content");
		if (!(contentObj instanceof Map<?, ?> content)) {
			return "";
		}
		for (Object mediaObj : content.values()) {
			if (!(mediaObj instanceof Map<?, ?> media)) {
				continue;
			}
			Object example = media.get("example");
			if (example != null) {
				return stringifyExample(example);
			}
			Object examples = media.get("examples");
			if (examples instanceof Map<?, ?> examplesMap && !examplesMap.isEmpty()) {
				Object first = examplesMap.values().iterator().next();
				if (first instanceof Map<?, ?> exampleMap) {
					Object value = exampleMap.get("value");
					if (value != null) {
						return stringifyExample(value);
					}
				}
			}
		}
		return "";
	}

	private String stringifyExample(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String text) {
			return text;
		}
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception e) {
			return String.valueOf(value);
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/** Path + optional server origin parsed from a request URL. */
	public static final class ParsedUrl {

		public final String path;
		public final String serverUrl;

		ParsedUrl(
			String path,
			String serverUrl
		) {
			this.path = path;
			this.serverUrl = serverUrl;
		}
	}

	/** Request fields reconstructed from an OpenAPI operation. */
	public static final class RequestData {

		public String name;
		public String method;
		public String url;
		public List<HeaderEntryState> headers = new ArrayList<>();
		public List<HeaderEntryState> params = new ArrayList<>();
		public String body = "";
		public String beforeScript = "";
		public String afterScript = "";
	}
}
