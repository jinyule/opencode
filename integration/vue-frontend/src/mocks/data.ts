import type { Session, Message, Provider, Skill } from '@/types/workbench'

// ---- 会话 ----

export const MOCK_SESSIONS: Session[] = [
  {
    id: 'sess-001',
    title: '分析用户模块代码',
    createdAt: '2026-03-25T08:00:00Z',
    updatedAt: '2026-03-25T08:12:00Z',
  },
  {
    id: 'sess-002',
    title: '添加单元测试',
    createdAt: '2026-03-24T14:30:00Z',
    updatedAt: '2026-03-24T15:00:00Z',
  },
  {
    id: 'sess-003',
    title: '新对话',
    createdAt: '2026-03-25T10:00:00Z',
    updatedAt: '2026-03-25T10:00:00Z',
  },
]

// ---- 消息（含工具调用步骤） ----

export const MOCK_MESSAGES: Record<string, Message[]> = {
  'sess-001': [
    {
      id: 'msg-u-001',
      sessionId: 'sess-001',
      role: 'user',
      parts: [
        { id: 'p0', type: 'text', state: { text: '帮我分析一下 src/user 模块的代码结构' }, time: { created: '2026-03-25T08:00:00Z' } },
      ],
      createdAt: '2026-03-25T08:00:00Z',
    },
    {
      id: 'msg-a-001',
      sessionId: 'sess-001',
      role: 'assistant',
      model: 'anthropic:claude-sonnet-4-20250514',
      parts: [
        {
          id: 'p-step-1',
          type: 'step-start',
          state: {},
          time: { created: '2026-03-25T08:00:01Z' },
        },
        {
          id: 'p-tool-1',
          type: 'tool',
          state: {
            status: 'completed',
            tool: 'glob',
            title: '搜索文件 src/user/**',
            input: { pattern: 'src/user/**' },
            output: 'src/user/index.ts\nsrc/user/service.ts\nsrc/user/repository.ts\nsrc/user/dto.ts',
            time: { start: '2026-03-25T08:00:02Z', end: '2026-03-25T08:00:02Z' },
          },
          time: { created: '2026-03-25T08:00:02Z' },
        },
        {
          id: 'p-tool-2',
          type: 'tool',
          state: {
            status: 'completed',
            tool: 'read',
            title: '读取文件 src/user/service.ts',
            input: { path: 'src/user/service.ts' },
            output: 'export class UserService { ... }',
            time: { start: '2026-03-25T08:00:03Z', end: '2026-03-25T08:00:03Z' },
          },
          time: { created: '2026-03-25T08:00:03Z' },
        },
        {
          id: 'p-tool-3',
          type: 'tool',
          state: {
            status: 'completed',
            tool: 'bash',
            title: '执行命令 wc -l src/user/*.ts',
            input: { command: 'wc -l src/user/*.ts' },
            output: '  45 src/user/index.ts\n  120 src/user/service.ts\n   89 src/user/repository.ts\n   34 src/user/dto.ts\n  288 total',
            time: { start: '2026-03-25T08:00:04Z', end: '2026-03-25T08:00:04Z' },
          },
          time: { created: '2026-03-25T08:00:04Z' },
        },
        {
          id: 'p-step-f',
          type: 'step-finish',
          state: { tokens: { input: 1240, output: 380 }, cost: 0.002 },
          time: { created: '2026-03-25T08:00:05Z' },
        },
        {
          id: 'p-text-1',
          type: 'text',
          state: {
            text: `## src/user 模块结构分析

该模块共 4 个文件，总计 **288 行**，分层清晰：

| 文件 | 行数 | 职责 |
|------|------|------|
| \`index.ts\` | 45 | 模块导出入口 |
| \`service.ts\` | 120 | 业务逻辑层 |
| \`repository.ts\` | 89 | 数据访问层 |
| \`dto.ts\` | 34 | 数据传输对象 |

### 建议
- \`service.ts\` 已超过 100 行，可考虑拆分为 \`auth.service.ts\` 和 \`profile.service.ts\`
- \`dto.ts\` 中缺少输入校验注解，建议补充 \`class-validator\` 装饰器`,
          },
          time: { created: '2026-03-25T08:00:06Z' },
        },
      ],
      createdAt: '2026-03-25T08:00:01Z',
    },
  ],
  'sess-002': [
    {
      id: 'msg-u-002',
      sessionId: 'sess-002',
      role: 'user',
      parts: [
        { id: 'p0', type: 'text', state: { text: '为 UserService 添加单元测试' }, time: { created: '2026-03-24T14:30:00Z' } },
      ],
      createdAt: '2026-03-24T14:30:00Z',
    },
    {
      id: 'msg-a-002',
      sessionId: 'sess-002',
      role: 'assistant',
      model: 'anthropic:claude-sonnet-4-20250514',
      parts: [
        { id: 'ps1', type: 'step-start', state: {}, time: { created: '2026-03-24T14:30:01Z' } },
        {
          id: 'pt1',
          type: 'tool',
          state: { status: 'completed', tool: 'read', title: '读取文件 src/user/service.ts', time: { start: '2026-03-24T14:30:02Z', end: '2026-03-24T14:30:02Z' } },
          time: { created: '2026-03-24T14:30:02Z' },
        },
        {
          id: 'pt2',
          type: 'tool',
          state: { status: 'completed', tool: 'write', title: '创建文件 src/user/service.spec.ts', time: { start: '2026-03-24T14:30:03Z', end: '2026-03-24T14:30:03Z' } },
          time: { created: '2026-03-24T14:30:03Z' },
        },
        {
          id: 'pt3',
          type: 'tool',
          state: { status: 'completed', tool: 'bash', title: '执行命令 bun test src/user/service.spec.ts', time: { start: '2026-03-24T14:30:04Z', end: '2026-03-24T14:30:05Z' } },
          time: { created: '2026-03-24T14:30:04Z' },
        },
        { id: 'psf', type: 'step-finish', state: { tokens: { input: 980, output: 540 } }, time: { created: '2026-03-24T14:30:06Z' } },
        {
          id: 'ptx',
          type: 'text',
          state: { text: '已为 `UserService` 创建测试文件 `src/user/service.spec.ts`，包含 8 个测试用例，全部通过 ✅' },
          time: { created: '2026-03-24T14:30:07Z' },
        },
        {
          id: 'ppatch',
          type: 'patch',
          state: { files: ['src/user/service.spec.ts'], additions: 87, deletions: 0 },
          time: { created: '2026-03-24T14:30:07Z' },
        },
      ],
      createdAt: '2026-03-24T14:30:01Z',
    },
  ],
  'sess-003': [],
}

// ---- LLM 提供商 ----

export const MOCK_PROVIDERS: Provider[] = [
  {
    id: 'anthropic',
    name: 'Anthropic',
    models: [
      { id: 'claude-opus-4-20250514',    name: 'Claude Opus 4',    contextLength: 200000, supportsVision: true },
      { id: 'claude-sonnet-4-20250514',  name: 'Claude Sonnet 4',  contextLength: 200000, supportsVision: true },
      { id: 'claude-haiku-4-5-20251001', name: 'Claude Haiku 4.5', contextLength: 200000, supportsVision: false },
    ],
  },
  {
    id: 'openai',
    name: 'OpenAI',
    models: [
      { id: 'gpt-4o',      name: 'GPT-4o',      contextLength: 128000, supportsVision: true },
      { id: 'gpt-4o-mini', name: 'GPT-4o Mini', contextLength: 128000, supportsVision: true },
      { id: 'o3',          name: 'o3',           contextLength: 200000, supportsVision: false },
    ],
  },
  {
    id: 'google',
    name: 'Google',
    models: [
      { id: 'gemini-2.5-pro',   name: 'Gemini 2.5 Pro',   contextLength: 1000000, supportsVision: true },
      { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash', contextLength: 1000000, supportsVision: true },
    ],
  },
]

// ---- 技能 ----

export const MOCK_SKILLS: Skill[] = [
  {
    id: 1,
    name: 'text-statistics',
    description: '文本统计技能，分析字数、段落、摘要',
    content: '当用户要求分析文本时，统计字数、段落数，提取首句摘要，输出格式化报告。',
    scope: 'global',
    createdAt: '2026-03-20T09:00:00Z',
    updatedAt: '2026-03-20T09:00:00Z',
  },
  {
    id: 2,
    name: 'code-review',
    description: '代码审查技能，检查规范、安全与性能',
    content: '对指定文件进行代码审查：1. 检查代码规范 2. 识别安全隐患 3. 提出性能优化建议，输出结构化审查报告。',
    scope: 'project',
    createdAt: '2026-03-22T11:00:00Z',
    updatedAt: '2026-03-22T11:00:00Z',
  },
]
