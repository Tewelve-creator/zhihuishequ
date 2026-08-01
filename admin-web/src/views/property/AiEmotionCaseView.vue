<template>
  <div class="page-card">
    <!--
      隐私隔离：居民对话原文仅授权物业人员查看，禁止导出外泄。
      本页不做导出按钮；详情弹窗内原文仅在线查看。
    -->
    <div class="page-header">
      <div>
        <h2>情绪调解工单</h2>
        <p class="sub">隐私隔离：原文仅物业管理员/物业工作人员可见，请勿外传</p>
      </div>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="请严格保护居民隐私，仅用于轻调解与社区治理"
      style="margin-bottom: 12px"
    />

    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-label">工单总数</div>
        <div class="kpi-value">{{ stats.total }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">待处理工单</div>
        <div class="kpi-value warn">{{ stats.pending }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">已办结工单</div>
        <div class="kpi-value ok">{{ stats.closed }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">高危情绪工单</div>
        <div class="kpi-value danger">{{ stats.highRisk }}</div>
        <div class="kpi-hint">标签含愤怒 / 激烈冲突</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px">
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="handling" />
        <el-option label="已办结" value="closed" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="居民姓名"
        style="width: 140px"
        @keyup.enter="load"
      />
      <el-select v-model="filters.emotionTag" clearable placeholder="情绪标签" style="width: 140px">
        <el-option label="愤怒" value="愤怒" />
        <el-option label="激烈冲突" value="激烈冲突" />
        <el-option label="焦虑" value="焦虑" />
        <el-option label="委屈" value="委屈" />
        <el-option label="不满" value="不满" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-tag v-if="demoMode" type="info" size="small">演示数据</el-tag>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      empty-text="暂无调解工单"
    >
      <el-table-column prop="id" label="单号" width="80" />
      <el-table-column label="居民" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ residentName(row) }}</template>
      </el-table-column>
      <el-table-column label="情绪" width="90">
        <template #default="{ row }">{{ sentimentText(row.sentiment) }}</template>
      </el-table-column>
      <el-table-column prop="emotionTags" label="标签" width="120" />
      <el-table-column prop="content" label="原文（保密）" min-width="200" show-overflow-tooltip />
      <el-table-column label="疏导建议" min-width="180">
        <template #default="{ row }">
          <span class="advice-cell">{{ adviceText(row.advice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small" round>
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canAccept(row)"
            link
            type="primary"
            @click="onAccept(row)"
          >
            受理
          </el-button>
          <el-button
            v-if="canClose(row)"
            link
            type="success"
            @click="onClose(row)"
          >
            结案
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="情绪调解工单详情" width="640px" destroy-on-close>
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="block">
          <el-descriptions-item label="单号">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="居民">{{ residentName(current) }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ current.userPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(current.createTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">居民原始对话全文</div>
        <div class="privacy-box">{{ current.content || '—' }}</div>

        <div class="sec-title">AI 识别结果</div>
        <el-descriptions :column="2" border size="small" class="block">
          <el-descriptions-item label="情绪类型">{{ sentimentText(current.sentiment) }}</el-descriptions-item>
          <el-descriptions-item label="情绪标签">{{ current.emotionTags || '—' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="current.highRisk ? 'danger' : 'info'" size="small">
              {{ current.riskLevel || (current.highRisk ? '高危' : '一般') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">完整 AI 疏导建议</div>
        <div class="text-box">{{ adviceText(current.advice) }}</div>

        <div class="sec-title">调解处置记录</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="调解过程">
            {{ current.mediateRecord || current.handleRemark || '（受理后可填写）' }}
          </el-descriptions-item>
          <el-descriptions-item label="处置结果">
            {{ current.handleResult || '（结案时填写）' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="current && canAccept(current)" type="primary" @click="onAccept(current)">受理</el-button>
        <el-button v-if="current && canClose(current)" type="success" @click="onClose(current)">结案</el-button>
        <el-button v-if="current" type="danger" plain @click="onDelete(current)">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  deleteEmotionCase,
  getEmotionCaseStats,
  listEmotionCases,
  updateEmotionCase,
} from '@/api/aiFeature'

const user = useUserStore()
const loading = ref(false)
const demoMode = ref(false)
const list = ref<any[]>([])
const detailVisible = ref(false)
const current = ref<any>(null)

const filters = reactive({
  status: '' as string,
  keyword: '' as string,
  emotionTag: '' as string,
  dateRange: null as [string, string] | null,
})

const stats = reactive({
  total: 0,
  pending: 0,
  closed: 0,
  highRisk: 0,
})

/** 演示阶段模拟数据（真实登录失败或演示 token 时使用） */
const DEMO_LIST = [
  {
    id: 1001,
    userId: 12,
    userName: '王芳',
    userNickname: '王芳',
    userPhone: '138****2201',
    sentiment: 'negative',
    emotionTags: '愤怒,激烈冲突',
    content: '楼上装修噪音已经三天了，我跟物业说了好几次都没人管，实在忍不了了！',
    advice: '先安抚居民情绪，确认装修时段是否合规；建议上门协调双方沟通时段，必要时出具书面提醒。',
    status: 'pending',
    createTime: '2026-07-30 09:20:00',
    highRisk: true,
    riskLevel: '高危',
    mediateRecord: '',
    handleResult: '',
  },
  {
    id: 1002,
    userId: 18,
    userName: '李强',
    userNickname: '李强',
    userPhone: '139****6633',
    sentiment: 'negative',
    emotionTags: '焦虑,不满',
    content: '停车位被占用，上班要迟到，心里很着急。',
    advice: '引导居民提供车牌与时间照片；联系秩序维护人员现场核查并反馈处理进度。',
    status: 'handling',
    createTime: '2026-07-29 16:40:00',
    highRisk: false,
    riskLevel: '中等',
    mediateRecord: '已电话安抚，安排安保核查车位占用情况。',
    handleResult: '',
  },
  {
    id: 1003,
    userId: 7,
    userName: '赵敏',
    userNickname: '赵敏',
    userPhone: '136****8899',
    sentiment: 'negative',
    emotionTags: '委屈',
    content: '公共区域堆放杂物影响通行，多次反映未解决。',
    advice: '核实堆放位置与责任人，限时清理并回访居民。',
    status: 'closed',
    createTime: '2026-07-28 11:05:00',
    highRisk: false,
    riskLevel: '一般',
    mediateRecord: '上门沟通并通知责任住户清理。',
    handleResult: '杂物已清理，居民确认满意，结案。',
  },
  {
    id: 1004,
    userId: 25,
    userName: '陈磊',
    userNickname: '陈磊',
    userPhone: '137****1122',
    sentiment: 'negative',
    emotionTags: '愤怒',
    content: '电梯里被人恶意按楼层，感觉被针对了，非常生气。',
    advice: '倾听诉求、避免对立；调取监控核实后反馈，必要时启动调解。',
    status: 'pending',
    createTime: '2026-07-31 08:15:00',
    highRisk: true,
    riskLevel: '高危',
    mediateRecord: '',
    handleResult: '',
  },
]

function residentName(row: any) {
  const name = row?.userName || row?.userNickname
  return name && String(name).trim() ? String(name).trim() : '未知居民'
}

function formatTime(t?: string) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function sentimentText(s?: string) {
  if (s === 'negative') return '负面'
  if (s === 'positive') return '正面'
  if (s === 'neutral') return '中性'
  return s || '—'
}

function statusText(s?: string) {
  const map: Record<string, string> = {
    pending: '待处理',
    handling: '处理中',
    processing: '处理中',
    closed: '已办结',
    done: '已办结',
  }
  return (s && map[s]) || s || '—'
}

function statusTagType(s?: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  if (s === 'pending') return 'info'
  if (s === 'handling' || s === 'processing') return 'primary'
  if (s === 'closed' || s === 'done') return 'success'
  return 'info'
}

function adviceText(s?: string) {
  if (!s) return '—'
  return String(s).replace(/网格员/g, '物业工作人员')
}

function isHighRisk(row: any) {
  if (row?.highRisk) return true
  const tags = String(row?.emotionTags || '')
  return tags.includes('愤怒') || tags.includes('激烈冲突')
}

function canAccept(row: any) {
  return row?.status === 'pending'
}

function canClose(row: any) {
  // 待处理先受理；结案仅处理中可见
  return row?.status === 'handling' || row?.status === 'processing'
}

function applyDemoStats(rows: any[]) {
  stats.total = rows.length
  stats.pending = rows.filter((r) => r.status === 'pending').length
  stats.closed = rows.filter((r) => r.status === 'closed' || r.status === 'done').length
  stats.highRisk = rows.filter((r) => isHighRisk(r)).length
}

function filterDemo(rows: any[]) {
  return rows.filter((r) => {
    if (filters.status) {
      if (filters.status === 'closed') {
        if (!(r.status === 'closed' || r.status === 'done')) return false
      } else if (filters.status === 'handling') {
        if (!(r.status === 'handling' || r.status === 'processing')) return false
      } else if (r.status !== filters.status) {
        return false
      }
    }
    if (filters.keyword) {
      const kw = filters.keyword.trim()
      const hit =
        String(r.userName || '').includes(kw) ||
        String(r.userNickname || '').includes(kw) ||
        String(r.userId || '').includes(kw)
      if (!hit) return false
    }
    if (filters.emotionTag && !String(r.emotionTags || '').includes(filters.emotionTag)) return false
    if (filters.dateRange?.[0] && filters.dateRange?.[1]) {
      const day = String(r.createTime || '').slice(0, 10)
      if (day < filters.dateRange[0] || day > filters.dateRange[1]) return false
    }
    return true
  })
}

async function load() {
  loading.value = true
  try {
    if (!user.hasRealToken) {
      demoMode.value = true
      const rows = filterDemo(DEMO_LIST.map((x) => ({ ...x })))
      list.value = rows
      applyDemoStats(DEMO_LIST)
      return
    }
    const [rows, st] = await Promise.all([
      listEmotionCases({
        status: filters.status || undefined,
        keyword: filters.keyword || undefined,
        emotionTag: filters.emotionTag || undefined,
        startDate: filters.dateRange?.[0],
        endDate: filters.dateRange?.[1],
        limit: 80,
      }),
      getEmotionCaseStats().catch(() => null),
    ])
    demoMode.value = false
    list.value = Array.isArray(rows) ? rows : []
    if (st) {
      stats.total = Number(st.total) || 0
      stats.pending = Number(st.pending) || 0
      stats.closed = Number(st.closed) || 0
      stats.highRisk = Number(st.highRisk) || 0
    } else {
      applyDemoStats(list.value)
    }
  } catch {
    demoMode.value = true
    const rows = filterDemo(DEMO_LIST.map((x) => ({ ...x })))
    list.value = rows
    applyDemoStats(DEMO_LIST)
  } finally {
    loading.value = false
  }
}

function openDetail(row: any) {
  current.value = { ...row }
  detailVisible.value = true
}

async function onAccept(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写调解过程记录', '受理工单', {
      inputType: 'textarea',
      inputPlaceholder: '调解过程、沟通要点等',
      confirmButtonText: '确认受理',
      cancelButtonText: '取消',
      inputValidator: (v) => (!!v && !!String(v).trim()) || '请填写调解记录',
    })
    if (demoMode.value) {
      row.status = 'handling'
      row.mediateRecord = value
      row.handleRemark = value
      if (current.value?.id === row.id) {
        current.value = { ...row }
      }
      applyDemoStats(list.value)
      ElMessage.success('已受理（演示）')
      return
    }
    await updateEmotionCase(row.id, { status: 'handling', mediateRecord: value, handleRemark: value })
    ElMessage.success('已受理，状态变为处理中')
    detailVisible.value = false
    await load()
  } catch {
    /* cancel */
  }
}

async function onClose(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写最终处置结果', '结案', {
      inputType: 'textarea',
      inputPlaceholder: '处置结果、回访情况等',
      confirmButtonText: '确认结案',
      cancelButtonText: '取消',
      inputValidator: (v) => (!!v && !!String(v).trim()) || '请填写处置结果',
    })
    if (demoMode.value) {
      row.status = 'closed'
      row.handleResult = value
      if (current.value?.id === row.id) {
        current.value = { ...row }
      }
      applyDemoStats(list.value)
      ElMessage.success('已结案（演示）')
      return
    }
    await updateEmotionCase(row.id, { status: 'closed', handleResult: value, handleRemark: value })
    ElMessage.success('已结案')
    detailVisible.value = false
    await load()
  } catch {
    /* cancel */
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除情绪调解工单 #${row.id}（${residentName(row)}）？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (demoMode.value) {
    list.value = list.value.filter((x) => x.id !== row.id)
    applyDemoStats(list.value)
    if (current.value?.id === row.id) detailVisible.value = false
    ElMessage.success('已删除（演示）')
    return
  }
  await deleteEmotionCase(row.id)
  ElMessage.success('已删除')
  if (current.value?.id === row.id) detailVisible.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
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

.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.kpi-card {
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(31, 35, 41, 0.04);
  min-height: 86px;
  box-sizing: border-box;
}
.kpi-label {
  font-size: 13px;
  color: #646a73;
}
.kpi-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  color: #1f2329;
  line-height: 1;
}
.kpi-value.warn { color: #d48806; }
.kpi-value.ok { color: #389e0d; }
.kpi-value.danger { color: #cf1322; }
.kpi-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.advice-cell {
  white-space: normal;
  word-break: break-word;
  line-height: 1.45;
  color: #1f2329;
}

.sec-title {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}
.block { margin-bottom: 4px; }
.privacy-box,
.text-box {
  background: #f7f8fa;
  border: 1px solid #e8eaed;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #1f2329;
  white-space: pre-wrap;
  word-break: break-word;
}
.privacy-box {
  background: #fffbe6;
  border-color: #ffe58f;
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
