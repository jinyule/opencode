package com.platform.workbench.controller;

import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.repository.SandboxInstanceRepository;
import com.platform.workbench.service.PermissionPolicyEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 权限回复 API。
 * 对应 OpenAPI: /api/workbench/permission/*
 *
 * 当用户在 UI 点击批准/拒绝权限请求时，前端调用此接口。
 */
@RestController
@RequestMapping("/api/workbench/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionPolicyEngine permissionPolicy;
    private final SandboxInstanceRepository sandboxRepo;

    /** POST /api/workbench/permission/{requestId}/reply */
    @PostMapping("/{requestId}/reply")
    public ResponseEntity<Void> reply(
            @AuthenticationPrincipal Long userId,
            @PathVariable String requestId,
            @RequestBody Map<String, String> body) {
        String reply = body.get("reply"); // once | always | reject
        SandboxInstance sandbox = sandboxRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No active sandbox for user: " + userId));
        permissionPolicy.replyFromUser(sandbox, requestId, reply);
        return ResponseEntity.noContent().build();
    }
}
