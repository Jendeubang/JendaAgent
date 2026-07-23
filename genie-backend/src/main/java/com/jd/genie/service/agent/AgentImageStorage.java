package com.jd.genie.service.agent;

import com.jd.genie.model.agent.StoredAgentImage;
import org.springframework.web.multipart.MultipartFile;

public interface AgentImageStorage {
    StoredAgentImage store(MultipartFile file);
}
