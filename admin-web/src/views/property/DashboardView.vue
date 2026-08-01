<template>
  <!-- 物业工作台首页：仅右侧内容区；顶栏面包屑/账号由 AdminLayout 保留 -->
  <div class="wb" v-loading="loading">
    <div class="wb-head">
      <div>
        <h2>物业工作台</h2>
        <p class="sub">实时汇总报修、审批、巡检、访客等待办，数据来自当前社区真实业务</p>
      </div>
      <div class="head-actions">
        <FeatureGate feature="data_dashboard">
          <el-button type="primary" plain :disabled="!user.can('data_dashboard')" @click="go('/property/cockpit')">
            数据大屏
          </el-button>
        </FeatureGate>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- ① 横向标准 KPI 卡片（强制四列等宽） -->
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.key" class="kpi-card panel">
        <div class="kpi-head">
          <span class="kpi-label">{{ k.label }}</span>
          <span class="kpi-trend" :class="k.delta >= 0 ? 'up' : 'down'">
            较昨日{{ k.delta >= 0 ? '↑' : '↓' }}{{ Math.abs(k.delta) }}
          </span>
        </div>
        <div class="kpi-mid">
          <span class="kpi-value">{{ k.value }}</span>
          <el-button type="primary" size="small" class="kpi-btn" @click="go(k.to)">前往处理</el-button>
        </div>
      </div>
    </div>

    <!-- ② 70% 左侧(待办+预警) + 30% 侧栏 -->
    <div class="main-grid">
      <div class="left-col">
        <div class="panel todo-panel">
          <div class="panel-title">
            <h3>今日待办工单</h3>
            <div class="panel-extra">
              <el-tag v-if="pieFilter" closable type="warning" size="small" @close="pieFilter = ''">
                已筛选：{{ pieFilter }}
              </el-tag>
              <span class="muted">高优先级置顶 · 逾期行标红</span>
            </div>
          </div>
          <div class="todo-table-wrap">
            <el-table
              :data="filteredTodos"
              size="small"
              stripe
              empty-text="暂无待办，可在右侧快捷入口进入业务模块"
              :row-class-name="todoRowClass"
              height="100%"
              class="todo-table"
            >
              <el-table-column prop="type" label="类型" width="72" />
              <el-table-column prop="content" label="内容" min-width="160" show-overflow-tooltip />
              <el-table-column label="优先级" width="78" align="center">
                <template #default="{ row }">
                  <el-tag :type="priorityTag(row.priority)" size="small" round class="prio-tag">
                    {{ row.priority }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" width="140">
                <template #default="{ row }">{{ fmt(row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="截止时间" width="140">
                <template #default="{ row }">
                  <span :class="{ 'overdue-text': row.overdue }">{{ fmt(row.deadline) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="工单状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTag(row.statusTone)" size="small" round>{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="156" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="go(row.to)">查看详情</el-button>
                  <el-button type="primary" size="small" @click="go(row.to)">{{ row.actionLabel }}</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 风险预警：左下角卡片，与待办同列 -->
        <div class="panel alert-panel">
          <div class="panel-title">
            <h3>风险预警</h3>
            <span class="muted">紧急报修 · 逾期巡检 · 欠费 · 维保</span>
          </div>
          <el-table
            :data="alerts"
            size="small"
            stripe
            empty-text="暂无紧急风险项，社区运行平稳"
            max-height="200"
          >
            <el-table-column prop="type" label="类型" width="88">
              <template #default="{ row }">
                <el-tag type="danger" size="small" round>{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="text" label="风险事项" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="go(row.to)">前往处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="side-col">
        <div class="panel shortcut-panel">
          <h3>快捷入口</h3>
          <div v-for="g in shortcutGroups" :key="g.title" class="sc-group">
            <div class="sc-label">{{ g.title }}</div>
            <div class="sc-grid">
              <FeatureGate
                v-for="s in g.items"
                :key="s.path"
                :feature="s.feature || ''"
                :show-lock="!!s.feature && !user.can(s.feature)"
              >
                <button
                  class="sc-btn"
                  :class="{ hot: s.hot, locked: s.feature && !user.can(s.feature) }"
                  type="button"
                  @click="go(s.path, s.feature)"
                >
                  <el-icon :size="16"><component :is="s.icon" /></el-icon>
                  <span>{{ s.title }}</span>
                </button>
              </FeatureGate>
            </div>
          </div>
        </div>

        <div class="panel chart-panel">
          <div class="panel-title">
            <h3>工单数据统计</h3>
            <el-select v-model="chartPeriod" size="small" style="width: 88px">
              <el-option label="今日" value="today" />
              <el-option label="本周" value="week" />
              <el-option label="本月" value="month" />
            </el-select>
          </div>
          <div class="chart-box">
            <v-chart :option="pieOption" autoresize @click="onPieClick" />
          </div>
          <p class="chart-hint">点击扇区筛选左侧待办类型</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 物业管理员工作台
 * - 不改侧栏/顶栏，仅重构本页
 * - 数据全部走现有列表接口，前端聚合，不新增后端
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  Tools,
  MapLocation,
  Avatar,
  SetUp,
  CreditCard,
  Switch,
  DataBoard,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import FeatureGate from '@/components/FeatureGate.vue'
import { listRepairOrders, type RepairOrder } from '@/api/repair'
import { listVisits, type VisitItem } from '@/api/visit'
import { listInspTasks, type InspTask } from '@/api/inspection'
import { listMoveApplies, type MoveApply } from '@/api/household'
import { listFeeBills, type FeeBill } from '@/api/fee'
import { listEquipDue, type EquipDevice } from '@/api/equip'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])

const router = useRouter()
const user = useUserStore()
const loading = ref(false)
const chartPeriod = ref<'today' | 'week' | 'month'>('week')
const pieFilter = ref('')

const repairs = ref<RepairOrder[]>([])
const visits = ref<VisitItem[]>([])
const tasks = ref<InspTask[]>([])
const moves = ref<MoveApply[]>([])
const fees = ref<FeeBill[]>([])
const equipDue = ref<EquipDevice[]>([])

interface TodoRow {
  id: string
  type: string
  content: string
  priority: '高' | '中' | '低'
  priorityRank: number
  createTime?: string
  deadline?: string
  status: string
  statusTone: 'danger' | 'warning' | 'success' | 'info'
  overdue: boolean
  to: string
  actionLabel: string
}

interface AlertItem {
  type: string
  text: string
  to: string
}

interface KpiItem {
  key: string
  label: string
  value: number
  delta: number
  to: string
}

function go(path: string, feature?: string) {
  if (feature && !user.can(feature)) {
    user.openFeatureLimit(feature)
    return
  }
  router.push(path)
}

function fmt(v?: string) {
  if (!v) return '—'
  return dayjs(v).isValid() ? dayjs(v).format('YYYY-MM-DD HH:mm') : String(v).replace('T', ' ').slice(0, 16)
}

function priorityTag(p: string) {
  if (p === '高') return 'danger'
  if (p === '中') return 'warning'
  return 'info'
}

function statusTag(t: TodoRow['statusTone']) {
  return t
}

function todoRowClass({ row }: { row: TodoRow }) {
  return row.overdue ? 'row-overdue' : ''
}

function repairPriority(level?: string): TodoRow['priority'] {
  const s = level || ''
  if (s.includes('严') || s.includes('高') || s === '紧急') return '高'
  if (s.includes('中')) return '中'
  return '低'
}

function rankOf(p: TodoRow['priority']) {
  return p === '高' ? 0 : p === '中' ? 1 : 2
}

function isOpenRepair(o: RepairOrder) {
  return o.status === 'pending' || o.status === 'processing'
}

function isOpenInsp(t: InspTask) {
  return (t.status || '').toUpperCase() !== 'DONE'
}

function isVisitTodo(v: VisitItem) {
  return v.status === 'approved' || v.status === 'pending'
}

function isUnpaid(f: FeeBill) {
  const s = (f.rawStatus || f.status || '').toUpperCase()
  return s === 'UNPAID' || s === 'OVERDUE' || f.statusLabel === '已逾期'
}

function isLongOverdueFee(f: FeeBill) {
  if (!isUnpaid(f) || !f.dueDate) return false
  return dayjs().diff(dayjs(f.dueDate), 'day') >= 7
}

function repairDeadline(o: RepairOrder) {
  const base = o.createTime ? dayjs(o.createTime) : dayjs()
  const hours = repairPriority(o.damageLevel) === '高' ? 24 : 48
  return base.add(hours, 'hour').format('YYYY-MM-DD HH:mm:ss')
}

/** 今日新增开放项 − 昨日新增开放项，作为「较昨日」趋势 */
function dayDelta(createTimes: (string | undefined)[]) {
  const today = dayjs().startOf('day')
  const yest = today.subtract(1, 'day')
  let t = 0
  let y = 0
  for (const c of createTimes) {
    if (!c || !dayjs(c).isValid()) continue
    const d = dayjs(c)
    if (d.isSame(today, 'day')) t++
    else if (d.isSame(yest, 'day')) y++
  }
  return t - y
}

const kpis = computed<KpiItem[]>(() => {
  const pendingRepair = repairs.value.filter(isOpenRepair)
  const pendingMove = moves.value.filter((m) => m.status === 'PENDING')
  const unfinishedInsp = tasks.value.filter(isOpenInsp)
  const pendingVisit = visits.value.filter(isVisitTodo)

  return [
    {
      key: 'repair',
      label: '待派报修',
      value: pendingRepair.length,
      delta: dayDelta(pendingRepair.map((x) => x.createTime)),
      to: '/property/repair',
    },
    {
      key: 'move',
      label: '待审迁入迁出',
      value: pendingMove.length,
      delta: dayDelta(pendingMove.map((x) => x.createTime)),
      to: '/property/move/approve',
    },
    {
      key: 'insp',
      label: '今日巡检未完成',
      value: unfinishedInsp.length,
      delta: dayDelta(unfinishedInsp.map((x) => x.startTime)),
      to: '/property/inspection',
    },
    {
      key: 'visit',
      label: '待核销访客',
      value: pendingVisit.length,
      delta: dayDelta(pendingVisit.map((x) => x.createTime)),
      to: '/property/residents/visits',
    },
  ]
})

const alerts = computed<AlertItem[]>(() => {
  const list: AlertItem[] = []
  for (const o of repairs.value.filter(isOpenRepair)) {
    if (repairPriority(o.damageLevel) !== '高') continue
    list.push({
      type: '紧急报修',
      text: `${o.title || '报修单'}（#${o.id}）未处理`,
      to: '/property/repair',
    })
  }
  const soon = dayjs().add(24, 'hour')
  for (const t of tasks.value.filter(isOpenInsp)) {
    if (!t.endTime) continue
    const end = dayjs(t.endTime)
    if (end.isBefore(dayjs()) || end.isBefore(soon)) {
      list.push({
        type: end.isBefore(dayjs()) ? '巡检逾期' : '巡检将逾期',
        text: t.title || t.task || '巡检任务',
        to: '/property/inspection',
      })
    }
  }
  for (const f of fees.value.filter(isLongOverdueFee)) {
    list.push({
      type: '欠费预警',
      text: `${f.houseLabel || f.room || '住户'} 逾期≥7天（¥${Number(f.amount || 0).toFixed(2)}）`,
      to: '/property/fee/arrears',
    })
  }
  for (const d of equipDue.value) {
    const overdue =
      (d.status || '').toUpperCase() === 'OVERDUE' ||
      !!(d.nextMaintDate && dayjs(d.nextMaintDate).isBefore(dayjs(), 'day'))
    if (!overdue) continue
    list.push({
      type: '维保逾期',
      text: `${d.name}${d.nextMaintDate ? `（应维保 ${d.nextMaintDate}）` : ''}`,
      to: '/property/maintenance',
    })
  }
  return list
})

const todos = computed<TodoRow[]>(() => {
  const rows: TodoRow[] = []
  const now = dayjs()

  for (const o of repairs.value.filter(isOpenRepair)) {
    const priority = repairPriority(o.damageLevel)
    const deadline = repairDeadline(o)
    const overdue = dayjs(deadline).isBefore(now)
    rows.push({
      id: `repair-${o.id}`,
      type: '报修',
      content: o.title || o.description || `报修单 #${o.id}`,
      priority,
      priorityRank: rankOf(priority),
      createTime: o.createTime,
      deadline,
      status: o.status === 'processing' ? '处理中' : '待派单',
      statusTone: overdue ? 'danger' : o.status === 'processing' ? 'warning' : 'info',
      overdue,
      to: '/property/repair',
      actionLabel: '派单',
    })
  }

  for (const m of moves.value.filter((x) => x.status === 'PENDING')) {
    const create = m.createTime
    const deadline = create ? dayjs(create).add(2, 'day').format('YYYY-MM-DD HH:mm:ss') : undefined
    const overdue = !!(deadline && dayjs(deadline).isBefore(now))
    rows.push({
      id: `move-${m.id}`,
      type: '审批',
      content: `${m.type || m.applyType || '迁入迁出'} · ${m.name || ''} ${m.room || m.houseLabel || ''}`.trim(),
      priority: '中',
      priorityRank: 1,
      createTime: create,
      deadline,
      status: m.statusLabel || '待审批',
      statusTone: overdue ? 'danger' : 'warning',
      overdue,
      to: '/property/move/approve',
      actionLabel: '处理',
    })
  }

  for (const t of tasks.value.filter(isOpenInsp)) {
    const deadline = t.endTime
    const overdue = !!(deadline && dayjs(deadline).isBefore(now))
    const left = Math.max(0, (t.pointTotal || t.points || 0) - (t.pointDone || t.done || 0))
    rows.push({
      id: `insp-${t.id}`,
      type: '巡检',
      content: `${t.title || t.task || '巡检任务'}${left ? `（剩余 ${left} 点）` : ''}`,
      priority: overdue ? '高' : '中',
      priorityRank: overdue ? 0 : 1,
      createTime: t.startTime,
      deadline,
      status: t.statusLabel || t.status || '进行中',
      statusTone: overdue ? 'danger' : 'warning',
      overdue,
      to: '/property/inspection',
      actionLabel: '处理',
    })
  }

  for (const v of visits.value.filter(isVisitTodo)) {
    const deadline = v.expireTime || v.visitTime
    const overdue = !!(deadline && dayjs(deadline).isBefore(now))
    rows.push({
      id: `visit-${v.id}`,
      type: '访客',
      content: `${v.visitorName || '访客'}待核销${v.visitReason ? ` · ${v.visitReason}` : ''}`,
      priority: overdue ? '高' : '中',
      priorityRank: overdue ? 0 : 1,
      createTime: v.createTime,
      deadline,
      status: '待核销',
      statusTone: overdue ? 'danger' : 'warning',
      overdue,
      to: '/property/residents/visits',
      actionLabel: '处理',
    })
  }

  for (const d of equipDue.value.slice(0, 8)) {
    const deadline = d.nextMaintDate
    const overdue =
      (d.status || '').toUpperCase() === 'OVERDUE' ||
      !!(deadline && dayjs(deadline).isBefore(now, 'day'))
    rows.push({
      id: `equip-${d.id}`,
      type: '维保',
      content: `${d.name}${d.location ? ` · ${d.location}` : ''}`,
      priority: overdue ? '高' : '中',
      priorityRank: overdue ? 0 : 1,
      createTime: undefined,
      deadline,
      status: d.statusLabel || (overdue ? '已逾期' : '待维保'),
      statusTone: overdue ? 'danger' : 'warning',
      overdue,
      to: '/property/maintenance',
      actionLabel: '处理',
    })
  }

  rows.sort((a, b) => {
    if (a.priorityRank !== b.priorityRank) return a.priorityRank - b.priorityRank
    if (a.overdue !== b.overdue) return a.overdue ? -1 : 1
    return dayjs(b.createTime || 0).valueOf() - dayjs(a.createTime || 0).valueOf()
  })
  return rows
})

const filteredTodos = computed(() => {
  if (!pieFilter.value) return todos.value
  return todos.value.filter((t) => t.type === pieFilter.value)
})

const shortcutGroups = computed(() => [
  {
    title: '运营总览',
    items: [
      {
        title: '数据大屏',
        path: '/property/cockpit',
        icon: DataBoard,
        hot: true,
        feature: 'data_dashboard',
      },
    ],
  },
  {
    title: '工单业务',
    items: [
      { title: '报事报修', path: '/property/repair', icon: Tools, hot: true, feature: 'basic_repair' },
      { title: '巡检任务', path: '/property/inspection', icon: MapLocation, hot: true },
      { title: '访客记录', path: '/property/residents/visits', icon: Avatar, hot: true },
      { title: '设备维保', path: '/property/maintenance', icon: SetUp, hot: false },
    ],
  },
  {
    title: '收费业务',
    items: [
      { title: '缴费查询', path: '/property/fee/bills', icon: CreditCard, hot: true },
    ],
  },
  {
    title: '审批业务',
    items: [{ title: '迁入迁出审批', path: '/property/move', icon: Switch, hot: true }],
  },
].filter((g) => g.items.length > 0))

function inPeriod(t?: string) {
  if (!t || !dayjs(t).isValid()) return false
  const d = dayjs(t)
  if (chartPeriod.value === 'today') return d.isSame(dayjs(), 'day')
  if (chartPeriod.value === 'week') return d.isAfter(dayjs().startOf('week').subtract(1, 'ms'))
  return d.isSame(dayjs(), 'month')
}

const pieData = computed(() => {
  const bucket: Record<string, number> = { 报修: 0, 巡检: 0, 访客: 0, 维保: 0, 审批: 0 }
  for (const o of repairs.value) if (inPeriod(o.createTime)) bucket['报修']++
  for (const t of tasks.value) if (inPeriod(t.startTime)) bucket['巡检']++
  for (const v of visits.value) if (inPeriod(v.createTime)) bucket['访客']++
  for (const d of equipDue.value) if (inPeriod(d.nextMaintDate)) bucket['维保']++
  for (const m of moves.value) if (inPeriod(m.createTime)) bucket['审批']++
  return Object.entries(bucket)
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name, value }))
})

const pieOption = computed(() => ({
  color: ['#1677ff', '#faad14', '#52c41a', '#ff4d4f', '#722ed1'],
  tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
  legend: { bottom: 0, textStyle: { fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['38%', '62%'],
      center: ['50%', '46%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}', fontSize: 11 },
      data: pieData.value.length
        ? pieData.value
        : [{ name: '暂无数据', value: 1, itemStyle: { color: '#d9d9d9' } }],
    },
  ],
}))

function onPieClick(params: { name?: string }) {
  const name = params?.name
  if (!name || name === '暂无数据') return
  pieFilter.value = pieFilter.value === name ? '' : name
}

async function loadAll() {
  loading.value = true
  try {
    const [r, v, t, m, f, e] = await Promise.all([
      listRepairOrders().catch(() => [] as RepairOrder[]),
      listVisits().catch(() => [] as VisitItem[]),
      listInspTasks().catch(() => [] as InspTask[]),
      listMoveApplies({ page: 1, size: 100 })
        .then((res) => res.records || [])
        .catch(() => [] as MoveApply[]),
      listFeeBills({ status: 'UNPAID' }).catch(() => [] as FeeBill[]),
      listEquipDue().catch(() => [] as EquipDevice[]),
    ])
    repairs.value = r || []
    visits.value = v || []
    tasks.value = t || []
    moves.value = m || []
    fees.value = f || []
    equipDue.value = e || []
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.wb {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: calc(100vh - 112px);
  padding-bottom: 8px;
}
.wb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.wb-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}
.sub {
  margin: 2px 0 0;
  color: #8f959e;
  font-size: 12px;
}

/* 统一卡片壳 */
.panel {
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e8eaed;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
}

/* —— KPI：强制四列横向标准卡 —— */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.kpi-card {
  height: 88px;
  padding: 12px 14px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.kpi-label {
  font-size: 13px;
  color: #646a73;
  line-height: 1;
}
.kpi-trend {
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  color: #8f959e;
}
.kpi-trend.up {
  color: #f54a45;
}
.kpi-trend.down {
  color: #34c724;
}
.kpi-mid {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: #1677ff;
  line-height: 1;
  letter-spacing: -0.5px;
}
.kpi-btn {
  flex-shrink: 0;
}

/* —— 主区 70 / 30 —— */
.main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 7fr 3fr;
  gap: 10px;
  min-height: 0;
  align-items: stretch;
}
.left-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  min-height: 0;
}
.todo-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 320px;
  padding: 12px;
}
.todo-table-wrap {
  flex: 1;
  min-height: 0;
  height: 100%;
}
.todo-table {
  width: 100%;
}
.alert-panel {
  flex: 0 0 auto;
  padding: 12px;
}
.side-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  height: 100%;
}
.shortcut-panel,
.chart-panel {
  padding: 12px;
}
.shortcut-panel {
  flex: 0 0 auto;
}
.chart-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 260px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.panel-title h3,
.shortcut-panel h3,
.chart-panel h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}
.panel-extra {
  display: flex;
  align-items: center;
  gap: 8px;
}
.muted {
  color: #8f959e;
  font-size: 12px;
}
.overdue-text {
  color: #f54a45;
}
:deep(.row-overdue) > td {
  background: #fff8f7 !important;
}
:deep(.prio-tag) {
  border-radius: 10px;
}

/* 快捷入口 */
.sc-group + .sc-group {
  margin-top: 10px;
}
.sc-label {
  font-size: 11px;
  color: #8f959e;
  margin-bottom: 6px;
  line-height: 1;
}
.sc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.sc-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 56px;
  padding: 6px 4px;
  border: 1px solid #e8eaed;
  border-radius: 6px;
  background: #fafbfc;
  color: #4e5969;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.2;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.sc-btn:hover {
  border-color: #91caff;
  color: #1677ff;
  background: #f0f7ff;
}
.sc-btn.hot {
  background: #f0f7ff;
  border-color: #adc6ff;
  color: #0958d9;
}

.chart-box {
  flex: 1;
  min-height: 200px;
}
.chart-hint {
  margin: 4px 0 0;
  text-align: center;
  color: #8f959e;
  font-size: 11px;
}

@media (max-width: 1200px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .main-grid {
    grid-template-columns: 1fr;
  }
  .todo-panel {
    min-height: 360px;
  }
}
</style>
