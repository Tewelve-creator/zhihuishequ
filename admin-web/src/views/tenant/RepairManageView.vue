<template>
  <div class="page-card">
    <div class="page-header">
      <h2>工单管理</h2>
      <el-button @click="onExport">导出 Excel</el-button>
    </div>
    <div class="filter-bar">
      <el-select v-model="status" placeholder="状态" clearable style="width: 140px">
        <el-option label="待处理" value="待处理" />
        <el-option label="处理中" value="处理中" />
        <el-option label="已完成" value="已完成" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始"
        end-placeholder="结束"
      />
      <el-button type="primary">查询</el-button>
    </div>
    <el-table :data="filtered" stripe>
      <el-table-column prop="no" label="工单号" width="140" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="reporter" label="报修人" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="advance(row)">流转</el-button>
          <el-button link @click="detail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'

interface RepairRow {
  id: number
  no: string
  title: string
  reporter: string
  createdAt: string
  status: string
}

const status = ref('')
const range = ref<[string, string] | null>(null)
const list = ref<RepairRow[]>([
  { id: 1, no: 'WO20260717001', title: '1栋电梯异响', reporter: '王芳', createdAt: '2026-07-15', status: '待处理' },
  { id: 2, no: 'WO20260717002', title: '下水道堵塞', reporter: '李强', createdAt: '2026-07-16', status: '处理中' },
  { id: 3, no: 'WO20260717003', title: '路灯不亮', reporter: '赵敏', createdAt: '2026-07-14', status: '已完成' },
])

const filtered = computed(() =>
  list.value.filter((r) => {
    const hitStatus = !status.value || r.status === status.value
    const hitRange =
      !range.value ||
      (r.createdAt >= range.value[0] && r.createdAt <= range.value[1])
    return hitStatus && hitRange
  }),
)

function advance(row: RepairRow) {
  const map: Record<string, string> = { 待处理: '处理中', 处理中: '已完成' }
  if (!map[row.status]) {
    ElMessage.info('工单已完成')
    return
  }
  row.status = map[row.status]
  ElMessage.success(`已流转为「${row.status}」`)
}

function detail(row: RepairRow) {
  ElMessageBox.alert(`${row.no} · ${row.title}`, '工单详情')
}

function onExport() {
  exportExcel(filtered.value, `工单列表_${Date.now()}`)
}
</script>
