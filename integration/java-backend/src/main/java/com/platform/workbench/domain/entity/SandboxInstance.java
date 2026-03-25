package com.platform.workbench.domain.entity;

import com.platform.workbench.domain.enums.SandboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "sandbox_instance")
@Getter
@Setter
public class SandboxInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_id")
    private Long projectId;

    /** Docker 容器 ID */
    @Column(name = "container_id", length = 100)
    private String containerId;

    /** 容器内网地址，如 172.18.0.5:4096 */
    @Column(name = "host", nullable = false, length = 200)
    private String host;

    /** opencode Basic Auth 密码（每沙箱唯一随机生成） */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SandboxStatus status;

    /** workspace 持久卷名称 */
    @Column(name = "workspace_volume", length = 200)
    private String workspaceVolume;

    /** opencode 数据库持久卷名称 */
    @Column(name = "data_volume", length = 200)
    private String dataVolume;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;
}
