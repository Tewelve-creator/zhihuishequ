<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>历史记录</h2>
        <p class="sub">归档所有已办结（通过 / 驳回）的迁入迁出申请，支持查询与导出</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="结果" style="width: 140px" @change="onFilter">
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

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无历史记录">
      <el-table-column prop="applyNo" label="申请单号" width="180" />
      <el-table-column prop="name" label="申请人" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="房屋" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column prop="identityLabel" label="身份" width="80" />
      <el-table-column prop="statusLabel" label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'APPROVED' ? 'success' : 'danger'" size="small">
            {{ row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditName" label="审批人" width="100" />
      <el-table-column prop="auditRemark" label="审批意见" min-width="140" show-overflow-tooltip />
      <el-table-column prop="auditTime" label="审批时间" width="170" />
      <el-table-column prop="createTime" label="申请时间" width="170" />
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
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { listMoveApplies, type MoveApply } from '@/api/household'

const loading = ref(false)
const list = ref<MoveApply[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const applyType = ref('')
const keyword = ref('')

function onFilter() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const data = await listMoveApplies({
      status: status.value || 'HISTORY',
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

async function onExport() {
  const data = await listMoveApplies({
    status: status.value || 'HISTORY',
    applyType: applyType.value || undefined,
    keyword: keyword.value.trim() || undefined,
    page: 1,
    size: 2000,
  })
  const rows = (data?.records || []).map((r) => ({
    申请单号: r.applyNo,
    申请人: r.name,
    手机号: r.phone,
    房屋: r.room || r.houseLabel,
    类型: r.type,
    身份: r.identityLabel,
    结果: r.statusLabel,
    审批人: r.auditName,
    审批意见: r.auditRemark,
    审批时间: r.auditTime,
    申请时间: r.createTime,
  }))
  exportExcel(rows, `迁入迁出历史_${Date.now()}`)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

onMounted(load)
</script>

<style scoped>
.page-card { background: #fff; border-radius: 12px; padding: 20px 24px; }
.page-header { display: flex; justify-content: space-between; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.sub { margin: 6px 0 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
