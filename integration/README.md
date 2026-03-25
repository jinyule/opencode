# OpenCode 集成文档目录

将 opencode 作为编程 Agent 后端接入 Agent 开发平台（Coze 风格）的完整集成方案。

## 文件清单

| 文件 | 说明 |
|------|------|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 完整架构文档（含所有 Mermaid/ASCII 图表） |
| [`workbench-api-v1.yaml`](./workbench-api-v1.yaml) | OpenAPI 3.0 接口规范（平台网关 API） |
| [`sandbox/Dockerfile`](./sandbox/Dockerfile) | opencode 沙箱容器镜像模板 |
| [`sandbox/docker-compose.yml`](./sandbox/docker-compose.yml) | 本地开发 Docker Compose 配置 |

## 快速开始

### 1. 本地验证 opencode API

```bash
# 在 opencode 项目根目录运行
bun dev serve --port 4096

# 验证服务就绪
curl http://localhost:4096/global/health

# 监听 SSE 事件流
curl -N http://localhost:4096/event

# 创建会话并发送消息
SESSION=$(curl -s -X POST http://localhost:4096/session/ \
  -H "Content-Type: application/json" -d '{}' | jq -r .id)

curl -X POST http://localhost:4096/session/$SESSION/prompt_async \
  -H "Content-Type: application/json" \
  -d '{"parts":[{"type":"text","text":"列出当前目录文件"}]}'
```

### 2. 启动本地沙箱容器

```bash
cd integration/sandbox

# 配置环境变量
export OPENCODE_SERVER_PASSWORD=dev-test-password
export ANTHROPIC_API_KEY=sk-ant-xxx   # 至少配置一个 LLM Key

# 启动（首次构建镜像需要几分钟）
docker-compose up -d

# 查看日志
docker-compose logs -f

# 测试连通性
curl -u "opencode:dev-test-password" http://localhost:4096/global/health
```

### 3. 查看 API 文档

`workbench-api-v1.yaml` 是标准 OpenAPI 3.0 规范，可用以下方式预览：

```bash
# 方式一: 使用 Swagger UI Docker 镜像
docker run -p 8080:8080 \
  -e SWAGGER_JSON=/tmp/api.yaml \
  -v $(pwd)/workbench-api-v1.yaml:/tmp/api.yaml \
  swaggerapi/swagger-ui

# 方式二: 使用 VS Code 插件 "OpenAPI (Swagger) Editor"

# 方式三: 在线预览 https://editor.swagger.io/
# 将 workbench-api-v1.yaml 内容粘贴进去
```

## 集成核心要点

### opencode API 地址格式
```
http://{sandbox_internal_ip}:4096/{endpoint}
```

### 所有请求必须携带的 Header
```
Authorization: Basic base64("opencode:{sandbox_password}")
x-opencode-directory: /workspace
```

### SSE 事件流连接
```
GET http://{sandbox_ip}:4096/event
Accept: text/event-stream
Authorization: Basic ...
```

### 异步 Prompt 流程
```
1. POST /session/{id}/prompt_async  →  204 (立即返回)
2. 监听 SSE 事件流获取实时进度
3. 收到 session.status{type:"idle"} 表示执行完成
```

### 步骤日志渲染核心事件
```
message.part.updated + part.type="text"   →  流式追加文本 (delta 字段)
message.part.updated + part.type="tool"   →  工具执行状态 (pending/running/completed/error)
message.part.updated + part.type="step-start"  →  新步骤组开始
session.status{type:"busy"|"idle"}         →  全局执行状态
permission.updated                         →  需要用户确认
```

## 相关 opencode 源码

- 服务器主文件: `packages/opencode/src/server/server.ts`
- 会话 API: `packages/opencode/src/server/routes/session.ts`
- SSE 事件流: `packages/opencode/src/server/routes/event.ts`
- 技能系统: `packages/opencode/src/skill/index.ts`
- Agent 循环: `packages/opencode/src/session/prompt.ts`
- 类型定义: `packages/sdk/js/src/gen/types.gen.ts`
