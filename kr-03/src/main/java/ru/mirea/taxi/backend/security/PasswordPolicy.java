package ru.mirea.taxi.backend.security;

import ru.mirea.taxi.exception.BusinessException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isUpperCase)
                || password.codePoints().noneMatch(Character::isLowerCase)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new BusinessException("Пароль: от 12 символов и до 72 байт, с прописной, строчной буквой и цифрой.");
        }
    }
}
