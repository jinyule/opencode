-- ============================================================
-- OpenCode 编程工作台 - 数据库初始化脚本
-- 数据库迁移工具: Flyway
-- ============================================================

-- -----------------------------------------------------------
-- sandbox_instance: 用户沙箱实例
-- -----------------------------------------------------------
CREATE TABLE sandbox_instance (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    project_id        BIGINT,
    container_id      VARCHAR(128),
    host              VARCHAR(255),                          -- 容器内网地址, 如 172.18.0.5:4096
    password          VARCHAR(128) NOT NULL,                 -- opencode Basic Auth 密码（每沙箱唯一）
    status            VARCHAR(32)  NOT NULL DEFAULT 'CREATING',
    workspace_volume  VARCHAR(255),                          -- Docker 持久卷名称（用户代码）
    data_volume       VARCHAR(255),                          -- Docker 持久卷名称（opencode DB）
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_active_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sandbox_user_id       ON sandbox_instance (user_id);
CREATE INDEX idx_sandbox_user_project  ON sandbox_instance (user_id, project_id);
CREATE INDEX idx_sandbox_status        ON sandbox_instance (status);
CREATE INDEX idx_sandbox_last_active   ON sandbox_instance (last_active_at);

COMMENT ON TABLE  sandbox_instance              IS '用户 Docker 沙箱实例';
COMMENT ON COLUMN sandbox_instance.host         IS '容器内网 IP:PORT，如 172.18.0.5:4096';
COMMENT ON COLUMN sandbox_instance.password     IS '每沙箱独立的 opencode Basic Auth 密码';
COMMENT ON COLUMN sandbox_instance.status       IS 'CREATING/STARTING/RUNNING/IDLE/SUSPENDING/SUSPENDED/DESTROYING/DESTROYED';
COMMENT ON COLUMN sandbox_instance.workspace_volume IS '/workspace 持久卷名称';
COMMENT ON COLUMN sandbox_instance.data_volume  IS '~/.local/share/opencode 持久卷名称';

-- -----------------------------------------------------------
-- session_mapping: 平台会话 → opencode 会话映射
-- -----------------------------------------------------------
CREATE TABLE session_mapping (
    id                    BIGSERIAL PRIMARY KEY,
    sandbox_id            BIGINT       NOT NULL REFERENCES sandbox_instance (id) ON DELETE CASCADE,
    opencode_session_id   VARCHAR(128) NOT NULL,             -- opencode 内部会话 ID
    title                 VARCHAR(512),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_sandbox_id          ON session_mapping (sandbox_id);
CREATE UNIQUE INDEX idx_session_oc_id        ON session_mapping (opencode_session_id);

COMMENT ON TABLE  session_mapping                       IS '平台会话到 opencode 内部会话 ID 的映射';
COMMENT ON COLUMN session_mapping.opencode_session_id  IS 'opencode 内部会话 ID（降序生成）';

-- -----------------------------------------------------------
-- user_skill: 用户自定义技能
-- -----------------------------------------------------------
CREATE TABLE user_skill (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,                       -- 技能名称，URL 友好（小写字母+数字+连字符）
    description VARCHAR(512),
    content     TEXT         NOT NULL,                       -- SKILL.md 正文（不含 frontmatter）
    scope       VARCHAR(32)  NOT NULL DEFAULT 'project',     -- global / project
    project_id  BIGINT,                                      -- scope=project 时关联的项目 ID
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_skill_user_name ON user_skill (user_id, name);
CREATE INDEX idx_skill_user_id          ON user_skill (user_id);
CREATE INDEX idx_skill_user_project     ON user_skill (user_id, project_id);

COMMENT ON TABLE  user_skill            IS '用户自定义 opencode 技能（对应 SKILL.md 文件）';
COMMENT ON COLUMN user_skill.name       IS '技能标识符，仅允许 a-z0-9-，全局唯一（同用户下）';
COMMENT ON COLUMN user_skill.content    IS 'SKILL.md 正文 Markdown，frontmatter 由系统自动生成';
COMMENT ON COLUMN user_skill.scope      IS 'global=全局可用；project=仅当前项目可用';
