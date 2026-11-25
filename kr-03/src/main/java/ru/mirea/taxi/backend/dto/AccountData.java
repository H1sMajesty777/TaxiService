package ru.mirea.taxi.backend.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import ru.mirea.taxi.backend.domain.ClientType;
import ru.mirea.taxi.backend.domain.Role;
import ru.mirea.taxi.backend.domain.UserAccount;
import java.util.UUID;

public final class AccountData {
    private AccountData() {}

    public record Login(@NotBlank @Email @Size(max = 120) String email,
                        @NotBlank @Size(max = 72) String password, @NotNull ClientType clientType) {}

    public record Refresh(@NotNull ClientType clientType, @Size(max = 128) String refreshToken) {}
    public record ChangePassword(@NotBlank @Size(max = 72) String currentPassword,
                                 @NotBlank @Size(max = 72) String newPassword,
                                 @NotNull ClientType clientType) {}
    public record Profile(UUID id, String email, String name, Role role, boolean active,
                          boolean mustChangePassword) {
        public static Profile from(UserAccount user) {
            return new Profile(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                    user.isActive(), user.isMustChangePassword());
        }
    }

    public record Tokens(String accessToken, String refreshToken, Profile user) {}

    public record ProfileInput(@NotBlank @Email @Size(max = 120) String email,
                               @NotBlank @Size(max = 100) String name, @NotNull Role role,
                               boolean active) {}

    public record CreateAccount(@Valid @NotNull ProfileInput profile,
                                @NotBlank @Size(max = 72) String temporaryPassword) {}
    public record ResetPassword(@NotBlank @Size(max = 72) String temporaryPassword) {}
}
