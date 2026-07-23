package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.model.auth.AgentPrincipal;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** User-scoped COS asset metadata with MySQL 5.1-compatible timestamp bindings. */
@Repository
public class AgentAssetMetadataStore {
    private final JdbcTemplate jdbcTemplate;

    public AgentAssetMetadataStore(Environment environment) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_asset (asset_id VARCHAR(64) PRIMARY KEY, owner_user_id VARCHAR(64) NOT NULL, session_id VARCHAR(64) NOT NULL, run_id VARCHAR(64), file_name VARCHAR(512), media_type VARCHAR(128), size_bytes BIGINT, object_key VARCHAR(1024), image_url TEXT NOT NULL, source VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL)");
        try { jdbcTemplate.execute("CREATE INDEX idx_agent_asset_owner_session ON agent_asset (owner_user_id, session_id, created_at)"); } catch (Exception ignored) { }
    }

    public void recordUpload(AgentPrincipal owner, String sessionId, AgentImageUploadResponse response, String objectKey) {
        upsert(new AgentAssetMetadata(response.assetId(), owner.userId(), sessionId, null, response.fileName(), response.mediaType(), response.size(), objectKey, response.imageUrl(), "upload", Instant.now()));
    }

    public void recordGenerated(com.jd.genie.model.auth.AgentPrincipal owner, String sessionId, String runId,
                                 StoredAgentImage storedImage, String imageUrl, String source) {
        upsert(new AgentAssetMetadata(storedImage.assetId(), owner.userId(), sessionId, runId,
                storedImage.originalFileName(), storedImage.mediaType(), storedImage.size(),
                storedImage.storedFileName(), imageUrl, source, Instant.now()));
    }
    public void recordGeneratedForSession(String sessionId, String runId, String assetId, String title, String imageUrl) {
        List<String> owners = jdbcTemplate.query("SELECT owner_user_id FROM agent_session WHERE session_id = ?", (resultSet, rowNum) -> resultSet.getString(1), sessionId);
        if (!owners.isEmpty()) upsert(new AgentAssetMetadata(assetId, owners.get(0), sessionId, runId, title, "image/*", 0L, null, imageUrl, "generated", Instant.now()));
    }

    public AgentAssetPage pageOwned(String ownerUserId, String sessionId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 60));
        boolean filterSession = sessionId != null && !sessionId.isBlank();
        long total = filterSession
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_asset WHERE owner_user_id = ? AND session_id = ?", Long.class, ownerUserId, sessionId)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_asset WHERE owner_user_id = ?", Long.class, ownerUserId);
        int offset = (safePage - 1) * safeSize;
        String sql = "SELECT asset_id, owner_user_id, session_id, run_id, file_name, media_type, size_bytes, object_key, image_url, source, created_at FROM agent_asset WHERE owner_user_id = ?" + (filterSession ? " AND session_id = ?" : "") + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        Object[] arguments = filterSession ? new Object[]{ownerUserId, sessionId, safeSize, offset} : new Object[]{ownerUserId, safeSize, offset};
        List<AgentAssetMetadata> items = jdbcTemplate.query(sql, (resultSet, rowNum) -> row(resultSet), arguments);
        return new AgentAssetPage(items, total, safePage, safeSize);
    }

    public Optional<AgentAssetMetadata> findOwned(String ownerUserId, String assetId) {
        List<AgentAssetMetadata> assets = jdbcTemplate.query("SELECT asset_id, owner_user_id, session_id, run_id, file_name, media_type, size_bytes, object_key, image_url, source, created_at FROM agent_asset WHERE asset_id = ? AND owner_user_id = ?", (resultSet, rowNum) -> row(resultSet), assetId, ownerUserId);
        return assets.isEmpty() ? Optional.empty() : Optional.of(assets.get(0));
    }

    public boolean deleteOwned(String ownerUserId, String assetId) {
        return jdbcTemplate.update("DELETE FROM agent_asset WHERE asset_id = ? AND owner_user_id = ?", assetId, ownerUserId) == 1;
    }

    private AgentAssetMetadata row(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AgentAssetMetadata(resultSet.getString("asset_id"), resultSet.getString("owner_user_id"), resultSet.getString("session_id"), resultSet.getString("run_id"), resultSet.getString("file_name"), resultSet.getString("media_type"), resultSet.getLong("size_bytes"), resultSet.getString("object_key"), resultSet.getString("image_url"), resultSet.getString("source"), resultSet.getTimestamp("created_at").toInstant());
    }

    private void upsert(AgentAssetMetadata asset) {
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("UPDATE agent_asset SET owner_user_id = ?, session_id = ?, run_id = ?, file_name = ?, media_type = ?, size_bytes = ?, object_key = ?, image_url = ?, source = ?, created_at = ? WHERE asset_id = ?");
            setValues(statement, asset);
            return statement;
        });
        if (updated == 0) jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO agent_asset (owner_user_id, session_id, run_id, file_name, media_type, size_bytes, object_key, image_url, source, created_at, asset_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            setValues(statement, asset);
            return statement;
        });
    }

    private void setValues(PreparedStatement statement, AgentAssetMetadata asset) throws java.sql.SQLException {
        statement.setString(1, asset.ownerUserId()); statement.setString(2, asset.sessionId()); statement.setString(3, asset.runId()); statement.setString(4, asset.fileName()); statement.setString(5, asset.mediaType()); statement.setLong(6, asset.size()); statement.setString(7, asset.objectKey()); statement.setString(8, asset.imageUrl()); statement.setString(9, asset.source()); statement.setTimestamp(10, Timestamp.from(asset.createdAt())); statement.setString(11, asset.assetId());
    }
}
