<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p class="sub">{{ pageSub }}</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <FeatureGate feature="data_export">
          <el-button :disabled="!user.can('data_export')" @click="onExport">导出 Excel</el-button>
        </FeatureGate>
      </div>
    </div>

    <el-alert
      v-if="!user.hasRealToken"
      type="warning"
      :closable="false"
      show-icon
      title="请使用物业真实账号登录（如 13800000001 / 123456）"
      style="margin-bottom: 12px"
    />

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="processing" />
        <el-option label="已完成" value="done" />
      </el-select>
      <el-select v-model="urgency" clearable placeholder="紧急等级" style="width: 120px" @change="load">
        <el-option label="高" value="高" />
        <el-option label="中" value="中" />
        <el-option label="低" value="低" />
      </el-select>
      <el-checkbox v-model="emotionOnly" @change="load">仅情绪预警</el-checkbox>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题/位置/分类/处理人/AI标签"
        style="width: 240px"
        @keyup.enter="noop"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="filtered" stripe empty-text="暂无工单">
      <el-table-column prop="no" label="工单号" width="100" />
      <el-table-column v-if="!fixedOrderType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.orderType === 'report' ? 'warning' : 'primary'" size="small">
            {{ row.orderTypeLabel || (row.orderType === 'report' ? '报事' : '报修') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="问题描述" min-width="140" show-overflow-tooltip />
      <el-table-column label="位置" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="locationText(row)">{{ locationText(row) }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="categoryName" label="分类" width="100">
        <template #default="{ row }">{{ row.categoryName || row.facilityType || '—' }}</template>
      </el-table-column>
      <el-table-column prop="urgency" label="紧急" width="70">
        <template #default="{ row }">
          <el-tag :type="urgTag(row.urgency)" size="small">
            {{ row.urgency || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="damageLevel" label="损坏程度" width="96">
        <template #default="{ row }">
          <el-tag v-if="row.damageLevel" :type="damageTag(row.damageLevel)" size="small" effect="plain">
            {{ row.damageLevel }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="AI/情绪" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.fromFeedback || row.feedbackId" type="warning" size="small">意见反馈</el-tag>
          <el-tag v-else-if="row.emotionAlert || row.emotionCaseId" type="danger" size="small">情绪预警</el-tag>
          <el-tag v-else-if="(row.aiImageLogCount || 0) > 0 || row.aiTag" type="success" size="small">识图</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="handlerName" label="处理人" width="100">
        <template #default="{ row }">{{ row.handlerName || '未指派' }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
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
      <el-table-column prop="createTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button v-if="canAssign && row.status !== 'done'" link type="primary" @click="openAssign(row)">
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
          <el-button link type="danger" :loading="actingId === row.id" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="assignVisible" title="指派 / 改派负责人" width="460px" destroy-on-close>
      <p v-if="assignRow" class="assign-tip">工单 {{ assignRow.no }}「{{ assignRow.title || '工单' }}」</p>
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import FeatureGate from '@/components/FeatureGate.vue'
import { exportExcel } from '@/utils/exportExcel'
import { listStaffUsers, type StaffUser } from '@/api/users'
import {
  acceptRepairOrder,
  assignRepairOrder,
  deleteRepairOrder,
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
const route = useRoute()
const loading = ref(false)
const actingId = ref<number | null>(null)
const status = ref('')
const orderType = ref('')
const urgency = ref('')
const emotionOnly = ref(false)
const keyword = ref('')
const list = ref<Row[]>([])

const fixedOrderType = computed(() => {
  const t = route.meta.orderType
  return t === 'repair' || t === 'report' ? t : ''
})
const pageTitle = computed(() =>
  fixedOrderType.value === 'report' ? '报事工单' : fixedOrderType.value === 'repair' ? '报修工单' : '工单列表',
)
const pageSub = computed(() =>
  fixedOrderType.value === 'report'
    ? '邻里报事 / 意见反馈转单，按小区隔离'
    : fixedOrderType.value === 'repair'
      ? '设施报修工单台账，按小区隔离'
      : '报修 / 报事统一台账，按小区隔离',
)

function syncOrderTypeFromRoute() {
  orderType.value = fixedOrderType.value || ''
}

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

function noop() {}
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
function urgTag(u?: string) {
  if (u === '高') return 'danger'
  if (u === '低') return 'info'
  return 'warning'
}
function damageTag(d?: string) {
  if (!d) return 'info'
  if (d.includes('严重') || d.includes('重度')) return 'danger'
  if (d.includes('轻微') || d.includes('轻度')) return 'success'
  return 'warning'
}
/** 入户：几栋几单元房号；公区（电梯/照明等）：几栋几单元 · 电梯，不带房号 */
function locationText(
  row: Partial<RepairOrder> & {
    locationLabel?: string
    building?: string
    unit?: string
    roomNo?: string
    categoryName?: string
    facilityType?: string
    title?: string
  },
) {
  const building = normalizeBuilding(row.building)
  const unit = normalizeUnit(row.unit)
  const pub = isPublicFacility(row)
  const room = pub ? '' : (row.roomNo || '').toString().trim()
  const core = [building, unit, room].filter(Boolean).join('')
  let spot = (row.locationLabel || '').includes('·')
    ? (row.locationLabel || '').split('·').slice(1).join('·').trim()
    : ''
  if (pub && !spot) spot = publicSpot(row)
  if (core) return spot ? `${core} · ${spot}` : core
  return (row.locationLabel || '').trim()
}
function isPublicFacility(row: { categoryName?: string; facilityType?: string; title?: string }) {
  const t = `${row.categoryName || ''} ${row.facilityType || ''} ${row.title || ''}`
  return /电梯|路灯|门禁|单元门|公共|公区|健身|消防|照明|排水|楼道|外墙|停车场|车库|绿化|监控/.test(t)
}
function publicSpot(row: { categoryName?: string; facilityType?: string }) {
  const c = row.categoryName || row.facilityType || ''
  if (c.includes('电梯')) return '电梯'
  if (c.includes('路灯')) return '路灯'
  if (c.includes('门禁') || c.includes('单元门')) return '门禁'
  if (c.includes('照明')) return '照明'
  if (c.includes('排水')) return '排水'
  if (c.includes('健身')) return '健身区'
  if (c.includes('消防')) return '消防'
  if (c.includes('公共') || c.includes('公区')) return '公共区域'
  return c || '公共区域'
}
function normalizeBuilding(v?: string) {
  if (!v) return ''
  const s = String(v).trim()
  if (/[栋幢]|号楼$/.test(s)) return s
  if (/^[0-9A-Za-z一二三四五六七八九十]+$/.test(s)) return `${s}栋`
  return s
}
function normalizeUnit(v?: string) {
  if (!v) return ''
  const s = String(v).trim()
  if (s.endsWith('单元')) return s
  if (/^[0-9A-Za-z一二三四五六七八九十]+$/.test(s)) return `${s}单元`
  return s
}
function imageUrl(u?: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('data:') || u.startsWith('blob:')) return u
  return `/api${u.startsWith('/') ? u : '/' + u}`
}
function mapRow(o: RepairOrder): Row {
  const ot = o.orderType || 'repair'
  return {
    ...o,
    orderType: ot,
    no: ot === 'report' ? `BS${o.id}` : `BX${o.id}`,
    statusLabel: STATUS_LABEL[o.status || ''] || o.status || '-',
    previewUrl: imageUrl(o.imageUrl),
  }
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const focusId = Number(route.query.id || 0)
  return list.value.filter((r) => {
    if (focusId > 0 && r.id !== focusId) return false
    if (!k) return true
    const blob = `${r.no || ''} ${r.id || ''} ${r.title || ''} ${locationText(r)} ${r.facilityType || ''} ${r.categoryName || ''} ${r.description || ''} ${r.aiTag || ''} ${r.handlerName || ''} ${r.emotionTags || ''}`.toLowerCase()
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
    const type = fixedOrderType.value || orderType.value || undefined
    const rows = await listRepairOrders({
      status: status.value || undefined,
      orderType: type,
      urgency: urgency.value || undefined,
      emotionAlert: emotionOnly.value || undefined,
    })
    list.value = (rows || []).map(mapRow)
    // 识图日志等业务溯源：?id= 定位并打开详情
    const focusId = Number(route.query.id || 0)
    if (focusId > 0) {
      const hit = list.value.find((r) => r.id === focusId)
      if (hit) {
        keyword.value = hit.no
        detail(hit)
      }
    }
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
    ElMessage.success('已指派')
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
    const { value } = await ElMessageBox.prompt('可填写完工备注（可选）', '确认办结', {
      confirmButtonText: '办结',
      cancelButtonText: '取消',
      inputPlaceholder: '处理说明',
    })
    actingId.value = row.id
    await finishRepairOrder(row.id, { handleRemark: value || '' })
    ElMessage.success('已办结')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      /* toasted */
    }
  } finally {
    actingId.value = null
  }
}

async function onDelete(row: Row) {
  try {
    await ElMessageBox.confirm(`确认删除工单 ${row.no}？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await deleteRepairOrder(row.id)
    ElMessage.success('已删除')
    await load()
  } finally {
    actingId.value = null
  }
}

function detail(row: Row) {
  ElMessageBox.alert(
    [
      `工单号：${row.no}`,
      `类型：${row.orderTypeLabel || row.orderType}`,
      `标题：${row.title || '-'}`,
      `位置：${locationText(row) || '-'}`,
      `详情：${row.description || '-'}`,
      `分类：${row.categoryName || row.facilityType || '-'}`,
      `紧急：${row.urgency || '-'}`,
      `损坏程度：${row.damageLevel || '-'}`,
      `AI标签：${row.aiTag || '-'}`,
      `识图日志：${row.aiImageLogCount ?? 0} 条`,
      `情绪预警：${row.emotionCaseId ? `是（案#${row.emotionCaseId} ${row.emotionTags || row.sentiment || ''}）` : '否'}`,
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
      类型: r.orderTypeLabel || r.orderType,
      标题: r.title,
      位置: locationText(r),
      分类: r.categoryName || r.facilityType,
      紧急: r.urgency,
      损坏程度: r.damageLevel,
      AI标签: r.aiTag,
      情绪案: r.emotionCaseId || '',
      处理人: r.handlerName || '未指派',
      状态: r.statusLabel,
      时间: r.createTime,
    })),
    '工单列表.xlsx',
  )
}

watch(
  () => route.meta.orderType,
  () => {
    syncOrderTypeFromRoute()
    load()
  },
)

onMounted(() => {
  syncOrderTypeFromRoute()
  load()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; align-items: center; }
.assign-tip { margin: 0 0 12px; color: #64748b; }
.muted { color: #94a3b8; }
</style>
