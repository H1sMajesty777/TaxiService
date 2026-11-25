package ru.mirea.taxi.backend.security;

import org.junit.jupiter.api.Test;
import ru.mirea.taxi.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test
    void requiresMinimumLengthAndMixedCharacterTypes() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("shortA1"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("longpassword123"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("Аа1".repeat(16)));
        assertDoesNotThrow(() -> PasswordPolicy.validate("ExampleLong9"));
    }
}
