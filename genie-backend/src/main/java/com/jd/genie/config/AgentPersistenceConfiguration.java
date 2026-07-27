package com.jd.genie.config;

import com.jd.genie.persistence.agent.mapper.AgentAssetMapper;
import com.jd.genie.persistence.agent.mapper.AgentEventMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanApprovalMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanExecutionMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanTaskStateMapper;
import com.jd.genie.persistence.agent.mapper.AgentRunMapper;
import com.jd.genie.persistence.agent.mapper.AgentSessionMapper;
import com.jd.genie.persistence.agent.mapper.AgentToolCallMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Keeps agent operational data in its own database connection. Upstream JDGenie
 * demo metadata continues using its primary embedded H2 source.
 */
@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(AgentPersistenceProperties.class)
public class AgentPersistenceConfiguration {

    /** The original JDGenie demo source, intentionally kept primary. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties genieDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    public DataSource genieDataSource(DataSourceProperties genieDataSourceProperties) {
        return genieDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "agentPersistenceDataSource")
    public DataSource agentPersistenceDataSource(AgentPersistenceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setMaximumPoolSize(12);
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("agent-persistence-pool");
        return dataSource;
    }

    @Bean(name = "agentFlyway", initMethod = "migrate")
    public Flyway agentFlyway(@Qualifier("agentPersistenceDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/agent")
                // Existing local databases already contain the pre-Flyway JDBC tables.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    /**
     * The internal SqlSessionTemplate must not be a Spring bean: MyBatis-Plus
     * owns the primary session factory/template used by unmodified modules.
     */
    @Bean(name = "agentPersistenceMapperRegistry")
    @DependsOn("agentFlyway")
    public AgentPersistenceMapperRegistry agentPersistenceMapperRegistry(
            @Qualifier("agentPersistenceDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AgentSessionMapper.class);
        configuration.addMapper(AgentRunMapper.class);
        configuration.addMapper(AgentEventMapper.class);
        configuration.addMapper(AgentToolCallMapper.class);
        configuration.addMapper(AgentAssetMapper.class);
        configuration.addMapper(AgentPlanExecutionMapper.class);
        configuration.addMapper(AgentPlanTaskStateMapper.class);
        configuration.addMapper(AgentPlanApprovalMapper.class);
        factory.setConfiguration(configuration);
        return new AgentPersistenceMapperRegistry(new SqlSessionTemplate(factory.getObject()));
    }

    @Bean
    public AgentSessionMapper agentSessionMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentSessionMapper.class);
    }

    @Bean
    public AgentRunMapper agentRunMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentRunMapper.class);
    }

    @Bean
    public AgentEventMapper agentEventMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentEventMapper.class);
    }

    @Bean
    public AgentToolCallMapper agentToolCallMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentToolCallMapper.class);
    }

    @Bean
    public AgentAssetMapper agentAssetMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentAssetMapper.class);
    }

    @Bean
    public AgentPlanExecutionMapper agentPlanExecutionMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentPlanExecutionMapper.class);
    }

    @Bean
    public AgentPlanTaskStateMapper agentPlanTaskStateMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentPlanTaskStateMapper.class);
    }

    @Bean
    public AgentPlanApprovalMapper agentPlanApprovalMapper(AgentPersistenceMapperRegistry registry) {
        return registry.mapper(AgentPlanApprovalMapper.class);
    }

    @Bean(name = "agentPersistenceTransactionManager")
    public PlatformTransactionManager agentPersistenceTransactionManager(
            @Qualifier("agentPersistenceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}