package ru.mirea.taxi.backend.error;

import org.springframework.http.HttpStatus;
import ru.mirea.taxi.backend.filter.CorrelationFilter;

public record ErrorResponse(int status, String code, String message, String correlationId) {
    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), status.name(), message, CorrelationFilter.current());
    }
}
