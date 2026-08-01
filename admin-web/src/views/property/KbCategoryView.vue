<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>知识库分类</h2>
        <p class="sub">维护知识目录，对各类物业资料进行归类</p>
      </div>
      <div class="ops">
        <el-button v-if="canManage" type="primary" @click="openEdit()">新增分类</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无分类">
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 || row.enabled === true ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 || row.enabled === true ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column v-if="canManage" label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" :title="form.id ? '编辑分类' : '新增分类'" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 policy / fee" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="分类显示名称" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { deleteKbCategory, listKbCategories, saveKbCategory } from '@/api/kbDoc'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const dlg = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  sortNo: 0,
  enabled: 1,
})

async function load() {
  loading.value = true
  try {
    list.value = (await listKbCategories()) || []
  } finally {
    loading.value = false
  }
}

function openEdit(row?: any) {
  form.id = row?.id
  form.code = row?.code || ''
  form.name = row?.name || ''
  form.description = row?.description || ''
  form.sortNo = row?.sortNo ?? 0
  form.enabled = row?.enabled === 0 ? 0 : 1
  dlg.value = true
}

async function submit() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  saving.value = true
  try {
    await saveKbCategory({ ...form })
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示')
  await deleteKbCategory(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; }
</style>
