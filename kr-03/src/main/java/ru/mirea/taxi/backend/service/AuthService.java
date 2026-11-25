package ru.mirea.taxi.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.ClientType;
import ru.mirea.taxi.backend.domain.RefreshSession;
import ru.mirea.taxi.backend.domain.UserAccount;
import ru.mirea.taxi.backend.dto.AccountData;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.backend.repository.RefreshSessionRepository;
import ru.mirea.taxi.backend.repository.UserAccountRepository;
import ru.mirea.taxi.backend.security.JwtTokens;
import ru.mirea.taxi.backend.security.LoginAttempts;
import ru.mirea.taxi.backend.security.PasswordPolicy;
import ru.mirea.taxi.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    public static final Duration SESSION_LIFETIME = Duration.ofDays(14);
    private final UserAccountRepository accounts;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final LoginAttempts attempts;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final String decoyHash;

    public AuthService(UserAccountRepository accounts, RefreshSessionRepository sessions, PasswordEncoder passwords,
                       JwtTokens tokens, LoginAttempts attempts, AuditService audit) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.attempts = attempts;
        this.audit = audit;
        decoyHash = passwords.encode(randomToken());
    }

    @Transactional
    public AccountData.Tokens login(AccountData.Login credentials, String remoteAddress) {
        String email = normalizeEmail(credentials.email());
        String key = remoteAddress + "|" + email;
        attempts.reserve(key);
        UserAccount account = accounts.findByEmail(email).orElse(null);
        String hash = account == null ? decoyHash : account.getPasswordHash();
        boolean matches = matches(credentials.password(), hash);
        if (account == null || !account.isActive() || !matches) {
            audit.rejectedLogin();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль.");
        }
        attempts.clear(key);
        sessions.deleteByExpiresAtBefore(Instant.now());
        audit.record(account.getId(), "LOGIN", "ACCOUNT", account.getId(), "Вход " + credentials.clientType());
        return issueSession(account, credentials.clientType());
    }

    @Transactional
    public AccountData.Tokens refresh(String refreshToken, ClientType client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefresh();
        }
        var session = sessions.findByTokenHash(digest(refreshToken)).orElseThrow(AuthService::invalidRefresh);
        var account = session.getAccount();
        if (session.getClient() != client || !session.getExpiresAt().isAfter(Instant.now()) || !account.isActive()
                || session.getTokenVersion() != account.getTokenVersion()) {
            throw invalidRefresh();
        }
        sessions.delete(session);
        return issueSession(account, client);
    }

    @Transactional
    public void logout(String refreshToken, ClientType client) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.findByTokenHash(digest(refreshToken)).filter(session -> session.getClient() == client)
                    .ifPresent(session -> {
                        UUID accountId = session.getAccount().getId();
                        sessions.delete(session);
                        audit.record(accountId, "LOGOUT", "ACCOUNT", accountId, "Выход " + client);
                    });
        }
    }

    @Transactional(readOnly = true)
    public AccountData.Profile current(UUID accountId) {
        return accounts.findById(accountId).map(AccountData.Profile::from).orElseThrow(AuthService::invalidRefresh);
    }

    @Transactional
    public AccountData.Tokens changePassword(UUID accountId, AccountData.ChangePassword request) {
        UserAccount account = accounts.lockById(accountId).filter(UserAccount::isActive)
                .orElseThrow(AuthService::invalidRefresh);
        if (!matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BusinessException("Текущий пароль указан неверно.");
        }
        PasswordPolicy.validate(request.newPassword());
        if (matches(request.newPassword(), account.getPasswordHash())) {
            throw new BusinessException("Укажите новый пароль, отличный от текущего.");
        }
        account.setPassword(passwords.encode(request.newPassword()), false);
        sessions.deleteByAccountId(accountId);
        audit.record(accountId, "PASSWORD_CHANGED", "ACCOUNT", accountId, "Пароль изменён");
        return issueSession(account, request.clientType());
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String input, String hash) {
        return input.getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(input, hash);
    }

    private AccountData.Tokens issueSession(UserAccount account, ClientType client) {
        String refreshToken = randomToken();
        sessions.save(new RefreshSession(account, digest(refreshToken), client, Instant.now().plus(SESSION_LIFETIME)));
        return new AccountData.Tokens(tokens.issue(account), refreshToken, AccountData.Profile.from(account));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен.", error);
        }
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Войдите заново.");
    }
}
