package ru.mirea.taxi.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.mirea.taxi.backend.dto.AccountData;
import ru.mirea.taxi.backend.dto.PageResponse;
import ru.mirea.taxi.backend.service.AdminService;
import ru.mirea.taxi.backend.service.AnalyticsService;
import ru.mirea.taxi.backend.service.AuditService;

import java.util.UUID;

@RestController
public class DashboardController {
    private final AdminService accounts;
    private final AnalyticsService analytics;
    private final AuditService audit;

    public DashboardController(AdminService accounts, AnalyticsService analytics, AuditService audit) {
        this.accounts = accounts;
        this.analytics = analytics;
        this.audit = audit;
    }

    @GetMapping("/api/analytics/summary")
    public AnalyticsService.Summary analytics() {
        return analytics.summarize();
    }

    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditService.EventView> audit(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "time") String sort, @RequestParam(defaultValue = "desc") String dir) {
        return audit.search(q, page, size, sort, dir);
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccountData.Profile> users(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return accounts.list(page, size);
    }

    @PostMapping("/api/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AccountData.Profile create(@Valid @RequestBody AccountData.CreateAccount input, Authentication user) {
        return accounts.create(input, UUID.fromString(user.getName()));
    }

    @PutMapping("/api/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountData.Profile update(@PathVariable UUID id, @Valid @RequestBody AccountData.ProfileInput input,
                                      Authentication user) {
        return accounts.update(id, input, UUID.fromString(user.getName()));
    }

    @PostMapping("/api/admin/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountData.Profile reset(@PathVariable UUID id, @Valid @RequestBody AccountData.ResetPassword input,
                                     Authentication user) {
        return accounts.reset(id, input.temporaryPassword(), UUID.fromString(user.getName()));
    }
}
