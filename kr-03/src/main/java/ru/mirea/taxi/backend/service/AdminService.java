package ru.mirea.taxi.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.backend.domain.Role;
import ru.mirea.taxi.backend.domain.UserAccount;
import ru.mirea.taxi.backend.dto.AccountData;
import ru.mirea.taxi.backend.dto.PageResponse;
import ru.mirea.taxi.backend.error.ApiException;
import ru.mirea.taxi.backend.filter.QueryFilters;
import ru.mirea.taxi.backend.repository.RefreshSessionRepository;
import ru.mirea.taxi.backend.repository.UserAccountRepository;
import ru.mirea.taxi.backend.security.PasswordPolicy;
import ru.mirea.taxi.exception.BusinessException;

import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {
    private final UserAccountRepository accounts;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public AdminService(UserAccountRepository accounts, RefreshSessionRepository sessions, PasswordEncoder passwords,
                        AuditService audit) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional
    public void bootstrap(String email, String password) {
        if (accounts.existsByRole(Role.ADMIN)) {
            return;
        }
        PasswordPolicy.validate(password);
        accounts.save(new UserAccount(AuthService.normalizeEmail(email), "Администратор",
                Role.ADMIN, passwords.encode(password), true));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountData.Profile> list(int page, int size) {
        return PageResponse.from(accounts.findAll(QueryFilters.page(page, size, "email", "asc", Map.of("email", "email")))
                .map(AccountData.Profile::from));
    }

    @Transactional
    public AccountData.Profile create(AccountData.CreateAccount request, UUID actor) {
        PasswordPolicy.validate(request.temporaryPassword());
        var profile = request.profile();
        String email = AuthService.normalizeEmail(profile.email());
        var account = new UserAccount(email, profile.name().strip(), profile.role(),
                passwords.encode(request.temporaryPassword()), true);
        account.updateProfile(email, profile.name().strip(), profile.role(), profile.active());
        accounts.save(account);
        audit.record(actor, "ACCOUNT_CREATED", "ACCOUNT", account.getId(), "Учётная запись создана");
        return AccountData.Profile.from(account);
    }

    @Transactional
    public AccountData.Profile update(UUID id, AccountData.ProfileInput request, UUID actor) {
        if (id.equals(actor) && (!request.active() || request.role() != Role.ADMIN)) {
            throw new BusinessException("Администратор не может отключить себя или изменить собственную роль.");
        }
        UserAccount account = lock(id);
        if (!request.active() || account.getRole() != request.role()) {
            sessions.deleteByAccountId(id);
        }
        account.updateProfile(AuthService.normalizeEmail(request.email()), request.name().strip(),
                request.role(), request.active());
        audit.record(actor, "ACCOUNT_UPDATED", "ACCOUNT", id, "Профиль и права обновлены");
        return AccountData.Profile.from(account);
    }

    @Transactional
    public AccountData.Profile reset(UUID id, String password, UUID actor) {
        if (id.equals(actor)) {
            throw new BusinessException("Для своей учётной записи используйте смену пароля.");
        }
        PasswordPolicy.validate(password);
        UserAccount account = lock(id);
        account.setPassword(passwords.encode(password), true);
        sessions.deleteByAccountId(id);
        audit.record(actor, "ACCOUNT_PASSWORD_RESET", "ACCOUNT", id, "Установлен временный пароль");
        return AccountData.Profile.from(account);
    }

    private UserAccount lock(UUID id) {
        return accounts.lockById(id).orElseThrow(() -> ApiException.notFound("Учётная запись не найдена."));
    }
}
