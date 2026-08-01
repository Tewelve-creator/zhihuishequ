<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>审批处理</h2>
        <p class="sub">在线审批待办申请；通过后自动同步住户档案，驳回须填写原因</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="applyType" clearable placeholder="类型" style="width: 140px" @change="onFilter">
        <el-option label="迁入" value="MOVE_IN" />
        <el-option label="迁出" value="MOVE_OUT" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="单号/姓名/手机/房屋"
        style="width: 220px"
        @keyup.enter="onFilter"
      />
      <el-button type="primary" :loading="loading" @click="onFilter">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无待审批申请">
      <el-table-column prop="applyNo" label="申请单号" width="180" />
      <el-table-column prop="name" label="申请人" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="房屋" min-width="180" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="identityLabel" label="身份" width="80" />
      <el-table-column prop="remark" label="申请备注" min-width="120" show-overflow-tooltip />
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :loading="actingId === row.id" @click="onApprove(row)">通过</el-button>
          <el-button link type="danger" :loading="actingId === row.id" @click="onReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveMove, listMoveApplies, rejectMove, type MoveApply } from '@/api/household'

const loading = ref(false)
const list = ref<MoveApply[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const applyType = ref('')
const keyword = ref('')
const actingId = ref<number | null>(null)

function onFilter() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const data = await listMoveApplies({
      status: 'PENDING',
      applyType: applyType.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    list.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function onApprove(row: MoveApply) {
  const { value } = await ElMessageBox.prompt(
    `确认通过「${row.type}」申请？\n${row.name} · ${row.room}\n通过后将同步更新住户信息。`,
    '审批通过',
    {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPlaceholder: '审批意见（可选）',
      inputValue: '',
    },
  ).catch(() => ({ value: null as string | null }))
  if (value === null) return
  actingId.value = row.id
  try {
    await approveMove(row.id, value || undefined)
    ElMessage.success('已通过，住户数据已同步')
    await load()
  } finally {
    actingId.value = null
  }
}

async function onReject(row: MoveApply) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因（必填）', '驳回申请', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '驳回原因',
    inputValidator: (v) => (!!v && String(v).trim() ? true : '请填写驳回原因'),
  }).catch(() => ({ value: null as string | null }))
  if (value === null) return
  actingId.value = row.id
  try {
    await rejectMove(row.id, String(value).trim())
    ElMessage.success('已驳回')
    await load()
  } finally {
    actingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 12px; padding: 20px 24px; }
.page-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.sub { margin: 6px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
