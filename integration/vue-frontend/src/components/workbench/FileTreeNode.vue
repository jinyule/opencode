<script setup lang="ts">
interface FileNode {
  name: string
  path: string
  type: 'file' | 'directory'
  children?: FileNode[]
}

const props = defineProps<{
  node: FileNode
  expanded: Set<string>
  depth?: number
}>()

const emit = defineEmits<{
  toggle: [path: string]
  open: [path: string]
}>()

const depth = props.depth ?? 0
const isDir = props.node.type === 'directory'
const isOpen = props.expanded.has(props.node.path)

function handleClick() {
  if (isDir) {
    emit('toggle', props.node.path)
  } else {
    emit('open', props.node.path)
  }
}
</script>

<template>
  <div class="fn-node">
    <div
      class="fn-node__row"
      :class="isDir ? 'fn-node__row--dir' : 'fn-node__row--file'"
      :style="{ paddingLeft: `${8 + depth * 12}px` }"
      @click="handleClick"
    >
      <span class="fn-node__icon">
        <template v-if="isDir">{{ isOpen ? '▼' : '▶' }}</template>
        <template v-else>·</template>
      </span>
      <span class="fn-node__name">{{ node.name }}</span>
    </div>

    <!-- 递归展开子节点 -->
    <template v-if="isDir && isOpen && node.children?.length">
      <FileTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :expanded="expanded"
        :depth="depth + 1"
        @toggle="emit('toggle', $event)"
        @open="emit('open', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
.fn-node__row {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.fn-node__row:hover { background: #f3f4f6; }
.fn-node__icon { width: 14px; text-align: center; color: #9ca3af; font-size: 10px; flex-shrink: 0; }
.fn-node__name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fn-node__row--dir .fn-node__name { font-weight: 500; }
</style>
