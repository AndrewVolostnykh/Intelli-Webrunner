package com.intelli.webrunner.proto;

final class ProtoParser {

	private final ProtoTokenizer tokenizer;
	private final ProtoFile file;

	ProtoParser(
		String text,
		String fileName,
		String path
	) {
		this.tokenizer = new ProtoTokenizer(text == null ? "" : text);
		this.file = new ProtoFile(fileName, path);
	}

	ProtoFile parse() {
		while (tokenizer.hasNext()) {
			if (tokenizer.match("package")) {
				file.packageName = readQualifiedName();
				tokenizer.consume(";");
				continue;
			}
			if (tokenizer.match("import")) {
				if (tokenizer.peekText("public") || tokenizer.peekText("weak")) {
					tokenizer.next();
				}
				String path = tokenizer.readString();
				if (path != null) {
					file.imports.add(path);
				}
				tokenizer.consume(";");
				continue;
			}
			if (tokenizer.match("message")) {
				parseMessage(null);
				continue;
			}
			if (tokenizer.match("enum")) {
				parseEnum(null);
				continue;
			}
			skipStatementOrBlock();
		}
		return file;
	}

	private void parseMessage(String parentFullName) {
		String name = tokenizer.readIdentifier();
		if (name == null) {
			return;
		}
		String fullName = buildFullName(file.packageName, parentFullName, name);
		ProtoMessage message = new ProtoMessage();
		message.name = name;
		message.packageName = file.packageName;
		message.fullName = fullName;
		message.displayName = file.fileName + ":" + fullName;
		file.messages.add(message);
		if (!tokenizer.consume("{")) {
			return;
		}
		while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
			if (tokenizer.match("message")) {
				parseMessage(fullName);
				continue;
			}
			if (tokenizer.match("enum")) {
				parseEnum(fullName);
				continue;
			}
			if (tokenizer.match("oneof")) {
				parseOneof(message);
				continue;
			}
			if (tokenizer.peekText("option") || tokenizer.peekText("reserved") ||
				tokenizer.peekText("extensions")) {
				tokenizer.next();
				skipStatementOrBlock();
				continue;
			}
			ProtoField field = parseField(true, fullName);
			if (field != null) {
				message.fields.add(field);
			} else {
				skipStatementOrBlock();
			}
		}
		tokenizer.consume("}");
	}

	private void parseEnum(String parentFullName) {
		String name = tokenizer.readIdentifier();
		if (name == null) {
			return;
		}
		String fullName = buildFullName(file.packageName, parentFullName, name);
		ProtoEnum protoEnum = new ProtoEnum();
		protoEnum.name = name;
		protoEnum.fullName = fullName;
		if (!tokenizer.consume("{")) {
			return;
		}
		while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
			if (tokenizer.peekText("option") || tokenizer.peekText("reserved")) {
				tokenizer.next();
				skipStatementOrBlock();
				continue;
			}
			String value = tokenizer.readIdentifier();
			if (value == null) {
				tokenizer.next();
				continue;
			}
			protoEnum.values.add(value);
			skipStatementOrBlock();
		}
		tokenizer.consume("}");
		file.enums.add(protoEnum);
	}

	private void parseOneof(ProtoMessage message) {
		tokenizer.readIdentifier();
		if (!tokenizer.consume("{")) {
			return;
		}
		while (tokenizer.hasNext() && !tokenizer.peekText("}")) {
			ProtoField field = parseField(false, message.fullName);
			if (field != null) {
				message.fields.add(field);
			} else {
				skipStatementOrBlock();
			}
		}
		tokenizer.consume("}");
	}

	private ProtoField parseField(
		boolean allowLabel,
		String currentMessageFullName
	) {
		boolean repeated = false;
		if (allowLabel &&
			(tokenizer.peekText("repeated") || tokenizer.peekText("optional") || tokenizer.peekText("required"))) {
			repeated = tokenizer.match("repeated");
			if (!repeated) {
				tokenizer.next();
			}
		}
		String type;
		ProtoField field = new ProtoField();
		if (tokenizer.match("map")) {
			if (!tokenizer.consume("<")) {
				return null;
			}
			String keyType = readTypeName();
			if (!tokenizer.consume(",")) {
				return null;
			}
			String valueType = readTypeName();
			if (!tokenizer.consume(">")) {
				return null;
			}
			type = "map";
			field.isMap = true;
			field.mapKeyType = keyType;
			field.mapValueType = valueType;
		} else {
			type = readTypeName();
		}
		if (type == null) {
			return null;
		}
		String name = tokenizer.readIdentifier();
		if (name == null) {
			return null;
		}
		field.name = name;
		field.repeated = repeated;
		field.type = type;
		skipFieldRemainder();
		return field;
	}

	private String readTypeName() {
		if (tokenizer.match(".")) {
			String first = tokenizer.readIdentifier();
			if (first == null) {
				return null;
			}
			return "." + readQualifiedNameTail(first);
		}
		String first = tokenizer.readIdentifier();
		if (first == null) {
			return null;
		}
		return readQualifiedNameTail(first);
	}

	private String readQualifiedName() {
		String first = tokenizer.readIdentifier();
		if (first == null) {
			return null;
		}
		return readQualifiedNameTail(first);
	}

	private String readQualifiedNameTail(String first) {
		StringBuilder builder = new StringBuilder(first);
		while (tokenizer.match(".")) {
			String next = tokenizer.readIdentifier();
			if (next == null) {
				break;
			}
			builder.append('.').append(next);
		}
		return builder.toString();
	}

	private void skipFieldRemainder() {
		int bracketDepth = 0;
		while (tokenizer.hasNext()) {
			String text = tokenizer.nextText();
			if ("[".equals(text)) {
				bracketDepth++;
			} else if ("]".equals(text)) {
				bracketDepth = Math.max(0, bracketDepth - 1);
			} else if (";".equals(text) && bracketDepth == 0) {
				return;
			} else if ("{".equals(text)) {
				tokenizer.skipBlock();
			}
		}
	}

	private void skipStatementOrBlock() {
		if (tokenizer.peekText("{")) {
			tokenizer.skipBlock();
			tokenizer.consume(";");
			return;
		}
		while (tokenizer.hasNext()) {
			String text = tokenizer.nextText();
			if ("{".equals(text)) {
				tokenizer.skipBlock();
				continue;
			}
			if (";".equals(text)) {
				return;
			}
		}
	}

	private static String buildFullName(
		String packageName,
		String parentFullName,
		String name
	) {
		if (parentFullName != null && !parentFullName.isBlank()) {
			return parentFullName + "." + name;
		}
		if (packageName != null && !packageName.isBlank()) {
			return packageName + "." + name;
		}
		return name;
	}
}
