package com.jd.genie.config;

import com.jd.genie.service.agent.AgentHistoryStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * Keeps upstream DataAgent on its in-memory H2 database while persisting only JendaAgent history.
 */
@Component
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
public class PersistentAgentHistoryStoreInjector implements BeanPostProcessor {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PersistentAgentHistoryStoreInjector(Environment environment) {
        this.jdbcUrl = environment.getProperty("agent.history.jdbc-url",
                "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        this.username = environment.getProperty("agent.history.username", "sa");
        this.password = environment.getProperty("agent.history.password", "");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof AgentHistoryStore)) {
            return bean;
        }
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
            Field jdbcTemplateField = AgentHistoryStore.class.getDeclaredField("jdbcTemplate");
            jdbcTemplateField.setAccessible(true);
            jdbcTemplateField.set(bean, new JdbcTemplate(dataSource));
            return bean;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to configure the persistent agent history store", error);
        }
    }
}
