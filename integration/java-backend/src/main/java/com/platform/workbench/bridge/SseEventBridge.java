package com.platform.workbench.bridge;

import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.opencode.dto.OCEvent;
import com.platform.workbench.service.PermissionPolicyEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 事件桥接器。
 *
 * 职责：
 * 1. 为每个活跃沙箱维护一个持久 SSE 连接（到 opencode /event）
 * 2. 收到事件后：
 *    - 权限类事件 → PermissionPolicyEngine 自动处理
 *    - 其他事件   → WebSocketDispatcher 推送到对应用户
 * 3. 心跳超时自动重连
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventBridge {

    private final OpenCodeClient openCodeClient;
    private final WebSocketDispatcher wsDispatcher;
    private final PermissionPolicyEngine permissionPolicy;

    @Value("${workbench.opencode.sse-heartbeat-timeout:15}")
    private int heartbeatTimeoutSeconds;

    /** sandboxId → SSE 订阅句柄 */
    private final Map<Long, Disposable> subscriptions = new ConcurrentHashMap<>();

    /**
     * 连接到指定沙箱的 SSE 事件流。
     * 已存在的连接先断开再重连。
     */
    public void connect(SandboxInstance sandbox) {
        disconnect(sandbox.getId());

        Disposable subscription = buildEventStream(sandbox)
                .subscribe(
                        event -> handleEvent(sandbox, event),
                        err  -> onError(sandbox, err),
                        ()   -> onComplete(sandbox)
                );

        subscriptions.put(sandbox.getId(), subscription);
        log.info("SSE bridge connected for sandbox {}", sandbox.getId());
    }

    /** 断开指定沙箱的 SSE 连接 */
    public void disconnect(Long sandboxId) {
        Disposable existing = subscriptions.remove(sandboxId);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
            log.info("SSE bridge disconnected for sandbox {}", sandboxId);
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private Flux<OCEvent> buildEventStream(SandboxInstance sandbox) {
        return openCodeClient.subscribeEvents(sandbox)
                // 心跳超时检测：超过 N 秒无任何事件则触发重连
                .timeout(Duration.ofSeconds(heartbeatTimeoutSeconds + 5))
                // 忽略心跳事件（不需要转发到前端）
                .filter(e -> !OCEvent.SERVER_HEARTBEAT.equals(e.getType()))
                // 错误时延迟 2 秒重试（无限次）
                .retryWhen(reactor.util.retry.Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .doBeforeRetry(sig -> log.warn("SSE retry for sandbox {} (attempt {})",
                                sandbox.getId(), sig.totalRetries() + 1)));
    }

    private void handleEvent(SandboxInstance sandbox, OCEvent event) {
        // 权限请求：交由策略引擎处理，无需转发给前端
        if (OCEvent.PERMISSION_UPDATED.equals(event.getType())) {
            permissionPolicy.handle(sandbox, event);
            return;
        }

        // server.instance.disposed：断开连接
        if (OCEvent.SERVER_INSTANCE_DISPOSED.equals(event.getType())) {
            disconnect(sandbox.getId());
            return;
        }

        // 其他事件：路由到对应用户的 WebSocket
        wsDispatcher.dispatch(sandbox.getUserId(), event);
    }

    private void onError(SandboxInstance sandbox, Throwable err) {
        log.error("SSE stream error for sandbox {}: {}", sandbox.getId(), err.getMessage());
    }

    private void onComplete(SandboxInstance sandbox) {
        log.info("SSE stream completed for sandbox {}", sandbox.getId());
        subscriptions.remove(sandbox.getId());
    }
}
