package com.intelli.webrunner.proto;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProtoImportIndex {

	private final Map<String, List<VirtualFile>> byPath = new HashMap<>();
	private final List<VirtualFile> allFiles = new ArrayList<>();
	private final String basePath;

	ProtoImportIndex(
		Project project,
		Collection<VirtualFile> files
	) {
		this.basePath = project.getBasePath() == null ? null : project.getBasePath().replace("\\", "/");
		for (VirtualFile file : files) {
			allFiles.add(file);
			add(file.getName(), file);
			String path = file.getPath().replace("\\", "/");
			if (basePath != null && path.startsWith(basePath + "/")) {
				String relative = path.substring(basePath.length() + 1);
				add(relative, file);
			}
		}
	}

	VirtualFile resolve(String importPath) {
		if (importPath == null || importPath.isBlank()) {
			return null;
		}
		String key = importPath.replace("\\", "/");
		List<VirtualFile> direct = byPath.get(key);
		if (direct != null && !direct.isEmpty()) {
			return direct.get(0);
		}
		for (VirtualFile file : allFiles) {
			String path = file.getPath().replace("\\", "/");
			if (path.endsWith(key)) {
				return file;
			}
		}
		return null;
	}

	private void add(
		String key,
		VirtualFile file
	) {
		byPath.computeIfAbsent(key, ignore -> new ArrayList<>()).add(file);
	}
}
