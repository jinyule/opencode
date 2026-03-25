package com.platform.workbench.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.workbench.bridge.WebSocketDispatcher;
import com.platform.workbench.security.JwtUtil;
import com.platform.workbench.service.PermissionPolicyEngine;
import com.platform.workbench.repository.SandboxInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前端 WebSocket 连接处理器。
 *
 * 连接地址: ws://{host}/ws/workbench?token={jwt}
 *
 * 收到的前端消息格式:
 * {"type":"session.prompt", "payload":{...}}
 * {"type":"permission.reply", "payload":{"requestId":"...", "reply":"once|reject"}}
 * {"type":"session.abort", "payload":{"sessionId":"..."}}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkbenchWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketDispatcher wsDispatcher;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final PermissionPolicyEngine permissionPolicy;
    private final SandboxInstanceRepository sandboxRepo;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            return;
        }
        session.getAttributes().put("userId", userId);
        wsDispatcher.register(userId, session);
        log.info("WS connected: user={}, session={}", userId, session.getId());

        // 通知前端连接就绪
        wsDispatcher.dispatchRaw(userId, Map.of("type", "ws.connected"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        JsonNode msg = objectMapper.readTree(message.getPayload());
        String type  = msg.path("type").asText();
        JsonNode payload = msg.path("payload");

        log.debug("WS message from user {}: type={}", userId, type);

        switch (type) {
            case "permission.reply" -> handlePermissionReply(userId, payload);
            default -> log.warn("Unknown WS message type from user {}: {}", userId, type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            wsDispatcher.unregister(userId);
            log.info("WS disconnected: user={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error: {}", exception.getMessage());
    }

    // ── 消息处理 ──────────────────────────────────────────────────

    private void handlePermissionReply(Long userId, JsonNode payload) {
        String requestId = payload.path("requestId").asText();
        String reply     = payload.path("reply").asText(); // once | reject

        sandboxRepo.findByUserId(userId).ifPresent(sandbox ->
                permissionPolicy.replyFromUser(sandbox, requestId, reply));
    }

    // ── 认证 ──────────────────────────────────────────────────────

    private Long extractUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        Map<String, String> params = parseQuery(uri.getQuery());
        String token = params.get("token");
        if (token == null) return null;

        try {
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            log.warn("Invalid WS token: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) return Map.of();
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(p -> p.length == 2)
                .collect(Collectors.toMap(p -> p[0], p -> p[1]));
    }
}
