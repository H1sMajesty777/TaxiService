package ru.mirea.taxi.backend.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.mirea.taxi.exception.BusinessException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> respond(ApiException failure) {
        return response(failure.status(), failure.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> respond(BusinessException failure) {
        return response(HttpStatus.BAD_REQUEST, failure.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> respond(MethodArgumentNotValidException failure) {
        var field = failure.getBindingResult().getFieldError();
        return response(HttpStatus.BAD_REQUEST, field == null
                ? "Проверьте поля формы." : "Проверьте значение поля «" + field.getField() + "».");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, ServletRequestBindingException.class})
    public ResponseEntity<ErrorResponse> invalidRequest() {
        return response(HttpStatus.BAD_REQUEST, "Проверьте формат данных и обязательные параметры.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> concurrentUpdate() {
        return response(HttpStatus.CONFLICT, "Данные изменились. Обновите запись и повторите действие.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrityFailure() {
        return response(HttpStatus.CONFLICT, "Запись конфликтует с существующими данными или ограничениями базы.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied() {
        return response(HttpStatus.FORBIDDEN, "Для действия недостаточно прав.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> fileTooLarge() {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "CSV должен быть не больше 512 КБ.");
    }

    private static ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }
}
