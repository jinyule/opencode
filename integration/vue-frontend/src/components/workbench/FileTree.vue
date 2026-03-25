<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fileApi } from '@/api/workbench'
import FileTreeNode from './FileTreeNode.vue'

const props = defineProps<{ projectId?: number }>()
const emit = defineEmits<{ fileOpen: [path: string] }>()

interface FileNode {
  name: string
  path: string
  type: 'file' | 'directory'
  children?: FileNode[]
}

const tree = ref<FileNode[]>([])
const expanded = ref<Set<string>>(new Set())
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(() => loadTree('.'))

async function loadTree(path: string) {
  loading.value = true
  error.value = null
  try {
    const res = await fileApi.tree(path, props.projectId)
    const data = res.data as { children?: FileNode[] }
    tree.value = data?.children ?? []
    // 默认展开根节点下的第一层目录
    tree.value.forEach(n => { if (n.type === 'directory') expanded.value.add(n.path) })
  } catch {
    error.value = '加载文件树失败'
  } finally {
    loading.value = false
  }
}

function toggleDir(path: string) {
  if (expanded.value.has(path)) {
    expanded.value.delete(path)
  } else {
    expanded.value.add(path)
  }
  // 触发响应式更新（Set 不触发 reactivity，需要替换引用）
  expanded.value = new Set(expanded.value)
}
</script>

<template>
  <div class="file-tree">
    <div class="file-tree__header">
      <span>文件树</span>
      <button class="file-tree__refresh" title="刷新" @click="loadTree('.')">↻</button>
    </div>

    <div v-if="loading" class="file-tree__hint">加载中...</div>
    <div v-else-if="error" class="file-tree__hint file-tree__hint--error">{{ error }}</div>
    <div v-else class="file-tree__body">
      <FileTreeNode
        v-for="node in tree"
        :key="node.path"
        :node="node"
        :expanded="expanded"
        :depth="0"
        @toggle="toggleDir"
        @open="emit('fileOpen', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.file-tree { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
.file-tree__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.file-tree__refresh { background: none; border: none; cursor: pointer; color: #9ca3af; font-size: 14px; padding: 2px; }
.file-tree__refresh:hover { color: #111827; }
.file-tree__hint { padding: 16px; font-size: 13px; color: #9ca3af; text-align: center; }
.file-tree__hint--error { color: #ef4444; }
.file-tree__body { flex: 1; overflow-y: auto; padding: 4px 0; }
</style>
