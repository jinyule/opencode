import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import { sessionApi, messageApi } from '@/api/workbench'
import type { Session, Message, Part, SendMessageRequest, SessionStatusType } from '@/types/workbench'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const activeSessionId = ref<string | null>(null)

  // 用 reactive 对象代替 Map，Vue 3 对普通对象属性的响应式追踪更可靠
  const messages = reactive<Record<string, Message[]>>({})
  const sessionStatus = reactive<Record<string, SessionStatusType>>({})

  const busy = computed(() => {
    if (!activeSessionId.value) return false
    return sessionStatus[activeSessionId.value] === 'busy'
  })

  const activeMessages = computed<Message[]>(() => {
    if (!activeSessionId.value) return []
    return messages[activeSessionId.value] ?? []
  })

  async function loadSessions() {
    const res = await sessionApi.list()
    sessions.value = res.data
  }

  async function createSession(projectId?: number) {
    const res = await sessionApi.create(projectId)
    sessions.value.unshift(res.data)
    activeSessionId.value = res.data.id
    messages[res.data.id] = []
    return res.data
  }

  async function selectSession(id: string) {
    activeSessionId.value = id
    if (!messages[id]) await loadMessages(id)
  }

  async function loadMessages(sessionId: string) {
    const res = await messageApi.list(sessionId)
    messages[sessionId] = res.data
  }

  /** 乐观插入用户消息，直接写入 reactive 对象确保响应式生效 */
  function pushUserMessage(text: string) {
    const sessionId = activeSessionId.value
    if (!sessionId) return
    if (!messages[sessionId]) messages[sessionId] = []
    messages[sessionId].push({
      id: `user-${Date.now()}`,
      sessionId,
      role: 'user',
      parts: [{ id: 'p0', type: 'text', state: { text }, time: { created: new Date().toISOString() } }],
      createdAt: new Date().toISOString(),
    })
  }

  async function sendMessage(req: SendMessageRequest) {
    if (!activeSessionId.value) throw new Error('No active session')
    await messageApi.send(activeSessionId.value, req)
    sessionStatus[activeSessionId.value] = 'busy'
  }

  async function abortSession() {
    if (!activeSessionId.value) return
    await sessionApi.abort(activeSessionId.value)
  }

  // --- WS 事件处理 ---

  function handlePartUpdated(sessionId: string, messageId: string, part: Part, delta?: string) {
    // 确保消息数组存在
    if (!messages[sessionId]) messages[sessionId] = []
    const list = messages[sessionId]

    const msgIdx = list.findIndex(m => m.id === messageId)

    if (msgIdx === -1) {
      // 新消息：首个 part 到达时创建
      list.push({
        id: messageId,
        sessionId,
        role: 'assistant',
        parts: [part],
        createdAt: part.time.created,
      })
      return
    }

    const msg = list[msgIdx]
    const partIdx = msg.parts.findIndex(p => p.id === part.id)

    if (partIdx === -1) {
      msg.parts.push(part)
    } else if (delta && part.type === 'text') {
      // 增量追加文本，避免整体替换闪烁
      const existing = msg.parts[partIdx]
      ;(existing.state as { text: string }).text += delta
      existing.time = part.time
    } else {
      msg.parts[partIdx] = part
    }
  }

  function handleSessionStatus(sessionId: string, status: SessionStatusType) {
    sessionStatus[sessionId] = status
    // 注意：idle 时不重新拉取消息，避免覆盖流式渲染的内容
  }

  async function deleteSession(id: string) {
    await sessionApi.delete(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    delete messages[id]
    delete sessionStatus[id]
    if (activeSessionId.value === id) {
      activeSessionId.value = sessions.value[0]?.id ?? null
    }
  }

  return {
    sessions,
    activeSessionId,
    activeMessages,
    sessionStatus,
    busy,
    loadSessions,
    createSession,
    selectSession,
    pushUserMessage,
    sendMessage,
    abortSession,
    deleteSession,
    handlePartUpdated,
    handleSessionStatus,
  }
})
