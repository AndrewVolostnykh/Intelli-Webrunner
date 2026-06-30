package com.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashingServiceTest {

	@Test
	void hashesWithSelectedAlgorithmWhenSecretIsEmpty() {
		assertEquals(
			"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
			HashingService.hash("hello", "SHA-256", "")
		);
	}

	@Test
	void hashesWithHmacWhenSecretIsPresent() {
		assertEquals(
			"88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b",
			HashingService.hash("hello", "SHA-256", "secret")
		);
	}

	@Test
	void rejectsUnsupportedAlgorithm() {
		assertThrows(IllegalArgumentException.class, () -> HashingService.hash("hello", "SHA-999", ""));
	}
}
