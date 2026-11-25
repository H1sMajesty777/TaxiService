package ru.mirea.taxi.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain next)
            throws ServletException, IOException {
        String identifier = request.getHeader(HEADER);
        if (identifier == null || !identifier.matches("[a-zA-Z0-9_-]{1,64}")) {
            identifier = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, identifier);
        try (var ignored = MDC.putCloseable(MDC_KEY, identifier)) {
            next.doFilter(request, response);
        }
    }

    public static String current() {
        String identifier = MDC.get(MDC_KEY);
        return identifier == null ? UUID.randomUUID().toString() : identifier;
    }
}
