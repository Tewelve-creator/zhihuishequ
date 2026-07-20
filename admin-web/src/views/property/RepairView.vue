<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>报事报修</h2>
        <p class="sub">可指派本社区物业工作人员；被指派人 App 首页会收到待办提醒</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <el-alert
      v-if="!user.hasRealToken"
      type="warning"
      :closable="false"
      show-icon
      title="当前为演示登录，无法拉取真实工单。请使用物业手机号登录，例如 13800000001 / 123456（社区选阳光花园）。"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" placeholder="状态" clearable style="width: 140px" @change="load">
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="processing" />
        <el-option label="已完成" value="done" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题/类型/处理人"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe empty-text="暂无报修工单">
      <el-table-column prop="no" label="工单号" width="100" />
      <el-table-column prop="title" label="问题描述" min-width="140" show-overflow-tooltip />
      <el-table-column prop="facilityType" label="故障类型" width="100" />
      <el-table-column prop="damageLevel" label="损坏程度" width="90" />
      <el-table-column prop="handlerName" label="处理人" width="110">
        <template #default="{ row }">
          <span>{{ row.handlerName || '未指派' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="160" />
      <el-table-column label="现场图" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.previewUrl"
            :src="row.previewUrl"
            :preview-src-list="[row.previewUrl]"
            fit="cover"
            style="width: 44px; height: 44px; border-radius: 6px"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button
            v-if="canAssign && row.status !== 'done'"
            link
            type="primary"
            @click="openAssign(row)"
          >
            指派
          </el-button>
          <el-button
            v-if="row.status === 'pending'"
            link
            type="success"
            :loading="actingId === row.id"
            @click="onAccept(row)"
          >
            接单
          </el-button>
          <el-button
            v-if="row.status === 'processing'"
            link
            type="warning"
            :loading="actingId === row.id"
            @click="onFinish(row)"
          >
            完工
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="assignVisible" title="指派物业工作人员" width="460px" destroy-on-close>
      <p v-if="assignRow" class="assign-tip">
        工单 {{ assignRow.no }}「{{ assignRow.title || '报修' }}」
      </p>
      <el-select
        v-model="assignHandlerId"
        filterable
        placeholder="选择处理人"
        style="width: 100%"
        :loading="staffLoading"
      >
        <el-option
          v-for="s in staffOptions"
          :key="s.id"
          :label="`${s.nickname || s.phone}（${roleLabel(s.role)}）`"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="confirmAssign">确认指派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import { listStaffUsers, type StaffUser } from '@/api/users'
import {
  acceptRepairOrder,
  assignRepairOrder,
  finishRepairOrder,
  listRepairOrders,
  type RepairOrder,
} from '@/api/repair'

interface Row extends RepairOrder {
  no: string
  statusLabel: string
  previewUrl: string
}

const user = useUserStore()
const loading = ref(false)
const actingId = ref<number | null>(null)
const status = ref('')
const keyword = ref('')
const list = ref<Row[]>([])

const assignVisible = ref(false)
const assignRow = ref<Row | null>(null)
const assignHandlerId = ref<number | undefined>()
const staffOptions = ref<StaffUser[]>([])
const staffLoading = ref(false)
const assigning = ref(false)

const canAssign = computed(() => user.hasRealToken && user.role === 'property_admin')

const STATUS_LABEL: Record<string, string> = {
  pending: '待处理',
  processing: '处理中',
  done: '已完成',
}

function roleLabel(r?: string) {
  if (r === 'property_admin') return '物业管理员'
  if (r === 'grid_worker') return '网格员'
  return r || ''
}

function statusTag(s?: string) {
  if (s === 'done') return 'success'
  if (s === 'processing') return 'warning'
  return 'info'
}

function imageUrl(u?: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('data:') || u.startsWith('blob:')) return u
  return `/api${u.startsWith('/') ? u : '/' + u}`
}

function mapRow(o: RepairOrder): Row {
  return {
    ...o,
    no: `BX${o.id}`,
    statusLabel: STATUS_LABEL[o.status || ''] || o.status || '-',
    previewUrl: imageUrl(o.imageUrl),
  }
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((r) => {
    const blob = `${r.title || ''} ${r.facilityType || ''} ${r.description || ''} ${r.aiTag || ''} ${r.handlerName || ''}`.toLowerCase()
    return blob.includes(k)
  })
})

async function load() {
  if (!user.hasRealToken) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const rows = await listRepairOrders(status.value || undefined)
    list.value = (rows || []).map(mapRow)
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function openAssign(row: Row) {
  assignRow.value = row
  assignHandlerId.value = row.handlerId
  assignVisible.value = true
  staffLoading.value = true
  try {
    const data = await listStaffUsers({ page: 1, size: 100, staffOnly: true })
    staffOptions.value = (data?.records || []).filter((s) => s.status === 1)
  } catch {
    staffOptions.value = []
  } finally {
    staffLoading.value = false
  }
}

async function confirmAssign() {
  if (!assignRow.value || !assignHandlerId.value) {
    ElMessage.warning('请选择处理人')
    return
  }
  assigning.value = true
  try {
    await assignRepairOrder(assignRow.value.id, assignHandlerId.value)
    ElMessage.success('已指派，对方 App 首页将显示待办提醒')
    assignVisible.value = false
    await load()
  } finally {
    assigning.value = false
  }
}

async function onAccept(row: Row) {
  actingId.value = row.id
  try {
    await acceptRepairOrder(row.id)
    ElMessage.success('已接单')
    await load()
  } finally {
    actingId.value = null
  }
}

async function onFinish(row: Row) {
  try {
    const { value } = await ElMessageBox.prompt('可填写完工备注（可选）', '确认完工', {
      confirmButtonText: '完工',
      cancelButtonText: '取消',
      inputPlaceholder: '处理说明',
      inputValue: '',
    })
    actingId.value = row.id
    await finishRepairOrder(row.id, { handleRemark: value || '' })
    ElMessage.success('已完工')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      /* toasted */
    }
  } finally {
    actingId.value = null
  }
}

function detail(row: Row) {
  ElMessageBox.alert(
    [
      `工单号：${row.no}`,
      `标题：${row.title || '-'}`,
      `详情：${row.description || '-'}`,
      `类型：${row.facilityType || '-'}`,
      `程度：${row.damageLevel || '-'}`,
      `处理人：${row.handlerName || '未指派'}`,
      `状态：${row.statusLabel}`,
      `提交：${row.createTime || '-'}`,
      `备注：${row.handleRemark || '-'}`,
    ].join('\n'),
    '工单详情',
    { confirmButtonText: '关闭' },
  )
}

function onExport() {
  exportExcel(
    filtered.value.map((r) => ({
      工单号: r.no,
      标题: r.title,
      类型: r.facilityType,
      程度: r.damageLevel,
      处理人: r.handlerName || '未指派',
      状态: r.statusLabel,
      时间: r.createTime,
    })),
    `报事报修_${Date.now()}`,
  )
  ElMessage.success('导出成功')
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
}
.sub {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.muted {
  color: #94a3b8;
}
.assign-tip {
  margin: 0 0 12px;
  color: #475569;
  font-size: 14px;
}
</style>
