<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>临时通行记录</h2>
        <p class="sub">含道闸进出与访客预约驾车登记车牌；预约登记原因含「访客预约登记」</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="plateNo" clearable placeholder="车牌号" style="width: 140px" @keyup.enter="load" />
      <el-select v-model="direction" clearable placeholder="进出" style="width: 110px" @change="load">
        <el-option label="进场" value="IN" />
        <el-option label="出场" value="OUT" />
      </el-select>
      <el-select v-model="passResult" clearable placeholder="结果" style="width: 110px" @change="load">
        <el-option label="放行" value="ALLOW" />
        <el-option label="拒绝" value="DENY" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无通行记录">
      <el-table-column prop="plateNo" label="车牌号" width="120" />
      <el-table-column label="方向" width="90">
        <template #default="{ row }">
          <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small">
            {{ row.direction === 'IN' ? '进场' : '出场' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="gateName" label="道闸" width="120">
        <template #default="{ row }">{{ row.gateName || row.gateCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.passResult === 'ALLOW' ? 'success' : 'danger'" size="small">
            {{ row.passResult === 'ALLOW' ? '放行' : '拒绝' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="passReason" label="原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="passTime" label="通行时间" width="180">
        <template #default="{ row }">{{ fmt(row.passTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { exportExcel } from '@/utils/exportExcel'
import { listParkAccessLogs, type ParkAccessLog } from '@/api/parking'

const loading = ref(false)
const list = ref<ParkAccessLog[]>([])
const plateNo = ref('')
const direction = ref('')
const passResult = ref('')

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 19)
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listParkAccessLogs({
        plateNo: plateNo.value || undefined,
        direction: direction.value || undefined,
        passResult: passResult.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      车牌号: r.plateNo,
      方向: r.direction === 'IN' ? '进场' : '出场',
      道闸: r.gateName || '',
      结果: r.passResult === 'ALLOW' ? '放行' : '拒绝',
      原因: r.passReason || '',
      通行时间: fmt(r.passTime),
    })),
    '临时通行记录.xlsx',
  )
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
