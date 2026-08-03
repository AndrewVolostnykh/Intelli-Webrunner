package com.non_organic_onion.intelli.webrunner.proto;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses {@code .proto} files (resolving imports across the project) and produces sample JSON
 * bodies for a selected message. Contains no UI; the tool window only supplies a chosen file
 * and a chosen {@link ProtoMessageSelection}.
 */
public final class ProtoBodyGenerator {

	private final Project project;

	public ProtoBodyGenerator(Project project) {
		this.project = project;
	}

	/**
	 * Loads the registry rooted at {@code file} and returns the selectable messages declared in it.
	 */
	public List<ProtoMessageSelection> loadMessages(VirtualFile file) {
		ProtoRegistry registry = loadProtoRegistry(file);
		List<ProtoMessage> fileMessages = registry.messagesByFile.getOrDefault(file.getPath(), List.of());
		java.util.List<ProtoMessageSelection> selections = new java.util.ArrayList<>();
		for (ProtoMessage message : fileMessages) {
			selections.add(new ProtoMessageSelection(message.displayName, registry, message));
		}
		return selections;
	}

	public Map<String, Object> buildBody(
		ProtoMessageSelection selection,
		boolean useNulls
	) {
		return buildBodyForProtoMessage(selection, new java.util.HashSet<>(), 0, useNulls);
	}

	private ProtoRegistry loadProtoRegistry(VirtualFile rootFile) {
		ProtoRegistry registry = new ProtoRegistry();
		Collection<VirtualFile> files =
			FilenameIndex.getAllFilesByExt(project, "proto", GlobalSearchScope.projectScope(project));
		ProtoImportIndex index = new ProtoImportIndex(project, files);
		registry.loadFrom(rootFile, index);
		return registry;
	}

	private Map<String, Object> buildBodyForProtoMessage(
		ProtoMessageSelection selection,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (selection == null) {
			return Map.of();
		}
		String key = selection.qualifiedName;
		if (key != null) {
			if (visiting.contains(key)) {
				return Map.of();
			}
			visiting.add(key);
		}
		if (depth > 4) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		for (ProtoField field : selection.message.fields) {
			Object value = valueForProtoField(field,
											  selection.registry,
											  selection.message.fullName,
											  visiting,
											  depth + 1,
											  useNulls
			);
			result.put(field.name, value);
		}
		if (key != null) {
			visiting.remove(key);
		}
		return result;
	}

	private Object valueForProtoField(
		ProtoField field,
		ProtoRegistry registry,
		String currentMessageName,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (useNulls) {
			return null;
		}
		if (field.isMap) {
			Object key = valueForProtoScalar(field.mapKeyType);
			Object value =
				valueForProtoType(field.mapValueType, registry, currentMessageName, visiting, depth, useNulls);
			if (key == null) {
				key = "key";
			}
			return Map.of(String.valueOf(key), value);
		}
		Object value = valueForProtoType(field.type, registry, currentMessageName, visiting, depth, useNulls);
		if (field.repeated) {
			if (value == null) {
				return List.of();
			}
			return List.of(value);
		}
		return value;
	}

	private Object valueForProtoType(
		String type,
		ProtoRegistry registry,
		String currentMessageName,
		Set<String> visiting,
		int depth,
		boolean useNulls
	) {
		if (useNulls) {
			return null;
		}
		Object scalar = valueForProtoScalar(type);
		if (scalar != null) {
			return scalar;
		}
		if (type.startsWith(".google.protobuf.") || type.startsWith("google.protobuf.")) {
			String shortName = type.startsWith(".") ? type.substring(".google.protobuf.".length()) :
				type.substring("google.protobuf.".length());
			return valueForWellKnown(shortName);
		}
		String resolved = registry.resolveType(type, currentMessageName);
		if (resolved != null && registry.enums.containsKey(resolved)) {
			ProtoEnum protoEnum = registry.enums.get(resolved);
			if (protoEnum != null && !protoEnum.values.isEmpty()) {
				return protoEnum.values.get(0);
			}
			return "";
		}
		ProtoMessage message = resolved == null ? null : registry.messages.get(resolved);
		if (message == null) {
			return "";
		}
		return buildBodyForProtoMessage(
			new ProtoMessageSelection(message.displayName, registry, message),
			visiting,
			depth + 1,
			useNulls
		);
	}

	private Object valueForProtoScalar(String type) {
		if (type == null) {
			return null;
		}
		return switch (type) {
			case "string" -> "";
			case "bool" -> false;
			case "double", "float" -> 0.0;
			case "int32", "int64", "sint32", "sint64", "uint32", "uint64", "fixed32", "fixed64", "sfixed32",
				 "sfixed64" -> 0;
			case "bytes" -> "";
			default -> null;
		};
	}

	private Object valueForWellKnown(String shortName) {
		return switch (shortName) {
			case "Timestamp" -> "2024-01-01T00:00:00Z";
			case "StringValue" -> "";
			case "BoolValue" -> false;
			case "Int32Value", "UInt32Value", "Int64Value", "UInt64Value" -> 0;
			case "DoubleValue", "FloatValue" -> 0.0;
			default -> "";
		};
	}
}
