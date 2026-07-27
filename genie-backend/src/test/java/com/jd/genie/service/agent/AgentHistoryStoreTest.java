package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.persistence.agent.service.AgentOperationalPersistenceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentHistoryStoreTest {
    @Test
    void delegatesEventWritesToTheOperationalPersistenceService() {
        AgentOperationalPersistenceService persistence = mock(AgentOperationalPersistenceService.class);
        AgentHistoryStore store = new AgentHistoryStore(persistence, new ObjectMapper());
        AgentEvent event = new AgentEvent("v1", "event-1", "session-1", "run-1", 1,
                AgentEventType.PLAN, AgentEventStatus.COMPLETE, "PlanningAgent", Instant.now(),
                Map.of("title", "plan", "content", "delegated persistence"));

        store.append(event);

        verify(persistence).append(event);
    }
}