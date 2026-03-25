package com.platform.workbench.controller;

import com.platform.workbench.domain.entity.UserSkill;
import com.platform.workbench.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理 API。
 * 对应 OpenAPI: /api/workbench/skill/*
 */
@RestController
@RequestMapping("/api/workbench/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /** GET /api/workbench/skill - 列出技能 */
    @GetMapping
    public ResponseEntity<List<UserSkill>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(skillService.listSkills(userId));
    }

    /** POST /api/workbench/skill - 创建技能 */
    @PostMapping
    public ResponseEntity<UserSkill> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<String, Object> body) {
        String name        = (String) body.get("name");
        String description = (String) body.get("description");
        String content     = (String) body.get("content");
        String scope       = (String) body.getOrDefault("scope", "project");
        Long projectId     = body.containsKey("projectId")
                ? Long.valueOf(body.get("projectId").toString()) : null;

        UserSkill skill = skillService.createSkill(userId, projectId, name, description, content, scope);
        return ResponseEntity.ok(skill);
    }

    /** PUT /api/workbench/skill/{name} - 更新技能 */
    @PutMapping("/{name}")
    public ResponseEntity<UserSkill> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        UserSkill skill = skillService.updateSkill(
                userId, name, body.get("description"), body.get("content"));
        return ResponseEntity.ok(skill);
    }

    /** DELETE /api/workbench/skill/{name} - 删除技能 */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String name) {
        skillService.deleteSkill(userId, name);
        return ResponseEntity.noContent().build();
    }
}
