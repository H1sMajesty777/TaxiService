package ru.mirea.taxi.backend.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import ru.mirea.taxi.backend.domain.UserAccount;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokens {
    public static final String ISSUER = "mirea-taxi";
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private final JwtEncoder encoder;

    JwtTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(UserAccount account) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(account.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_LIFETIME))
                .claim("ver", account.getTokenVersion())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
