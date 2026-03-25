import { ref, onUnmounted } from 'vue'
import type { WSEvent, PermissionReply } from '@/types/workbench'

type EventHandler = (event: WSEvent) => void

/**
 * 编程工作台 WebSocket 连接管理。
 *
 * 功能：
 * - 自动携带 JWT token（query param）
 * - 断线后指数退避重连（最长 30s）
 * - 事件分发到注册的 handler
 */
export function useWorkbenchWS() {
  const connected = ref(false)
  const handlers = new Set<EventHandler>()

  let ws: WebSocket | null = null
  let retryDelay = 1000
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let stopped = false

  function connect() {
    if (stopped) return
    const token = localStorage.getItem('token') ?? ''
    const url = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/workbench?token=${token}`
    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      retryDelay = 1000
    }

    ws.onmessage = (e) => {
      try {
        const event: WSEvent = JSON.parse(e.data)
        handlers.forEach(h => h(event))
      } catch {
        // 忽略非 JSON 帧
      }
    }

    ws.onclose = () => {
      connected.value = false
      if (!stopped) scheduleRetry()
    }

    ws.onerror = () => ws?.close()
  }

  function scheduleRetry() {
    retryTimer = setTimeout(() => {
      retryDelay = Math.min(retryDelay * 2, 30_000)
      connect()
    }, retryDelay)
  }

  function disconnect() {
    stopped = true
    if (retryTimer) clearTimeout(retryTimer)
    ws?.close()
    ws = null
  }

  function send(type: string, payload: Record<string, unknown>) {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type, ...payload }))
    }
  }

  /** 回复权限请求（通过 WS 发送给后端） */
  function replyPermission(requestId: string, reply: PermissionReply) {
    send('permission.reply', { requestId, reply })
  }

  function on(handler: EventHandler) {
    handlers.add(handler)
    return () => handlers.delete(handler)
  }

  onUnmounted(disconnect)

  return { connected, connect, disconnect, on, replyPermission }
}
