package com.non_organic_onion.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormattingTest {

	@Test
	void minifyCollapsesWhitespaceAndTrims() {
		assertEquals("first second third", TextFormatting.minify("  first\n\t second   third  "));
	}

	@Test
	void beautifyAddsLineBreakAfterEveryPeriod() {
		assertEquals("one.\ntwo.\n", TextFormatting.beautify("one.two."));
	}
}
