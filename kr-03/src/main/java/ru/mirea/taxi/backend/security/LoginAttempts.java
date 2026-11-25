package ru.mirea.taxi.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.mirea.taxi.backend.error.ApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LoginAttempts {
    private static final Duration INTERVAL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_KEYS = 20_000;
    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>();

    public LoginAttempts() {
        this(Clock.systemUTC());
    }

    LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    public synchronized void reserve(String key) {
        Instant now = clock.instant();
        windows.values().removeIf(window -> !window.until().isAfter(now));
        Window previous = windows.get(key);
        if (previous == null && windows.size() >= MAX_KEYS) {
            throw throttled();
        }
        if (previous != null && previous.attempts() >= MAX_ATTEMPTS) {
            throw throttled();
        }
        windows.put(key, previous == null ? new Window(1, now.plus(INTERVAL))
                : new Window(previous.attempts() + 1, previous.until()));
    }

    public synchronized void clear(String key) {
        windows.remove(key);
    }

    private static ApiException throttled() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток входа. Повторите через 10 минут.");
    }

    private record Window(int attempts, Instant until) {}
}
