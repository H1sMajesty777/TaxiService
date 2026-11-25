package ru.mirea.taxi.backend.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120, unique = true)
    private String email;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(nullable = false, length = 72)
    private String passwordHash;
    private boolean active;
    private boolean mustChangePassword;
    private long tokenVersion;

    protected UserAccount() {}

    public UserAccount(String email, String name, Role role, String passwordHash, boolean temporary) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.active = true;
        setPassword(passwordHash, temporary);
    }

    public void updateProfile(String email, String name, Role role, boolean active) {
        this.email = email;
        this.name = name;
        if (this.role != role || this.active != active) {
            tokenVersion++;
        }
        this.role = role;
        this.active = active;
    }

    public void setPassword(String passwordHash, boolean temporary) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = temporary;
        tokenVersion++;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public long getTokenVersion() { return tokenVersion; }
}
