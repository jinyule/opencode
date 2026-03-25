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

/**
 * 文件操作 API。
 * 对应 OpenAPI: /api/workbench/file/* 和 /api/workbench/search/*
 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class FileController {

    private final SandboxManager sandboxManager;
    private final OpenCodeClient openCodeClient;

    /** GET /api/workbench/file/tree?path=. - 文件树 */
    @GetMapping("/file/tree")
    public Mono<ResponseEntity<JsonNode>> fileTree(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = ".") String path,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.listFiles(sandbox, path).map(ResponseEntity::ok);
    }

    /** GET /api/workbench/file/content?path=src/main.ts - 文件内容 */
    @GetMapping("/file/content")
    public Mono<ResponseEntity<JsonNode>> fileContent(
            @AuthenticationPrincipal Long userId,
            @RequestParam String path,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.getFileContent(sandbox, path).map(ResponseEntity::ok);
    }

    /** GET /api/workbench/file/status - Git 状态 */
    @GetMapping("/file/status")
    public Mono<ResponseEntity<JsonNode>> fileStatus(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.getFileStatus(sandbox).map(ResponseEntity::ok);
    }

    /** GET /api/workbench/search/file?query=Main - 搜索文件名 */
    @GetMapping("/search/file")
    public Mono<ResponseEntity<JsonNode>> searchFile(
            @AuthenticationPrincipal Long userId,
            @RequestParam String query,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.searchFiles(sandbox, query).map(ResponseEntity::ok);
    }

    /** GET /api/workbench/search/code?pattern=TODO - 搜索代码 */
    @GetMapping("/search/code")
    public Mono<ResponseEntity<JsonNode>> searchCode(
            @AuthenticationPrincipal Long userId,
            @RequestParam String pattern,
            @RequestParam(required = false) String glob,
            @RequestParam(required = false) Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.searchCode(sandbox, pattern, glob).map(ResponseEntity::ok);
    }
}
