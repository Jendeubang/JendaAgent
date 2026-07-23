package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunMode;
import com.jd.genie.model.agent.AgentRunRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentHistoryStoreTest {
    @Test
    void storesAndReplaysTypedEventsInSequenceOrder() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:agent-history;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        AgentHistoryStore store = new AgentHistoryStore(new JdbcTemplate(dataSource), new ObjectMapper());
        store.initializeSchema();

        AgentRunRequest request = new AgentRunRequest();
        request.setPrompt("生成产品海报");
        request.setMode(AgentRunMode.PLAN_SOLVE);
        request.setImageUrls(List.of("https://cos.example.com/reference.png"));
        store.startRun("session-1", "run-1", request);

        AgentEvent plan = new AgentEvent("v1", "event-plan", "session-1", "run-1", 2,
                AgentEventType.PLAN, AgentEventStatus.COMPLETE, "PlanningAgent", Instant.now(),
                Map.of("title", "任务规划", "content", "已完成任务拆解", "steps", List.of("分析目标", "安排执行")));
        AgentEvent image = new AgentEvent("v1", "event-image", "session-1", "run-1", 4,
                AgentEventType.IMAGE, AgentEventStatus.COMPLETE, "ImageToolchain", Instant.now(),
                Map.of("assetId", "asset-1", "imageUrl", "https://cos.example.com/output.png", "title", "视觉方案 A", "content", "生成完成"));
        store.append(image);
        store.append(plan);
        store.completeRun("session-1", "run-1", AgentEventStatus.COMPLETE);

        List<AgentEvent> events = store.replay("session-1");
        assertEquals(List.of("event-plan", "event-image"), events.stream().map(AgentEvent::eventId).toList());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(1) FROM agent_plan_message", Integer.class));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(1) FROM agent_image_message", Integer.class));
    }
}
