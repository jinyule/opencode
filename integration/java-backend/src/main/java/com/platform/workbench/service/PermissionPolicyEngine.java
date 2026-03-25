package com.platform.workbench.service;

import com.platform.workbench.bridge.WebSocketDispatcher;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.opencode.dto.OCEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 权限策略引擎。
 *
 * 当 opencode 触发权限请求（permission.updated 事件）时，
 * 根据策略自动批准/拒绝，或转发给用户确认。
 *
 * 默认策略（沙箱内已隔离，均可安全自动批准）：
 *  - edit, bash, webfetch, mcp → always (自动批准)
 *  - external_directory         → reject  (自动拒绝)
 *  - 其他未知类型               → forward (转发用户确认)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionPolicyEngine {

    private final OpenCodeClient openCodeClient;
    private final WebSocketDispatcher wsDispatcher;

    /** 自动批准的权限类型 */
    private static final Set<String> AUTO_ALLOW = Set.of("edit", "bash", "webfetch", "mcp");

    /** 自动拒绝的权限类型 */
    private static final Set<String> AUTO_DENY = Set.of("external_directory");

    /**
     * 处理 permission.updated 事件。
     */
    public void handle(SandboxInstance sandbox, OCEvent event) {
        String requestId = event.getProperties().path("id").asText();
        String type      = event.getProperties().path("type").asText();
        String title     = event.getProperties().path("title").asText();

        log.debug("Permission request [sandbox={}, id={}, type={}, title={}]",
                sandbox.getId(), requestId, type, title);

        if (AUTO_ALLOW.contains(type)) {
            autoAllow(sandbox, requestId, type);
        } else if (AUTO_DENY.contains(type)) {
            autoDeny(sandbox, requestId, type, title);
        } else {
            forwardToUser(sandbox, requestId, type, title, event);
        }
    }

    /**
     * 由用户在 UI 点击批准/拒绝后调用此方法。
     */
    public void replyFromUser(SandboxInstance sandbox, String requestId, String reply) {
        openCodeClient.replyPermission(sandbox, requestId, reply)
                .doOnSuccess(v -> log.info("Permission {} replied: {} (user)", requestId, reply))
                .subscribe();
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private void autoAllow(SandboxInstance sandbox, String requestId, String type) {
        openCodeClient.replyPermission(sandbox, requestId, "always")
                .doOnSuccess(v -> log.debug("Auto-allowed permission {} (type={})", requestId, type))
                .subscribe();
    }

    private void autoDeny(SandboxInstance sandbox, String requestId, String type, String title) {
        log.warn("Auto-denied permission {} (type={}, title={})", requestId, type, title);
        openCodeClient.replyPermission(sandbox, requestId, "reject")
                .subscribe();
    }

    private void forwardToUser(SandboxInstance sandbox, String requestId,
                                String type, String title, OCEvent event) {
        log.info("Forwarding permission {} to user {} (type={}, title={})",
                requestId, sandbox.getUserId(), type, title);

        // 包装成平台 WS 事件推送给前端
        wsDispatcher.dispatchRaw(sandbox.getUserId(), Map.of(
                "type", "permission.request",
                "properties", Map.of(
                        "requestId", requestId,
                        "sandboxId", sandbox.getId(),
                        "permType", type,
                        "title", title,
                        "metadata", event.getProperties().path("metadata")
                )
        ));
    }
}
