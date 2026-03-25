<script setup lang="ts">
/**
 * Demo 调试工具栏（仅开发模式可见）
 * 提供快捷按钮模拟各类 WS 事件，方便 Demo 演示
 */
import { simulatePermissionRequest } from '@/mocks/wsSimulator'
import { useSessionStore } from '@/stores/sessionStore'

const sessionStore = useSessionStore()

function triggerPermission() {
  simulatePermissionRequest()
}
</script>

<template>
  <div v-if="true" class="demo-toolbar">
    <span class="demo-toolbar__label">Demo</span>
    <button class="demo-btn" @click="triggerPermission">模拟权限请求</button>
  </div>
</template>

<style scoped>
.demo-toolbar {
  position: fixed;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #1f2937;
  color: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3);
}
.demo-toolbar__label {
  font-weight: 700;
  color: #fbbf24;
  margin-right: 4px;
}
.demo-btn {
  background: #374151;
  color: #d1d5db;
  border: none;
  border-radius: 4px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 11px;
}
.demo-btn:hover { background: #4b5563; }
</style>
