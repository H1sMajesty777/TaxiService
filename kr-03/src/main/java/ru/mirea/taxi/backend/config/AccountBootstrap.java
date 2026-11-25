package ru.mirea.taxi.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.mirea.taxi.backend.service.AdminService;

@Configuration
public class AccountBootstrap {
    @Bean
    ApplicationRunner prepareAdministrator(AdminService users, @Value("${taxi.admin-email}") String email,
                                           @Value("${taxi.admin-password}") String password) {
        return args -> {
            if (!password.isBlank()) {
                users.bootstrap(email, password);
            }
        };
    }
}
