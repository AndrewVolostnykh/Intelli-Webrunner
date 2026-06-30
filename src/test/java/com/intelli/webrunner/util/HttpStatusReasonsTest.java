package com.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpStatusReasonsTest {

	@Test
	void formatsKnownStatusCodeWithReason() {
		assertEquals("200 - OK", HttpStatusReasons.format(200, ""));
	}

	@Test
	void usesProvidedStatusMessageWhenPresent() {
		assertEquals("7 - PERMISSION_DENIED", HttpStatusReasons.format(7, "PERMISSION_DENIED"));
	}

	@Test
	void keepsUnknownStatusCodeWithoutReason() {
		assertEquals("599", HttpStatusReasons.format(599, ""));
	}
}
