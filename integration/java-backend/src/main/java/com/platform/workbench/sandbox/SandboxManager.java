package com.platform.workbench.sandbox;

import com.platform.workbench.bridge.SseEventBridge;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.domain.enums.SandboxStatus;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.repository.SandboxInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 沙箱生命周期管理器。
 *
 * 职责：
 * - 按需创建/恢复 Docker 容器
 * - 健康检查 + 初始化权限配置
 * - 建立并维护 SSE 长连接
 * - 定时检查空闲超时和挂起超时
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxManager {

    private final SandboxInstanceRepository sandboxRepo;
    private final DockerService dockerService;
    private final OpenCodeClient openCodeClient;
    private final SseEventBridge sseEventBridge;

    @Value("${workbench.sandbox.idle-timeout:15}")
    private int idleTimeoutMinutes;

    @Value("${workbench.sandbox.suspend-timeout:60}")
    private int suspendTimeoutMinutes;

    @Value("${workbench.sandbox.expire-days:30}")
    private int expireDays;

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 确保指定用户的沙箱处于 RUNNING 状态，按需创建或恢复。
     * 返回就绪的 SandboxInstance。
     */
    @Transactional
    public SandboxInstance ensureRunning(Long userId, Long projectId) {
        Optional<SandboxInstance> existing = sandboxRepo
                .findByUserIdAndStatusNot(userId, SandboxStatus.DESTROYED);

        if (existing.isPresent()) {
            SandboxInstance sandbox = existing.get();
            return switch (sandbox.getStatus()) {
                case RUNNING, IDLE -> {
                    touch(sandbox);
                    yield sandbox;
                }
                case SUSPENDED -> resumeSandbox(sandbox);
                case STARTING -> waitUntilRunning(sandbox);
                default -> throw new IllegalStateException(
                        "Sandbox " + sandbox.getId() + " is in status " + sandbox.getStatus());
            };
        }

        return createSandbox(userId, projectId);
    }

    /**
     * 创建新沙箱。
     */
    @Transactional
    public SandboxInstance createSandbox(Long userId, Long projectId) {
        log.info("Creating sandbox for user={}", userId);

        String password = UUID.randomUUID().toString();
        String workspaceVolume = "ws-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
        String dataVolume      = "oc-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);

        // 保存记录
        SandboxInstance sandbox = new SandboxInstance();
        sandbox.setUserId(userId);
        sandbox.setProjectId(projectId);
        sandbox.setPassword(password);
        sandbox.setStatus(SandboxStatus.CREATING);
        sandbox.setWorkspaceVolume(workspaceVolume);
        sandbox.setDataVolume(dataVolume);
        sandbox = sandboxRepo.save(sandbox);

        // 异步启动容器（避免阻塞当前事务）
        final SandboxInstance finalSandbox = sandbox;
        new Thread(() -> startContainer(finalSandbox)).start();

        return sandbox;
    }

    // ── 内部操作 ──────────────────────────────────────────────────

    private void startContainer(SandboxInstance sandbox) {
        try {
            sandbox.setStatus(SandboxStatus.STARTING);
            sandboxRepo.save(sandbox);

            // 1. 启动 Docker 容器
            DockerService.ContainerInfo info = dockerService.startSandboxContainer(sandbox);
            sandbox.setContainerId(info.containerId());
            sandbox.setHost(info.host() + ":4096");
            sandboxRepo.save(sandbox);

            // 2. 等待 opencode 就绪
            waitForHealth(sandbox);

            // 3. 初始化权限配置（auto-allow 所有沙箱内安全操作）
            initPermissions(sandbox);

            // 4. 更新状态为 RUNNING
            sandbox.setStatus(SandboxStatus.RUNNING);
            sandbox.setLastActiveAt(Instant.now());
            sandboxRepo.save(sandbox);

            // 5. 建立 SSE 长连接
            sseEventBridge.connect(sandbox);

            log.info("Sandbox {} is ready (host={})", sandbox.getId(), sandbox.getHost());

        } catch (Exception e) {
            log.error("Failed to start sandbox {}: {}", sandbox.getId(), e.getMessage(), e);
            sandbox.setStatus(SandboxStatus.DESTROYED);
            sandboxRepo.save(sandbox);
        }
    }

    private SandboxInstance resumeSandbox(SandboxInstance sandbox) {
        log.info("Resuming sandbox {}", sandbox.getId());
        sandbox.setStatus(SandboxStatus.STARTING);
        sandboxRepo.save(sandbox);

        new Thread(() -> startContainer(sandbox)).start();
        return waitUntilRunning(sandbox);
    }

    private SandboxInstance waitUntilRunning(SandboxInstance sandbox) {
        int maxWait = 60; // 秒
        for (int i = 0; i < maxWait; i++) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            sandbox = sandboxRepo.findById(sandbox.getId()).orElseThrow();
            if (sandbox.getStatus() == SandboxStatus.RUNNING) return sandbox;
        }
        throw new RuntimeException("Sandbox " + sandbox.getId() + " failed to reach RUNNING state");
    }

    private void waitForHealth(SandboxInstance sandbox) {
        int maxSeconds = 30;
        for (int i = 0; i < maxSeconds; i++) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            Boolean healthy = openCodeClient.health(sandbox).block();
            if (Boolean.TRUE.equals(healthy)) {
                log.info("Sandbox {} health check passed (attempt {})", sandbox.getId(), i + 1);
                return;
            }
        }
        throw new RuntimeException("Sandbox " + sandbox.getId() + " health check timeout");
    }

    private void initPermissions(SandboxInstance sandbox) {
        Map<String, Object> config = Map.of(
                "permission", Map.of(
                        "edit", "allow",
                        "bash", "allow",
                        "webfetch", "allow",
                        "mcp", "allow",
                        "external_directory", "deny"
                )
        );
        openCodeClient.patchConfig(sandbox, config).block();
        log.info("Sandbox {} permissions initialized", sandbox.getId());
    }

    private void touch(SandboxInstance sandbox) {
        sandbox.setLastActiveAt(Instant.now());
        sandboxRepo.save(sandbox);
    }

    // ── 定时任务 ──────────────────────────────────────────────────

    /** 每分钟检查空闲超时 */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void checkIdleTimeout() {
        Instant idleThreshold = Instant.now().minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        sandboxRepo.findByStatusAndLastActiveAtBefore(SandboxStatus.RUNNING, idleThreshold)
                .forEach(sandbox -> {
                    log.info("Sandbox {} idle timeout, marking IDLE", sandbox.getId());
                    sandbox.setStatus(SandboxStatus.IDLE);
                    sandboxRepo.save(sandbox);
                });
    }

    /** 每5分钟检查挂起超时 */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void checkSuspendTimeout() {
        Instant suspendThreshold = Instant.now().minus(suspendTimeoutMinutes, ChronoUnit.MINUTES);
        sandboxRepo.findByStatusAndLastActiveAtBefore(SandboxStatus.IDLE, suspendThreshold)
                .forEach(sandbox -> {
                    log.info("Sandbox {} suspend timeout, suspending", sandbox.getId());
                    suspendSandbox(sandbox);
                });
    }

    /** 每天检查过期沙箱 */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkExpired() {
        Instant expireThreshold = Instant.now().minus(expireDays, ChronoUnit.DAYS);
        sandboxRepo.findByStatusAndLastActiveAtBefore(SandboxStatus.SUSPENDED, expireThreshold)
                .forEach(sandbox -> {
                    log.info("Sandbox {} expired, destroying", sandbox.getId());
                    destroySandbox(sandbox);
                });
    }

    @Transactional
    public void suspendSandbox(SandboxInstance sandbox) {
        sandbox.setStatus(SandboxStatus.SUSPENDING);
        sandboxRepo.save(sandbox);
        sseEventBridge.disconnect(sandbox.getId());
        dockerService.stopContainer(sandbox.getContainerId());
        sandbox.setStatus(SandboxStatus.SUSPENDED);
        sandboxRepo.save(sandbox);
    }

    @Transactional
    public void destroySandbox(SandboxInstance sandbox) {
        sandbox.setStatus(SandboxStatus.DESTROYING);
        sandboxRepo.save(sandbox);
        sseEventBridge.disconnect(sandbox.getId());
        dockerService.removeContainer(sandbox.getContainerId());
        dockerService.removeVolume(sandbox.getWorkspaceVolume());
        dockerService.removeVolume(sandbox.getDataVolume());
        sandbox.setStatus(SandboxStatus.DESTROYED);
        sandboxRepo.save(sandbox);
    }
}
