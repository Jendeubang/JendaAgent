package com.jd.genie.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Optional Docker-backed contract test: run explicitly with -Dtest=AgentContainerContractsIT. */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class AgentContainerContractsIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jenda_agent_test")
            .withUsername("jenda")
            .withPassword("test-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void providesCompatibleMysqlAndRedisInfrastructure() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
        assertTrue(REDIS.isRunning());
        assertTrue(REDIS.getMappedPort(6379) > 0);
    }
}