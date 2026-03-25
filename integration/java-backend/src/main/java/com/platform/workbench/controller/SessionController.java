package com.platform.workbench.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.workbench.domain.entity.SessionMapping;
import com.platform.workbench.opencode.dto.OCPromptRequest;
import com.platform.workbench.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 会话管理 API。
 * 对应 OpenAPI: /api/workbench/session/*
 */
@RestController
@RequestMapping("/api/workbench/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /** POST /api/workbench/session - 新建会话 */
    @PostMapping
    public ResponseEntity<SessionMapping> createSession(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long projectId) {
        return ResponseEntity.ok(sessionService.createSession(userId, projectId));
    }

    /** GET /api/workbench/session - 会话列表 */
    @GetMapping
    public ResponseEntity<List<SessionMapping>> listSessions(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(sessionService.listSessions(userId));
    }

    /** POST /api/workbench/session/{id}/message - 发送消息（异步） */
    @PostMapping("/{id}/message")
    public Mono<ResponseEntity<Void>> sendMessage(
            @PathVariable Long id,
            @RequestBody OCPromptRequest req) {
        return sessionService.promptAsync(id, req)
                .thenReturn(ResponseEntity.<Void>accepted().build());
    }

    /** GET /api/workbench/session/{id}/message - 获取历史消息 */
    @GetMapping("/{id}/message")
    public Mono<ResponseEntity<JsonNode>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {
        return sessionService.listMessages(id, limit, before)
                .map(ResponseEntity::ok);
    }

    /** POST /api/workbench/session/{id}/abort - 取消执行 */
    @PostMapping("/{id}/abort")
    public Mono<ResponseEntity<Void>> abort(@PathVariable Long id) {
        return sessionService.abort(id)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    /** POST /api/workbench/session/{id}/fork - 分支会话 */
    @PostMapping("/{id}/fork")
    public ResponseEntity<SessionMapping> fork(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String messageId = body != null ? body.get("messageId") : null;
        return ResponseEntity.ok(sessionService.forkSession(id, messageId));
    }

    /** GET /api/workbench/session/{id}/diff - 获取文件差异 */
    @GetMapping("/{id}/diff")
    public Mono<ResponseEntity<JsonNode>> getDiff(
            @PathVariable Long id,
            @RequestParam String messageId) {
        return sessionService.getDiff(id, messageId).map(ResponseEntity::ok);
    }

    /** POST /api/workbench/session/{id}/revert - 回滚 */
    @PostMapping("/{id}/revert")
    public Mono<ResponseEntity<Void>> revert(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return sessionService.revert(id, body.get("messageId"))
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    /** PATCH /api/workbench/session/{id} - 更新标题 */
    @PatchMapping("/{id}")
    public ResponseEntity<SessionMapping> update(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(sessionService.updateTitle(id, body.get("title")));
    }

    /** DELETE /api/workbench/session/{id} - 删除会话 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
