<template>
  <div class="sensitive-panel">
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索敏感词" class="kw" @keyup.enter="noop" />
      <el-input v-model="word" clearable placeholder="新增敏感词" class="kw" @keyup.enter="add" />
      <el-button type="primary" :loading="saving" @click="add">添加</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb"
      title="接口仅支持词条与创建时间。分类、风险等级、匹配方式、替换策略、启停、批量导入导出暂无后端字段。"
    />
    <el-table v-loading="loading" :data="filtered" empty-text="暂无平台敏感词">
      <el-table-column prop="word" label="敏感词" min-width="160" />
      <el-table-column label="分类" width="100">
        <template #default><span class="muted">—</span></template>
      </el-table-column>
      <el-table-column label="风险等级" width="100">
        <template #default><span class="muted">—</span></template>
      </el-table-column>
      <el-table-column label="匹配方式" width="100">
        <template #default><span class="muted">全文包含</span></template>
      </el-table-column>
      <el-table-column label="替换策略" width="100">
        <template #default><span class="muted">—</span></template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default><span class="pill ok">启用</span></template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ row.createTime || '—' }}</template>
      </el-table-column>
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

function noop() {}

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
  try {
    await ElMessageBox.confirm(
      `删除平台敏感词「${row.word}」后，各社区公告/活动审核将不再命中该词。确认删除？`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deletePlatformSensitiveWord(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.kw {
  width: 200px;
  max-width: 100%;
}
.mb {
  margin-bottom: 12px;
}
.muted {
  color: #667085;
  font-size: 13px;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #027a48;
}
@media (max-width: 768px) {
  .kw {
    width: 100%;
  }
}
</style>
