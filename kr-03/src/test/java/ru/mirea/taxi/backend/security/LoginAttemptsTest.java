package ru.mirea.taxi.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.mirea.taxi.backend.error.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptsTest {
    @Test
    void limitsRepeatedAttemptsWithoutLockingDifferentLogin() {
        var attempts = new LoginAttempts(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int index = 0; index < 8; index++) {
            attempts.reserve("test|alice");
        }
        var failure = assertThrows(ApiException.class, () -> attempts.reserve("test|alice"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.status());
        assertDoesNotThrow(() -> attempts.reserve("test|bob"));
        attempts.clear("test|alice");
        assertDoesNotThrow(() -> attempts.reserve("test|alice"));
    }
}
