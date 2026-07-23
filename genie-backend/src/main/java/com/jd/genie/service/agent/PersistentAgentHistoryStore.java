package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

/**
 * Primary history repository for local development. The upstream repository remains available for its H2 demo data.
 */
@Primary
@Repository
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
public class PersistentAgentHistoryStore extends AgentHistoryStore {
    public PersistentAgentHistoryStore(ObjectMapper objectMapper, Environment environment) {
        super(createJdbcTemplate(environment), objectMapper);
    }

    private static JdbcTemplate createJdbcTemplate(Environment environment) {
        String jdbcUrl = environment.getProperty("agent.history.jdbc-url",
                "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        return new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, username, password));
    }
}
