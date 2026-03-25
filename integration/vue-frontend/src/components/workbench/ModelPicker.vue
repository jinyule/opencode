<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { providerApi } from '@/api/workbench'
import type { Provider, ModelRef } from '@/types/workbench'

const model = defineModel<ModelRef | null>()

const providers = ref<Provider[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await providerApi.list()
    providers.value = res.data
    // 默认选中第一个可用模型
    if (!model.value && providers.value.length > 0) {
      const first = providers.value[0]
      if (first.models.length > 0) {
        model.value = { providerID: first.id, modelID: first.models[0].id }
      }
    }
  } finally {
    loading.value = false
  }
})

const displayLabel = computed(() => {
  if (!model.value) return '选择模型'
  const provider = providers.value.find(p => p.id === model.value!.providerID)
  const m = provider?.models.find(m => m.id === model.value!.modelID)
  return m?.name ?? model.value.modelID
})

function select(providerID: string, modelID: string) {
  model.value = { providerID, modelID }
  open.value = false
}

const open = ref(false)
</script>

<template>
  <div class="model-picker">
    <button class="model-picker__trigger" @click="open = !open">
      <span class="model-picker__label">{{ displayLabel }}</span>
      <span>{{ open ? '▲' : '▼' }}</span>
    </button>

    <div v-if="open" class="model-picker__dropdown">
      <div v-if="loading" class="model-picker__loading">加载中...</div>
      <template v-else>
        <div
          v-for="provider in providers"
          :key="provider.id"
          class="model-picker__group"
        >
          <div class="model-picker__group-label">{{ provider.name }}</div>
          <button
            v-for="m in provider.models"
            :key="m.id"
            class="model-picker__option"
            :class="{ 'model-picker__option--active': model?.modelID === m.id && model?.providerID === provider.id }"
            @click="select(provider.id, m.id)"
          >
            {{ m.name }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.model-picker { position: relative; }
.model-picker__trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.model-picker__trigger:hover { background: #e5e7eb; }
.model-picker__label { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-picker__dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  min-width: 220px;
  max-height: 320px;
  overflow-y: auto;
  z-index: 100;
  padding: 4px;
}
.model-picker__loading { padding: 12px; text-align: center; color: #9ca3af; font-size: 13px; }
.model-picker__group { margin-bottom: 4px; }
.model-picker__group-label {
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.model-picker__option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  font-size: 13px;
  color: #374151;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.model-picker__option:hover { background: #f3f4f6; }
.model-picker__option--active { background: #eff6ff; color: #1d4ed8; font-weight: 500; }
</style>
