package com.platform.workbench.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.workbench.opencode.dto.OCEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 分发器。
 *
 * 维护 userId → WebSocketSession 映射，
 * 将 SSE 事件转发到对应用户的 WebSocket 连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDispatcher {

    private final ObjectMapper objectMapper;

    /** userId → WebSocketSession */
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("WebSocket registered for user {}", userId);
    }

    public void unregister(Long userId) {
        sessions.remove(userId);
        log.info("WebSocket unregistered for user {}", userId);
    }

    /**
     * 将 opencode 事件转发给指定用户。
     */
    public void dispatch(Long userId, OCEvent event) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.debug("No active WS session for user {}, dropping event {}", userId, event.getType());
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send WS message to user {}: {}", userId, e.getMessage());
            unregister(userId);
        }
    }

    /**
     * 向指定用户推送自定义事件（平台自有事件，如 sandbox.ready）。
     */
    public void dispatchRaw(Long userId, Map<String, Object> payload) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send WS raw message to user {}: {}", userId, e.getMessage());
        }
    }
}
