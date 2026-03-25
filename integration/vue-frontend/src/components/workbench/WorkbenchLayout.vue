<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useWorkbenchWS } from '@/composables/useWorkbenchWS'
import { useSandboxStore } from '@/stores/sandboxStore'
import { useSessionStore } from '@/stores/sessionStore'
import type { MessagePartUpdatedEvent, SessionStatusEvent, PermissionRequestEvent } from '@/types/workbench'
import SandboxStatusBar from './SandboxStatusBar.vue'
import ChatPanel from './ChatPanel.vue'
import StepLog from './StepLog.vue'
import FileTree from './FileTree.vue'
import PermissionRequest from './PermissionRequest.vue'
import DemoToolbar from './DemoToolbar.vue'

const props = defineProps<{ projectId?: number }>()
const isDev = import.meta.env.DEV

const sandboxStore = useSandboxStore()
const sessionStore = useSessionStore()
const ws = useWorkbenchWS()

// 待处理的权限请求
const permissionRequest = ref<PermissionRequestEvent | null>(null)

onMounted(async () => {
  // 1. 确保沙箱就绪
  await sandboxStore.ensureSandbox(props.projectId)
  // 2. 建立 WS 连接
  ws.connect()
  // 3. 注册事件分发
  ws.on(event => {
    if (event.type === 'sandbox.ready') {
      sessionStore.loadSessions()
    } else if (event.type === 'message.part.updated') {
      const e = event as MessagePartUpdatedEvent
      sessionStore.handlePartUpdated(e.sessionId, e.messageId, e.part, e.delta)
    } else if (event.type === 'session.status') {
      const e = event as SessionStatusEvent
      sessionStore.handleSessionStatus(e.sessionId, e.status.type)
    } else if (event.type === 'permission.request') {
      permissionRequest.value = event as PermissionRequestEvent
    } else if (event.type === 'permission.replied') {
      permissionRequest.value = null
    }
  })
  // 4. 加载会话列表
  await sessionStore.loadSessions()
  if (!sessionStore.activeSessionId && sessionStore.sessions.length > 0) {
    await sessionStore.selectSession(sessionStore.sessions[0].id)
  }
})

async function handlePermissionReply(reply: 'once' | 'always' | 'reject') {
  if (!permissionRequest.value) return
  ws.replyPermission(permissionRequest.value.requestId, reply)
  permissionRequest.value = null
}
</script>

<template>
  <div class="workbench-layout">
    <!-- 顶部状态栏 -->
    <SandboxStatusBar :connected="ws.connected.value" />

    <!-- 权限请求弹窗（覆盖在最上层） -->
    <PermissionRequest
      v-if="permissionRequest"
      :request="permissionRequest"
      @reply="handlePermissionReply"
    />

    <!-- Demo 调试工具栏（开发模式） -->
    <DemoToolbar v-if="isDev" />

    <!-- 三列主布局 -->
    <div class="workbench-body">
      <!-- 左侧：文件树 -->
      <aside class="panel panel--file-tree">
        <FileTree />
      </aside>

      <!-- 中间：对话面板 -->
      <main class="panel panel--chat">
        <ChatPanel />
      </main>

      <!-- 右侧：步骤日志 -->
      <aside class="panel panel--step-log">
        <StepLog />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.workbench-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
.workbench-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #e5e7eb;
}
.panel--file-tree { width: 240px; flex-shrink: 0; }
.panel--chat { flex: 1; }
.panel--step-log { width: 320px; flex-shrink: 0; border-right: none; border-left: 1px solid #e5e7eb; }
</style>
