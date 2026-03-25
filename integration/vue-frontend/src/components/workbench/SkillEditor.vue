<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSkillStore } from '@/stores/skillStore'
import type { Skill, CreateSkillRequest } from '@/types/workbench'

const skillStore = useSkillStore()
onMounted(() => skillStore.loadSkills())

const editing = ref<Skill | null>(null)
const creating = ref(false)

const form = ref<CreateSkillRequest>({
  name: '',
  description: '',
  content: '',
  scope: 'project',
})

function startCreate() {
  creating.value = true
  editing.value = null
  form.value = { name: '', description: '', content: '', scope: 'project' }
}

function startEdit(skill: Skill) {
  editing.value = skill
  creating.value = false
  form.value = {
    name: skill.name,
    description: skill.description ?? '',
    content: skill.content,
    scope: skill.scope,
    projectId: skill.projectId,
  }
}

function cancel() {
  editing.value = null
  creating.value = false
}

async function save() {
  if (creating.value) {
    await skillStore.createSkill(form.value)
  } else if (editing.value) {
    await skillStore.updateSkill(editing.value.name, form.value.description ?? '', form.value.content)
  }
  cancel()
}

async function remove(skill: Skill) {
  if (confirm(`确定删除技能「${skill.name}」吗？`)) {
    await skillStore.deleteSkill(skill.name)
  }
}
</script>

<template>
  <div class="skill-editor">
    <div class="skill-editor__header">
      <span>技能管理</span>
      <button class="btn btn--primary" @click="startCreate">+ 新建技能</button>
    </div>

    <!-- 技能列表 -->
    <div v-if="!creating && !editing" class="skill-editor__list">
      <div v-if="skillStore.loading" class="skill-editor__loading">加载中...</div>
      <div
        v-for="skill in skillStore.skills"
        :key="skill.id"
        class="skill-card"
      >
        <div class="skill-card__info">
          <div class="skill-card__name">{{ skill.name }}</div>
          <div class="skill-card__desc">{{ skill.description }}</div>
          <div class="skill-card__scope">{{ skill.scope === 'global' ? '全局' : '项目' }}</div>
        </div>
        <div class="skill-card__actions">
          <button class="btn btn--ghost" @click="startEdit(skill)">编辑</button>
          <button class="btn btn--danger" @click="remove(skill)">删除</button>
        </div>
      </div>
      <div v-if="skillStore.skills.length === 0 && !skillStore.loading" class="skill-editor__empty">
        暂无技能，点击「新建技能」创建
      </div>
    </div>

    <!-- 新建 / 编辑表单 -->
    <div v-else class="skill-editor__form">
      <div class="form-field">
        <label>技能名称 <span class="form-hint">（仅字母、数字、连字符）</span></label>
        <input
          v-model="form.name"
          :disabled="!!editing"
          class="form-input"
          placeholder="如: text-statistics"
        />
      </div>
      <div class="form-field">
        <label>描述</label>
        <input v-model="form.description" class="form-input" placeholder="技能的用途说明" />
      </div>
      <div class="form-field">
        <label>范围</label>
        <select v-model="form.scope" class="form-input" :disabled="!!editing">
          <option value="project">项目级</option>
          <option value="global">全局</option>
        </select>
      </div>
      <div class="form-field form-field--grow">
        <label>技能指令内容 <span class="form-hint">（Markdown 格式）</span></label>
        <textarea
          v-model="form.content"
          class="form-input form-input--textarea"
          placeholder="当用户请求...时，执行以下步骤：&#10;1. ...&#10;2. ..."
          rows="12"
        />
      </div>
      <div class="form-actions">
        <button class="btn btn--ghost" @click="cancel">取消</button>
        <button class="btn btn--primary" @click="save">保存</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skill-editor { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
.skill-editor__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
  font-weight: 600;
  font-size: 14px;
}
.skill-editor__list { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
.skill-editor__loading, .skill-editor__empty { color: #9ca3af; font-size: 13px; text-align: center; padding: 24px; }
.skill-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fafafa;
}
.skill-card__name { font-weight: 600; font-size: 14px; color: #111827; }
.skill-card__desc { font-size: 13px; color: #6b7280; margin-top: 2px; }
.skill-card__scope {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
}
.skill-card__actions { display: flex; gap: 6px; flex-shrink: 0; }
.skill-editor__form { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.form-field { display: flex; flex-direction: column; gap: 4px; }
.form-field--grow { flex: 1; }
.form-field label { font-size: 13px; font-weight: 500; color: #374151; }
.form-hint { font-weight: 400; color: #9ca3af; }
.form-input {
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 13px;
  outline: none;
}
.form-input:focus { border-color: #3b82f6; }
.form-input:disabled { background: #f3f4f6; color: #9ca3af; }
.form-input--textarea { resize: vertical; flex: 1; font-family: monospace; line-height: 1.6; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; }
.btn { padding: 6px 14px; border-radius: 6px; border: none; cursor: pointer; font-size: 13px; }
.btn--primary { background: #3b82f6; color: #fff; }
.btn--primary:hover { background: #2563eb; }
.btn--ghost { background: none; border: 1px solid #d1d5db; color: #374151; }
.btn--ghost:hover { background: #f3f4f6; }
.btn--danger { background: none; border: 1px solid #fca5a5; color: #ef4444; }
.btn--danger:hover { background: #fef2f2; }
</style>
