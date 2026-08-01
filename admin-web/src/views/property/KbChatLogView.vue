<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>问答日志</h2>
        <p class="sub">记录业主和 AI 管家的对话，支持查看提问、回答与用户评价</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">刷新</el-button>
    </div>

    <!-- 当日统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">今日对话总数</div>
        <div class="stat-num">{{ stats.todayTotal ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">知识库命中问答</div>
        <div class="stat-num accent">{{ stats.kbHitCount ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">用户好评数量</div>
        <div class="stat-num good">{{ stats.goodCount ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">用户差评数量</div>
        <div class="stat-num bad">{{ stats.badCount ?? 0 }}</div>
      </div>
    </div>

    <!-- 高频咨询 TOP10 -->
    <div class="chart-panel">
      <div class="chart-hd">
        <div>
          <div class="chart-title">业主高频咨询问题 TOP10</div>
          <div class="chart-sub">按完整咨询提问聚合；已排除业务流程中的短答/确认语（如时间、姓名、不开车）</div>
        </div>
        <el-radio-group v-model="topRange" size="small" @change="loadStats">
          <el-radio-button value="today">今日</el-radio-button>
          <el-radio-button value="7d">近7天</el-radio-button>
          <el-radio-button value="30d">近30天</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="!topQuestions.length" class="chart-empty">暂无咨询数据</div>
      <v-chart v-else class="top-chart" :option="topChartOption" autoresize />
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索提问/回答/昵称"
        style="width: 240px"
        @keyup.enter="load"
      />
      <el-select v-model="source" clearable placeholder="来源" style="width: 140px" @change="load">
        <el-option label="问答知识库" value="kb" />
        <el-option label="大模型" value="llm" />
        <el-option label="业务流程" value="biz" />
        <el-option label="闲聊" value="chitchat" />
      </el-select>
      <el-select v-model="rating" clearable placeholder="评价" style="width: 120px" @change="load">
        <el-option label="好评" :value="1" />
        <el-option label="差评" :value="2" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无对话记录">
      <el-table-column prop="userNickname" label="业主" width="110">
        <template #default="{ row }">{{ row.userNickname || `#${row.userId || '—'}` }}</template>
      </el-table-column>
      <el-table-column prop="question" label="提问" min-width="180" show-overflow-tooltip />
      <el-table-column prop="answer" label="回答" min-width="220" show-overflow-tooltip />
      <el-table-column label="来源" width="120">
        <template #default="{ row }">
          <el-tag :type="sourceTag(row.sourceGroup || sourceGroupOf(row.source))" size="small" effect="plain">
            {{ row.sourceLabel || sourceLabelOf(row.source) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评价" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.rating === 1" type="success" size="small">好评</el-tag>
          <el-tag v-else-if="row.rating === 2" type="danger" size="small">差评</el-tag>
          <span v-else class="muted">未评价</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="对话详情" width="720px" destroy-on-close>
      <el-descriptions v-if="cur" :column="1" border>
        <el-descriptions-item label="业主">
          {{ cur.userNickname || `#${cur.userId}` }}
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ cur.createTime }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          {{ cur.sourceLabel || sourceLabelOf(cur.source) }}
          <span v-if="cur.source" class="src-code">（{{ cur.source }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="评价">
          {{ cur.ratingLabel }}
          <span v-if="cur.ratingRemark">（{{ cur.ratingRemark }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="提问">
          <div class="qa">{{ cur.question }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="回答">
          <div class="qa">{{ cur.answer }}</div>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 知识库召回排查区 -->
      <div v-if="cur && showRag(cur)" class="rag-box">
        <div class="rag-hd">知识库召回</div>
        <div class="rag-meta">
          <span v-if="cur.ragDocTitle">文档 / 问答：{{ cur.ragDocTitle }}</span>
          <span v-if="cur.ragScore != null || cur.ragScoreText">
            相似度：
            <b class="score">{{ cur.ragScoreText || Number(cur.ragScore).toFixed(2) }}</b>
          </span>
        </div>
        <div v-if="cur.ragChunk" class="rag-chunk">{{ cur.ragChunk }}</div>
        <div v-else class="muted">该条为知识库来源，但未记录召回片段（历史数据）。新对话将自动记录。</div>
      </div>

      <template v-if="canManage && cur && !cur.rating" #footer>
        <el-button type="success" @click="rate(1)">代录好评</el-button>
        <el-button type="danger" @click="rate(2)">代录差评</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useUserStore } from '@/stores/user'
import { getKbChatStats, listKbChatLogs, rateKbChatLog } from '@/api/kbDoc'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const user = useUserStore()
const canManage = computed(
  () =>
    user.role === 'property_admin' ||
    user.role === 'grid_worker' ||
    user.role === 'platform_admin',
)
const loading = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const rating = ref<number | undefined>()
const source = ref('')
const topRange = ref<'today' | '7d' | '30d'>('today')
const topQuestions = ref<{ question: string; count: number }[]>([])
const dlg = ref(false)
const cur = ref<any>(null)
const stats = reactive({
  todayTotal: 0,
  kbHitCount: 0,
  goodCount: 0,
  badCount: 0,
})

/** 图表配色：跟随页面主色，并兼容深色偏好 */
const chartTheme = computed(() => {
  const dark =
    typeof window !== 'undefined' &&
    (document.documentElement.classList.contains('dark') ||
      window.matchMedia('(prefers-color-scheme: dark)').matches)
  return dark
    ? {
        axis: '#94a3b8',
        split: 'rgba(148,163,184,0.18)',
        label: '#e2e8f0',
        bar: '#2dd4bf',
        tipBg: 'rgba(15,23,42,0.92)',
        tipText: '#f1f5f9',
      }
    : {
        axis: '#64748b',
        split: '#eef2f7',
        label: '#334155',
        bar: '#2f9e6f',
        tipBg: 'rgba(255,255,255,0.96)',
        tipText: '#1e293b',
      }
})

const topChartOption = computed(() => {
  const items = [...topQuestions.value].reverse()
  const labels = items.map((i) => {
    const q = String(i.question || '')
    return q.length > 22 ? `${q.slice(0, 22)}…` : q
  })
  const values = items.map((i) => i.count)
  const t = chartTheme.value
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: t.tipBg,
      borderWidth: 0,
      textStyle: { color: t.tipText, fontSize: 12 },
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        const full = items[p?.dataIndex ?? 0]?.question || p?.name || ''
        return `${full}<br/>咨询次数：<b>${p?.value ?? 0}</b>`
      },
    },
    grid: { left: 12, right: 28, top: 8, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: t.axis },
      splitLine: { lineStyle: { color: t.split } },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'category',
      data: labels,
      axisLabel: { color: t.label, width: 180, overflow: 'truncate' },
      axisLine: { lineStyle: { color: t.split } },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        data: values,
        barMaxWidth: 22,
        itemStyle: {
          color: t.bar,
          borderRadius: [0, 6, 6, 0],
        },
        label: {
          show: true,
          position: 'right',
          color: t.axis,
          fontSize: 12,
        },
      },
    ],
  }
})

function sourceGroupOf(src?: string) {
  if (!src) return 'other'
  if (String(src).startsWith('biz_')) return 'biz'
  if (['tenant_kb', 'tenant_kb_llm', 'tenant_doc', 'tenant_doc_llm'].includes(src)) return 'kb'
  if (['mock_chitchat', 'llm_chitchat'].includes(src)) return 'chitchat'
  if (['llm_general', 'llm_general_fallback', 'mock_general', 'llm_http', 'mock_llm', 'mock'].includes(src)) {
    return 'llm'
  }
  return 'other'
}

function sourceLabelOf(src?: string) {
  const g = sourceGroupOf(src)
  if (g === 'kb') return '问答知识库'
  if (g === 'llm') return '大模型'
  if (g === 'biz') return '业务流程'
  if (g === 'chitchat') return '闲聊'
  if (src === 'kb_miss') return '知识库未命中'
  return src || '—'
}

function sourceTag(g?: string) {
  if (g === 'kb') return 'success'
  if (g === 'llm') return 'primary'
  if (g === 'biz') return 'warning'
  if (g === 'chitchat') return 'info'
  return ''
}

function showRag(row: any) {
  if (!row) return false
  if (row.ragChunk) return true
  return (row.sourceGroup || sourceGroupOf(row.source)) === 'kb'
}

async function loadStats() {
  try {
    const s: any = (await getKbChatStats(topRange.value)) || {}
    stats.todayTotal = s.todayTotal ?? 0
    stats.kbHitCount = s.kbHitCount ?? 0
    stats.goodCount = s.goodCount ?? 0
    stats.badCount = s.badCount ?? 0
    topQuestions.value = Array.isArray(s.topQuestions) ? s.topQuestions : []
  } catch {
    /* ignore */
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listKbChatLogs({
        keyword: keyword.value || undefined,
        rating: rating.value,
        source: source.value || undefined,
        limit: 200,
      })) || []
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadStats(), load()])
}

function openDetail(row: any) {
  cur.value = row
  dlg.value = true
}

async function rate(r: number) {
  if (!cur.value?.id) return
  await rateKbChatLog(cur.value.id, { rating: r })
  ElMessage.success('已记录评价')
  dlg.value = false
  refreshAll()
}

onMounted(refreshAll)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; font-size: 13px; }
.qa { white-space: pre-wrap; line-height: 1.6; color: #334155; }
.src-code { color: #94a3b8; font-size: 12px; margin-left: 6px; }

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 16px 18px;
}
.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}
.stat-num {
  font-size: 28px;
  font-weight: 800;
  color: #1f2a28;
  line-height: 1.1;
}
.stat-num.accent { color: #2f9e6f; }
.stat-num.good { color: #16a34a; }
.stat-num.bad { color: #dc2626; }

.chart-panel {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 14px 16px 8px;
  margin-bottom: 16px;
}
.chart-hd {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.chart-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2a28;
}
.chart-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}
.top-chart { height: 320px; width: 100%; }
.chart-empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;
}

@media (prefers-color-scheme: dark) {
  .stat-card,
  .chart-panel {
    background: #1e293b;
    border-color: #334155;
  }
  .stat-label,
  .chart-sub { color: #94a3b8; }
  .stat-num,
  .chart-title { color: #f1f5f9; }
}

.rag-box {
  margin-top: 16px;
  padding: 14px 16px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
}
.rag-hd {
  font-weight: 700;
  color: #166534;
  margin-bottom: 8px;
}
.rag-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #475569;
  margin-bottom: 10px;
}
.score { color: #15803d; font-size: 16px; }
.rag-chunk {
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 13px;
  color: #334155;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
}

@media (max-width: 960px) {
  .stat-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
