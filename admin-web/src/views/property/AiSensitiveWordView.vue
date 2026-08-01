<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>社区敏感词库</h2>
        <p class="sub">本小区自定义词库，与平台词库合并用于公告/活动 AI 审核</p>
      </div>
      <div class="actions">
        <el-input v-model="word" placeholder="新增敏感词" style="width: 200px" @keyup.enter="add" />
        <el-button type="primary" :loading="saving" @click="add">添加</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="list" stripe empty-text="暂无自定义词，将使用平台默认词库">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="160" />
      <el-table-column prop="createTime" label="添加时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addSensitiveWord, deleteSensitiveWord, listSensitiveWords } from '@/api/aiFeature'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const word = ref('')

async function load() {
  loading.value = true
  try {
    list.value = (await listSensitiveWords()) || []
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
    await addSensitiveWord(word.value.trim())
    ElMessage.success('已添加')
    word.value = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`删除敏感词「${row.word}」？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteSensitiveWord(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; align-items: center; }
</style>
