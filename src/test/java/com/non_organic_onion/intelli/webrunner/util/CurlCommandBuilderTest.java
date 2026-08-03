package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.state.FormEntryState;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurlCommandBuilderTest {

	@Test
	void buildsRawRequestWithParamsHeadersAndBody() {
		HeaderEntryState header = header("Authorization", "Bearer token", true);
		HeaderEntryState disabledHeader = header("Ignored", "value", false);
		HeaderEntryState param = header("search", "hello world", true);

		String curl = CurlCommandBuilder.build(
			"post",
			"example.com/items",
			List.of(header, disabledHeader),
			List.of(param),
			"{\"name\":\"O'Reilly\"}",
			"RAW",
			List.of(),
			""
		);

		assertEquals(
			"curl -X 'POST' -H 'Authorization: Bearer token' --data-raw '{\"name\":\"O'\"'\"'Reilly\"}' "
				+ "'https://example.com/items?search=hello+world'",
			curl
		);
	}

	@Test
	void buildsMultipartRequest() {
		FormEntryState text = form("name", "value", false, true);
		FormEntryState file = form("upload", "/tmp/file.txt", true, true);
		FormEntryState disabled = form("ignored", "value", false, false);

		String curl = CurlCommandBuilder.build(
			"POST",
			"localhost:8080/upload",
			List.of(),
			List.of(),
			"",
			"FORM_DATA",
			List.of(text, file, disabled),
			""
		);

		assertEquals(
			"curl -X 'POST' -F 'name=value' -F 'upload=@/tmp/file.txt' 'http://localhost:8080/upload'",
			curl
		);
	}

	@Test
	void buildsBinaryRequestWithDefaultContentType() {
		String curl = CurlCommandBuilder.build(
			"PUT",
			"https://example.com/file",
			List.of(),
			List.of(),
			"",
			"BINARY",
			List.of(),
			"/tmp/file.bin"
		);

		assertEquals(
			"curl -X 'PUT' -H 'Content-Type: application/octet-stream' --data-binary '@/tmp/file.bin' "
				+ "'https://example.com/file'",
			curl
		);
	}

	private HeaderEntryState header(String name, String value, boolean enabled) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		return entry;
	}

	private FormEntryState form(String name, String value, boolean file, boolean enabled) {
		FormEntryState entry = new FormEntryState();
		entry.name = name;
		entry.value = value;
		entry.file = file;
		entry.enabled = enabled;
		return entry;
	}
}
