package ru.mirea.taxi.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import ru.mirea.taxi.backend.error.ErrorResponse;
import ru.mirea.taxi.backend.repository.UserAccountRepository;
import ru.mirea.taxi.backend.security.JwtTokens;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${taxi.jwt-secret}") String configuredKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("TAXI_JWT_SECRET должен быть Base64.", error);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("TAXI_JWT_SECRET должен содержать минимум 32 случайных байта.");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTokens.ISSUER));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountRepository accounts, ObjectMapper mapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/ui/**", "/favicon.ico",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health",
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/error").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password").authenticated()
                        .requestMatchers("/api/**").hasAnyRole("VIEWER", "DISPATCHER", "ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                write(mapper, response, HttpStatus.UNAUTHORIZED, "Выполните вход."))
                        .accessDeniedHandler((request, response, error) ->
                                write(mapper, response, HttpStatus.FORBIDDEN, "Для действия недостаточно прав.")))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, error) ->
                                write(mapper, response, HttpStatus.UNAUTHORIZED, "Сессия недействительна."))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> authenticate(token, accounts))));
        return http.build();
    }

    private static JwtAuthenticationToken authenticate(Jwt token, UserAccountRepository accounts) {
        UUID id;
        try {
            id = UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException error) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        Object version = token.getClaim("ver");
        var account = accounts.findById(id).filter(user -> user.isActive() && version instanceof Number number
                && number.longValue() == user.getTokenVersion()).orElseThrow(
                () -> new InvalidBearerTokenException("Account unavailable"));
        String authority = account.isMustChangePassword() ? "PASSWORD_CHANGE_REQUIRED" : "ROLE_" + account.getRole();
        return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority(authority)), id.toString());
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, message));
    }
}
