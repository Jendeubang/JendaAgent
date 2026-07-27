package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentSessionOverview;
import com.jd.genie.model.agent.AgentWorkspaceAsset;
import com.jd.genie.model.agent.AgentWorkspaceSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** User-scoped workspace catalog over the isolated JendaAgent persistence database. */
@Component
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
public class PersistentAgentWorkspaceCatalog {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;

    public PersistentAgentWorkspaceCatalog(ObjectMapper objectMapper, Environment environment, ObjectProvider<CosSignedUrlService> signedUrlServiceProvider) {
        String jdbcUrl = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, username, password));
        this.objectMapper = objectMapper;
        this.signedUrlServiceProvider = signedUrlServiceProvider;
    }

    public List<AgentSessionOverview> list(String ownerUserId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT s.session_id, s.latest_run_id, s.mode, s.latest_prompt, s.run_status, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_run r WHERE r.session_id = s.session_id) AS run_count, (SELECT COUNT(*) FROM agent_message m WHERE m.session_id = s.session_id) AS event_count FROM agent_session s WHERE s.owner_user_id = ? ORDER BY s.updated_at DESC LIMIT ?", (resultSet, rowNum) -> toSession(resultSet), ownerUserId, safeLimit);
    }

    public Optional<AgentWorkspaceSnapshot> workspace(String ownerUserId, String sessionId) {
        List<AgentSessionOverview> sessions = jdbcTemplate.query("SELECT s.session_id, s.latest_run_id, s.mode, s.latest_prompt, s.run_status, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_run r WHERE r.session_id = s.session_id) AS run_count, (SELECT COUNT(*) FROM agent_message m WHERE m.session_id = s.session_id) AS event_count FROM agent_session s WHERE s.session_id = ? AND s.owner_user_id = ?", (resultSet, rowNum) -> toSession(resultSet), sessionId, ownerUserId);
        if (sessions.isEmpty()) return Optional.empty();
        List<AgentWorkspaceAsset> assets = new ArrayList<>();
        jdbcTemplate.query("SELECT run_id, image_urls_json, created_at FROM agent_run WHERE session_id = ? AND owner_user_id = ? ORDER BY created_at DESC", (RowCallbackHandler) resultSet -> {
            String runId = resultSet.getString("run_id");
            Instant createdAt = instant(resultSet.getTimestamp("created_at"));
            List<String> imageUrls = imageUrls(resultSet.getString("image_urls_json"));
            for (int index = 0; index < imageUrls.size(); index++) assets.add(new AgentWorkspaceAsset("reference-" + runId + "-" + index, runId, "Reference " + (index + 1), refreshUrl(imageUrls.get(index)), "reference", createdAt));
        }, sessionId, ownerUserId);
        jdbcTemplate.query("SELECT m.run_id, m.occurred_at, i.asset_id, i.title, COALESCE(a.image_url, i.image_url) AS image_url, a.object_key FROM agent_image_message i JOIN agent_message m ON m.event_id = i.event_id JOIN agent_session s ON s.session_id = m.session_id LEFT JOIN agent_asset a ON a.asset_id = i.asset_id AND a.owner_user_id = s.owner_user_id WHERE m.session_id = ? AND s.owner_user_id = ? ORDER BY m.occurred_at DESC", (RowCallbackHandler) resultSet -> assets.add(new AgentWorkspaceAsset(resultSet.getString("asset_id"), resultSet.getString("run_id"), resultSet.getString("title"), refreshUrl(resultSet.getString("object_key"), resultSet.getString("image_url")), "generated", instant(resultSet.getTimestamp("occurred_at")))), sessionId, ownerUserId);
        assets.sort(Comparator.comparing(AgentWorkspaceAsset::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return Optional.of(new AgentWorkspaceSnapshot(sessions.get(0), assets));
    }

    private AgentSessionOverview toSession(java.sql.ResultSet resultSet) throws java.sql.SQLException { return new AgentSessionOverview(resultSet.getString("session_id"), resultSet.getString("latest_run_id"), resultSet.getString("mode"), resultSet.getString("latest_prompt"), resultSet.getString("run_status"), instant(resultSet.getTimestamp("created_at")), instant(resultSet.getTimestamp("updated_at")), resultSet.getInt("run_count"), resultSet.getInt("event_count")); }
    private List<String> imageUrls(String value) { try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() { }); } catch (Exception ignored) { return List.of(); } }

    private String refreshUrl(String objectKey, String imageUrl) {
        CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
        if (signer == null) return imageUrl;
        if (objectKey != null && !objectKey.isBlank()) return signer.createGetUrl(objectKey);
        return signer.createGetUrlIfOwned(imageUrl);
    }

    private String refreshUrl(String imageUrl) { return refreshUrl(null, imageUrl); }

    private Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
