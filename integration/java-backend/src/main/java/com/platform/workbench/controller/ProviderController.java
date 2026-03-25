package com.platform.workbench.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.sandbox.SandboxManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * LLM 提供商 API。
 * 对应 OpenAPI: /api/workbench/provider/*
 */
@RestController
@RequestMapping("/api/workbench/provider")
@RequiredArgsConstructor
public class ProviderController {

    private final SandboxManager sandboxManager;
    private final OpenCodeClient openCodeClient;

    /** GET /api/workbench/provider - 列出提供商和模型 */
    @GetMapping
    public Mono<ResponseEntity<JsonNode>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.listProviders(sandbox).map(ResponseEntity::ok);
    }

    /** PUT /api/workbench/provider/{id}/auth - 设置用户自有 API Key */
    @PutMapping("/{providerId}/auth")
    public Mono<ResponseEntity<Void>> setAuth(
            @AuthenticationPrincipal Long userId,
            @PathVariable String providerId,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.setProviderAuth(sandbox, providerId, body)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
