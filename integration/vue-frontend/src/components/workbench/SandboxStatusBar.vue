<script setup lang="ts">
import { computed } from 'vue'
import { useSandboxStore } from '@/stores/sandboxStore'
import { useSessionStore } from '@/stores/sessionStore'

defineProps<{ connected: boolean }>()

const sandboxStore = useSandboxStore()
const sessionStore = useSessionStore()

const statusText = computed(() => {
  const s = sandboxStore.sandbox?.status
  if (!s) return '初始化中...'
  const map: Record<string, string> = {
    CREATING: '创建沙箱...',
    STARTING: '启动中...',
    RUNNING: '运行中',
    IDLE: '空闲',
    SUSPENDING: '挂起中...',
    SUSPENDED: '已挂起',
  }
  return map[s] ?? s
})

const isAgentBusy = computed(() => sessionStore.busy)
</script>

<template>
  <header class="status-bar">
    <div class="status-bar__left">
      <span class="dot" :class="connected ? 'dot--green' : 'dot--red'" />
      <span class="status-bar__label">{{ statusText }}</span>
    </div>
    <div class="status-bar__center">
      <span v-if="isAgentBusy" class="status-bar__busy">
        <span class="spinner" /> Agent 工作中...
      </span>
    </div>
    <div class="status-bar__right">
      <slot name="right" />
    </div>
  </header>
</template>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  height: 36px;
  background: #1f2937;
  color: #d1d5db;
  font-size: 13px;
  flex-shrink: 0;
}
.status-bar__left, .status-bar__center, .status-bar__right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot--green { background: #22c55e; }
.dot--red   { background: #ef4444; }
.status-bar__busy { color: #fbbf24; display: flex; align-items: center; gap: 6px; }
.spinner {
  width: 12px; height: 12px;
  border: 2px solid #fbbf24;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
