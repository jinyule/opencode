import MockAdapter from 'axios-mock-adapter'
import axios from 'axios'
import { MOCK_SESSIONS, MOCK_MESSAGES, MOCK_PROVIDERS, MOCK_SKILLS } from './data'
import { simulateAIResponse } from './wsSimulator'
import type { Skill } from '@/types/workbench'

// 共享 mock 状态（运行时可变）
const sessions = [...MOCK_SESSIONS]
const messages: Record<string, unknown[]> = { ...MOCK_MESSAGES }
const skills: Skill[] = [...MOCK_SKILLS]
let skillIdSeq = 10

export function setupAxiosMocks() {
  const mock = new MockAdapter(axios, { delayResponse: 120 })

  // ── 沙箱 ─────────────────────────────────────────────────

  mock.onPost('/api/workbench/sandbox').reply(202, {
    id: 1, userId: 1, projectId: null,
    status: 'RUNNING',
    createdAt: new Date().toISOString(),
    lastActiveAt: new Date().toISOString(),
  })

  mock.onGet(/\/api\/workbench\/sandbox\/\d+$/).reply(200, {
    id: 1, status: 'RUNNING',
    createdAt: new Date().toISOString(),
    lastActiveAt: new Date().toISOString(),
  })

  mock.onPost(/\/api\/workbench\/sandbox\/\d+\/resume/).reply(202, { id: 1, status: 'RUNNING' })
  mock.onPost(/\/api\/workbench\/sandbox\/\d+\/suspend/).reply(202)
  mock.onDelete(/\/api\/workbench\/sandbox\/\d+/).reply(204)

  // ── 会话 ─────────────────────────────────────────────────

  mock.onGet('/api/workbench/session').reply(200, sessions)

  mock.onPost('/api/workbench/session').reply(config => {
    const newSession = {
      id: `sess-${Date.now()}`,
      title: '新对话',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    sessions.unshift(newSession)
    messages[newSession.id] = []
    return [201, newSession]
  })

  mock.onGet(/\/api\/workbench\/session\/[^/]+$/).reply(config => {
    const id = config.url!.split('/').pop()!
    const session = sessions.find(s => s.id === id)
    return session ? [200, session] : [404, { message: 'Not found' }]
  })

  mock.onPatch(/\/api\/workbench\/session\/[^/]+$/).reply(config => {
    const id = config.url!.split('/').pop()!
    const body = JSON.parse(config.data)
    const session = sessions.find(s => s.id === id)
    if (!session) return [404]
    session.title = body.title
    return [200, session]
  })

  mock.onDelete(/\/api\/workbench\/session\/[^/]+$/).reply(config => {
    const id = config.url!.split('/').pop()!
    const idx = sessions.findIndex(s => s.id === id)
    if (idx !== -1) sessions.splice(idx, 1)
    return [204]
  })

  mock.onPost(/\/api\/workbench\/session\/[^/]+\/abort/).reply(204)
  mock.onPost(/\/api\/workbench\/session\/[^/]+\/revert/).reply(204)
  mock.onPost(/\/api\/workbench\/session\/[^/]+\/unrevert/).reply(204)

  mock.onPost(/\/api\/workbench\/session\/[^/]+\/fork/).reply(config => {
    const forked = {
      id: `sess-fork-${Date.now()}`,
      title: '分支对话',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    sessions.unshift(forked)
    return [201, forked]
  })

  // ── 消息 ─────────────────────────────────────────────────

  mock.onGet(/\/api\/workbench\/session\/[^/]+\/message/).reply(config => {
    const parts = config.url!.split('/')
    const sessionId = parts[parts.indexOf('session') + 1]
    return [200, messages[sessionId] ?? []]
  })

  mock.onPost(/\/api\/workbench\/session\/[^/]+\/message/).reply(config => {
    const parts = config.url!.split('/')
    const sessionId = parts[parts.indexOf('session') + 1]
    // 延迟触发 WS 模拟回复
    setTimeout(() => simulateAIResponse(sessionId), 300)
    return [202]
  })

  // ── 文件 ─────────────────────────────────────────────────

  mock.onGet('/api/workbench/file/tree').reply(200, {
    name: 'workspace',
    path: '.',
    type: 'directory',
    children: [
      {
        name: 'src', path: 'src', type: 'directory',
        children: [
          { name: 'main.ts', path: 'src/main.ts', type: 'file' },
          { name: 'app.ts',  path: 'src/app.ts',  type: 'file' },
          {
            name: 'user', path: 'src/user', type: 'directory',
            children: [
              { name: 'index.ts',      path: 'src/user/index.ts',      type: 'file' },
              { name: 'service.ts',    path: 'src/user/service.ts',    type: 'file' },
              { name: 'repository.ts', path: 'src/user/repository.ts', type: 'file' },
              { name: 'dto.ts',        path: 'src/user/dto.ts',        type: 'file' },
            ],
          },
        ],
      },
      { name: 'package.json', path: 'package.json', type: 'file' },
      { name: 'tsconfig.json', path: 'tsconfig.json', type: 'file' },
      { name: 'README.md', path: 'README.md', type: 'file' },
    ],
  })

  mock.onGet('/api/workbench/file/content').reply(config => {
    const path = config.params?.path ?? ''
    return [200, { path, content: `// ${path}\n// （Mock 内容）\nexport default {}\n` }]
  })

  mock.onGet('/api/workbench/file/status').reply(200, {
    modified: ['src/user/service.ts'],
    added: ['src/user/service.spec.ts'],
    deleted: [],
  })

  mock.onGet('/api/workbench/search/file').reply(config => {
    const q = config.params?.query ?? ''
    return [200, { results: [`src/${q}.ts`, `src/user/${q}.ts`] }]
  })

  mock.onGet('/api/workbench/search/code').reply(config => {
    return [200, { results: [] }]
  })

  // ── 提供商 ────────────────────────────────────────────────

  mock.onGet('/api/workbench/provider').reply(200, MOCK_PROVIDERS)
  mock.onPut(/\/api\/workbench\/provider\/[^/]+\/auth/).reply(204)

  // ── 技能 ─────────────────────────────────────────────────

  mock.onGet('/api/workbench/skill').reply(200, skills)

  mock.onPost('/api/workbench/skill').reply(config => {
    const body = JSON.parse(config.data)
    const skill: Skill = {
      id: ++skillIdSeq,
      name: body.name,
      description: body.description ?? '',
      content: body.content,
      scope: body.scope ?? 'project',
      projectId: body.projectId,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    skills.unshift(skill)
    return [201, skill]
  })

  mock.onPut(/\/api\/workbench\/skill\/[^/]+$/).reply(config => {
    const name = config.url!.split('/').pop()!
    const body = JSON.parse(config.data)
    const skill = skills.find(s => s.name === name)
    if (!skill) return [404]
    skill.description = body.description
    skill.content = body.content
    skill.updatedAt = new Date().toISOString()
    return [200, skill]
  })

  mock.onDelete(/\/api\/workbench\/skill\/[^/]+$/).reply(config => {
    const name = config.url!.split('/').pop()!
    const idx = skills.findIndex(s => s.name === name)
    if (idx !== -1) skills.splice(idx, 1)
    return [204]
  })

  // ── 权限 / 配置 ───────────────────────────────────────────

  mock.onPost(/\/api\/workbench\/permission\/[^/]+\/reply/).reply(204)
  mock.onGet('/api/workbench/config').reply(200, { permission: { edit: 'allow', bash: 'allow' } })
  mock.onPatch('/api/workbench/config').reply(200)

  console.info('[Mock] Axios Mock 已启动，所有 /api/workbench/* 请求均由 Mock 响应')
}
