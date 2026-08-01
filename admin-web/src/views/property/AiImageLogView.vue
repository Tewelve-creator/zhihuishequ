<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>AI 识图日志</h2>
        <p class="sub">查看居民报修识图结果、置信度与是否需手选，支持原图预览与工单溯源</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <div class="insight-row">
      <div class="kpi-grid">
        <div class="kpi" data-tone="ink">
          <div class="kpi-label">今日识图总量</div>
          <div class="kpi-value">{{ stats.todayTotal ?? 0 }}</div>
          <div class="kpi-foot">当日累计识别次数</div>
        </div>
        <div class="kpi" data-tone="green">
          <div class="kpi-label">AI识别</div>
          <div class="kpi-value">{{ stats.autoPass ?? 0 }}</div>
          <div class="kpi-foot">模型自动给出结果</div>
        </div>
        <div class="kpi" data-tone="amber">
          <div class="kpi-label">人工识别</div>
          <div class="kpi-value">{{ stats.needReview ?? 0 }}</div>
          <div class="kpi-foot">置信度偏低由业主手选</div>
        </div>
        <div class="kpi" data-tone="teal">
          <div class="kpi-label">高置信度 ≥90%</div>
          <div class="kpi-value">{{ stats.highConfidence ?? 0 }}</div>
          <div class="kpi-foot">高质量识别记录</div>
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-hd">
          <div>
            <h3>识别结果分布</h3>
            <p>AI识别 / 人工识别</p>
          </div>
        </div>
        <div v-if="!hasModeData" class="chart-empty">暂无识图数据</div>
        <v-chart v-else class="mode-chart" :option="ringOption" autoresize />
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="facilityType" clearable filterable allow-create placeholder="设施类型" style="width: 140px" @change="load">
        <el-option v-for="f in facilityOptions" :key="f" :label="f" :value="f" />
      </el-select>
      <el-select v-model="damageLevel" clearable filterable allow-create placeholder="损坏程度" style="width: 130px" @change="load">
        <el-option v-for="d in damageOptions" :key="d" :label="d" :value="d" />
        <el-option label="轻微" value="轻微" />
        <el-option label="中等" value="中等" />
        <el-option label="严重" value="严重" />
      </el-select>
      <el-select v-model="needManual" clearable placeholder="识别模式" style="width: 130px" @change="load">
        <el-option label="AI识别" :value="0" />
        <el-option label="人工识别" :value="1" />
      </el-select>
      <el-select v-model="confRange" clearable placeholder="置信度区间" style="width: 150px" @change="load">
        <el-option label="≥90%（高）" value="high" />
        <el-option label="70%~90%" value="mid" />
        <el-option label="＜70%（低）" value="low" />
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

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无识图日志">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片预览" width="90">
        <template #default="{ row }">
          <el-image
            v-if="previewSrc(row.imageUrl)"
            :src="previewSrc(row.imageUrl)"
            :preview-src-list="[previewSrc(row.imageUrl)]"
            fit="cover"
            class="thumb"
            preview-teleported
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户" width="80" />
      <el-table-column prop="facilityType" label="设施类型" width="120" />
      <el-table-column prop="locationHint" label="位置建议" min-width="140" show-overflow-tooltip />
      <el-table-column prop="damageLevel" label="损坏程度" width="100" />
      <el-table-column label="置信度" width="100">
        <template #default="{ row }">
          <span :class="['conf', confClass(row)]">{{ confText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="识别模式" width="100">
        <template #default="{ row }">
          <span :class="['mode-pill', row.needManual === 1 ? 'manual' : 'auto']">
            {{ row.needManual === 1 ? '人工识别' : 'AI识别' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 详情 -->
    <el-dialog v-model="dlg" title="识图详情" width="760px" destroy-on-close>
      <template v-if="cur">
        <div class="detail-grid">
          <div class="img-box">
            <div class="sec-hd">报修原图</div>
            <el-image
              v-if="previewSrc(cur.imageUrl)"
              :src="previewSrc(cur.imageUrl)"
              :preview-src-list="[previewSrc(cur.imageUrl)]"
              fit="contain"
              class="detail-img"
              preview-teleported
            />
            <div v-else class="chart-empty">暂无图片</div>
          </div>
          <div>
            <div class="sec-hd">AI 识别输出</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="设施类型">{{ cur.facilityType || '—' }}</el-descriptions-item>
              <el-descriptions-item label="推荐位置">{{ cur.locationHint || '—' }}</el-descriptions-item>
              <el-descriptions-item label="损坏等级">{{ cur.damageLevel || '—' }}</el-descriptions-item>
              <el-descriptions-item label="置信分数">
                <span :class="['conf', confClass(cur)]">{{ confText(cur) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="识别模式">
                <span :class="['mode-pill', cur.needManual === 1 ? 'manual' : 'auto']">
                  {{ cur.needManual === 1 ? '人工识别' : 'AI识别' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="时间">{{ fmt(cur.createTime) }}</el-descriptions-item>
            </el-descriptions>

            <div class="sec-hd mt16">关联报修工单</div>
            <div v-if="cur.orderId" class="order-box">
              <div>
                工单号：<b>{{ cur.orderNo || `BX${cur.orderId}` }}</b>
                <span class="muted">（ID {{ cur.orderId }}）</span>
              </div>
              <div class="muted">{{ cur.orderTitle || '—' }}</div>
              <el-button type="primary" size="small" class="mt8" @click="goOrder(cur)">
                一键跳转工单
              </el-button>
            </div>
            <div v-else class="muted">未关联报修工单（可能仅识别未提交）</div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { fileUrl } from '@/api/file'
import { deleteImageLog, getImageLogDetail, getImageLogStats, listImageLogs } from '@/api/aiFeature'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])

const router = useRouter()
const loading = ref(false)
const list = ref<any[]>([])
const facilityType = ref('')
const damageLevel = ref('')
const needManual = ref<number | undefined>()
const confRange = ref('')
const timeRange = ref<[string, string] | null>(null)
const facilityOptions = ref<string[]>([])
const damageOptions = ref<string[]>([])
const dlg = ref(false)
const cur = ref<any>(null)
const stats = reactive({
  todayTotal: 0,
  autoPass: 0,
  needReview: 0,
  highConfidence: 0,
  modeDist: [] as Array<{ name: string; value: number }>,
})

const hasModeData = computed(() => (stats.modeDist || []).some((x) => (x.value || 0) > 0))

const ringOption = computed(() => {
  const data = (stats.modeDist || []).filter((x) => (x.value || 0) > 0)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#646a73', fontSize: 12 },
    },
    color: ['#22c55e', '#f59e0b'],
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 13, fontWeight: 600, color: '#1f2329' },
          scaleSize: 4,
        },
        data: data.map((d) => ({ name: d.name, value: d.value })),
      },
    ],
  }
})

function previewSrc(u?: string) {
  return fileUrl(u)
}

function fmt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function confPct(row: any) {
  if (row?.confidencePct != null) return Number(row.confidencePct)
  if (row?.confidence == null) return null
  return Math.round(Number(row.confidence) * 100)
}

function confText(row: any) {
  const p = confPct(row)
  return p == null || Number.isNaN(p) ? '—' : `${p}%`
}

function confClass(row: any) {
  const level = row?.confidenceLevel
  if (level === 'high' || level === 'mid' || level === 'low') return level
  const p = confPct(row)
  if (p == null) return ''
  if (p >= 90) return 'high'
  if (p >= 70) return 'mid'
  return 'low'
}

function confParams() {
  if (confRange.value === 'high') return { confMin: 0.9, confMax: 1 }
  if (confRange.value === 'mid') return { confMin: 0.7, confMax: 0.8999 }
  if (confRange.value === 'low') return { confMin: 0, confMax: 0.6999 }
  return {}
}

async function loadStats() {
  try {
    const s: any = (await getImageLogStats()) || {}
    stats.todayTotal = s.todayTotal ?? 0
    stats.autoPass = s.autoPass ?? 0
    stats.needReview = s.needReview ?? 0
    stats.highConfidence = s.highConfidence ?? 0
    stats.modeDist = Array.isArray(s.modeDist) ? s.modeDist : []
    if (Array.isArray(s.facilityOptions)) facilityOptions.value = s.facilityOptions
    if (Array.isArray(s.damageOptions)) damageOptions.value = s.damageOptions
  } catch {
    /* ignore */
  }
}

async function load() {
  loading.value = true
  try {
    const conf = confParams()
    list.value =
      (await listImageLogs({
        facilityType: facilityType.value || undefined,
        damageLevel: damageLevel.value || undefined,
        needManual: needManual.value,
        confMin: conf.confMin,
        confMax: conf.confMax,
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
    cur.value = (await getImageLogDetail(row.id)) || row
  } catch {
    cur.value = row
  }
  dlg.value = true
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`确认删除识图日志 #${row.id}？`, '删除确认', { type: 'warning' })
  await deleteImageLog(row.id)
  ElMessage.success('已删除')
  if (cur.value?.id === row.id) dlg.value = false
  refreshAll()
}

function goOrder(row: any) {
  const path = row.orderPath || `/property/repair?id=${row.orderId}`
  dlg.value = false
  router.push(path)
}

onMounted(refreshAll)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}
.sub { margin: 0; color: #8f959e; font-size: 13px; }
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
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  border: 1px solid #e8eaed;
}

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
  background: #3370ff;
}
.kpi[data-tone='green']::before { background: #22c55e; }
.kpi[data-tone='amber']::before { background: #f59e0b; }
.kpi[data-tone='teal']::before { background: #14b8a6; }
.kpi[data-tone='ink']::before { background: #3370ff; }
.kpi-label {
  font-size: 13px;
  color: #646a73;
  margin-bottom: 8px;
}
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  color: #1f2329;
  letter-spacing: -0.02em;
}
.kpi[data-tone='green'] .kpi-value { color: #16a34a; }
.kpi[data-tone='amber'] .kpi-value { color: #d97706; }
.kpi[data-tone='teal'] .kpi-value { color: #0f766e; }
.kpi-foot {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
}

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
.mode-chart { height: 200px; width: 100%; }
.chart-empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8f959e;
  font-size: 13px;
}

.conf {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.conf.high { color: #15803d; background: #dcfce7; }
.conf.mid { color: #a16207; background: #fef9c3; }
.conf.low { color: #c2410c; background: #ffedd5; }

.mode-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}
.mode-pill.auto { color: #15803d; background: #dcfce7; }
.mode-pill.manual { color: #c2410c; background: #ffedd5; }

.detail-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
}
.sec-hd {
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 10px;
  font-size: 14px;
}
.mt16 { margin-top: 16px; }
.mt8 { margin-top: 8px; }
.detail-img {
  width: 100%;
  height: 280px;
  border-radius: 8px;
  background: #f5f6f7;
  border: 1px solid #e8eaed;
}
.order-box {
  background: #f0fdfa;
  border: 1px solid #99f6e4;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 13px;
  color: #334155;
}

@media (max-width: 1100px) {
  .insight-row { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .kpi-grid { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
