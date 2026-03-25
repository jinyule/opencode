<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import StepItem from './StepItem.vue'
import type { Part, ToolPartState } from '@/types/workbench'

const sessionStore = useSessionStore()

/**
 * 将当前会话的消息 parts 整理成步骤组（step-start / step-finish 为分组边界）。
 */
interface StepGroup {
  index: number
  parts: Part[]
  finished: boolean
}

const stepGroups = computed<StepGroup[]>(() => {
  const groups: StepGroup[] = []
  let current: StepGroup | null = null

  for (const msg of sessionStore.activeMessages) {
    if (msg.role !== 'assistant') continue
    for (const part of msg.parts) {
      if (part.type === 'step-start') {
        current = { index: groups.length + 1, parts: [], finished: false }
        groups.push(current)
      } else if (part.type === 'step-finish') {
        if (current) current.finished = true
        current = null
      } else if (part.type === 'tool' && current) {
        current.parts.push(part)
      }
    }
  }
  return groups
})

function groupStatus(group: StepGroup) {
  const hasRunning = group.parts.some(p => (p.state as ToolPartState).status === 'running')
  const hasError = group.parts.some(p => (p.state as ToolPartState).status === 'error')
  if (hasError) return 'error'
  if (hasRunning) return 'running'
  if (group.finished) return 'finished'
  return 'pending'
}
</script>

<template>
  <div class="step-log">
    <div class="step-log__header">步骤日志</div>
    <div class="step-log__body">
      <template v-if="stepGroups.length === 0">
        <div class="step-log__empty">等待 Agent 执行...</div>
      </template>
      <div
        v-for="group in stepGroups"
        :key="group.index"
        class="step-group"
      >
        <!-- 步骤组标题（可折叠） -->
        <details open class="step-group__details">
          <summary class="step-group__summary">
            <span class="step-group__index">Step {{ group.index }}</span>
            <span v-if="groupStatus(group) === 'running'" class="step-group__spinner" />
            <span v-else-if="groupStatus(group) === 'error'" class="step-group__icon">❌</span>
            <span v-else-if="groupStatus(group) === 'finished'" class="step-group__icon">✅</span>
          </summary>
          <div class="step-group__items">
            <StepItem
              v-for="part in group.parts"
              :key="part.id"
              :part="part"
            />
          </div>
        </details>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step-log {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.step-log__header {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.step-log__body { flex: 1; overflow-y: auto; padding: 8px; }
.step-log__empty { color: #9ca3af; font-size: 13px; padding: 20px; text-align: center; }
.step-group { margin-bottom: 4px; }
.step-group__details { border-radius: 6px; background: #f9fafb; }
.step-group__summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  user-select: none;
  list-style: none;
}
.step-group__summary::-webkit-details-marker { display: none; }
.step-group__index { color: #6b7280; }
.step-group__icon { font-size: 12px; }
.step-group__spinner {
  width: 12px; height: 12px;
  border: 2px solid #3b82f6;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}
@keyframes spin { to { transform: rotate(360deg); } }
.step-group__items { padding: 0 8px 6px 20px; display: flex; flex-direction: column; gap: 2px; }
</style>
