<script setup lang="ts">
import WorkbenchLayout from '@/components/workbench/WorkbenchLayout.vue'

// projectId 可从路由参数或全局状态中注入，此处用 undefined 表示单沙箱模式
const projectId = undefined
</script>

<template>
  <WorkbenchLayout :project-id="projectId" />
</template>

<style>
*, *::before, *::after { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
</style>
