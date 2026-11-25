package ru.mirea.taxi.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.backend.domain.ClientType;
import ru.mirea.taxi.backend.dto.AccountData;
import ru.mirea.taxi.backend.service.AuthService;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE = "taxi_refresh";
    private final AuthService auth;
    private final boolean secureCookie;

    public AuthController(AuthService auth, @Value("${taxi.secure-cookie}") boolean secureCookie) {
        this.auth = auth;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<AccountData.Tokens> login(@Valid @RequestBody AccountData.Login body,
                                                    HttpServletRequest request) {
        return response(auth.login(body, request.getRemoteAddr()), body.clientType());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccountData.Tokens> refresh(@Valid @RequestBody AccountData.Refresh body,
                                                      @CookieValue(name = COOKIE, required = false) String cookie) {
        return response(auth.refresh(refreshToken(body, cookie), body.clientType()), body.clientType());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody AccountData.Refresh body,
                                        @CookieValue(name = COOKIE, required = false) String cookie) {
        auth.logout(refreshToken(body, cookie), body.clientType());
        var response = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        if (body.clientType() == ClientType.WEB) {
            response.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
        }
        return response.build();
    }

    @GetMapping("/me")
    public AccountData.Profile me(Authentication user) {
        return auth.current(UUID.fromString(user.getName()));
    }

    @PostMapping("/password")
    public ResponseEntity<AccountData.Tokens> password(Authentication user,
            @Valid @RequestBody AccountData.ChangePassword body) {
        return response(auth.changePassword(UUID.fromString(user.getName()), body), body.clientType());
    }

    private ResponseEntity<AccountData.Tokens> response(AccountData.Tokens tokens, ClientType client) {
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (client == ClientType.NATIVE) {
            return response.body(tokens);
        }
        return response.header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), AuthService.SESSION_LIFETIME)
                        .toString())
                .body(new AccountData.Tokens(tokens.accessToken(), null, tokens.user()));
    }

    private static String refreshToken(AccountData.Refresh body, String cookie) {
        return body.clientType() == ClientType.WEB ? cookie : body.refreshToken();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
    }
}
