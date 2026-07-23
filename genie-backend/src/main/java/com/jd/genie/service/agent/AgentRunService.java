package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentRunRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentRunService {
    SseEmitter startRun(String sessionId, AgentRunRequest request);
}
