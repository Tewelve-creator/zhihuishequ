<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>申请列表</h2>
        <p class="sub">统一查看居民 App 提交的迁入 / 迁出申请单据（按小区隔离）</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 140px" @change="onFilter">
        <el-option label="待审批" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
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

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无申请">
      <el-table-column prop="applyNo" label="申请单号" width="180" />
      <el-table-column prop="name" label="申请人" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="房屋" min-width="180" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="identityLabel" label="身份" width="80" />
      <el-table-column prop="statusLabel" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="申请备注" min-width="120" show-overflow-tooltip />
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
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

    <el-dialog v-model="dlg" title="申请详情" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="单号">{{ cur?.applyNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ cur?.type }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ cur?.name }} / {{ cur?.phone }}</el-descriptions-item>
        <el-descriptions-item label="房屋">{{ cur?.room }}</el-descriptions-item>
        <el-descriptions-item label="身份">{{ cur?.identityLabel }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ cur?.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="申请备注">{{ cur?.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ cur?.auditName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ cur?.auditRemark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ cur?.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ cur?.createTime }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="cur?.status === 'PENDING'" #footer>
        <el-button type="primary" @click="goApprove">去审批处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMoveApplies, type MoveApply } from '@/api/household'

const router = useRouter()
const loading = ref(false)
const list = ref<MoveApply[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const applyType = ref('')
const keyword = ref('')
const dlg = ref(false)
const cur = ref<MoveApply | null>(null)

function tagType(s?: string) {
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}

function onFilter() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const data = await listMoveApplies({
      status: status.value || undefined,
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

function detail(row: MoveApply) {
  cur.value = row
  dlg.value = true
}

function goApprove() {
  dlg.value = false
  router.push('/property/move/approve')
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
