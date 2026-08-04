package com.non_organic_onion.intelli.webrunner.util;

import com.non_organic_onion.intelli.webrunner.execution.HttpPayloadType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadTypesTest {

	@Test
	void resolvesUrlEncodedAliases() {
		assertEquals("x-www-form-urlencoded", PayloadTypes.resolveLabel("X_WWW_FORM_URLENCODED"));
		assertEquals("X_WWW_FORM_URLENCODED", PayloadTypes.resolveValue("x-www-form-urlencoded"));
		assertEquals(HttpPayloadType.X_WWW_FORM_URLENCODED, PayloadTypes.resolveType("X_WWW_FORM_URLENCODED"));
		assertEquals(HttpPayloadType.X_WWW_FORM_URLENCODED, PayloadTypes.resolveType("x-www-form-urlencoded"));
		assertEquals(
			HttpPayloadType.X_WWW_FORM_URLENCODED,
			PayloadTypes.resolveType("application/x-www-form-urlencoded")
		);
	}
}
