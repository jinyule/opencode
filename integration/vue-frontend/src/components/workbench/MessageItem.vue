<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import type { Message, Part, TextPartState, ReasoningPartState, ToolPartState, PatchPartState } from '@/types/workbench'

const props = defineProps<{ message: Message }>()

const isUser = computed(() => props.message.role === 'user')

function renderMarkdown(text: string) {
  return marked.parse(text) as string
}

// 只渲染文本和推理 part，其余步骤在 StepLog 中显示
const textParts = computed(() =>
  props.message.parts.filter(p => p.type === 'text' || p.type === 'reasoning')
)

function toolIcon(status: string) {
  const map: Record<string, string> = {
    pending: '⏳',
    running: '🔄',
    completed: '✅',
    error: '❌',
  }
  return map[status] ?? '🔧'
}

function patchSummary(part: Part) {
  const s = part.state as PatchPartState
  return `修改了 ${s.files?.length ?? 0} 个文件（+${s.additions ?? 0} / -${s.deletions ?? 0}）`
}
</script>

<template>
  <div class="message-item" :class="isUser ? 'message-item--user' : 'message-item--assistant'">
    <!-- 用户消息 -->
    <template v-if="isUser">
      <div class="message-item__avatar message-item__avatar--user">You</div>
      <div class="message-item__bubble message-item__bubble--user">
        <template v-for="part in message.parts" :key="part.id">
          <span v-if="part.type === 'text'">{{ (part.state as TextPartState).text }}</span>
        </template>
      </div>
    </template>

    <!-- 助手消息 -->
    <template v-else>
      <div class="message-item__avatar">AI</div>
      <div class="message-item__content">
        <template v-for="part in message.parts" :key="part.id">
          <!-- 文本流式输出 -->
          <div
            v-if="part.type === 'text'"
            class="message-item__text"
            v-html="renderMarkdown((part.state as TextPartState).text)"
          />

          <!-- 推理过程（可折叠） -->
          <details v-else-if="part.type === 'reasoning'" class="message-item__reasoning">
            <summary>思考过程</summary>
            <pre>{{ (part.state as ReasoningPartState).text }}</pre>
          </details>

          <!-- 工具调用（内联简洁版，详细在 StepLog） -->
          <div v-else-if="part.type === 'tool'" class="message-item__tool">
            <span>{{ toolIcon((part.state as ToolPartState).status) }}</span>
            <span class="message-item__tool-title">{{ (part.state as ToolPartState).title }}</span>
          </div>

          <!-- 文件变更摘要 -->
          <div v-else-if="part.type === 'patch'" class="message-item__patch">
            📝 {{ patchSummary(part) }}
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.message-item--user { flex-direction: row-reverse; }
.message-item__avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: #3b82f6;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message-item__avatar--user { background: #6366f1; }
.message-item__bubble--user {
  background: #eff6ff;
  border-radius: 12px 2px 12px 12px;
  padding: 8px 12px;
  font-size: 14px;
  max-width: 70%;
  line-height: 1.6;
}
.message-item__content { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.message-item__text {
  font-size: 14px;
  line-height: 1.7;
  color: #111827;
}
.message-item__text :deep(pre) {
  background: #1f2937;
  color: #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
}
.message-item__text :deep(code) {
  background: #f3f4f6;
  border-radius: 4px;
  padding: 1px 4px;
  font-size: 13px;
}
.message-item__reasoning {
  font-size: 13px;
  color: #6b7280;
  border-left: 2px solid #d1d5db;
  padding-left: 8px;
}
.message-item__reasoning pre { white-space: pre-wrap; word-break: break-word; margin: 4px 0 0; }
.message-item__tool {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}
.message-item__tool-title { color: #374151; }
.message-item__patch {
  font-size: 13px;
  color: #059669;
}
</style>
