package com.intelli.webrunner.util;

import java.io.File;
import java.util.Locale;

/**
 * Pure helpers for file naming and {@code .http} file detection.
 */
public final class FileNameUtils {

	private FileNameUtils() {
	}

	public static boolean isHttpComment(String line) {
		String trimmed = line == null ? "" : line.trim();
		return trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("@");
	}

	public static boolean hasHttpExtension(File file) {
		if (file == null) {
			return false;
		}
		return file.getName().toLowerCase(Locale.ROOT).endsWith(".http");
	}

	public static File ensureExtension(
		File file,
		String extension
	) {
		if (file == null) {
			return null;
		}
		String name = file.getName();
		String suffix = "." + extension;
		if (name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
			return file;
		}
		File parent = file.getParentFile();
		String fixed = name + suffix;
		return parent == null ? new File(fixed) : new File(parent, fixed);
	}

	public static String safeFileName(String value) {
		String input = value == null ? "request" : value.trim();
		if (input.isEmpty()) {
			input = "request";
		}
		StringBuilder sanitized = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			boolean illegal = c == ((char) 92) // backslash
				|| c == '/'
				|| c == ':'
				|| c == '*'
				|| c == '?'
				|| c == ((char) 34) // double quote
				|| c == '<'
				|| c == '>'
				|| c == '|';
			sanitized.append(illegal ? '_' : c);
		}
		String result = sanitized.toString();
		return result.isEmpty() ? "request" : result;
	}
}
