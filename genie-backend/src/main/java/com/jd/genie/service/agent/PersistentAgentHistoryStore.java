package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.persistence.agent.service.AgentOperationalPersistenceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

/** Primary history facade backed by the Flyway-managed MyBatis-Flex data layer. */
@Primary
@Repository
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
public class PersistentAgentHistoryStore extends AgentHistoryStore {
    public PersistentAgentHistoryStore(AgentOperationalPersistenceService persistence, ObjectMapper objectMapper) {
        super(persistence, objectMapper);
    }
}