<script setup lang="ts">
import type { PermissionRequestEvent, PermissionReply } from '@/types/workbench'

defineProps<{ request: PermissionRequestEvent }>()
const emit = defineEmits<{ reply: [reply: PermissionReply] }>()
</script>

<template>
  <div class="perm-overlay">
    <div class="perm-dialog">
      <div class="perm-dialog__icon">⚠️</div>
      <div class="perm-dialog__title">Agent 请求权限</div>
      <div class="perm-dialog__body">
        <div class="perm-dialog__label">操作类型</div>
        <div class="perm-dialog__value">{{ request.permissionType }}</div>
        <div class="perm-dialog__label">操作说明</div>
        <div class="perm-dialog__value">{{ request.title }}</div>

        <!-- 附加元数据 -->
        <template v-if="request.metadata && Object.keys(request.metadata).length > 0">
          <div class="perm-dialog__label">详情</div>
          <pre class="perm-dialog__meta">{{ JSON.stringify(request.metadata, null, 2) }}</pre>
        </template>
      </div>
      <div class="perm-dialog__actions">
        <button class="btn btn--reject" @click="emit('reply', 'reject')">拒绝</button>
        <button class="btn btn--once" @click="emit('reply', 'once')">允许一次</button>
        <button class="btn btn--always" @click="emit('reply', 'always')">始终允许</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.perm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.perm-dialog {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
  padding: 24px;
  width: 420px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.perm-dialog__icon { font-size: 28px; text-align: center; }
.perm-dialog__title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  text-align: center;
}
.perm-dialog__body { display: flex; flex-direction: column; gap: 6px; }
.perm-dialog__label { font-size: 11px; font-weight: 600; color: #9ca3af; text-transform: uppercase; letter-spacing: 0.05em; }
.perm-dialog__value { font-size: 14px; color: #374151; }
.perm-dialog__meta {
  background: #f3f4f6;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
  color: #374151;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.perm-dialog__actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }
.btn { padding: 8px 16px; border-radius: 6px; border: none; cursor: pointer; font-size: 13px; font-weight: 500; }
.btn--reject { background: #f3f4f6; color: #374151; }
.btn--reject:hover { background: #e5e7eb; }
.btn--once { background: #eff6ff; color: #1d4ed8; }
.btn--once:hover { background: #dbeafe; }
.btn--always { background: #3b82f6; color: #fff; }
.btn--always:hover { background: #2563eb; }
</style>
