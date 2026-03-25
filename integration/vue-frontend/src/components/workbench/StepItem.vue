<script setup lang="ts">
import { computed } from 'vue'
import type { Part, ToolPartState } from '@/types/workbench'

const props = defineProps<{ part: Part }>()

const state = computed(() => props.part.state as ToolPartState)

const icon = computed(() => {
  const map: Record<string, string> = {
    pending: '⏳',
    running: '🔄',
    completed: '✅',
    error: '❌',
  }
  return map[state.value.status] ?? '🔧'
})

const isRunning = computed(() => state.value.status === 'running')

/** 工具名 → 可读显示名 */
const toolDisplayName = computed(() => {
  const tool = state.value.tool ?? ''
  if (tool.startsWith('mcp__')) return `MCP: ${tool.replace('mcp__', '')}`
  if (tool.startsWith('skill__')) return `技能: ${tool.replace('skill__', '')}`
  const map: Record<string, string> = {
    bash: '执行命令',
    edit: '编辑文件',
    write: '创建文件',
    read: '读取文件',
    glob: '搜索文件',
    grep: '搜索内容',
    fetch: '获取网页',
    task: '子任务',
  }
  return map[tool] ?? tool
})

const elapsed = computed(() => {
  if (!state.value.time?.start || !state.value.time?.end) return null
  const ms = new Date(state.value.time.end).getTime() - new Date(state.value.time.start).getTime()
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
})
</script>

<template>
  <div class="step-item" :class="`step-item--${state.status}`">
    <!-- 图标 -->
    <span class="step-item__icon">
      <span v-if="isRunning" class="step-item__spinner" />
      <span v-else>{{ icon }}</span>
    </span>

    <!-- 内容 -->
    <div class="step-item__body">
      <span class="step-item__tool">{{ toolDisplayName }}</span>
      <span class="step-item__title">{{ state.title }}</span>
      <span v-if="elapsed" class="step-item__elapsed">{{ elapsed }}</span>
    </div>

    <!-- 错误信息（展开） -->
    <div v-if="state.status === 'error' && state.error" class="step-item__error">
      {{ state.error }}
    </div>
  </div>
</template>

<style scoped>
.step-item {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 4px;
  padding: 3px 0;
  font-size: 12px;
  line-height: 1.5;
}
.step-item__icon { width: 14px; flex-shrink: 0; text-align: center; }
.step-item__spinner {
  display: inline-block;
  width: 10px; height: 10px;
  border: 1.5px solid #3b82f6;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.step-item__body { display: flex; align-items: center; gap: 4px; flex: 1; min-width: 0; overflow: hidden; }
.step-item__tool { color: #6b7280; flex-shrink: 0; }
.step-item__title {
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
.step-item__elapsed { color: #9ca3af; flex-shrink: 0; }
.step-item--error .step-item__title { color: #ef4444; }
.step-item__error {
  width: 100%;
  margin-left: 18px;
  color: #dc2626;
  font-size: 11px;
  background: #fef2f2;
  border-radius: 4px;
  padding: 2px 6px;
}
</style>
