package com.platform.workbench.repository;

import com.platform.workbench.domain.entity.SandboxInstance;
import com.platform.workbench.domain.enums.SandboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SandboxInstanceRepository extends JpaRepository<SandboxInstance, Long> {

    /** 查找用户的活跃沙箱（非 DESTROYED 状态） */
    Optional<SandboxInstance> findByUserIdAndStatusNot(Long userId, SandboxStatus status);

    /** 查找用户特定项目的活跃沙箱 */
    Optional<SandboxInstance> findByUserIdAndProjectIdAndStatusNot(
            Long userId, Long projectId, SandboxStatus status);

    /** 根据 ID 和用户 ID 查找（防止越权） */
    Optional<SandboxInstance> findByIdAndUserId(Long id, Long userId);

    /** 根据用户 ID 查找最近活跃的沙箱 */
    Optional<SandboxInstance> findTopByUserIdOrderByLastActiveAtDesc(Long userId);

    /** 查找所有指定状态的沙箱 */
    List<SandboxInstance> findAllByStatus(SandboxStatus status);

    /** 查找超过空闲时限的 RUNNING 沙箱 */
    @Query("SELECT s FROM SandboxInstance s WHERE s.status = 'RUNNING' AND s.lastActiveAt < :threshold")
    List<SandboxInstance> findIdleSandboxes(@Param("threshold") LocalDateTime threshold);

    /** 查找超过挂起时限的 IDLE 沙箱 */
    @Query("SELECT s FROM SandboxInstance s WHERE s.status = 'IDLE' AND s.lastActiveAt < :threshold")
    List<SandboxInstance> findSuspendCandidates(@Param("threshold") LocalDateTime threshold);

    /** 查找过期的 SUSPENDED 沙箱（超过保留天数） */
    @Query("SELECT s FROM SandboxInstance s WHERE s.status = 'SUSPENDED' AND s.lastActiveAt < :threshold")
    List<SandboxInstance> findExpiredSandboxes(@Param("threshold") LocalDateTime threshold);

    /** 更新最后活跃时间 */
    @Modifying
    @Query("UPDATE SandboxInstance s SET s.lastActiveAt = :now WHERE s.id = :id")
    void updateLastActiveAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 更新沙箱状态 */
    @Modifying
    @Query("UPDATE SandboxInstance s SET s.status = :status WHERE s.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") SandboxStatus status);
}
