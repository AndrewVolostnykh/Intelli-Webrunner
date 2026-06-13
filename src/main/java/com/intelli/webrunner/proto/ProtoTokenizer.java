package com.intelli.webrunner.proto;

import java.util.ArrayList;
import java.util.List;

final class ProtoTokenizer {

	private final List<Token> tokens = new ArrayList<>();
	private int index = 0;

	ProtoTokenizer(String text) {
		tokenize(text == null ? "" : text);
	}

	boolean hasNext() {
		return index < tokens.size();
	}

	boolean match(String value) {
		if (peekText(value)) {
			index++;
			return true;
		}
		return false;
	}

	boolean peekText(String value) {
		if (!hasNext()) {
			return false;
		}
		return value.equals(tokens.get(index).text);
	}

	Token next() {
		if (!hasNext()) {
			return null;
		}
		return tokens.get(index++);
	}

	String nextText() {
		Token token = next();
		return token == null ? null : token.text;
	}

	boolean consume(String value) {
		return match(value);
	}

	String readIdentifier() {
		if (!hasNext()) {
			return null;
		}
		Token token = tokens.get(index);
		if (token.type == TokenType.IDENT) {
			index++;
			return token.text;
		}
		return null;
	}

	String readString() {
		if (!hasNext()) {
			return null;
		}
		Token token = tokens.get(index);
		if (token.type == TokenType.STRING) {
			index++;
			return token.text;
		}
		return null;
	}

	void skipBlock() {
		if (!match("{")) {
			return;
		}
		int depth = 1;
		while (hasNext()) {
			String text = nextText();
			if ("{".equals(text)) {
				depth++;
			} else if ("}".equals(text)) {
				depth--;
				if (depth == 0) {
					return;
				}
			}
		}
	}

	private void tokenize(String text) {
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (Character.isWhitespace(ch)) {
				i++;
				continue;
			}
			if (ch == '/' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				if (next == '/') {
					i = skipLineComment(text, i + 2);
					continue;
				}
				if (next == '*') {
					i = skipBlockComment(text, i + 2);
					continue;
				}
			}
			if (ch == '"' || ch == '\'') {
				Token token = readStringToken(text, i, ch);
				tokens.add(token);
				i = token.end;
				continue;
			}
			if (isIdentifierStart(ch)) {
				Token token = readIdentifierToken(text, i);
				tokens.add(token);
				i = token.end;
				continue;
			}
			if (isDigit(ch) || ch == '-') {
				Token token = readNumberToken(text, i);
				tokens.add(token);
				i = token.end;
				continue;
			}
			if (isSymbol(ch)) {
				tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), i + 1));
				i++;
				continue;
			}
			i++;
		}
	}

	private static int skipLineComment(
		String text,
		int start
	) {
		int i = start;
		while (i < text.length() && text.charAt(i) != '\n') {
			i++;
		}
		return i;
	}

	private static int skipBlockComment(
		String text,
		int start
	) {
		int i = start;
		while (i + 1 < text.length()) {
			if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
				return i + 2;
			}
			i++;
		}
		return text.length();
	}

	private static Token readStringToken(
		String text,
		int start,
		char quote
	) {
		int i = start + 1;
		StringBuilder builder = new StringBuilder();
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (ch == '\\' && i + 1 < text.length()) {
				builder.append(text.charAt(i + 1));
				i += 2;
				continue;
			}
			if (ch == quote) {
				return new Token(TokenType.STRING, builder.toString(), i + 1);
			}
			builder.append(ch);
			i++;
		}
		return new Token(TokenType.STRING, builder.toString(), i);
	}

	private static Token readIdentifierToken(
		String text,
		int start
	) {
		int i = start;
		while (i < text.length() && isIdentifierPart(text.charAt(i))) {
			i++;
		}
		return new Token(TokenType.IDENT, text.substring(start, i), i);
	}

	private static Token readNumberToken(
		String text,
		int start
	) {
		int i = start;
		if (text.charAt(i) == '-') {
			i++;
		}
		while (i < text.length() && isDigit(text.charAt(i))) {
			i++;
		}
		return new Token(TokenType.IDENT, text.substring(start, i), i);
	}

	private static boolean isIdentifierStart(char ch) {
		return Character.isLetter(ch) || ch == '_';
	}

	private static boolean isIdentifierPart(char ch) {
		return Character.isLetterOrDigit(ch) || ch == '_';
	}

	private static boolean isDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	private static boolean isSymbol(char ch) {
		return "{}[]()<>=;,:.".indexOf(ch) >= 0;
	}

	private enum TokenType {
		IDENT,
		STRING,
		SYMBOL
	}

	private static final class Token {

		final TokenType type;
		final String text;
		final int end;

		Token(
			TokenType type,
			String text,
			int end
		) {
			this.type = type;
			this.text = text;
			this.end = end;
		}
	}
}
