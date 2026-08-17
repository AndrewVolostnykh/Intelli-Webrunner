package com.non_organic_onion.intelli.webrunner.proto;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.non_organic_onion.webrunner.core.proto.ProtoImportResolver;
import com.non_organic_onion.webrunner.core.proto.ProtoSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProtoImportIndex implements ProtoImportResolver {

	private final Map<String, List<VirtualFile>> byPath = new HashMap<>();
	private final List<VirtualFile> allFiles = new ArrayList<>();
	private final String basePath;

	public ProtoImportIndex(
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

	@Override
	public Optional<ProtoSource> resolve(String importPath) {
		if (importPath == null || importPath.isBlank()) {
			return Optional.empty();
		}
		String key = importPath.replace("\\", "/");
		List<VirtualFile> direct = byPath.get(key);
		if (direct != null && !direct.isEmpty()) {
			return sourceOf(direct.get(0));
		}
		for (VirtualFile file : allFiles) {
			String path = file.getPath().replace("\\", "/");
			if (path.endsWith(key)) {
				return sourceOf(file);
			}
		}
		return Optional.empty();
	}

	public ProtoSource sourceOfRoot(VirtualFile file) {
		return sourceOf(file).orElse(null);
	}

	private void add(
		String key,
		VirtualFile file
	) {
		byPath.computeIfAbsent(key, ignore -> new ArrayList<>()).add(file);
	}

	private Optional<ProtoSource> sourceOf(VirtualFile file) {
		if (file == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ProtoSource(file.getName(), file.getPath(), VfsUtilCore.loadText(file)));
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}
}
