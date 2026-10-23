package com.foundora.database;

import com.google.firebase.auth.FirebaseAuth;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.data-source-properties.sslmode=disable"})
class SchemaValidationTest {

    private static EmbeddedPostgres postgres;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        postgres = EmbeddedPostgres.start();
        SupabaseStubs.create(postgres.getPostgresDatabase());
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void entitiesMatchTheMigratedSchema() {
    }
}
