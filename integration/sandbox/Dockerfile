FROM oven/bun:1.3-slim

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    git \
    ripgrep \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 安装 opencode（从本地源码构建，或从 npm 安装正式版）
# 方式一：从本地源码复制（用于开发）
COPY packages/opencode/dist/ /app/opencode/
WORKDIR /app/opencode
RUN bun install --production

# 方式二：从 npm 安装（用于生产，取消注释此行并注释上方三行）
# RUN bun install -g opencode

# 创建工作目录
RUN mkdir -p /workspace

WORKDIR /workspace

# 健康检查
HEALTHCHECK --interval=5s --timeout=3s --start-period=15s --retries=6 \
    CMD curl -f http://localhost:4096/global/health || exit 1

EXPOSE 4096

# 启动 opencode 无头服务
# 环境变量由 Docker 运行时注入:
#   OPENCODE_SERVER_PASSWORD - Basic Auth 密码（必须）
#   ANTHROPIC_API_KEY        - Anthropic API Key
#   OPENAI_API_KEY           - OpenAI API Key
#   GOOGLE_GENERATIVE_AI_API_KEY - Google API Key
CMD ["bun", "/app/opencode/src/index.ts", "serve", "--port", "4096", "--hostname", "0.0.0.0"]
