package com.platform.workbench.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_skill", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_skill_name", columnNames = {"user_id", "name"})
})
@Getter
@Setter
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 技能名称，kebab-case，如 text-statistics */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    /** SKILL.md 的 markdown 正文内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** global 或 project */
    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    /** scope=project 时有值 */
    @Column(name = "project_id")
    private Long projectId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
