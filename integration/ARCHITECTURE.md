# OpenCode 集成架构文档

> 将 opencode 作为编程 Agent 后端接入 Agent 开发平台（参考 Coze 架构）

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [系统总体架构](#2-系统总体架构)
3. [沙箱隔离方案](#3-沙箱隔离方案)
4. [核心交互时序](#4-核心交互时序)
5. [OpenCode Agent 内部流程](#5-opencode-agent-内部流程)
6. [数据模型设计](#6-数据模型设计)
7. [SSE 事件流详解](#7-sse-事件流详解)
8. [前端步骤日志渲染](#8-前端步骤日志渲染)
9. [Java 后端网关设计](#9-java-后端网关设计)
10. [权限策略引擎](#10-权限策略引擎)
11. [Skill Creator 集成](#11-skill-creator-集成)
12. [多 LLM 提供商支持](#12-多-llm-提供商支持)
13. [安全架构](#13-安全架构)
14. [关键源文件参考](#14-关键源文件参考)
15. [实施路线图](#15-实施路线图)

---

## 1. 背景与目标

### 平台现状

- Agent 开发平台参考 **Coze** 构建，提供 Agent 编排、技能管理、工作流等能力
- 技术栈：**Java 后端**（Spring Boot）+ **Vue/React 前端**
- 需要引入一个**独立编程工作台**：用户可以在其中与 AI 编程助手交互，生成代码、执行命令、管理文件

### 为什么选择 opencode

opencode 是完全开源、不绑定特定 AI 提供商的 AI 编程助手，提供：

- 完整的无头 API 服务器（`opencode serve`，端口 4096）
- 基于 Hono 框架的 REST API + SSE 实时事件流
- 完整的 Agent 循环：用户输入 → LLM 推理 → 工具调用 → 结果 → 循环
- 内置工具集：文件读写、Shell 执行、代码搜索、网页获取等
- 多 LLM 提供商支持：Anthropic、OpenAI、Google、Azure 等 18+ 提供商
- SKILL.md 技能系统：可扩展的自定义 AI 技能

### 集成目标

| 需求 | opencode 提供的能力 |
|------|-------------------|
| 代码生成与编辑 | Agent 循环 + edit/write 工具 |
| 工具/命令执行 | bash/glob/grep/fetch 等工具 |
| 多轮对话管理 | Session 会话系统 + SQLite 持久化 |
| 多 LLM 提供商 | Provider 抽象层，18+ 提供商 |
| Skill Creator | SKILL.md 技能文件系统 |
| 沙箱文件隔离 | Docker 容器 + 独立 /workspace 目录 |
| 实时步骤日志 | SSE 事件流（message.part.updated 等） |

---

## 2. 系统总体架构

### 2.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            用户浏览器 (Vue/React)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────┐  ┌────────┐  ┌──────────┐ │
│  │  对话面板     │  │  步骤日志    │  │  文件树   │  │ 技能编辑│  │ 模型选择  │ │
│  └──────┬──────┘  └──────┬──────┘  └────┬─────┘  └───┬────┘  └────┬─────┘ │
└─────────┼────────────────┼──────────────┼────────────┼───────────┼────────┘
          │   WebSocket (双向)             │  HTTP REST │           │
          ▼                               ▼            ▼           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Java 后端 (Spring Boot)                               │
│                                                                             │
│  ┌───────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ 用户认证    │  │ 沙箱管理器    │  │ SSE→WS 桥接  │  │ OpenCode API 客户端│  │
│  │ (JWT/SSO) │  │ (生命周期)    │  │ (事件分发)    │  │ (Spring WebClient)│  │
│  └───────────┘  └──────┬───────┘  └──────┬───────┘  └────────┬──────────┘  │
│                        │                 │                    │             │
│  ┌─────────────────────┴─────────────────┴────────────────────┘             │
│  │ 会话/沙箱映射层 (DB: sandbox_instance + session_mapping)                   │
│  └─────────────────────┬─────────────────────────────────────              │
└────────────────────────┼────────────────────────────────────────────────────┘
                         │  HTTP REST + SSE (per sandbox)
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Docker 沙箱集群                                       │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │ Sandbox-UserA   │  │ Sandbox-UserB   │  │ Sandbox-UserC   │   ...      │
│  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │            │
│  │ │opencode     │ │  │ │opencode     │ │  │ │opencode     │ │            │
│  │ │serve:4096   │ │  │ │serve:4096   │ │  │ │serve:4096   │ │            │
│  │ ├─────────────┤ │  │ ├─────────────┤ │  │ ├─────────────┤ │            │
│  │ │/workspace   │ │  │ │/workspace   │ │  │ │/workspace   │ │            │
│  │ │(持久卷)      │ │  │ │(持久卷)      │ │  │ │(持久卷)      │ │            │
│  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │            │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 组件关系图

```mermaid
graph TB
    subgraph Frontend["前端 (Vue/React)"]
        ChatPanel[对话面板]
        StepLog[步骤日志]
        FileTree[文件树浏览器]
        SkillEditor[技能编辑器]
        ModelPicker[模型选择器]
    end

    subgraph JavaBackend["Java 后端 (Spring Boot)"]
        AuthService[认证服务]
        SandboxManager[沙箱管理器]
        SSEBridge[SSE→WS 桥接器]
        OCClient[OpenCode API Client]
        SessionMapper[会话映射层]
        PermissionPolicy[权限策略引擎]
        SkillService[技能管理服务]
    end

    subgraph SandboxCluster["Docker 沙箱集群"]
        SandboxA["沙箱A (opencode serve)"]
        SandboxB["沙箱B (opencode serve)"]
        SandboxN["沙箱N ..."]
    end

    subgraph LLMProviders["LLM 提供商"]
        Anthropic[Anthropic]
        OpenAI[OpenAI]
        Others[其他 16+ 提供商]
    end

    Frontend -- "WebSocket" --> JavaBackend
    Frontend -- "HTTP REST" --> JavaBackend
    JavaBackend -- "HTTP REST + SSE" --> SandboxCluster
    SandboxCluster -- "LLM API Call" --> LLMProviders

    AuthService --> SessionMapper
    SandboxManager --> SandboxCluster
    SSEBridge --> OCClient
    OCClient --> SessionMapper
    PermissionPolicy --> OCClient
    SkillService --> OCClient
```

### 2.3 核心设计原则

| 原则 | 说明 |
|------|------|
| **微服务解耦** | opencode 作为独立服务，Java 后端通过 HTTP API 调用 |
| **异步 prompt** | 使用 `prompt_async` 避免长连接超时，通过 SSE 获取进度 |
| **网关代理** | Java 后端作为所有请求的代理，opencode 不直接对外暴露 |
| **容器隔离** | 每个用户/任务运行在独立 Docker 容器，文件系统天然隔离 |
| **事件驱动** | 所有实时状态变更通过 SSE→WebSocket 事件流推送 |

---

## 3. 沙箱隔离方案

### 3.1 沙箱生命周期状态图

```mermaid
stateDiagram-v2
    [*] --> Creating : 用户打开编程工作台

    Creating --> Starting : Docker 容器创建完成
    Starting --> Running : opencode serve 就绪\n(GET /global/health 返回 200)

    Running --> Running : 用户交互 (更新 last_active_at)
    Running --> Idle : 无活动超过 idle_timeout (默认15min)
    Idle --> Running : 用户重新发送消息

    Idle --> Suspending : 无活动超过 suspend_timeout (默认60min)
    Suspending --> Suspended : 快照保存完成, 容器停止

    Suspended --> Starting : 用户重新打开 (从快照恢复)

    Running --> Destroying : 用户删除项目
    Idle --> Destroying : 管理员清理
    Suspended --> Destroying : 过期清理 (默认30天)

    Destroying --> [*] : 容器 + 持久卷删除

    note right of Running
        监听 0.0.0.0:4096
        Basic Auth 保护
        SSE 长连接活跃
    end note

    note right of Suspended
        /workspace 持久卷保留
        SQLite DB 持久卷保留
        容器已停止，不占计算资源
    end note
```

### 3.2 容器内部架构

```
┌─────────────────── Docker Container ──────────────────────┐
│                                                           │
│  环境变量 (启动时注入):                                     │
│    OPENCODE_SERVER_PASSWORD = <每沙箱唯一随机密码>           │
│    ANTHROPIC_API_KEY        = <平台统一管理>                │
│    OPENAI_API_KEY           = <平台统一管理>                │
│    GOOGLE_GENERATIVE_AI_API_KEY = <平台统一管理>            │
│                                                           │
│  ┌───────────────────────────────────────────────────┐    │
│  │  opencode serve --port 4096 --hostname 0.0.0.0    │    │
│  │                                                   │    │
│  │  ┌────────────┐  ┌──────────┐  ┌──────────────┐  │    │
│  │  │  Hono API  │  │ SSE 事件  │  │  Agent 循环   │  │    │
│  │  │  REST 路由  │  │  Stream  │  │ LLM + Tools  │  │    │
│  │  └────────────┘  └──────────┘  └──────────────┘  │    │
│  │                                                   │    │
│  │  内置工具: bash, edit, write, read,               │    │
│  │           glob, grep, fetch, task, ...           │    │
│  │                                                   │    │
│  │  技能系统: /workspace/.opencode/skills/           │    │
│  │  权限配置: edit=allow, bash=allow (沙箱内安全)     │    │
│  └───────────────────────────────────────────────────┘    │
│                                                           │
│  持久卷挂载:                                               │
│    /workspace          → pvc-user-{id}-workspace          │
│    ~/.local/share/opencode → pvc-user-{id}-data           │
│                                                           │
│  资源限制:                                                 │
│    CPU: 2 cores    Memory: 4GB    Disk: 10GB              │
│    Network: 仅允许访问 LLM API 域名 + 内部网络             │
│                                                           │
│  对外暴露: 仅 TCP 4096 → Java 后端内网                      │
└───────────────────────────────────────────────────────────┘
```

### 3.3 数据持久化策略

| 数据 | 持久卷 | 恢复方式 |
|------|--------|---------|
| 用户代码文件 | `/workspace` 挂载独立 PVC | 容器重启自动恢复 |
| 会话历史 (SQLite) | `~/.local/share/opencode` 挂载独立 PVC | 容器重启自动恢复 |
| 技能文件 | `/workspace/.opencode/skills/` 随代码一起持久化 | 随 workspace PVC 恢复 |

---

## 4. 核心交互时序

### 4.1 用户发送消息完整流程

```mermaid
sequenceDiagram
    actor User as 用户浏览器
    participant Java as Java 后端
    participant OC as OpenCode 沙箱

    User->>Java: [WS] 发送消息 {text, files, model}
    Java->>Java: JWT 认证 + 查找沙箱实例

    alt 沙箱未运行
        Java->>Java: 启动/恢复沙箱容器
        loop 健康检查 (最多30s)
            Java->>OC: GET /global/health
            OC-->>Java: 200 {version}
        end
    end

    Java->>Java: 查找或创建 session_mapping
    Java->>OC: POST /session/{id}/prompt_async
    Note right of OC: Body: {parts:[{type:"text",text:"..."}],\nmodel:{providerID,modelID}, agent:"build"}
    OC-->>Java: 204 No Content (立即返回)

    Note over OC: Agent 循环开始执行...

    loop SSE 事件流 (已建立的持久连接)
        OC-->>Java: data: {type:"message.part.updated",\nproperties:{part:{type:"text"}, delta:"..."}}
        Java-->>User: [WS] 推送文本增量

        OC-->>Java: data: {type:"message.part.updated",\nproperties:{part:{type:"tool",\nstate:{status:"running",title:"Reading main.ts"}}}}
        Java-->>User: [WS] 推送步骤状态 (running)

        OC-->>Java: data: {type:"message.part.updated",\nproperties:{part:{type:"tool",\nstate:{status:"completed",title:"Read main.ts"}}}}
        Java-->>User: [WS] 推送步骤完成 (completed)
    end

    OC-->>Java: data: {type:"session.status", properties:{status:{type:"idle"}}}
    Java-->>User: [WS] 推送完成状态
```

### 4.2 沙箱创建与 SSE 连接建立

```mermaid
sequenceDiagram
    actor User as 用户浏览器
    participant Java as Java 后端
    participant Docker as Docker Engine
    participant OC as OpenCode 容器

    User->>Java: 打开编程工作台
    Java->>Java: 查找用户的沙箱实例记录

    alt sandbox_instance 不存在
        Java->>Java: 生成唯一 sandbox_password (UUID)
        Java->>Docker: docker run opencode-sandbox:latest\n  -e OPENCODE_SERVER_PASSWORD=...\n  -e ANTHROPIC_API_KEY=...\n  -v pvc-workspace:/workspace\n  -v pvc-data:~/.local/share/opencode
        Docker-->>Java: container_id, internal_ip
        Java->>Java: INSERT sandbox_instance (host=ip:4096, ...)

        loop 健康检查 (最多30s, 每1s)
            Java->>OC: GET http://{ip}:4096/global/health\n  Authorization: Basic ...
            OC-->>Java: 200 {version:"x.x.x"}
        end

        Java->>OC: PATCH /config\n  {permission:{edit:"allow",bash:"allow",webfetch:"allow",\n              external_directory:"deny"}}
        OC-->>Java: 200 OK
    end

    Java->>OC: GET /event\n  Accept: text/event-stream\n  Authorization: Basic ...\n  x-opencode-directory: /workspace
    OC-->>Java: data: {"type":"server.connected","properties":{}}
    Note over Java,OC: SSE 持久连接建立 (心跳: 10s)

    Java-->>User: [WS] workbench.ready
    Java->>OC: GET /session/?limit=20
    OC-->>Java: [{id,title,time,...}, ...]
    Java-->>User: [WS] 推送历史会话列表
```

### 4.3 权限请求处理

```mermaid
sequenceDiagram
    actor User as 用户浏览器
    participant Java as Java 后端
    participant OC as OpenCode 沙箱

    Note over OC: Agent 触发权限检查

    OC-->>Java: data: {type:"permission.updated",\nproperties:{id:"perm_xxx", type:"bash",\ntitle:"Execute: rm -rf ./dist", metadata:{}}}

    Java->>Java: 权限策略引擎判断

    alt 自动批准 (沙箱内安全操作: edit/bash/webfetch)
        Java->>OC: POST /permission/perm_xxx/reply\n  {reply: "always"}
        Note over Java: 无需打扰用户
    else 需用户确认 (如: 高危操作)
        Java-->>User: [WS] permission.request\n  {id, title, metadata, actions:[approve,reject]}
        User->>Java: [WS] permission.reply {id, action:"approve"}
        Java->>OC: POST /permission/perm_xxx/reply\n  {reply: "once"}
    else 自动拒绝 (如: 访问外部目录)
        Java->>OC: POST /permission/perm_xxx/reply\n  {reply: "reject"}
        Java-->>User: [WS] 通知权限已拒绝
    end

    OC-->>Java: data: {type:"permission.replied", ...}
    Note over OC: Agent 循环继续/中止
```

---

## 5. OpenCode Agent 内部流程

### 5.1 Agent 执行主循环

```mermaid
flowchart TD
    Start([用户消息到达]) --> CreateMsg[创建 UserMessage\n保存 Parts 到 SQLite]
    CreateMsg --> Loop{进入 Agent Loop\nwhile true}

    Loop --> LoadMsgs[加载消息历史\n过滤已压缩的消息]
    LoadMsgs --> CheckExit{上一个 Assistant 消息\n有终止原因?}

    CheckExit -- "是 + 无待处理工作" --> Prune[SessionCompaction.prune\n修剪旧工具输出]
    Prune --> Exit([退出循环\n返回最终 Assistant 消息])

    CheckExit -- "否" --> CheckSubtask{有待处理子任务?\nSubtaskPart}

    CheckSubtask -- "是" --> ExecTask[TaskTool 执行\n创建子会话, 递归 prompt\n子会话完成后继续]
    ExecTask --> Loop

    CheckSubtask -- "否" --> CheckCompact{存在 CompactionPart\n或上下文溢出?}
    CheckCompact -- "是" --> Compact[SessionCompaction.process\ncompaction agent 摘要压缩]
    Compact --> Loop

    CheckCompact -- "否" --> BuildCtx[构建调用上下文\n系统提示 + 工具列表\n消息格式转换]
    BuildCtx --> CallLLM[LLM.stream\nProvider.getLanguage\n→ Vercel AI SDK streamText]

    CallLLM --> StreamLoop{处理流式事件}
    StreamLoop --> |text-delta| UpdateText[更新 TextPart\n发布 Bus 事件 → SSE delta]
    StreamLoop --> |reasoning-delta| UpdateReasoning[更新 ReasoningPart\n → SSE]
    StreamLoop --> |tool-call| ExecTool[执行工具\npending→running→completed\n发布 Bus 事件 → SSE]
    StreamLoop --> |tool-error| HandleToolErr[更新 ToolPart 为 error\n检查是否 Permission.RejectedError]
    StreamLoop --> |finish-step| StepDone[计算 token/cost\n文件系统快照\n检查上下文溢出]
    StreamLoop --> |error| HandleErr{可重试?}

    UpdateText --> StreamLoop
    UpdateReasoning --> StreamLoop
    ExecTool --> StreamLoop
    HandleToolErr --> StreamLoop
    StepDone --> StreamLoop
    HandleErr -- "是" --> Retry[指数退避重试\nSessionRetry]
    Retry --> CallLLM
    HandleErr -- "否" --> Exit

    StepDone --> |finish reason| CheckFinish{完成原因}
    CheckFinish -- "stop" --> Exit
    CheckFinish -- "continue\n(有工具结果)" --> Loop
    CheckFinish -- "compact\n(上下文溢出)" --> Compact

    style Start fill:#4CAF50,color:#fff
    style Exit fill:#2196F3,color:#fff
    style CallLLM fill:#FF9800,color:#fff
    style ExecTool fill:#9C27B0,color:#fff
```

### 5.2 工具执行状态机

```mermaid
stateDiagram-v2
    [*] --> Pending : LLM 发起 tool-input-start 事件

    Pending --> Running : tool-call 事件\n(参数解析完成, 开始执行)
    Running --> Completed : tool-result 事件\n(工具执行成功返回)
    Running --> Error : tool-error 事件\n(执行异常)
    Running --> Blocked : Permission.RejectedError\n(用户拒绝授权)

    Error --> [*] : Agent 循环收到 error 继续
    Completed --> [*] : Agent 循环收到工具结果继续
    Blocked --> [*] : Agent 循环终止当前迭代

    note right of Pending
        SSE event: message.part.updated
        part.state.status = "pending"
    end note

    note right of Running
        SSE event: message.part.updated
        part.state.status = "running"
        part.state.title = "Running: npm install"
        part.state.input = {command: "npm install"}
    end note

    note right of Completed
        SSE event: message.part.updated
        part.state.status = "completed"
        part.state.title = "Run npm install"
        part.state.output = "added 123 packages..."
        part.state.time.end = "..."
    end note
```

### 5.3 内置工具集

| 工具 ID | 功能 | 沙箱内权限 |
|---------|------|----------|
| `bash` | 执行 shell 命令 | auto-allow |
| `edit` | 编辑文件（精确替换） | auto-allow |
| `write` | 创建/覆写文件 | auto-allow |
| `read` | 读取文件内容 | auto-allow |
| `glob` | 按模式搜索文件 | auto-allow |
| `grep` | 搜索文件内容 | auto-allow |
| `fetch` | 获取网页内容 | auto-allow |
| `task` | 创建子 Agent 任务 | auto-allow |
| `webSearch` | 搜索引擎查询 | auto-allow |
| `codeSearch` | 代码语义搜索 | auto-allow |
| `todoWrite` | 更新 Todo 列表 | auto-allow |
| `applyPatch` | 应用 unified diff patch | auto-allow |

---

## 6. 数据模型设计

### 6.1 平台侧数据库 ER 图

```mermaid
erDiagram
    USER ||--o{ SANDBOX_INSTANCE : "拥有"
    USER ||--o{ PLATFORM_PROJECT : "创建"
    PLATFORM_PROJECT ||--o{ SANDBOX_INSTANCE : "关联"
    SANDBOX_INSTANCE ||--o{ SESSION_MAPPING : "包含"
    USER ||--o{ USER_SKILL : "创建"
    USER_SKILL }o--o| PLATFORM_PROJECT : "归属(可选)"

    USER {
        bigint id PK
        varchar username
        varchar email
        varchar auth_token
        timestamp created_at
    }

    PLATFORM_PROJECT {
        bigint id PK
        bigint user_id FK
        varchar name
        varchar description
        varchar template
        timestamp created_at
    }

    SANDBOX_INSTANCE {
        bigint id PK
        bigint user_id FK
        bigint project_id FK
        varchar container_id "Docker 容器 ID"
        varchar host "容器内网地址 如 10.0.1.5:4096"
        varchar password "opencode Basic Auth 密码"
        varchar status "creating/starting/running/idle/suspended/destroying"
        varchar workspace_volume "workspace 持久卷名"
        varchar data_volume "opencode DB 持久卷名"
        timestamp created_at
        timestamp last_active_at
    }

    SESSION_MAPPING {
        bigint id PK
        bigint sandbox_id FK
        varchar opencode_session_id "opencode 内部会话 ID"
        varchar title "会话标题"
        timestamp created_at
    }

    USER_SKILL {
        bigint id PK
        bigint user_id FK
        varchar name "技能名称 (唯一, kebab-case)"
        varchar description "技能描述"
        text content "SKILL.md 正文内容"
        varchar scope "global 或 project"
        bigint project_id FK "scope=project 时有值"
        timestamp created_at
        timestamp updated_at
    }
```

### 6.2 OpenCode 内部数据模型（只读参考）

```mermaid
erDiagram
    SESSION ||--o{ MESSAGE : "包含"
    MESSAGE ||--o{ PART : "包含"
    SESSION ||--o| SESSION : "parentID (子会话)"

    SESSION {
        string id PK "降序生成，新会话排前"
        string slug "URL 友好短标识"
        string projectID
        string directory "/workspace"
        string title
        int version
        json summary "additions/deletions/files/diffs"
        string parentID FK "子 Agent 会话的父会话"
        json share "分享 URL"
        json revert "回滚信息"
        json time "created/updated/compacting/archived"
    }

    MESSAGE {
        string id PK
        string sessionID FK
        string role "user 或 assistant"
        string parentID "assistant 消息指向触发它的 user 消息"
        string model "providerID:modelID"
        string agent "agent 名称"
        json system "自定义系统提示"
        json format "输出格式"
    }

    PART {
        string id PK
        string messageID FK
        string type "text / reasoning / tool / file / step-start / step-finish / snapshot / patch / agent / retry / compaction / subtask"
        json state "不同 type 有不同 state 结构"
        json time "created/updated"
    }
```

---

## 7. SSE 事件流详解

### 7.1 连接与格式

```
连接: GET http://{sandbox_host}/event
认证: Authorization: Basic base64(opencode:{password})
目录: x-opencode-directory: /workspace

SSE 帧格式:
  data: {"type":"<event_type>","properties":{...}}\n\n

心跳 (每10秒，防止代理超时):
  data: {"type":"server.heartbeat","properties":{}}\n\n

连接建立后立即收到:
  data: {"type":"server.connected","properties":{}}\n\n
```

### 7.2 事件分类树

```
server.*
├── server.connected          连接建立
├── server.heartbeat          心跳 (10s/次)
└── server.instance.disposed  实例销毁

session.*
├── session.created           新会话创建
├── session.updated           会话元数据更新 (标题/摘要等)
├── session.deleted           会话删除
├── session.status            状态变更 {type: "busy"|"idle"|"retry"}
├── session.error             会话错误 {error}
├── session.compacted         上下文压缩完成
└── session.diff              文件变更 (patch 列表)

message.*
├── message.updated           消息创建或更新
├── message.removed           消息删除
├── message.part.updated      Part 更新 (最频繁，含 delta)
└── message.part.removed      Part 删除

permission.*
├── permission.updated        权限请求产生 (等待回复)
└── permission.replied        权限请求已回复

todo.*
└── todo.updated              Todo 列表更新

file.*
├── file.edited               文件被编辑
└── file.watcher.updated      文件监视器变更通知
```

### 7.3 关键 Payload 示例

**文本流式输出 (message.part.updated)**
```json
{
  "type": "message.part.updated",
  "properties": {
    "sessionID": "sess_01jx...",
    "messageID": "msg_01jx...",
    "part": {
      "id": "part_01jx...",
      "type": "text",
      "state": { "text": "完整累积文本..." },
      "time": { "created": "2026-03-24T10:00:00Z" }
    },
    "delta": "增量追加的字符"
  }
}
```

**工具执行 running (message.part.updated)**
```json
{
  "type": "message.part.updated",
  "properties": {
    "sessionID": "sess_01jx...",
    "messageID": "msg_01jx...",
    "part": {
      "id": "part_01jx...",
      "type": "tool",
      "state": {
        "status": "running",
        "tool": "bash",
        "title": "Running: npm test",
        "input": { "command": "npm test" },
        "time": { "start": "2026-03-24T10:00:01Z" }
      }
    }
  }
}
```

**工具执行 completed (message.part.updated)**
```json
{
  "type": "message.part.updated",
  "properties": {
    "part": {
      "type": "tool",
      "state": {
        "status": "completed",
        "tool": "bash",
        "title": "Run npm test",
        "output": "✓ 42 tests passed",
        "time": { "start": "...", "end": "..." }
      }
    }
  }
}
```

**会话状态变更 (session.status)**
```json
{
  "type": "session.status",
  "properties": {
    "sessionID": "sess_01jx...",
    "status": { "type": "busy" }
  }
}
```

**权限请求 (permission.updated)**
```json
{
  "type": "permission.updated",
  "properties": {
    "id": "perm_01jx...",
    "sessionID": "sess_01jx...",
    "type": "bash",
    "title": "Execute: rm -rf ./dist",
    "metadata": { "command": "rm -rf ./dist" },
    "time": { "created": "..." }
  }
}
```

### 7.4 SSE→WebSocket 桥接架构

```mermaid
flowchart LR
    subgraph OpenCode["OpenCode 沙箱"]
        Bus["内部 Bus\nBus.subscribeAll()"] --> SSE["SSE /event\nstreamSSE()"]
    end

    subgraph JavaBridge["Java 后端 SSE→WS 桥"]
        SSEClient["Spring WebClient\nSSE Consumer"] --> Parser["事件解析器\nJSON → Event POJO"]
        Parser --> Router["会话路由器\nopencode sessionID → platform userID"]
        Router --> Filter["事件过滤/转换\n适配前端 WS 协议"]
        Filter --> Dispatcher["WebSocket 分发器\n按 userID 推送"]
    end

    subgraph Frontend["前端"]
        WSHandler["WebSocket Handler"] --> StepStore["步骤日志 Store"]
        WSHandler --> ChatStore["对话消息 Store"]
        WSHandler --> StatusStore["状态/权限 Store"]
    end

    SSE -- "SSE data frames\n(text/event-stream)" --> SSEClient
    Dispatcher -- "WS text frames\n(JSON)" --> WSHandler
```

---

## 8. 前端步骤日志渲染

### 8.1 事件 → UI 映射

| SSE 事件 / Part 类型 | UI 渲染 | 状态图标 |
|---|---|---|
| `part.type="text"` + `delta` | 逐字追加 AI 回复（Markdown 渲染） | — |
| `part.type="reasoning"` | "思考中..." 可折叠推理过程 | 🧠 |
| `part.type="tool"` + `status="pending"` | 步骤行出现："准备: {工具名}" | ⏳ |
| `part.type="tool"` + `status="running"` | 旋转 + `state.title` 显示（如 "Running: npm test"） | 🔄 |
| `part.type="tool"` + `status="completed"` | 勾选 + `state.title` + 耗时 | ✅ |
| `part.type="tool"` + `status="error"` | 红叉 + `state.error` | ❌ |
| `part.type="step-start"` | 创建新的可折叠步骤组（Step N） | 📂 |
| `part.type="step-finish"` | 关闭步骤组，显示 token/费用摘要 | 📊 |
| `part.type="patch"` | "修改了 N 个文件" + 文件列表 | 📝 |
| `session.status` = `"busy"` | 全局旋转指示器："Agent 工作中..." | 🔄 |
| `session.status` = `"idle"` | 全局完成指示器 | ✅ |
| `session.status` = `"retry"` | "重试中 (第N次)..." + 倒计时 | ⏱️ |
| `permission.updated` | 暂停：显示权限确认卡片 | ⚠️ |

> **优先级**：展示时优先使用 `state.title`（人类可读描述），其次用工具 ID 映射表。

### 8.2 工具名称显示映射

| 工具 ID | 中文显示名 |
|--------|----------|
| `bash` | 执行命令 |
| `edit` | 编辑文件 |
| `write` | 创建文件 |
| `read` | 读取文件 |
| `glob` | 搜索文件 |
| `grep` | 搜索内容 |
| `fetch` | 获取网页 |
| `task` | 子任务 |
| `todoWrite` | 更新计划 |
| `webSearch` | 搜索网络 |
| `mcp__*` | MCP: {名称} |
| `skill__*` | 技能: {名称} |

### 8.3 步骤日志 UI 结构

```
┌─ 步骤日志 ───────────────────────────────────┐
│                                              │
│  ▼ Step 1                           [折叠▽]  │
│    ✅ 更新计划                                │
│    ✅ 更新计划                                │
│    ⚠️  更新计划 (需要确认)                    │
│    ✅ 更新计划                                │
│                                              │
│  ▼ Step 2                           [折叠▽]  │
│    ✅ 创建文件 /workspace/scripts/analyze.py  │
│    ✅ 创建文件 /workspace/SKILL.md            │
│                                              │
│  ▼ Step 3                           [折叠▽]  │
│    ✅ 执行命令 python analyze.py "..."        │
│    ✅ 执行命令 python analyze.py -t 第一段... │
│    ✅ 执行命令 python analyze.py -t 这是一段  │
│    ✅ 执行命令 python analyze.py "短文本。"   │
│                                              │
│  ▼ Step 4 (进行中)                  [展开▷]  │
│    ✅ 更新计划                                │
│    ✅ 执行命令 ls /workspace/                 │
│    ✅ 打包技能                                │
│    🔄 执行命令 ls -lh /workspace/...         │
│                                              │
└──────────────────────────────────────────────┘

渲染规则:
  step-start Part → 创建新的 Step N 折叠区域
  tool Part (running) → 🔄 动画行
  tool Part (completed) → ✅ 静态行 + 耗时
  tool Part (error) → ❌ 错误行
  step-finish Part → 关闭区域, 显示摘要
  patch Part → "修改了 N 个文件" 展开行
```

### 8.4 文本流式渲染逻辑

```
收到 message.part.updated (type=text):
  1. 按 part.id 查找/创建 buffer
  2. buffer += event.properties.delta
  3. 用 Markdown 渲染器重新渲染 buffer（debounce 16ms）

注意: state.text 是累积全文，delta 是增量。
      优先用 delta 做流式追加（性能好），
      state.text 用于断线重连后的状态恢复。
```

---

## 9. Java 后端网关设计

### 9.1 OpenCode API 调用清单

| 操作 | OpenCode API | HTTP 方法 | 关键参数 |
|------|-------------|----------|---------|
| 新建会话 | `/session/` | POST | `{title?, parentID?}` |
| 列出会话 | `/session/` | GET | `limit, before (cursor)` |
| 获取会话 | `/session/{id}` | GET | — |
| 删除会话 | `/session/{id}` | DELETE | — |
| 更新会话 | `/session/{id}` | PATCH | `{title?, archivedAt?}` |
| 发送消息（异步） | `/session/{id}/prompt_async` | POST | `{parts, model?, agent?}` |
| 发送消息（同步） | `/session/{id}/message` | POST | 同上（长连接等待完成） |
| 获取消息历史 | `/session/{id}/message` | GET | `limit, before` |
| 取消执行 | `/session/{id}/abort` | POST | — |
| 分支会话 | `/session/{id}/fork` | POST | `{messageID?}` |
| 文件差异 | `/session/{id}/diff` | GET | `messageID` |
| 回滚更改 | `/session/{id}/revert` | POST | `{messageID}` |
| 恢复回滚 | `/session/{id}/unrevert` | POST | — |
| 订阅事件 | `/event` | GET (SSE) | Header: x-opencode-directory |
| 浏览文件树 | `/file` | GET | `path` |
| 读取文件 | `/file/content` | GET | `path` |
| Git 状态 | `/file/status` | GET | — |
| 搜索文件名 | `/find/file` | GET | `query` |
| 搜索代码 | `/find` | GET | `pattern` |
| 列出提供商 | `/provider` | GET | — |
| 列出技能 | `/skill` | GET | — |
| 更新配置 | `/config` | PATCH | `{permission?, agent?, ...}` |
| 回复权限 | `/permission/{id}/reply` | POST | `{reply: "once"\|"always"\|"reject"}` |
| 回复问题 | `/question/{id}/reply` | POST | `{answers: [...]}` |
| 健康检查 | `/global/health` | GET | — |

### 9.2 请求代理流程

```mermaid
flowchart TD
    Req[前端 HTTP/WS 请求] --> AuthCheck{JWT 认证}
    AuthCheck -- 失败 --> R401[401 Unauthorized]
    AuthCheck -- 成功 --> FindSandbox[查找 sandbox_instance\n按 user_id]

    FindSandbox --> SandboxState{沙箱状态?}
    SandboxState -- running --> MapSession
    SandboxState -- idle --> UpdateActive[更新 last_active_at] --> MapSession
    SandboxState -- suspended --> Resume[恢复沙箱\n启动容器+等待就绪+重连SSE] --> MapSession
    SandboxState -- 不存在 --> Create[创建沙箱\n参见 3.2] --> MapSession

    MapSession[查找/创建 session_mapping\nplatform_session_id ↔ opencode_session_id]
    MapSession --> BuildReq[构建 opencode 请求\n注入 Basic Auth Header\n注入 x-opencode-directory: /workspace\n替换 session ID]

    BuildReq --> OCReq[调用 OpenCode API\nSpring WebClient]
    OCReq --> Transform[响应格式转换\nopencode schema → platform schema]
    Transform --> Resp[返回前端]
```

### 9.3 所有请求的公共 Header

```
Authorization: Basic base64("opencode:{sandbox_password}")
x-opencode-directory: /workspace
Content-Type: application/json
```

---

## 10. 权限策略引擎

### 10.1 决策流程

```mermaid
flowchart TD
    Event[收到 permission.updated SSE 事件] --> Parse{解析 permission.type}

    Parse -->|edit| Allow
    Parse -->|bash| Allow
    Parse -->|webfetch| Allow
    Parse -->|mcp| Allow
    Parse -->|external_directory| Deny
    Parse -->|unknown| UserAsk

    Allow["自动批准<br/>POST /permission/id/reply<br/>body: reply=always"]
    Deny["自动拒绝<br/>POST /permission/id/reply<br/>body: reply=reject"]
    UserAsk["推送 WS 事件给用户<br/>等待用户操作"]

    UserAsk -->|approve| OncePerm["POST /permission/id/reply<br/>body: reply=once"]
    UserAsk -->|reject| Deny

    style Allow fill:#4CAF50,color:#fff
    style Deny fill:#f44336,color:#fff
```

### 10.2 沙箱初始配置

在沙箱容器就绪后立即执行（通过 `PATCH /config`）：

```json
{
  "permission": {
    "edit": "allow",
    "bash": "allow",
    "webfetch": "allow",
    "mcp": "allow",
    "external_directory": "deny"
  }
}
```

> 沙箱容器已天然隔离，容器内所有文件操作和命令执行均在 `/workspace` 范围内，可安全自动批准。唯一需要拒绝的是尝试访问 `/workspace` 之外的路径。

---

## 11. Skill Creator 集成

### 11.1 技能创建流程

```mermaid
sequenceDiagram
    actor User as 用户
    participant FE as 前端技能编辑器
    participant Java as Java 后端
    participant OC as OpenCode 沙箱

    User->>FE: 填写技能 (名称, 描述, 指令内容)
    FE->>Java: POST /api/workbench/skill\n{name, description, content, scope}

    Java->>Java: 保存到 user_skill 表
    Java->>OC: POST /session/{id}/prompt_async\n{parts:[{type:"text", text:"创建技能文件..."}]}
    Note over OC: Agent 写入 SKILL.md 文件\n/workspace/.opencode/skills/{name}/SKILL.md

    OC-->>Java: SSE: session.status {type:"idle"}
    Java->>OC: GET /skill
    OC-->>Java: [{name, description, location, content}, ...]
    Java-->>FE: 技能创建成功 + 技能列表
    FE-->>User: 显示新创建的技能
```

### 11.2 SKILL.md 文件格式

```markdown
---
name: text-statistics
description: 文本统计技能，分析中文文本的字数、段落数、首句摘要
---

当用户要求分析文本时，请执行以下步骤：

1. **统计总字数**：逐字计数（中文每个汉字算1字）
2. **统计段落数**：以空行作为段落分隔符
3. **提取首句摘要**：取第一个句号/问号/感叹号前的内容
4. **输出格式化报告**：
   - 总字数: {N} 字
   - 段落数: {N} 段
   - 首句摘要: {文字}

输出时使用清晰的 Markdown 格式。
```

### 11.3 技能文件发现路径（优先级从高到低）

```
1. /workspace/.opencode/skills/{name}/SKILL.md   项目级（推荐）
2. ~/.opencode/skills/{name}/SKILL.md             用户级
3. ~/.claude/skills/{name}/SKILL.md               兼容 Claude Code
4. config.skills.paths 配置的自定义路径
5. config.skills.urls 配置的远程注册中心 URL
```

### 11.4 共享技能注册中心（可选）

平台可搭建中心化技能仓库，供所有用户共享：

```
HTTP 服务暴露:
  GET /index.json
  → {"skills": [{"name": "text-statistics", "files": ["SKILL.md"]}, ...]}

  GET /{skill-name}/SKILL.md
  → skill 文件内容

配置 opencode 拉取:
  PATCH /config
  {"skills": {"urls": ["https://skills.your-platform.com/"]}}
```

---

## 12. 多 LLM 提供商支持

### 12.1 模型选择流程

```mermaid
flowchart LR
    FE[前端模型选择器] -->|GET /api/workbench/provider| Java[Java 后端]
    Java -->|GET /provider| OC[OpenCode 沙箱]
    OC --> ProviderList["返回提供商列表<br/>anthropic / openai / google ..."]
    ProviderList --> Java --> FE

    FE -->|用户选择模型| Msg["发送消息时携带<br/>model: providerID + modelID"]
    Msg -->|prompt_async body| OC2[OpenCode 沙箱]
```

### 12.2 支持的主要提供商

opencode 内置支持（通过 Vercel AI SDK）：

| 提供商 ID | 名称 | 环境变量 |
|----------|------|---------|
| `anthropic` | Anthropic Claude | `ANTHROPIC_API_KEY` |
| `openai` | OpenAI GPT | `OPENAI_API_KEY` |
| `google` | Google Gemini | `GOOGLE_GENERATIVE_AI_API_KEY` |
| `amazon-bedrock` | AWS Bedrock | `AWS_ACCESS_KEY_ID` 等 |
| `azure` | Azure OpenAI | `AZURE_API_KEY` 等 |
| `openrouter` | OpenRouter | `OPENROUTER_API_KEY` |
| `github-copilot` | GitHub Copilot | OAuth 流程 |
| `xai` | xAI Grok | `XAI_API_KEY` |

### 12.3 API Key 管理方式

```
方式一: 平台统一管理（推荐）
  Docker 启动时通过环境变量注入，用户无感知

方式二: 用户自带 Key（BYOK）
  PUT /auth/{providerID}
  Body: {"type": "api", "key": "sk-..."}
  → opencode 存储在本地，仅当前沙箱生效
```

---

## 13. 安全架构

### 13.1 安全层次模型

```
┌──────────────────────────────────────────────────────┐
│  Layer 1: 前端认证层                                   │
│  平台 JWT Token / SSO / OAuth 2.0                     │
│  所有 HTTP 请求验证 Authorization Header               │
├──────────────────────────────────────────────────────┤
│  Layer 2: Java 网关层                                  │
│  请求鉴权 → 用户隔离 → 沙箱路由 → 限流                  │
│  防止用户 A 访问用户 B 的沙箱                           │
├──────────────────────────────────────────────────────┤
│  Layer 3: 容器网络层                                   │
│  Docker bridge 网络，容器间无法直接通信                   │
│  仅 TCP 4096 对 Java 后端内网开放                       │
│  出站限制：仅允许 LLM API 域名 (*.anthropic.com 等)     │
├──────────────────────────────────────────────────────┤
│  Layer 4: opencode 认证层                              │
│  HTTP Basic Auth，每个沙箱独立随机密码                   │
│  防止绕过 Java 网关直接访问 opencode                    │
├──────────────────────────────────────────────────────┤
│  Layer 5: 文件系统隔离层                               │
│  /workspace 持久卷仅当前容器挂载                        │
│  permission.external_directory = "deny"              │
│  禁止访问 /workspace 之外的路径                        │
├──────────────────────────────────────────────────────┤
│  Layer 6: 资源限制层                                   │
│  Docker cgroups: CPU=2核, Mem=4GB, Disk=10GB         │
│  Agent 步骤限制: config.agent.build.maxSteps=100     │
│  用户随时可调用 POST /session/{id}/abort 取消           │
└──────────────────────────────────────────────────────┘
```

---

## 14. 关键源文件参考

| 文件路径 | 用途 |
|---------|------|
| `packages/opencode/src/server/server.ts` | 服务器主文件：中间件链、路由注册、Basic Auth、CORS |
| `packages/opencode/src/server/routes/session.ts` | 会话 API 全部端点：请求/响应 Schema、prompt 流程 |
| `packages/opencode/src/server/routes/event.ts` | SSE 事件流：心跳 10s、Bus.subscribeAll、断连处理 |
| `packages/opencode/src/server/routes/permission.ts` | 权限 API：list + reply |
| `packages/opencode/src/server/routes/question.ts` | 问题 API：list + reply |
| `packages/opencode/src/server/routes/file.ts` | 文件操作 API：tree/content/status |
| `packages/opencode/src/server/routes/global.ts` | 全局 API：health check + global SSE stream |
| `packages/sdk/js/src/gen/types.gen.ts` | 完整 TypeScript 类型定义（Java DTO 生成参考） |
| `packages/opencode/src/skill/index.ts` | 技能系统：SKILL.md 格式、发现路径算法 |
| `packages/opencode/src/session/prompt.ts` | 核心 Agent 循环逻辑（loop、createUserMessage） |
| `packages/opencode/src/session/processor.ts` | 流式处理器（LLM stream → Part 更新 → Bus 发布） |
| `packages/opencode/src/tool/registry.ts` | 工具注册表（内置工具列表、MCP 工具加载） |
| `packages/opencode/src/provider/provider.ts` | 多 LLM 提供商抽象层（18+ 提供商） |
| `packages/opencode/src/bus/bus-event.ts` | 事件类型定义（完整事件 Schema） |
| `packages/opencode/src/cli/cmd/serve.ts` | serve 命令入口（端口、Basic Auth、CORS 参数） |

---

## 15. 实施路线图

```mermaid
gantt
    title OpenCode 集成实施路线图
    dateFormat  YYYY-MM-DD
    axisFormat  %m/%d

    section Phase 1: 基础集成 (MVP)
    Docker 沙箱镜像与生命周期管理    :p1a, 2026-04-01, 5d
    Java OpenCode REST Client       :p1b, 2026-04-01, 5d
    SSE Consumer + WS Bridge        :p1c, after p1b, 5d
    前端对话面板 + 流式文本渲染       :p1d, after p1c, 5d
    权限自动批准初始化配置            :p1e, after p1a, 2d
    端到端联调测试                   :p1f, after p1d, 3d
    输出 OpenAPI YAML (Phase 1)     :p1g, after p1f, 2d

    section Phase 2: 丰富 UI
    步骤日志组件 (工具状态动画)       :p2a, after p1f, 5d
    文件树浏览器                     :p2b, after p1f, 3d
    文件差异查看 (diff 视图)         :p2c, after p2b, 3d
    模型/提供商选择器                :p2d, after p2a, 3d
    输出 OpenAPI YAML (Phase 2)     :p2e, after p2d, 2d

    section Phase 3: 技能系统
    技能编辑器 UI (CRUD)             :p3a, after p2d, 5d
    技能列表展示与调用               :p3b, after p3a, 3d
    共享技能注册中心 (可选)           :p3c, after p3b, 5d
    输出 OpenAPI YAML (Phase 3)     :p3d, after p3b, 1d

    section Phase 4: 高级功能
    对话分支 (fork)                  :p4a, after p3b, 3d
    更改回滚 (revert/unrevert)      :p4b, after p4a, 3d
    沙箱快照与恢复                   :p4c, after p4b, 5d
    资源监控仪表盘                   :p4d, after p4c, 3d
    完整 OpenAPI YAML v1.0          :p4e, after p4d, 2d
```

---

## 附录：快速验证步骤

```bash
# 1. 本地启动 opencode 无头服务
cd packages/opencode
bun dev serve --port 4096

# 2. 验证 SSE 事件流格式
curl -N http://localhost:4096/event

# 3. 测试会话创建
curl -X POST http://localhost:4096/session/ \
  -H "Content-Type: application/json" \
  -d '{}'

# 4. 测试异步 prompt
curl -X POST http://localhost:4096/session/{SESSION_ID}/prompt_async \
  -H "Content-Type: application/json" \
  -d '{"parts":[{"type":"text","text":"hello, what can you do?"}]}'

# 5. 验证 Docker 沙箱镜像
docker build -f integration/sandbox/Dockerfile -t opencode-sandbox .
docker run -p 4096:4096 \
  -e OPENCODE_SERVER_PASSWORD=test123 \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  opencode-sandbox
```
