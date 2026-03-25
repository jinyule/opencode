<script setup lang="ts">
import type { Message } from '@/types/workbench'
import MessageItem from './MessageItem.vue'

defineProps<{ messages: Message[] }>()
</script>

<template>
  <div class="message-list">
    <template v-if="messages.length === 0">
      <div class="message-list__empty">
        发送消息开始对话
      </div>
    </template>
    <MessageItem
      v-for="msg in messages"
      :key="msg.id"
      :message="msg"
    />
  </div>
</template>

<style scoped>
.message-list { display: flex; flex-direction: column; gap: 16px; }
.message-list__empty {
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
  margin-top: 80px;
}
</style>
