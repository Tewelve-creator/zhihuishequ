<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>巡检任务</h2>
        <p class="sub">创建临时或周期巡检，选择点位、指定执行人与时间段；跟踪待执行/进行中/已完成/逾期</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
        <el-button @click="hazardVisible = true">异常台账</el-button>
        <el-button @click="$router.push('/property/inspection-records')">巡检记录</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增任务</el-button>
      </div>
    </div>

    <el-alert
      v-if="user.role === 'grid_worker'"
      type="info"
      :closable="false"
      show-icon
      title="网格员仅可查看任务，请在手机 App「巡检任务」中执行打卡上报"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="任务状态" style="width: 130px" @change="load">
        <el-option label="待执行" value="PENDING" />
        <el-option label="进行中" value="DOING" />
        <el-option label="已完成" value="DONE" />
        <el-option label="逾期" value="OVERDUE" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="任务名称/执行人"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无巡检任务">
      <el-table-column prop="task" label="任务名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="points" label="总点位" width="90" />
      <el-table-column prop="done" label="已巡数量" width="100" />
      <el-table-column prop="owner" label="执行人" width="110" />
      <el-table-column prop="startTime" label="开始时间" width="170">
        <template #default="{ row }">{{ fmt(row.startTime) }}</template>
      </el-table-column>
      <el-table-column prop="endTime" label="截止时间" width="170">
        <template #default="{ row }">{{ fmt(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="hazardCount" label="异常数" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.hazardCount > 0" type="danger" size="small">{{ row.hazardCount }}</el-tag>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column prop="cycleLabel" label="周期" width="80" />
      <el-table-column prop="statusLabel" label="任务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" :width="canManage ? 200 : 90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
          <template v-if="canManage">
            <el-button link type="primary" :disabled="row.status === 'DONE'" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button link type="danger" :disabled="row.status === 'DONE'" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <InspectionTaskDialog v-model="dialogVisible" :task="editing" @saved="load" />

    <el-drawer v-model="detailVisible" title="巡检明细" size="520px">
      <template v-if="detail">
        <p><b>任务：</b>{{ detail.title }}</p>
        <p><b>执行人：</b>{{ detail.assigneeName }}</p>
        <p><b>进度：</b>{{ detail.pointDone }}/{{ detail.pointTotal }} · {{ detail.statusLabel }}</p>
        <p><b>要求：</b>{{ detail.requirement || '—' }}</p>
        <el-divider>点位清单</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="p in detail.taskPoints || []"
            :key="p.id"
            :type="p.checked ? 'success' : 'primary'"
            :hollow="!p.checked"
          >
            <div class="pt-name">
              {{ p.pointName }}
              <el-tag size="small" :type="p.checked ? 'success' : 'info'" style="margin-left: 8px">
                {{ p.checked ? '已巡' : '待巡' }}
              </el-tag>
              <el-tag v-if="p.hasHazard" size="small" type="danger" style="margin-left: 6px">隐患</el-tag>
            </div>
            <div class="muted">{{ p.locationDesc || '—' }}</div>
            <div v-if="p.checked" class="pt-meta">
              <div>打卡人：{{ p.checkerName || '—' }}</div>
              <div>时间：{{ fmt(p.checkTime as string) }}</div>
              <div v-if="p.checkinRemark">备注：{{ p.checkinRemark }}</div>
              <div v-if="p.hazardDesc" class="hazard">隐患：{{ p.hazardDesc }}</div>
            </div>
            <div v-if="(p.photos || []).length" class="pt-photos">
              <el-image
                v-for="(url, i) in p.photos"
                :key="i"
                :src="mediaUrl(url)"
                :preview-src-list="(p.photos || []).map((u: string) => mediaUrl(u))"
                :initial-index="i"
                fit="cover"
                class="pt-img"
              />
            </div>
            <div v-else-if="p.checked" class="muted">暂无现场照片（历史打卡未上传到服务器）</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <el-dialog v-model="hazardVisible" title="巡检异常台账" width="860px" @open="loadHazards">
      <el-table v-loading="hazardLoading" :data="hazards" stripe max-height="420">
        <el-table-column prop="taskTitle" label="任务" min-width="140" />
        <el-table-column prop="pointName" label="点位" width="120" />
        <el-table-column prop="hazardDesc" label="异常描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="userName" label="上报人" width="100" />
        <el-table-column prop="checkTime" label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.checkTime) }}</template>
        </el-table-column>
        <el-table-column prop="hazardStatus" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.hazardStatus === 'FIXED' ? 'success' : 'danger'" size="small">
              {{ row.hazardStatus === 'FIXED' ? '已整改' : '待跟进' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="200">
          <template #default="{ row }">
            <el-button
              v-if="row.hazardStatus !== 'FIXED'"
              link
              type="warning"
              @click="onGenWo(row)"
            >生成维保工单</el-button>
            <el-button
              v-if="row.hazardStatus !== 'FIXED'"
              link
              type="primary"
              @click="onFix(row)"
            >标记整改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  deleteInspTask,
  fixInspHazard,
  getInspTask,
  listInspHazards,
  listInspTasks,
  type InspHazard,
  type InspTask,
} from '@/api/inspection'
import { createWorkOrderFromCheckin } from '@/api/equip'
import InspectionTaskDialog from './InspectionTaskDialog.vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const user = useUserStore()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const list = ref<InspTask[]>([])
const status = ref('')
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref<InspTask | null>(null)
const detailVisible = ref(false)
const detail = ref<InspTask | null>(null)
const hazardVisible = ref(false)
const hazardLoading = ref(false)
const hazards = ref<InspHazard[]>([])

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 19)
}

function mediaUrl(url?: string) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('data:') || url.startsWith('blob:')) return url
  return `/api${url.startsWith('/') ? url : '/' + url}`
}

function statusTag(s?: string) {
  if (s === 'DONE') return 'success'
  if (s === 'DOING') return 'warning'
  if (s === 'OVERDUE') return 'danger'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    list.value = await listInspTasks({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

async function openEdit(row: InspTask) {
  editing.value = await getInspTask(row.id)
  dialogVisible.value = true
}

async function openDetail(row: InspTask) {
  detail.value = await getInspTask(row.id)
  detailVisible.value = true
}

async function onDelete(row: InspTask) {
  if (row.status === 'DONE') {
    ElMessage.warning('已完成任务不可删除')
    return
  }
  await ElMessageBox.confirm(`确认删除任务「${row.task || row.title}」？`, '删除确认', { type: 'warning' })
  await deleteInspTask(row.id)
  ElMessage.success('已删除')
  load()
}

function onExport() {
  const rows = list.value.map((r) => ({
    任务名称: r.task || r.title,
    总点位: r.points ?? r.pointTotal,
    已巡数量: r.done ?? r.pointDone,
    执行人: r.owner || r.assigneeName,
    开始时间: fmt(r.startTime as string),
    截止时间: fmt(r.endTime as string),
    隐患数量: r.hazardCount ?? 0,
    状态: r.statusLabel,
    周期: r.cycleLabel,
  }))
  exportExcel(rows, `巡检任务_${Date.now()}`)
  ElMessage.success('导出成功')
}

async function loadHazards() {
  hazardLoading.value = true
  try {
    hazards.value = await listInspHazards()
  } catch {
    hazards.value = []
  } finally {
    hazardLoading.value = false
  }
}

async function onFix(row: InspHazard) {
  await fixInspHazard(row.id)
  ElMessage.success('已标记整改')
  loadHazards()
  load()
}

async function onGenWo(row: InspHazard) {
  await ElMessageBox.confirm(
    `确认为异常「${row.pointName}」一键生成设备维保工单？`,
    '巡检→维保闭环',
  )
  await createWorkOrderFromCheckin(row.id, {
    title: `巡检异常维修：${row.pointName || ''}`,
    deviceName: row.pointName,
    location: row.pointName,
  })
  ElMessage.success('维保工单已生成')
  hazardVisible.value = false
  router.push('/property/maintenance')
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions,
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-bar {
  margin-bottom: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 0;
  margin-top: 4px;
}
.pt-name {
  font-weight: 600;
}
.pt-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
.pt-meta .hazard {
  color: var(--el-color-danger);
}
.pt-photos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.pt-img {
  width: 88px;
  height: 88px;
  border-radius: 8px;
}
</style>
