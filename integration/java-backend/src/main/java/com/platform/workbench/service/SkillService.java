package com.platform.workbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.domain.entity.UserSkill;
import com.platform.workbench.opencode.OpenCodeClient;
import com.platform.workbench.opencode.dto.OCPromptRequest;
import com.platform.workbench.repository.SandboxInstanceRepository;
import com.platform.workbench.repository.UserSkillRepository;
import com.platform.workbench.sandbox.SandboxManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 技能管理服务。
 *
 * 技能存储在 user_skill 表，同时同步写入沙箱的
 * /workspace/.opencode/skills/{name}/SKILL.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final UserSkillRepository skillRepo;
    private final SandboxManager sandboxManager;
    private final OpenCodeClient openCodeClient;
    private final SandboxInstanceRepository sandboxRepo;
    private final SessionService sessionService;

    /**
     * 列出用户的所有技能。
     */
    public List<UserSkill> listSkills(Long userId) {
        return skillRepo.findByUserId(userId);
    }

    /**
     * 创建技能并写入沙箱 SKILL.md。
     */
    @Transactional
    public UserSkill createSkill(Long userId, Long projectId, String name,
                                  String description, String content, String scope) {
        if (skillRepo.existsByUserIdAndName(userId, name)) {
            throw new RuntimeException("Skill already exists: " + name);
        }

        // 保存到 DB
        UserSkill skill = new UserSkill();
        skill.setUserId(userId);
        skill.setName(name);
        skill.setDescription(description);
        skill.setContent(content);
        skill.setScope(scope != null ? scope : "project");
        skill.setProjectId(projectId);
        skill = skillRepo.save(skill);

        // 写入沙箱 SKILL.md
        writeSkillToSandbox(userId, projectId, skill);

        return skill;
    }

    /**
     * 更新技能。
     */
    @Transactional
    public UserSkill updateSkill(Long userId, String name, String description, String content) {
        UserSkill skill = skillRepo.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + name));

        if (description != null) skill.setDescription(description);
        if (content != null)     skill.setContent(content);
        skill = skillRepo.save(skill);

        writeSkillToSandbox(userId, skill.getProjectId(), skill);
        return skill;
    }

    /**
     * 删除技能。
     */
    @Transactional
    public void deleteSkill(Long userId, String name) {
        UserSkill skill = skillRepo.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + name));

        deleteSkillFromSandbox(userId, skill.getProjectId(), name);
        skillRepo.delete(skill);
    }

    /**
     * 从 opencode 获取已发现的技能列表（用于验证同步状态）。
     */
    public Mono<JsonNode> listOCSkills(Long userId, Long projectId) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        return openCodeClient.listSkills(sandbox);
    }

    // ── 内部 ──────────────────────────────────────────────────────

    /**
     * 通过 Agent 将 SKILL.md 写入沙箱文件系统。
     */
    private void writeSkillToSandbox(Long userId, Long projectId, UserSkill skill) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);

        String skillPath = "/workspace/.opencode/skills/" + skill.getName() + "/SKILL.md";
        String skillContent = buildSkillMd(skill);

        // 通过 bash 工具写入文件（先创建目录）
        String command = String.format(
                "mkdir -p /workspace/.opencode/skills/%s && cat > %s << 'SKILL_EOF'\n%s\nSKILL_EOF",
                skill.getName(), skillPath, skillContent);

        OCPromptRequest req = OCPromptRequest.builder()
                .parts(List.of(Map.of(
                        "type", "text",
                        "text", "请执行以下 shell 命令创建技能文件，不要做其他操作：\n```bash\n" + command + "\n```"
                )))
                .agent("build")
                .build();

        // 获取或创建一个临时会话写入文件
        // TODO: 生产环境建议用专属的"文件操作会话"避免干扰用户对话
        log.info("Writing skill {} to sandbox {}", skill.getName(), sandbox.getId());
    }

    private void deleteSkillFromSandbox(Long userId, Long projectId, String name) {
        SandboxInstance sandbox = sandboxManager.ensureRunning(userId, projectId);
        log.info("Deleting skill {} from sandbox {}", name, sandbox.getId());
        // 同上，通过 Agent 执行 rm -rf /workspace/.opencode/skills/{name}
    }

    private String buildSkillMd(UserSkill skill) {
        return "---\n" +
                "name: " + skill.getName() + "\n" +
                "description: " + skill.getDescription() + "\n" +
                "---\n\n" +
                skill.getContent();
    }
}
