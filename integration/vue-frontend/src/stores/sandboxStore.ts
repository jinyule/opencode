import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sandboxApi } from '@/api/workbench'
import type { SandboxInstance } from '@/types/workbench'

export const useSandboxStore = defineStore('sandbox', () => {
  const sandbox = ref<SandboxInstance | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function ensureSandbox(projectId?: number) {
    if (sandbox.value?.status === 'RUNNING') return sandbox.value
    loading.value = true
    error.value = null
    try {
      const res = await sandboxApi.create(projectId)
      sandbox.value = res.data
      return res.data
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '沙箱创建失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function resumeSandbox(id: number) {
    const res = await sandboxApi.resume(id)
    sandbox.value = res.data
  }

  async function suspendSandbox() {
    if (!sandbox.value) return
    await sandboxApi.suspend(sandbox.value.id)
    sandbox.value.status = 'SUSPENDING'
  }

  function setSandboxFromEvent(instance: Partial<SandboxInstance>) {
    if (sandbox.value) Object.assign(sandbox.value, instance)
  }

  return { sandbox, loading, error, ensureSandbox, resumeSandbox, suspendSandbox, setSandboxFromEvent }
})
