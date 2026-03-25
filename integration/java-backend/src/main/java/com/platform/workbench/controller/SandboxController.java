package com.platform.workbench.controller;

import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.repository.SandboxInstanceRepository;
import com.platform.workbench.sandbox.SandboxManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 沙箱管理 API。
 * 对应 OpenAPI: /api/workbench/sandbox/*
 */
@RestController
@RequestMapping("/api/workbench/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxManager sandboxManager;
    private final SandboxInstanceRepository sandboxRepo;

    /** POST /api/workbench/sandbox - 创建沙箱 */
    @PostMapping
    public ResponseEntity<SandboxInstance> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long projectId = body != null && body.containsKey("projectId")
                ? Long.valueOf(body.get("projectId").toString()) : null;
        SandboxInstance sandbox = sandboxManager.createSandbox(userId, projectId);
        return ResponseEntity.accepted().body(sandbox);
    }

    /** GET /api/workbench/sandbox/{id} - 获取沙箱状态 */
    @GetMapping("/{id}")
    public ResponseEntity<SandboxInstance> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return sandboxRepo.findByIdAndUserId(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/workbench/sandbox/{id}/resume - 恢复挂起的沙箱 */
    @PostMapping("/{id}/resume")
    public ResponseEntity<SandboxInstance> resume(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        SandboxInstance sandbox = sandboxRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Sandbox not found: " + id));
        SandboxInstance running = sandboxManager.ensureRunning(userId, sandbox.getProjectId());
        return ResponseEntity.accepted().body(running);
    }

    /** POST /api/workbench/sandbox/{id}/suspend - 挂起沙箱 */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        SandboxInstance sandbox = sandboxRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Sandbox not found: " + id));
        sandboxManager.suspendSandbox(sandbox);
        return ResponseEntity.accepted().build();
    }

    /** DELETE /api/workbench/sandbox/{id} - 销毁沙箱 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> destroy(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        SandboxInstance sandbox = sandboxRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Sandbox not found: " + id));
        sandboxManager.destroySandbox(sandbox);
        return ResponseEntity.noContent().build();
    }
}
