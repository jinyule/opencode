import { defineStore } from 'pinia'
import { ref } from 'vue'
import { skillApi } from '@/api/workbench'
import type { Skill, CreateSkillRequest } from '@/types/workbench'

export const useSkillStore = defineStore('skill', () => {
  const skills = ref<Skill[]>([])
  const loading = ref(false)

  async function loadSkills() {
    loading.value = true
    try {
      const res = await skillApi.list()
      skills.value = res.data
    } finally {
      loading.value = false
    }
  }

  async function createSkill(req: CreateSkillRequest) {
    const res = await skillApi.create(req)
    skills.value.unshift(res.data)
    return res.data
  }

  async function updateSkill(name: string, description: string, content: string) {
    const res = await skillApi.update(name, description, content)
    const idx = skills.value.findIndex(s => s.name === name)
    if (idx !== -1) skills.value[idx] = res.data
    return res.data
  }

  async function deleteSkill(name: string) {
    await skillApi.delete(name)
    skills.value = skills.value.filter(s => s.name !== name)
  }

  return { skills, loading, loadSkills, createSkill, updateSkill, deleteSkill }
})
