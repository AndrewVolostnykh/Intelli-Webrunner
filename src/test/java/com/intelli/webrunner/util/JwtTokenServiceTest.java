package com.intelli.webrunner.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void reportsExpiryFromStandardExpClaim() {
        String expired = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjF9.signature";
        String active = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQxMDI0NDQ4MDB9.signature";

        assertEquals("Expired", JwtTokenService.expiryStatus(expired));
        assertEquals("Not Expired", JwtTokenService.expiryStatus(active));
        assertEquals("Not exp field", JwtTokenService.expiryStatus("eyJhbGciOiJIUzI1NiJ9.e30.signature"));
    }

    @Test
    void signsEditedTokenWithAlgorithmFromHeader() throws Exception {
        String token = JwtTokenService.update("{\"header\":{\"alg\":\"HS256\",\"typ\":\"JWT\"},\"payload\":{\"sub\":\"user\"}}", "secret");

        assertEquals("Not exp field", JwtTokenService.expiryStatus(token));
        assertTrue(JwtTokenService.decode(token).contains("\"sub\" : \"user\""));
    }
}
