package com.platform.workbench.repository;

import com.platform.workbench.domain.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    /** 列出用户所有技能 */
    List<UserSkill> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 列出用户全局技能 */
    List<UserSkill> findByUserIdAndScope(Long userId, String scope);

    /** 列出用户项目级技能 */
    List<UserSkill> findByUserIdAndProjectId(Long userId, Long projectId);

    /** 根据用户 ID + 技能名查找 */
    Optional<UserSkill> findByUserIdAndName(Long userId, String name);

    /** 检查技能名是否已存在 */
    boolean existsByUserIdAndName(Long userId, String name);

    /** 删除用户的技能 */
    void deleteByUserIdAndName(Long userId, String name);
}
