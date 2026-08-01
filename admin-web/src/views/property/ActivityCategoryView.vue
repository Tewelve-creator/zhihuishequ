<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>活动分类</h2>
        <p class="sub">维护活动类目与默认标签，供发布选择与 AI 定向匹配；按小区隔离</p>
      </div>
      <el-button type="primary" @click="open()">新增分类</el-button>
    </div>

    <div class="filter-bar">
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无分类">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="分类名称" min-width="140" />
      <el-table-column prop="tags" label="默认标签" min-width="160" show-overflow-tooltip />
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑分类' : '新增分类'" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="32" placeholder="如 亲子活动" />
        </el-form-item>
        <el-form-item label="默认标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，如 亲子,儿童" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteActivityCategory,
  listActivityCategories,
  saveActivityCategory,
} from '@/api/activity'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const visible = ref(false)
const enabled = ref(true)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  tags: '',
  sortNo: 0,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    list.value = (await listActivityCategories()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function open(row?: any) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name || '',
      tags: row.tags || '',
      sortNo: row.sortNo || 0,
      remark: row.remark || '',
    })
    enabled.value = row.status !== 0
  } else {
    Object.assign(form, { id: undefined, name: '', tags: '', sortNo: 0, remark: '' })
    enabled.value = true
  }
  visible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('请填写分类名称')
    return
  }
  saving.value = true
  try {
    await saveActivityCategory({
      id: form.id,
      name: form.name.trim(),
      tags: form.tags.trim(),
      sortNo: form.sortNo,
      status: enabled.value ? 1 : 0,
      remark: form.remark || undefined,
    })
    visible.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '删除', { type: 'warning' })
  await deleteActivityCategory(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.filter-bar { margin-bottom: 12px; }
</style>
