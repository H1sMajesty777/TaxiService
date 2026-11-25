package ru.mirea.taxi.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:taxicontext;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "taxi.jwt-secret=VGhpcy1pcy1vbmx5LWEtbm9uc2VjcmV0LXRlc3Qta2V5LXN0cmluZw==",
        "taxi.admin-password="
})
class ContextTest {
    @Test
    void startsSpringContext() {
    }
}
