<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>AI 智能日程台账</h2>
        <p class="sub">
          面向业主的个性化日程自动生成、冲突检测与便民提醒（AI 管家增值能力）
        </p>
      </div>
      <div class="ops">
        <el-button @click="openSettings">提醒配置</el-button>
        <el-button :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <div class="insight-row">
      <div class="kpi-grid">
        <div class="kpi" data-tone="ink">
          <div class="kpi-label">日程总数</div>
          <div class="kpi-value">{{ stats.total ?? 0 }}</div>
          <div class="kpi-foot">本社区 AI 日程台账</div>
        </div>
        <div class="kpi" data-tone="teal">
          <div class="kpi-label">待推送</div>
          <div class="kpi-value">{{ stats.pending ?? 0 }}</div>
          <div class="kpi-foot">等待到期提醒</div>
        </div>
        <div class="kpi" data-tone="green">
          <div class="kpi-label">已推送</div>
          <div class="kpi-value">{{ stats.sent ?? 0 }}</div>
          <div class="kpi-foot">已写入站内消息</div>
        </div>
        <div class="kpi" data-tone="rose">
          <div class="kpi-label">冲突日程</div>
          <div class="kpi-value">{{ stats.conflict ?? 0 }}</div>
          <div class="kpi-foot">时段重叠需排查</div>
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-hd">
          <div>
            <h3>各类日程数量分布</h3>
            <p>物业费 / 社区活动 / 维保回访 / 便民预约</p>
          </div>
        </div>
        <div v-if="!hasTypeData" class="chart-empty">暂无日程数据</div>
        <v-chart v-else class="type-chart" :option="pieOption" autoresize />
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="业主昵称/手机/用户ID/标题"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-select v-model="scheduleType" clearable placeholder="日程类型" style="width: 140px" @change="load">
        <el-option label="物业费提醒" value="FEE" />
        <el-option label="社区活动" value="ACTIVITY" />
        <el-option label="维保回访" value="MAINT" />
        <el-option label="便民预约" value="CONVENIENCE" />
      </el-select>
      <el-select v-model="conflict" clearable placeholder="是否冲突" style="width: 120px" @change="load">
        <el-option label="仅冲突" :value="1" />
        <el-option label="无冲突" :value="0" />
      </el-select>
      <el-select v-model="status" clearable placeholder="推送状态" style="width: 120px" @change="load">
        <el-option label="待推送" value="pending" />
        <el-option label="已推送" value="sent" />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="width: 340px"
        @change="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      empty-text="暂无日程"
      :row-class-name="rowClass"
    >
      <el-table-column label="业主" width="130">
        <template #default="{ row }">
          <div>{{ row.userNickname || `用户#${row.userId}` }}</div>
          <div class="muted">ID {{ row.userId }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="日程类型" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.scheduleType)" size="small" effect="plain">
            {{ row.scheduleTypeLabel || typeLabel(row.sourceType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remindTime" label="提醒时间" width="170">
        <template #default="{ row }">{{ fmt(row.remindTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'sent' ? 'success' : 'info'" size="small" effect="plain">
            {{ row.statusLabel || (row.status === 'sent' ? '已推送' : '待推送') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="冲突" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.conflictFlag === 1" type="danger" effect="dark" size="small">冲突</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="jumpPath" label="跳转" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="日程详情" width="720px" destroy-on-close>
      <template v-if="cur">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="业主">
            {{ cur.userNickname || `#${cur.userId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="日程类型">
            <el-tag :type="typeTag(cur.scheduleType)" size="small">
              {{ cur.scheduleTypeLabel || typeLabel(cur.sourceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提醒时间">{{ fmt(cur.remindTime) }}</el-descriptions-item>
          <el-descriptions-item label="冲突">
            <el-tag v-if="cur.conflictFlag === 1" type="danger" effect="dark" size="small">冲突</el-tag>
            <span v-else>正常</span>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ cur.title }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <div class="qa">{{ cur.content || '—' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="跳转" :span="2">{{ cur.jumpPath || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section">
          <div class="sec-hd">生成依据</div>
          <div class="sec-body">
            {{ cur.sourceBasis?.summary || '—' }}
            <div v-if="cur.sourceBasis?.bizTitle" class="muted mt">
              业务：{{ cur.sourceBasis.bizTitle }}
              <span v-if="cur.sourceBasis.sourceId">（#{{ cur.sourceBasis.sourceId }}）</span>
            </div>
          </div>
        </div>

        <div class="section">
          <div class="sec-hd">用户画像标签</div>
          <div class="tags">
            <el-tag v-for="t in cur.profileTags || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
        </div>

        <div class="section">
          <div class="sec-hd">推送记录</div>
          <el-timeline v-if="(cur.pushLogs || []).length">
            <el-timeline-item
              v-for="(p, i) in cur.pushLogs"
              :key="i"
              :timestamp="fmt(p.sentTime || p.remindTime)"
              placement="top"
            >
              <div>
                <b>{{ p.channel === 'sys_message' ? '站内消息' : '日程推送' }}</b>
                · {{ p.status }}
              </div>
              <div class="muted">{{ p.remark || p.title || p.content || '' }}</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="muted">暂无推送记录</div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="cfgDlg" title="提醒提前推送配置" width="520px" destroy-on-close>
      <p class="cfg-tip">设置各类日程相对业务时间提前多少小时推送（影响新生成的日程）</p>
      <el-form label-width="140px">
        <el-form-item label="物业费提醒">
          <el-input-number v-model="cfg.aheadFeeHours" :min="0" :max="336" /> 小时
        </el-form-item>
        <el-form-item label="社区活动">
          <el-input-number v-model="cfg.aheadActivityHours" :min="0" :max="336" /> 小时
        </el-form-item>
        <el-form-item label="维保回访">
          <el-input-number v-model="cfg.aheadMaintHours" :min="0" :max="336" /> 小时
        </el-form-item>
        <el-form-item label="便民预约">
          <el-input-number v-model="cfg.aheadConvenienceHours" :min="0" :max="336" /> 小时
        </el-form-item>
        <el-form-item label="社区通知">
          <el-input-number v-model="cfg.aheadNoticeHours" :min="0" :max="336" /> 小时
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cfgDlg = false">取消</el-button>
        <el-button type="primary" :loading="cfgSaving" @click="saveCfg">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  deleteAdminSchedule,
  getAdminScheduleDetail,
  getAdminScheduleStats,
  getScheduleSettings,
  listAdminSchedule,
  saveScheduleSettings,
} from '@/api/aiFeature'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])

const loading = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const status = ref('')
const scheduleType = ref('')
const conflict = ref<number | undefined>()
const timeRange = ref<[string, string] | null>(null)
const dlg = ref(false)
const cur = ref<any>(null)
const cfgDlg = ref(false)
const cfgSaving = ref(false)
const cfg = reactive({
  aheadFeeHours: 24,
  aheadActivityHours: 6,
  aheadMaintHours: 24,
  aheadConvenienceHours: 12,
  aheadNoticeHours: 2,
})
const stats = reactive({
  total: 0,
  pending: 0,
  sent: 0,
  conflict: 0,
  typeDist: [] as Array<{ type: string; name: string; value: number }>,
})

const hasTypeData = computed(() => (stats.typeDist || []).some((x) => (x.value || 0) > 0))

const pieOption = computed(() => {
  const data = (stats.typeDist || []).filter((x) => (x.value || 0) > 0)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'middle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#646a73', fontSize: 12 },
    },
    color: ['#14b8a6', '#3370ff', '#f59e0b', '#8b5cf6'],
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['36%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 12, fontWeight: 600, color: '#1f2329' },
          scaleSize: 4,
        },
        data: data.map((d) => ({ name: d.name, value: d.value })),
      },
    ],
  }
})

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function typeLabel(src?: string) {
  const s = String(src || '').toUpperCase()
  if (s === 'FEE' || s === 'PROPERTY_FEE') return '物业费提醒'
  if (s === 'ACTIVITY') return '社区活动'
  if (['MAINT', 'MAINTENANCE', 'REPAIR', 'FOLLOWUP'].includes(s)) return '维保回访'
  return '便民预约'
}

function typeTag(t?: string) {
  const g = String(t || '').toUpperCase()
  if (g === 'FEE') return 'success'
  if (g === 'ACTIVITY') return 'primary'
  if (g === 'MAINT') return 'warning'
  return 'info'
}

function rowClass({ row }: { row: any }) {
  return row?.conflictFlag === 1 ? 'row-conflict' : ''
}

async function loadStats() {
  try {
    const s: any = (await getAdminScheduleStats()) || {}
    stats.total = s.total ?? 0
    stats.pending = s.pending ?? 0
    stats.sent = s.sent ?? 0
    stats.conflict = s.conflict ?? 0
    stats.typeDist = Array.isArray(s.typeDist) ? s.typeDist : []
  } catch {
    /* ignore */
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listAdminSchedule({
        keyword: keyword.value || undefined,
        status: status.value || undefined,
        scheduleType: scheduleType.value || undefined,
        conflict: conflict.value,
        startTime: timeRange.value?.[0],
        endTime: timeRange.value?.[1],
        limit: 200,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadStats(), load()])
}

async function openDetail(row: any) {
  try {
    cur.value = (await getAdminScheduleDetail(row.id)) || row
  } catch {
    cur.value = row
  }
  dlg.value = true
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`确认删除日程「${row.title}」？`, '删除确认', { type: 'warning' })
  await deleteAdminSchedule(row.id)
  ElMessage.success('已删除')
  refreshAll()
}

async function openSettings() {
  try {
    const s: any = (await getScheduleSettings()) || {}
    cfg.aheadFeeHours = Number(s.aheadFeeHours ?? 24)
    cfg.aheadActivityHours = Number(s.aheadActivityHours ?? 6)
    cfg.aheadMaintHours = Number(s.aheadMaintHours ?? 24)
    cfg.aheadConvenienceHours = Number(s.aheadConvenienceHours ?? 12)
    cfg.aheadNoticeHours = Number(s.aheadNoticeHours ?? 2)
  } catch {
    /* keep defaults */
  }
  cfgDlg.value = true
}

async function saveCfg() {
  cfgSaving.value = true
  try {
    await saveScheduleSettings({ ...cfg })
    ElMessage.success('提醒配置已保存')
    cfgDlg.value = false
  } finally {
    cfgSaving.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}
.sub { margin: 0; color: #8f959e; font-size: 13px; }
.ops { display: flex; gap: 8px; flex-shrink: 0; }
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
  padding: 12px;
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
}
.muted { color: #8f959e; font-size: 12px; }
.mt { margin-top: 4px; }
.qa { white-space: pre-wrap; line-height: 1.6; color: #334155; }

.insight-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.kpi {
  position: relative;
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px 12px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
  overflow: hidden;
}
.kpi::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
}
.kpi[data-tone='ink']::before { background: #3370ff; }
.kpi[data-tone='teal']::before { background: #14b8a6; }
.kpi[data-tone='green']::before { background: #22c55e; }
.kpi[data-tone='rose']::before { background: #f43f5e; }
.kpi-label { font-size: 13px; color: #646a73; margin-bottom: 8px; }
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  color: #1f2329;
  letter-spacing: -0.02em;
}
.kpi[data-tone='teal'] .kpi-value { color: #0f766e; }
.kpi[data-tone='green'] .kpi-value { color: #16a34a; }
.kpi[data-tone='rose'] .kpi-value { color: #e11d48; }
.kpi-foot { margin-top: 8px; font-size: 12px; color: #8f959e; }

.chart-card {
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
  min-height: 220px;
}
.chart-hd h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}
.chart-hd p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8f959e;
}
.type-chart { height: 200px; width: 100%; }
.chart-empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8f959e;
  font-size: 13px;
}

.section { margin-top: 16px; }
.sec-hd { font-weight: 600; color: #1f2329; margin-bottom: 8px; font-size: 14px; }
.sec-body {
  background: #f7f8fa;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 12px 14px;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.cfg-tip { margin: 0 0 12px; color: #8f959e; font-size: 13px; }

:deep(.row-conflict) {
  --el-table-tr-bg-color: #fff1f2;
}
:deep(.row-conflict td) {
  background: #fff1f2 !important;
}

@media (max-width: 1100px) {
  .insight-row { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .kpi-grid { grid-template-columns: 1fr; }
}
</style>
