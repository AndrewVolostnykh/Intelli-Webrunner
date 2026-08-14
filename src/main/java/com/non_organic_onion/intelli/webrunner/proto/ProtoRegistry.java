package com.non_organic_onion.intelli.webrunner.proto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProtoRegistry {

	final Map<String, ProtoMessage> messages = new LinkedHashMap<>();
	final Map<String, ProtoEnum> enums = new LinkedHashMap<>();
	final Map<String, List<ProtoMessage>> messagesByFile = new LinkedHashMap<>();
	private final Set<String> loadedFiles = new HashSet<>();

	void loadFrom(
		ProtoSource source,
		ProtoImportResolver resolver
	) {
		if (source == null) {
			return;
		}
		String path = source.path();
		if (loadedFiles.contains(path)) {
			return;
		}
		loadedFiles.add(path);
		try {
			ProtoParser parser = new ProtoParser(source.content(), source.name(), path);
			ProtoFile protoFile = parser.parse();
			for (ProtoMessage message : protoFile.messages) {
				messages.putIfAbsent(message.fullName, message);
				messagesByFile.computeIfAbsent(path, key -> new ArrayList<>()).add(message);
			}
			for (ProtoEnum protoEnum : protoFile.enums) {
				enums.putIfAbsent(protoEnum.fullName, protoEnum);
			}
			for (String importPath : protoFile.imports) {
				if (resolver != null) {
					resolver.resolve(importPath).ifPresent(imported -> loadFrom(imported, resolver));
				}
			}
		} catch (Exception ignored) {
		}
	}

	String resolveType(
		String type,
		String currentMessageFullName
	) {
		if (type == null || type.isBlank()) {
			return null;
		}
		String normalized = type.startsWith(".") ? type.substring(1) : type;
		if (messages.containsKey(normalized) || enums.containsKey(normalized)) {
			return normalized;
		}
		if (currentMessageFullName != null && !currentMessageFullName.isBlank()) {
			String scope = currentMessageFullName;
			while (scope.contains(".")) {
				String candidate = scope + "." + normalized;
				if (messages.containsKey(candidate) || enums.containsKey(candidate)) {
					return candidate;
				}
				scope = scope.substring(0, scope.lastIndexOf('.'));
			}
			int lastDot = currentMessageFullName.lastIndexOf('.');
			if (lastDot > 0) {
				String pkg = currentMessageFullName.substring(0, lastDot);
				String candidate = pkg + "." + normalized;
				if (messages.containsKey(candidate) || enums.containsKey(candidate)) {
					return candidate;
				}
			}
		}
		return normalized;
	}
}
