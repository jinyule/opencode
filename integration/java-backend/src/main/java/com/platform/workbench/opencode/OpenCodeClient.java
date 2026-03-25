package com.platform.workbench.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.opencode.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * opencode REST API 客户端。
 *
 * 所有请求：
 *  - Basic Auth: Authorization: Basic base64("opencode:{password}")
 *  - 目录范围:   x-opencode-directory: /workspace
 */
@Slf4j
@Component
public class OpenCodeClient {

    private static final String DIRECTORY = "/workspace";
    private static final String OC_USER   = "opencode";

    private final WebClient.Builder webClientBuilder;

    public OpenCodeClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private WebClient clientFor(SandboxInstance sandbox) {
        String credentials = OC_USER + ":" + sandbox.getPassword();
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return webClientBuilder
                .baseUrl("http://" + sandbox.getHost())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basic)
                .defaultHeader("x-opencode-directory", DIRECTORY)
                .build();
    }

    // ── Health ────────────────────────────────────────────────────

    public Mono<Boolean> health(SandboxInstance sandbox) {
        return clientFor(sandbox)
                .get().uri("/global/health")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> true)
                .onErrorReturn(false);
    }

    // ── Config ────────────────────────────────────────────────────

    public Mono<Void> patchConfig(SandboxInstance sandbox, Map<String, Object> config) {
        return clientFor(sandbox)
                .patch().uri("/config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    // ── Session ───────────────────────────────────────────────────

    public Mono<OCSession> createSession(SandboxInstance sandbox) {
        return clientFor(sandbox)
                .post().uri("/session/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(OCSession.class);
    }

    public Mono<List<OCSession>> listSessions(SandboxInstance sandbox, int limit) {
        return clientFor(sandbox)
                .get().uri(u -> u.path("/session/").queryParam("limit", limit).build())
                .retrieve()
                .bodyToFlux(OCSession.class)
                .collectList();
    }

    public Mono<OCSession> getSession(SandboxInstance sandbox, String sessionId) {
        return clientFor(sandbox)
                .get().uri("/session/{id}", sessionId)
                .retrieve()
                .bodyToMono(OCSession.class);
    }

    public Mono<Void> deleteSession(SandboxInstance sandbox, String sessionId) {
        return clientFor(sandbox)
                .delete().uri("/session/{id}", sessionId)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<OCSession> updateSession(SandboxInstance sandbox, String sessionId, Map<String, Object> body) {
        return clientFor(sandbox)
                .patch().uri("/session/{id}", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OCSession.class);
    }

    // ── Prompt ────────────────────────────────────────────────────

    /**
     * 异步发送 prompt，立即返回。
     * 进度通过 SSE 事件流实时推送。
     */
    public Mono<Void> promptAsync(SandboxInstance sandbox, String sessionId, OCPromptRequest req) {
        return clientFor(sandbox)
                .post().uri("/session/{id}/prompt_async", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> abortSession(SandboxInstance sandbox, String sessionId) {
        return clientFor(sandbox)
                .post().uri("/session/{id}/abort", sessionId)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<OCSession> forkSession(SandboxInstance sandbox, String sessionId, String messageId) {
        Map<String, Object> body = messageId != null ? Map.of("messageID", messageId) : Map.of();
        return clientFor(sandbox)
                .post().uri("/session/{id}/fork", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OCSession.class);
    }

    public Mono<Void> revertSession(SandboxInstance sandbox, String sessionId, String messageId) {
        return clientFor(sandbox)
                .post().uri("/session/{id}/revert", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("messageID", messageId))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> unrevertSession(SandboxInstance sandbox, String sessionId) {
        return clientFor(sandbox)
                .post().uri("/session/{id}/unrevert", sessionId)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    // ── Messages ──────────────────────────────────────────────────

    public Mono<JsonNode> listMessages(SandboxInstance sandbox, String sessionId, int limit, String before) {
        return clientFor(sandbox)
                .get()
                .uri(u -> {
                    var b = u.path("/session/{id}/message").queryParam("limit", limit);
                    if (before != null) b.queryParam("before", before);
                    return b.build(sessionId);
                })
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getDiff(SandboxInstance sandbox, String sessionId, String messageId) {
        return clientFor(sandbox)
                .get()
                .uri(u -> u.path("/session/{id}/diff").queryParam("messageID", messageId).build(sessionId))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ── SSE Event Stream ──────────────────────────────────────────

    /**
     * 订阅 opencode SSE 事件流。
     * 返回 Flux<OCEvent>，调用方负责处理背压和重连。
     */
    public Flux<OCEvent> subscribeEvents(SandboxInstance sandbox) {
        log.info("Connecting SSE stream for sandbox {}", sandbox.getId());
        return clientFor(sandbox)
                .get().uri("/event")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(OCEvent.class)
                .doOnNext(e -> log.debug("SSE [sandbox={}] type={}", sandbox.getId(), e.getType()))
                .doOnError(e -> log.warn("SSE error for sandbox {}: {}", sandbox.getId(), e.getMessage()))
                .doOnComplete(() -> log.info("SSE stream completed for sandbox {}", sandbox.getId()));
    }

    // ── Permission ────────────────────────────────────────────────

    public Mono<Void> replyPermission(SandboxInstance sandbox, String requestId, String reply) {
        return clientFor(sandbox)
                .post().uri("/permission/{id}/reply", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new OCPermissionReply(reply))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> replyQuestion(SandboxInstance sandbox, String requestId, List<String> answers) {
        return clientFor(sandbox)
                .post().uri("/question/{id}/reply", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("answers", answers))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    // ── File ──────────────────────────────────────────────────────

    public Mono<JsonNode> listFiles(SandboxInstance sandbox, String path) {
        return clientFor(sandbox)
                .get()
                .uri(u -> u.path("/file").queryParam("path", path).build())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getFileContent(SandboxInstance sandbox, String path) {
        return clientFor(sandbox)
                .get()
                .uri(u -> u.path("/file/content").queryParam("path", path).build())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> getFileStatus(SandboxInstance sandbox) {
        return clientFor(sandbox)
                .get().uri("/file/status")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> searchFiles(SandboxInstance sandbox, String query) {
        return clientFor(sandbox)
                .get()
                .uri(u -> u.path("/find/file").queryParam("query", query).build())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> searchCode(SandboxInstance sandbox, String pattern, String glob) {
        return clientFor(sandbox)
                .get()
                .uri(u -> {
                    var b = u.path("/find").queryParam("pattern", pattern);
                    if (glob != null) b.queryParam("glob", glob);
                    return b.build();
                })
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ── Skill ─────────────────────────────────────────────────────

    public Mono<JsonNode> listSkills(SandboxInstance sandbox) {
        return clientFor(sandbox)
                .get().uri("/skill")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ── Provider ──────────────────────────────────────────────────

    public Mono<JsonNode> listProviders(SandboxInstance sandbox) {
        return clientFor(sandbox)
                .get().uri("/provider")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<Void> setProviderAuth(SandboxInstance sandbox, String providerId, Map<String, Object> body) {
        return clientFor(sandbox)
                .put().uri("/auth/{id}", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}
