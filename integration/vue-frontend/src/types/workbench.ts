// ============================================================
// 编程工作台 TypeScript 类型定义
// 与 workbench-api-v1.yaml 中的 Schema 保持一致
// ============================================================

// --- 沙箱 ---

export type SandboxStatus =
  | 'CREATING' | 'STARTING' | 'RUNNING' | 'IDLE'
  | 'SUSPENDING' | 'SUSPENDED' | 'DESTROYING' | 'DESTROYED'

export interface SandboxInstance {
  id: number
  userId: number
  projectId?: number
  status: SandboxStatus
  createdAt: string
  lastActiveAt: string
}

// --- 会话 ---

export interface Session {
  id: string
  title?: string
  createdAt: string
  updatedAt: string
}

// --- 消息 Part ---

export type PartType =
  | 'text' | 'reasoning' | 'tool' | 'file'
  | 'step-start' | 'step-finish' | 'patch' | 'agent' | 'subtask'

export type ToolStatus = 'pending' | 'running' | 'completed' | 'error'

export interface TextPartState {
  text: string
}

export interface ReasoningPartState {
  text: string
}

export interface ToolPartState {
  status: ToolStatus
  tool: string
  title: string
  input?: Record<string, unknown>
  output?: string
  error?: string
  time?: { start?: string; end?: string }
}

export interface PatchPartState {
  files: string[]
  additions: number
  deletions: number
}

export interface StepFinishPartState {
  tokens?: { input: number; output: number }
  cost?: number
}

export interface Part {
  id: string
  type: PartType
  state: TextPartState | ReasoningPartState | ToolPartState | PatchPartState | StepFinishPartState | Record<string, unknown>
  time: { created: string; updated?: string }
}

// --- 消息 ---

export type MessageRole = 'user' | 'assistant'

export interface Message {
  id: string
  sessionId: string
  role: MessageRole
  model?: string
  agent?: string
  parts: Part[]
  createdAt: string
}

// --- 发送消息请求 ---

export interface TextPartInput {
  type: 'text'
  text: string
}

export interface FilePartInput {
  type: 'file'
  mime: string
  url: string
}

export interface ModelRef {
  providerID: string
  modelID: string
}

export interface SendMessageRequest {
  parts: (TextPartInput | FilePartInput)[]
  model?: ModelRef
  agent?: 'build' | 'plan' | 'explore'
}

// --- 提供商 / 模型 ---

export interface Model {
  id: string
  name: string
  contextLength?: number
  supportsVision?: boolean
}

export interface Provider {
  id: string
  name: string
  models: Model[]
}

// --- 技能 ---

export interface Skill {
  id: number
  name: string
  description?: string
  content: string
  scope: 'global' | 'project'
  projectId?: number
  createdAt: string
  updatedAt: string
}

export interface CreateSkillRequest {
  name: string
  description?: string
  content: string
  scope?: 'global' | 'project'
  projectId?: number
}

// --- WebSocket 事件 ---

export type WSEventType =
  | 'sandbox.ready'
  | 'message.part.updated'
  | 'session.status'
  | 'permission.request'
  | 'permission.replied'
  | 'session.error'
  | 'session.idle'

export interface SandboxReadyEvent {
  type: 'sandbox.ready'
  sandboxId: number
}

export interface MessagePartUpdatedEvent {
  type: 'message.part.updated'
  sessionId: string
  messageId: string
  part: Part
  delta?: string  // 仅 type=text 时有增量文本
}

export type SessionStatusType = 'busy' | 'idle' | 'retry'

export interface SessionStatusEvent {
  type: 'session.status'
  sessionId: string
  status: { type: SessionStatusType; attempt?: number; delay?: number }
}

export interface PermissionRequestEvent {
  type: 'permission.request'
  requestId: string
  permissionType: string
  title: string
  metadata?: Record<string, unknown>
}

export interface SessionErrorEvent {
  type: 'session.error'
  sessionId: string
  error: string
}

export type WSEvent =
  | SandboxReadyEvent
  | MessagePartUpdatedEvent
  | SessionStatusEvent
  | PermissionRequestEvent
  | SessionErrorEvent

// --- 权限回复 ---

export type PermissionReply = 'once' | 'always' | 'reject'
