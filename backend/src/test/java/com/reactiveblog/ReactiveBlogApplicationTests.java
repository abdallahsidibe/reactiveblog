package com.reactiveblog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.r2dbc.AutoConfigureDataR2dbc;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * Test de démarrage du contexte Spring.
 *
 * On exclut Flyway et R2DBC pour ne pas nécessiter une base PostgreSQL.
 * Ce test vérifie uniquement que l'ApplicationContext se charge sans erreur.
 * Les tests avec vraie BDD seront couverts via Docker Compose (étape 13).
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=false",
                "spring.r2dbc.url=r2dbc:postgresql://localhost:5432/test",
                "spring.r2dbc.username=test",
                "spring.r2dbc.password=test"
        }
)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
class ReactiveBlogApplicationTests {

    @Test
    void contextLoads() {
        // Vérifie que le contexte Spring démarre sans erreur
    }
}
