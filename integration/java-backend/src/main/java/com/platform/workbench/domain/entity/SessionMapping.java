package com.platform.workbench.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "session_mapping", indexes = {
        @Index(name = "idx_session_mapping_sandbox", columnList = "sandbox_id"),
        @Index(name = "idx_session_mapping_oc_id", columnList = "opencode_session_id")
})
@Getter
@Setter
public class SessionMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", nullable = false)
    private SandboxInstance sandbox;

    /** opencode 内部会话 ID */
    @Column(name = "opencode_session_id", nullable = false, length = 100)
    private String opencodeSessionId;

    @Column(name = "title", length = 500)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
