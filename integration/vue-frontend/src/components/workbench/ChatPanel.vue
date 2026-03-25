<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import ModelPicker from './ModelPicker.vue'
import MessageList from './MessageList.vue'
import type { ModelRef } from '@/types/workbench'

const sessionStore = useSessionStore()
const inputText = ref('')
const selectedModel = ref<ModelRef | null>(null)
const listRef = ref<HTMLElement | null>(null)

const canSend = computed(() => inputText.value.trim().length > 0 && !sessionStore.busy)

// 消息更新后自动滚动到底部
watch(
  () => sessionStore.activeMessages.length,
  () => nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
)

async function send() {
  const text = inputText.value.trim()
  if (!text || sessionStore.busy || !sessionStore.activeSessionId) return
  inputText.value = ''

  // 乐观更新：通过 store 方法写入，保证响应式
  sessionStore.pushUserMessage(text)

  await sessionStore.sendMessage({
    parts: [{ type: 'text', text }],
    model: selectedModel.value ?? undefined,
    agent: 'build',
  })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function newSession() {
  await sessionStore.createSession()
}
</script>

<template>
  <div class="chat-panel">
    <!-- 会话列表侧边栏（折叠式，简化版） -->
    <div class="chat-panel__sessions">
      <button class="btn-new" @click="newSession">+ 新对话</button>
      <ul class="session-list">
        <li
          v-for="s in sessionStore.sessions"
          :key="s.id"
          class="session-item"
          :class="{ 'session-item--active': s.id === sessionStore.activeSessionId }"
          @click="sessionStore.selectSession(s.id)"
        >
          {{ s.title || '新对话' }}
        </li>
      </ul>
    </div>

    <!-- 消息区域 -->
    <div class="chat-panel__main">
      <div ref="listRef" class="chat-panel__messages">
        <MessageList :messages="sessionStore.activeMessages" />
      </div>

      <!-- 输入区域 -->
      <div class="chat-panel__input-area">
        <div class="chat-panel__toolbar">
          <ModelPicker v-model="selectedModel" />
        </div>
        <div class="chat-panel__input-row">
          <textarea
            v-model="inputText"
            class="chat-panel__textarea"
            placeholder="输入消息，Shift+Enter 换行，Enter 发送..."
            rows="3"
            @keydown="onKeydown"
          />
          <div class="chat-panel__actions">
            <button
              v-if="sessionStore.busy"
              class="btn btn--abort"
              @click="sessionStore.abortSession"
            >
              停止
            </button>
            <button
              v-else
              class="btn btn--send"
              :disabled="!canSend"
              @click="send"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  height: 100%;
  overflow: hidden;
}
.chat-panel__sessions {
  width: 180px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 8px;
  overflow-y: auto;
}
.btn-new {
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 13px;
}
.session-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 2px; }
.session-item {
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item:hover { background: #f3f4f6; }
.session-item--active { background: #eff6ff; color: #1d4ed8; font-weight: 500; }
.chat-panel__main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.chat-panel__messages { flex: 1; overflow-y: auto; padding: 16px; }
.chat-panel__input-area {
  border-top: 1px solid #e5e7eb;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chat-panel__toolbar { display: flex; align-items: center; gap: 8px; }
.chat-panel__input-row { display: flex; gap: 8px; }
.chat-panel__textarea {
  flex: 1;
  resize: none;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  outline: none;
}
.chat-panel__textarea:focus { border-color: #3b82f6; }
.chat-panel__actions { display: flex; flex-direction: column; justify-content: flex-end; }
.btn { padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; }
.btn--send { background: #3b82f6; color: #fff; }
.btn--send:disabled { background: #93c5fd; cursor: not-allowed; }
.btn--abort { background: #ef4444; color: #fff; }
</style>
