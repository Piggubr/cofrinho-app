package com.piggu.testing;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes que precisam de banco de verdade.
 *
 * <p>Sobe um PostgreSQL em container e deixa o Flyway aplicar as migrations reais.
 * Nao usamos H2: as migrations dependem de jsonb, arrays de texto, indice GIN e
 * gen_random_uuid, e um banco em memoria fingindo ser Postgres esconderia
 * justamente os erros que queremos pegar.</p>
 *
 * <p>O container e estatico e compartilhado por todas as classes de teste do
 * modulo, o que evita subir um Postgres por classe.</p>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integracao")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
