package com.jd.genie.persistence.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.persistence.agent.entity.AgentAssetEntity;
import com.jd.genie.persistence.agent.entity.AgentEventEntity;
import com.jd.genie.persistence.agent.entity.AgentRunEntity;
import com.jd.genie.persistence.agent.entity.AgentSessionEntity;
import com.jd.genie.persistence.agent.entity.AgentToolCallEntity;
import com.jd.genie.persistence.agent.mapper.AgentAssetMapper;
import com.jd.genie.persistence.agent.mapper.AgentEventMapper;
import com.jd.genie.persistence.agent.mapper.AgentRunMapper;
import com.jd.genie.persistence.agent.mapper.AgentSessionMapper;
import com.jd.genie.persistence.agent.mapper.AgentToolCallMapper;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.agent.AgentSessionAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Authoritative persistence service for session, run, event, tool, and asset lineage. */
@Service
@RequiredArgsConstructor
public class AgentOperationalPersistenceService {
    private final AgentSessionMapper sessionMapper;
    private final AgentRunMapper runMapper;
    private final AgentEventMapper eventMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentAssetMapper assetMapper;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void claimSession(String ownerUserId, String sessionId) {
        String claimedOwner = sessionMapper.findClaimOwner(sessionId);
        if (claimedOwner == null) {
            try {
                sessionMapper.insertClaim(sessionId, ownerUserId, now());
            } catch (DuplicateKeyException ignored) {
                claimedOwner = sessionMapper.findClaimOwner(sessionId);
            }
        }
        String effectiveOwner = claimedOwner == null ? sessionMapper.findClaimOwner(sessionId) : claimedOwner;
        if (effectiveOwner != null && !effectiveOwner.equals(ownerUserId)) {
            throw new AgentSessionAccessDeniedException();
        }
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session != null && !session.getOwnerUserId().equals(ownerUserId)) {
            throw new AgentSessionAccessDeniedException();
        }
    }

    public boolean canAccess(String ownerUserId, String sessionId) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        if (session != null) return session.getOwnerUserId().equals(ownerUserId);
        String claimOwner = sessionMapper.findClaimOwner(sessionId);
        return claimOwner != null && claimOwner.equals(ownerUserId);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void startRun(String sessionId, String runId, AgentRunRequest request) {
        String ownerUserId = AgentRequestUserContext.current().userId();
        claimSession(ownerUserId, sessionId);
        Timestamp timestamp = now();
        AgentSessionEntity existing = sessionMapper.findById(sessionId);
        if (existing == null) {
            AgentSessionEntity session = new AgentSessionEntity();
            session.setSessionId(sessionId);
            session.setOwnerUserId(ownerUserId);
            session.setLatestRunId(runId);
            session.setMode(request.getMode().getValue());
            session.setLatestPrompt(request.getPrompt());
            session.setRunStatus(AgentEventStatus.RUNNING.getValue());
            session.setCreatedAt(timestamp);
            session.setUpdatedAt(timestamp);
            sessionMapper.insertSession(session);
        } else if (sessionMapper.updateRun(sessionId, ownerUserId, runId, request.getMode().getValue(), request.getPrompt(), AgentEventStatus.RUNNING.getValue(), timestamp) != 1) {
            throw new AgentSessionAccessDeniedException();
        }
        AgentRunEntity run = new AgentRunEntity();
        run.setRunId(runId);
        run.setSessionId(sessionId);
        run.setOwnerUserId(ownerUserId);
        run.setMode(request.getMode().getValue());
        run.setPrompt(request.getPrompt());
        run.setImageUrlsJson(json(request.getImageUrls()));
        run.setStatus(AgentEventStatus.RUNNING.getValue());
        run.setCreatedAt(timestamp);
        runMapper.insertRun(run);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void append(AgentEvent event) {
        AgentEventEntity entity = new AgentEventEntity();
        entity.setEventId(event.eventId());
        entity.setSessionId(event.sessionId());
        entity.setRunId(event.runId());
        entity.setSequenceNo(event.sequence());
        entity.setEventType(event.messageType().getValue());
        entity.setStatus(event.status().getValue());
        entity.setAgentName(event.agent());
        entity.setOccurredAt(Timestamp.from(event.occurredAt()));
        entity.setPayloadJson(json(event.payload()));
        eventMapper.insertEvent(entity);
        persistToolLineage(event);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void completeRun(String sessionId, String runId, AgentEventStatus status) {
        Timestamp timestamp = now();
        runMapper.complete(runId, status.getValue(), timestamp);
        sessionMapper.updateStatus(sessionId, status.getValue(), timestamp);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void updateRunStatus(String sessionId, String runId, AgentEventStatus status) {
        runMapper.updateStatus(sessionId, runId, status.getValue());
        sessionMapper.updateStatus(sessionId, status.getValue(), now());
    }

    public long lastSequence(String runId) {
        Long sequence = eventMapper.lastSequence(runId);
        return sequence == null ? 0L : sequence;
    }

    public List<AgentEventEntity> replay(String ownerUserId, String sessionId) {
        if (!canAccess(ownerUserId, sessionId)) throw new AgentSessionAccessDeniedException();
        return eventMapper.replay(sessionId);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void upsertAsset(AgentAssetMetadata asset) {
        AgentAssetEntity entity = assetEntity(asset);
        if (assetMapper.updateAsset(entity) == 0) assetMapper.insertAsset(entity);
    }

    public AgentAssetPage pageAssets(String ownerUserId, String sessionId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 60));
        int offset = (safePage - 1) * safeSize;
        boolean inSession = sessionId != null && !sessionId.isBlank();
        long total = inSession ? assetMapper.countOwnedInSession(ownerUserId, sessionId) : assetMapper.countOwned(ownerUserId);
        List<AgentAssetMetadata> items = (inSession
                ? assetMapper.pageOwnedInSession(ownerUserId, sessionId, safeSize, offset)
                : assetMapper.pageOwned(ownerUserId, safeSize, offset))
                .stream().map(this::assetMetadata).toList();
        return new AgentAssetPage(items, total, safePage, safeSize);
    }

    public Optional<AgentAssetMetadata> findOwnedAsset(String ownerUserId, String assetId) {
        return Optional.ofNullable(assetMapper.findOwned(ownerUserId, assetId)).map(this::assetMetadata);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public boolean deleteOwnedAsset(String ownerUserId, String assetId) {
        return assetMapper.deleteOwned(ownerUserId, assetId) == 1;
    }

    public String ownerOfSession(String sessionId) {
        AgentSessionEntity session = sessionMapper.findById(sessionId);
        return session == null ? null : session.getOwnerUserId();
    }

    private void persistToolLineage(AgentEvent event) {
        if (event.messageType().getValue().equals("tool_call")) {
            String owner = runMapper.findOwner(event.runId());
            if (owner == null) return;
            AgentToolCallEntity call = new AgentToolCallEntity();
            call.setToolCallId(UUID.randomUUID().toString());
            call.setSessionId(event.sessionId());
            call.setRunId(event.runId());
            call.setOwnerUserId(owner);
            call.setToolName(text(event.payload(), "tool"));
            call.setCallEventId(event.eventId());
            call.setStatus(event.status().getValue());
            call.setRequestJson(json(event.payload()));
            call.setStartedAt(Timestamp.from(event.occurredAt()));
            toolCallMapper.insertCall(call);
        }
        if (event.messageType().getValue().equals("tool_result")) {
            String toolName = text(event.payload(), "tool");
            String toolCallId = toolCallMapper.findLatestRunning(event.runId(), toolName);
            if (toolCallId != null) {
                toolCallMapper.complete(toolCallId, event.eventId(), event.status().getValue(), json(event.payload()), Timestamp.from(event.occurredAt()));
            }
        }
    }

    private AgentAssetEntity assetEntity(AgentAssetMetadata asset) {
        AgentAssetEntity entity = new AgentAssetEntity();
        entity.setAssetId(asset.assetId());
        entity.setOwnerUserId(asset.ownerUserId());
        entity.setSessionId(asset.sessionId());
        entity.setRunId(asset.runId());
        entity.setFileName(asset.fileName());
        entity.setMediaType(asset.mediaType());
        entity.setSizeBytes(asset.size());
        entity.setObjectKey(asset.objectKey());
        entity.setImageUrl(asset.imageUrl());
        entity.setSource(asset.source());
        entity.setCreatedAt(Timestamp.from(asset.createdAt()));
        return entity;
    }

    private AgentAssetMetadata assetMetadata(AgentAssetEntity entity) {
        return new AgentAssetMetadata(entity.getAssetId(), entity.getOwnerUserId(), entity.getSessionId(), entity.getRunId(), entity.getFileName(), entity.getMediaType(), entity.getSizeBytes(), entity.getObjectKey(), entity.getImageUrl(), entity.getSource(), entity.getCreatedAt().toInstant());
    }

    private Timestamp now() { return Timestamp.from(Instant.now()); }
    private String text(Map<String, Object> payload, String key) { Object value = payload.get(key); return value == null ? "unknown" : value.toString(); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException error) { throw new IllegalStateException("Unable to serialize agent persistence payload", error); } }
}