import axios from 'axios'
import type {
  SandboxInstance, Session, Message, SendMessageRequest,
  Provider, Skill, CreateSkillRequest,
} from '@/types/workbench'

const http = axios.create({ baseURL: '/api/workbench' })

// 注入 JWT token
http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

// --- 沙箱 ---

export const sandboxApi = {
  create: (projectId?: number) =>
    http.post<SandboxInstance>('/sandbox', projectId ? { projectId } : {}),

  get: (id: number) =>
    http.get<SandboxInstance>(`/sandbox/${id}`),

  resume: (id: number) =>
    http.post<SandboxInstance>(`/sandbox/${id}/resume`),

  suspend: (id: number) =>
    http.post<void>(`/sandbox/${id}/suspend`),

  destroy: (id: number) =>
    http.delete<void>(`/sandbox/${id}`),
}

// --- 会话 ---

export const sessionApi = {
  list: (projectId?: number) =>
    http.get<Session[]>('/session', { params: { projectId } }),

  create: (projectId?: number) =>
    http.post<Session>('/session', projectId ? { projectId } : {}),

  get: (id: string) =>
    http.get<Session>(`/session/${id}`),

  update: (id: string, title: string) =>
    http.patch<Session>(`/session/${id}`, { title }),

  delete: (id: string) =>
    http.delete<void>(`/session/${id}`),

  abort: (id: string) =>
    http.post<void>(`/session/${id}/abort`),

  fork: (id: string, messageId: string) =>
    http.post<Session>(`/session/${id}/fork`, { messageId }),

  getDiff: (id: string, messageId?: string) =>
    http.get<unknown>(`/session/${id}/diff`, { params: { messageId } }),

  revert: (id: string, messageId: string) =>
    http.post<void>(`/session/${id}/revert`, { messageId }),

  unrevert: (id: string) =>
    http.post<void>(`/session/${id}/unrevert`),
}

// --- 消息 ---

export const messageApi = {
  send: (sessionId: string, req: SendMessageRequest) =>
    http.post<void>(`/session/${sessionId}/message`, req),

  list: (sessionId: string, limit = 50, before?: string) =>
    http.get<Message[]>(`/session/${sessionId}/message`, { params: { limit, before } }),
}

// --- 文件 ---

export const fileApi = {
  tree: (path = '.', projectId?: number) =>
    http.get<unknown>('/file/tree', { params: { path, projectId } }),

  content: (path: string, projectId?: number) =>
    http.get<unknown>('/file/content', { params: { path, projectId } }),

  status: (projectId?: number) =>
    http.get<unknown>('/file/status', { params: { projectId } }),

  searchFile: (query: string, projectId?: number) =>
    http.get<unknown>('/search/file', { params: { query, projectId } }),

  searchCode: (pattern: string, glob?: string, projectId?: number) =>
    http.get<unknown>('/search/code', { params: { pattern, glob, projectId } }),
}

// --- 技能 ---

export const skillApi = {
  list: () =>
    http.get<Skill[]>('/skill'),

  create: (req: CreateSkillRequest) =>
    http.post<Skill>('/skill', req),

  update: (name: string, description: string, content: string) =>
    http.put<Skill>(`/skill/${name}`, { description, content }),

  delete: (name: string) =>
    http.delete<void>(`/skill/${name}`),
}

// --- 提供商 ---

export const providerApi = {
  list: (projectId?: number) =>
    http.get<Provider[]>('/provider', { params: { projectId } }),

  setAuth: (providerId: string, key: string) =>
    http.put<void>(`/provider/${providerId}/auth`, { type: 'api', key }),
}

// --- 权限 ---

export const permissionApi = {
  reply: (requestId: string, reply: 'once' | 'always' | 'reject') =>
    http.post<void>(`/permission/${requestId}/reply`, { reply }),
}

// --- 配置 ---

export const configApi = {
  get: () => http.get<unknown>('/config'),
  patch: (config: Record<string, unknown>) => http.patch<void>('/config', config),
}
