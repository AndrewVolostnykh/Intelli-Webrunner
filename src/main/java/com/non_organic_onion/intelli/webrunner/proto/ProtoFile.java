package com.non_organic_onion.intelli.webrunner.proto;

import java.util.ArrayList;
import java.util.List;

final class ProtoFile {

	final String fileName;
	final String path;
	String packageName = "";
	final List<String> imports = new ArrayList<>();
	final List<ProtoMessage> messages = new ArrayList<>();
	final List<ProtoEnum> enums = new ArrayList<>();

	ProtoFile(
		String fileName,
		String path
	) {
		this.fileName = fileName == null ? "" : fileName;
		this.path = path == null ? "" : path;
	}
}
