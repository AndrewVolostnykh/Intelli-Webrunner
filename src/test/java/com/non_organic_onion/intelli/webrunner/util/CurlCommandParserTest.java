package com.non_organic_onion.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurlCommandParserTest {

	@Test
	void parsesBrowserStyleMultilineCurl() {
		CurlRequest request = CurlCommandParser.parse("""
			curl 'https://example.com/items?active=true' \\
			  -H 'accept: application/json' \\
			  -H "authorization: Bearer token" \\
			  --data-raw '{"name":"test"}'
			""");

		assertEquals("POST", request.method);
		assertEquals("https://example.com/items?active=true", request.url);
		assertEquals(2, request.headers.size());
		assertEquals("application/json", request.headers.get(0).value);
		assertEquals("{\"name\":\"test\"}", request.body);
		assertEquals("RAW", request.payloadType);
		assertEquals("active", request.params.get(0).name);
		assertEquals("true", request.params.get(0).value);
	}

	@Test
	void parsesMultipartAndExplicitMethod() {
		CurlRequest request = CurlCommandParser.parse(
			"curl -X PUT http://localhost:8080/upload -F 'name=value' -F 'file=@C:\\tmp\\a.txt'"
		);

		assertEquals("PUT", request.method);
		assertEquals("FORM_DATA", request.payloadType);
		assertEquals(2, request.formData.size());
		assertEquals(false, request.formData.get(0).file);
		assertEquals(true, request.formData.get(1).file);
		assertEquals("C:\\tmp\\a.txt", request.formData.get(1).value);
	}

	@Test
	void parsesBinaryFileAndGetData() {
		CurlRequest binary = CurlCommandParser.parse(
			"curl --data-binary '@/tmp/body.bin' https://example.com/upload"
		);
		assertEquals("POST", binary.method);
		assertEquals("BINARY", binary.payloadType);
		assertEquals("/tmp/body.bin", binary.binaryFilePath);

		CurlRequest get = CurlCommandParser.parse(
			"curl -G https://example.com/search --data-urlencode 'q=hello world'"
		);
		assertEquals("GET", get.method);
		assertEquals("q", get.params.get(0).name);
		assertEquals("hello world", get.params.get(0).value);
	}
}
