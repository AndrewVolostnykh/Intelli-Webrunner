package com.intelli.webrunner.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure helpers for deriving a download file name from response headers.
 */
public final class ContentDispositionUtils {

	private ContentDispositionUtils() {
	}

	public static String suggestDownloadFilename(Map<String, List<String>> headers) {
		String contentDisposition = firstHeaderValue(headers, "content-disposition");
		String filename = extractFilenameFromDisposition(contentDisposition);
		if (filename != null && !filename.isBlank()) {
			return filename;
		}
		return "download.bin";
	}

	public static String extractFilenameFromDisposition(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		int index = lower.indexOf("filename*=");
		if (index >= 0) {
			String part = value.substring(index + "filename*=".length()).trim();
			part = trimDispositionPart(part);
			int charsetIndex = part.indexOf("''");
			if (charsetIndex >= 0) {
				String encoded = part.substring(charsetIndex + 2);
				try {
					return URLDecoder.decode(stripQuotes(encoded), StandardCharsets.UTF_8);
				} catch (Exception ignored) {
					return stripQuotes(encoded);
				}
			}
			return stripQuotes(part);
		}
		index = lower.indexOf("filename=");
		if (index >= 0) {
			String part = value.substring(index + "filename=".length()).trim();
			part = trimDispositionPart(part);
			return stripQuotes(part);
		}
		return null;
	}

	public static String trimDispositionPart(String value) {
		if (value == null) {
			return null;
		}
		int semicolon = value.indexOf(';');
		if (semicolon >= 0) {
			return value.substring(0, semicolon).trim();
		}
		return value.trim();
	}

	public static String stripQuotes(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		String doubleQuote = String.valueOf((char) 34);
		String singleQuote = String.valueOf((char) 39);
		if ((trimmed.startsWith(doubleQuote) && trimmed.endsWith(doubleQuote)) ||
			(trimmed.startsWith(singleQuote) && trimmed.endsWith(singleQuote))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	public static String firstHeaderValue(Map<String, List<String>> headers, String name) {
		if (headers == null || name == null) {
			return null;
		}
		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey() == null) {
				continue;
			}
			if (!entry.getKey().equalsIgnoreCase(name)) {
				continue;
			}
			List<String> values = entry.getValue();
			if (values == null || values.isEmpty()) {
				return null;
			}
			return values.get(0);
		}
		return null;
	}
}
