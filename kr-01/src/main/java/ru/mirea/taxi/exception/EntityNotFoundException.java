package ru.mirea.taxi.exception;

public final class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
