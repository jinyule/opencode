package com.platform.workbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.domain.entity.SessionMapping;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.opencode.dto.OCPromptRequest;
import com.platform.workbench.opencode.dto.OCSession;
import com.platform.workbench.repository.SessionMappingRepository;
import com.platform.workbench.sandbox.SandboxManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 会话业务逻辑层。
 *
 * 负责：
 * - 平台 sessionId ↔ opencode sessionId 映射
 * - 调用 OpenCodeClient 执行实际操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SandboxManager sandboxManager;
    private final OpenCodeClient openCodeClient;
    private final SessionMappingRepository sessionMappingRepo;

    /**
     * 新建会话。
     */
    @Transactional
    public SessionMapping createSession(Long userId, Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        OCSession ocSession = openCodeClient.createSession(sandbox).block();

        SessionMapping mapping = new SessionMapping();
        mapping.setSandbox(sandbox);
        mapping.setOpencodeSessionId(ocSession.getId());
        mapping.setTitle(ocSession.getTitle());
        return sessionMappingRepo.save(mapping);
    }

    /**
     * 获取用户所有会话列表。
     */
    public List<SessionMapping> listSessions(Long userId) {
        return sessionMappingRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 发送异步 prompt，立即返回（进度通过 SSE→WS 推送）。
     */
    public Mono<Void> promptAsync(Long platformSessionId, OCPromptRequest req) {
        SessionMapping mapping = sessionMappingRepo.findById(platformSessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + platformSessionId));

        SandboxInstance sandbox = mapping.getSandbox();
        sandboxManager.ensureRunning(sandbox.getUserId(), sandbox.getProjectId());

        return openCodeClient.promptAsync(sandbox, mapping.getOpencodeSessionId(), req);
    }

    /**
     * 取消当前执行。
     */
    public Mono<Void> abort(Long platformSessionId) {
        SessionMapping mapping = requireMapping(platformSessionId);
        return openCodeClient.abortSession(mapping.getSandbox(), mapping.getOpencodeSessionId());
    }

    /**
     * 获取消息历史。
     */
    public Mono<JsonNode> listMessages(Long platformSessionId, int limit, String before) {
        SessionMapping mapping = requireMapping(platformSessionId);
        return openCodeClient.listMessages(
                mapping.getSandbox(), mapping.getOpencodeSessionId(), limit, before);
    }

    /**
     * 分支会话。
     */
    @Transactional
    public SessionMapping forkSession(Long platformSessionId, String messageId) {
        SessionMapping original = requireMapping(platformSessionId);
        SandboxInstance sandbox = original.getSandbox();

        OCSession forked = openCodeClient.forkSession(
                sandbox, original.getOpencodeSessionId(), messageId).block();

        SessionMapping newMapping = new SessionMapping();
        newMapping.setSandbox(sandbox);
        newMapping.setOpencodeSessionId(forked.getId());
        newMapping.setTitle(forked.getTitle());
        return sessionMappingRepo.save(newMapping);
    }

    /**
     * 回滚更改。
     */
    public Mono<Void> revert(Long platformSessionId, String messageId) {
        SessionMapping mapping = requireMapping(platformSessionId);
        return openCodeClient.revertSession(
                mapping.getSandbox(), mapping.getOpencodeSessionId(), messageId);
    }

    /**
     * 获取文件差异。
     */
    public Mono<JsonNode> getDiff(Long platformSessionId, String messageId) {
        SessionMapping mapping = requireMapping(platformSessionId);
        return openCodeClient.getDiff(
                mapping.getSandbox(), mapping.getOpencodeSessionId(), messageId);
    }

    /**
     * 更新会话标题。
     */
    @Transactional
    public SessionMapping updateTitle(Long platformSessionId, String title) {
        SessionMapping mapping = requireMapping(platformSessionId);
        openCodeClient.updateSession(
                mapping.getSandbox(), mapping.getOpencodeSessionId(), Map.of("title", title)).block();
        mapping.setTitle(title);
        return sessionMappingRepo.save(mapping);
    }

    /**
     * 删除会话。
     */
    @Transactional
    public void deleteSession(Long platformSessionId) {
        SessionMapping mapping = requireMapping(platformSessionId);
        openCodeClient.deleteSession(
                mapping.getSandbox(), mapping.getOpencodeSessionId()).block();
        sessionMappingRepo.delete(mapping);
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private SessionMapping requireMapping(Long platformSessionId) {
        return sessionMappingRepo.findById(platformSessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + platformSessionId));
    }
}
