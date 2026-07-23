$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$showcase = Join-Path $root "showcase"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Write-Source([string]$Base, [string]$RelativePath, [string]$Source) {
    $path = Join-Path $Base $RelativePath
    [System.IO.File]::WriteAllText($path, $Source.TrimStart([char]13, [char]10) + "`n", $utf8)
    Write-Host "Updated $path"
}

Write-Source $backend "src/main/java/com/jd/genie/service/agent/AgentAssetMetadataStore.java" @'
package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.agent.AgentImageUploadResponse;
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
'@

Write-Source $backend "src/main/java/com/jd/genie/controller/AgentAssetController.java" @'
package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentAssetMetadataStore;
import com.jd.genie.service.agent.AgentAssetService;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Paginated asset library and destructive COS cleanup API for the current JWT user. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/assets")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentAssetController {
    private final AgentAssetMetadataStore assetStore;
    private final AgentAssetService assetService;

    @GetMapping
    public AgentAssetPage assets(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size, @RequestParam(required = false) String sessionId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetService.page(principal.userId(), sessionId, page, size);
    }

    @GetMapping("/{assetId}")
    public AgentAssetMetadata asset(@PathVariable String assetId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetStore.findOwned(principal.userId(), assetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    @DeleteMapping("/{assetId}")
    public AgentAssetMetadata delete(@PathVariable String assetId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetService.delete(principal.userId(), assetId);
    }
}
'@

$cosPath = Join-Path $backend "src/main/java/com/jd/genie/service/agent/CosAgentImageStorage.java"
$cosSource = [System.IO.File]::ReadAllText($cosPath)
$imageUrlNeedle = '    public String imageUrl(StoredAgentImage image) {' + [Environment]::NewLine + '        return baseUrl + "/" + encodeObjectKey(image.storedFileName());' + [Environment]::NewLine + '    }'
$imageUrlReplacement = $imageUrlNeedle + [Environment]::NewLine + [Environment]::NewLine + @'
    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(prefix)) throw new IllegalArgumentException("Refusing to delete an object outside the configured COS prefix");
        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));
        String contentType = "application/octet-stream";
        Request request = new Request.Builder().url(uri.toString()).header("Host", uri.getHost()).header("Content-Type", contentType).header("Authorization", authorization("DELETE", "/" + objectKey, uri.getHost(), contentType)).delete().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) throw new IllegalStateException("COS delete failed with HTTP " + response.code());
        } catch (IOException error) { throw new IllegalStateException("COS delete request failed: " + error.getMessage(), error); }
    }

    public void deleteObjectFromUrl(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            String objectKey = java.net.URLDecoder.decode(uri.getRawPath().replaceFirst("^/", ""), StandardCharsets.UTF_8);
            deleteObject(objectKey);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Unable to resolve COS object key from asset URL", error); }
    }
'@
if (-not $cosSource.Contains($imageUrlNeedle)) { throw "COS imageUrl method was not found. No COS source was changed." }
[System.IO.File]::WriteAllText($cosPath, $cosSource.Replace($imageUrlNeedle, $imageUrlReplacement), $utf8)
Write-Host "Updated $cosPath"

Write-Source $showcase "components/AgentAccountMenu.tsx" @'
"use client";

import { AppstoreOutlined, LoginOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Dropdown } from "antd";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearAgentAuthSession, readAgentAuthSession, type AgentAuthSession } from "../lib/agentAuth";

export function AgentAccountMenu() {
  const router = useRouter();
  const [session, setSession] = useState<AgentAuthSession>();
  useEffect(() => setSession(readAgentAuthSession()), []);
  if (!session) return <Button icon={<LoginOutlined />} onClick={() => router.push("/login")}>登录</Button>;
  return <Dropdown menu={{ items: [
    { key: "identity", disabled: true, label: `账号：${session.username}` },
    { key: "assets", icon: <AppstoreOutlined />, label: "资产中心", onClick: () => router.push("/assets") },
    { type: "divider" },
    { key: "logout", icon: <LogoutOutlined />, label: "退出登录", onClick: () => { clearAgentAuthSession(); router.replace("/login"); } },
  ] }} trigger={["click"]}><Button icon={<UserOutlined />}>{session.username}</Button></Dropdown>;
}
'@

Write-Host "Step 30 asset center backend and account menu changes completed. Compile and restart both services."
