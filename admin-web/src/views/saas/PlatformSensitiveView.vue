<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>敏感词库管理</h2>
        <p class="sub">平台全局敏感词，下发至各社区公告/活动 AI 审核合并使用</p>
      </div>
      <div class="actions">
        <el-input v-model="word" placeholder="新增敏感词" style="width: 200px" @keyup.enter="add" />
        <el-button type="primary" :loading="saving" @click="add">添加</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-tag type="info" effect="plain">共 {{ list.length }} 词</el-tag>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe empty-text="暂无平台敏感词">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="180" />
      <el-table-column prop="createTime" label="添加时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addPlatformSensitiveWord,
  deletePlatformSensitiveWord,
  listPlatformSensitiveWords,
} from '@/api/saas'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const word = ref('')
const keyword = ref('')

const filtered = computed(() => {
  const k = keyword.value.trim()
  if (!k) return list.value
  return list.value.filter((r) => String(r.word || '').includes(k))
})

async function load() {
  loading.value = true
  try {
    list.value = (await listPlatformSensitiveWords()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!word.value.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  saving.value = true
  try {
    await addPlatformSensitiveWord(word.value.trim())
    ElMessage.success('已添加')
    word.value = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除平台敏感词「${row.word}」？`, '确认', { type: 'warning' })
  await deletePlatformSensitiveWord(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; align-items: center; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
</style>
