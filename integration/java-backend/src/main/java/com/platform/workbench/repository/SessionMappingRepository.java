package com.platform.workbench.repository;

import com.platform.workbench.domain.entity.SessionMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionMappingRepository extends JpaRepository<SessionMapping, Long> {

    /** 根据沙箱 ID 查找所有会话映射（按创建时间倒序） */
    List<SessionMapping> findBySandboxIdOrderByCreatedAtDesc(Long sandboxId);

    /** 根据 opencode 会话 ID 查找映射 */
    Optional<SessionMapping> findByOpencodeSessionId(String opencodeSessionId);

    /** 根据沙箱 ID + opencode 会话 ID 查找映射 */
    Optional<SessionMapping> findBySandboxIdAndOpencodeSessionId(Long sandboxId, String opencodeSessionId);

    /** 删除沙箱的所有会话映射 */
    void deleteBySandboxId(Long sandboxId);
}
