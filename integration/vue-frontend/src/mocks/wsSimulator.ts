/**
 * Mock WebSocket 模拟器
 *
 * 替换 window.WebSocket，在 Demo 模式下：
 * - 自动触发 onopen
 * - 提供 pushEvent() 向前端推送 WS 事件
 * - 接收前端 send()（权限回复等），做简单处理
 */

type EventHandler = (event: MessageEvent) => void

class MockWebSocket {
  onopen: ((e: Event) => void) | null = null
  onmessage: EventHandler | null = null
  onclose: ((e: CloseEvent) => void) | null = null
  onerror: ((e: Event) => void) | null = null
  readyState: number = 0 // CONNECTING

  constructor(_url: string) {
    // 100ms 后模拟连接成功
    setTimeout(() => {
      this.readyState = 1 // OPEN
      this.onopen?.(new Event('open'))
      // 通知模拟器已就绪
      _activeMockWS = this
    }, 100)
  }

  send(data: string) {
    try {
      const msg = JSON.parse(data)
      // 前端发来权限回复：自动推送 permission.replied
      if (msg.type === 'permission.reply') {
        setTimeout(() => {
          this._push({ type: 'permission.replied', requestId: msg.requestId })
        }, 300)
      }
    } catch { /* ignore */ }
  }

  close() {
    this.readyState = 3 // CLOSED
    this.onclose?.(new CloseEvent('close'))
    _activeMockWS = null
  }

  /** 向前端推送一条 WS 事件 */
  _push(event: object) {
    if (this.readyState !== 1) return
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(event) }))
  }
}

// 当前活跃的 Mock WS 实例
let _activeMockWS: MockWebSocket | null = null

/** 获取当前 Mock WS 实例（供模拟器推送使用） */
export function getActiveMockWS() {
  return _activeMockWS
}

/** 注入 Mock WebSocket，替换全局 window.WebSocket */
export function installMockWS() {
  // @ts-expect-error 替换全局 WebSocket
  window.WebSocket = MockWebSocket
  console.info('[Mock] WebSocket 已替换为 MockWebSocket')
}

// ============================================================
// AI 回复模拟器：发送消息后推送流式事件序列
// ============================================================

/** 在 sessionId 下模拟一次 AI 流式回复 */
export async function simulateAIResponse(sessionId: string) {
  const ws = getActiveMockWS()
  if (!ws) return

  const msgId = `msg-live-${Date.now()}`
  const partTextId = `pt-text-${Date.now()}`
  const partTool1 = `pt-tool1-${Date.now()}`
  const partTool2 = `pt-tool2-${Date.now()}`
  const partTool3 = `pt-tool3-${Date.now()}`

  const push = (event: object, delay: number) =>
    new Promise<void>(resolve => setTimeout(() => { ws._push(event); resolve() }, delay))

  // 1. session busy
  await push({ type: 'session.status', sessionId, status: { type: 'busy' } }, 200)

  // 2. step-start
  await push({
    type: 'message.part.updated',
    sessionId, messageId: msgId,
    part: { id: 'ps1', type: 'step-start', state: {}, time: { created: new Date().toISOString() } },
  }, 100)

  // 3. 工具 1：glob 搜索文件（pending → running → completed）
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool1, type: 'tool', state: { status: 'pending', tool: 'glob', title: '准备搜索文件...' }, time: { created: new Date().toISOString() } },
  }, 200)
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool1, type: 'tool', state: { status: 'running', tool: 'glob', title: '搜索文件 src/**/*.ts', time: { start: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 400)
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool1, type: 'tool', state: { status: 'completed', tool: 'glob', title: '搜索文件 src/**/*.ts', output: '找到 12 个文件', time: { start: new Date().toISOString(), end: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 600)

  // 4. 工具 2：read 读取文件
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool2, type: 'tool', state: { status: 'running', tool: 'read', title: '读取文件 src/main.ts', time: { start: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 300)
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool2, type: 'tool', state: { status: 'completed', tool: 'read', title: '读取文件 src/main.ts', time: { start: new Date().toISOString(), end: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 500)

  // 5. 工具 3：bash 执行命令
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool3, type: 'tool', state: { status: 'running', tool: 'bash', title: '执行命令 bun typecheck', time: { start: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 200)
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: partTool3, type: 'tool', state: { status: 'completed', tool: 'bash', title: '执行命令 bun typecheck', output: '0 errors, 0 warnings', time: { start: new Date().toISOString(), end: new Date().toISOString() } }, time: { created: new Date().toISOString() } },
  }, 800)

  // 6. step-finish
  await push({
    type: 'message.part.updated', sessionId, messageId: msgId,
    part: { id: 'psf', type: 'step-finish', state: { tokens: { input: 1200, output: 420 }, cost: 0.003 }, time: { created: new Date().toISOString() } },
  }, 200)

  // 7. 流式文本回复（逐段推送，模拟打字机效果）
  const responseChunks = [
    '根据代码分析，',
    '项目结构清晰，',
    '共 **12 个 TypeScript 文件**。\n\n',
    '### 主要发现\n\n',
    '- `src/main.ts` 是入口文件，负责应用启动\n',
    '- 类型检查通过，无编译错误\n',
    '- 建议考虑添加单元测试覆盖核心逻辑\n',
  ]

  let accumulated = ''
  for (const chunk of responseChunks) {
    accumulated += chunk
    await push({
      type: 'message.part.updated', sessionId, messageId: msgId,
      part: { id: partTextId, type: 'text', state: { text: accumulated }, time: { created: new Date().toISOString(), updated: new Date().toISOString() } },
      delta: chunk,
    }, 150)
  }

  // 8. session idle
  await push({ type: 'session.status', sessionId, status: { type: 'idle' } }, 300)
}

/** 模拟触发一次权限请求（用于 Demo 演示） */
export function simulatePermissionRequest() {
  const ws = getActiveMockWS()
  if (!ws) return
  ws._push({
    type: 'permission.request',
    requestId: `perm-${Date.now()}`,
    permissionType: 'bash',
    title: '执行命令: rm -rf dist/',
    metadata: { command: 'rm -rf dist/', cwd: '/workspace' },
  })
}
