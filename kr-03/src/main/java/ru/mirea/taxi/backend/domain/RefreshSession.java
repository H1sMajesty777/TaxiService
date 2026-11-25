package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private UserAccount account;
    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClientType client;
    private long tokenVersion;
    @Column(nullable = false)
    private Instant expiresAt;

    protected RefreshSession() {}

    public RefreshSession(UserAccount account, String tokenHash, ClientType client, Instant expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.client = client;
        this.tokenVersion = account.getTokenVersion();
        this.expiresAt = expiresAt;
    }

    public UserAccount getAccount() { return account; }
    public ClientType getClient() { return client; }
    public long getTokenVersion() { return tokenVersion; }
    public Instant getExpiresAt() { return expiresAt; }
}
