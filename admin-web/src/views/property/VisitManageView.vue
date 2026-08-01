<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>访客预约通行</h2>
        <p class="sub">业主预约免审核；支持时间/手机筛选、通行日志与二维码窗口查看</p>
      </div>
      <div class="hdr-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" plain :disabled="!list.length" @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi" data-tone="green">
        <div class="kpi-label">生效中</div>
        <div class="kpi-value">{{ kpi.active }}</div>
        <div class="kpi-foot">当前可通行预约</div>
      </div>
      <div class="kpi" data-tone="teal">
        <div class="kpi-label">待生效</div>
        <div class="kpi-value">{{ kpi.pending }}</div>
        <div class="kpi-foot">未到访开始时间</div>
      </div>
      <div class="kpi" data-tone="blue">
        <div class="kpi-label">已通行</div>
        <div class="kpi-value">{{ kpi.passed }}</div>
        <div class="kpi-foot">本页统计（已核销）</div>
      </div>
      <div class="kpi" data-tone="gray">
        <div class="kpi-label">记录总数</div>
        <div class="kpi-value">{{ total }}</div>
        <div class="kpi-foot">筛选条件下合计</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 140px" @change="onSearch">
        <el-option label="生效中" value="active" />
        <el-option label="待生效" value="pending_effect" />
        <el-option label="已通行" value="checked_in" />
        <el-option label="已过期" value="expired" />
        <el-option label="业主撤销" value="cancelled" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="访客姓名/手机号"
        style="width: 200px"
        @keyup.enter="onSearch"
      />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="width: 360px"
        @change="onSearch"
      />
      <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无访客预约">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="visitorName" label="访客" width="100" />
      <el-table-column prop="visitorPhone" label="手机号" width="120">
        <template #default="{ row }">{{ row.visitorPhone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="hostHouse" label="拜访房屋" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.hostHouse || '—' }}</template>
      </el-table-column>
      <el-table-column prop="visitReason" label="事由" width="110" show-overflow-tooltip />
      <el-table-column label="驾车/车牌" width="120">
        <template #default="{ row }">
          {{ row.driveFlag === 1 ? row.plateNo || '驾车' : '否' }}
        </template>
      </el-table-column>
      <el-table-column prop="visitTime" label="到访时间" width="160">
        <template #default="{ row }">{{ fmt(row.visitTime) }}</template>
      </el-table-column>
      <el-table-column prop="expireTime" label="有效至" width="160">
        <template #default="{ row }">{{ fmt(row.expireTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span :class="['tone-pill', row.statusTone || 'gray']">{{ row.statusLabel || statusFallback(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="canCheckin" link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="onSearch"
      />
    </div>

    <el-dialog v-model="dlg" title="预约详情" width="780px" destroy-on-close>
      <template v-if="cur">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="访客">{{ cur.visitorName }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ cur.visitorPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="业主">{{ cur.hostNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item label="房屋">{{ cur.hostHouse || '—' }}</el-descriptions-item>
          <el-descriptions-item label="小区">{{ cur.communityName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="事由">{{ cur.visitReason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="到访">{{ fmt(cur.visitTime) }}</el-descriptions-item>
          <el-descriptions-item label="有效至">{{ fmt(cur.expireTime) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="['tone-pill', cur.statusTone || 'gray']">{{ cur.statusLabel }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="通行时间">{{ fmt(cur.checkinTime) || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="sec-hd mt16">通行链接（无静态二维码）</div>
        <div class="qr-box">
          <div class="qr-meta">
            <div>模式：{{ cur.accessMode === 'ONCE' ? '单次通行' : cur.accessMode || 'ONCE' }}</div>
            <div class="muted">二维码仅访客打开链接后由服务端实时校验签发；后台不展示可保存图片</div>
            <div class="break link-line">
              <span>链接：{{ cur.passUrl || '—' }}</span>
              <el-button v-if="cur.passUrl" link type="primary" @click="copyLink(cur.passUrl)">复制</el-button>
            </div>
            <div class="muted break">Token：{{ cur.passToken || '—' }}</div>
          </div>
        </div>

        <div class="sec-hd mt16">访问 / 通行日志</div>
        <el-table :data="cur.accessLogs || []" size="small" empty-text="暂无日志" max-height="280">
          <el-table-column prop="createTime" label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="resultLabel" label="结果" width="90">
            <template #default="{ row }">
              <span :class="resultClass(row.result)">{{ row.resultLabel || row.result }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="channelLabel" label="渠道" width="110" />
          <el-table-column prop="clientIp" label="IP" width="120">
            <template #default="{ row }">{{ row.clientIp || '—' }}</template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.failReason || '—' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="dlg = false">关闭</el-button>
        <el-button v-if="canCheckin && cur" type="danger" plain @click="onDelete(cur)">删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  deleteAdminVisit,
  getAdminVisitDetail,
  listAdminVisits,
  type VisitItem,
} from '@/api/visit'

const user = useUserStore()
const canCheckin = computed(
  () =>
    user.hasRealToken &&
    (user.role === 'property_admin' ||
      user.role === 'grid_worker' ||
      user.role === 'platform_admin'),
)

const loading = ref(false)
const list = ref<VisitItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const timeRange = ref<string[] | null>(null)
const dlg = ref(false)
const cur = ref<VisitItem | null>(null)

const kpi = reactive({ active: 0, pending: 0, passed: 0 })

function fmt(v?: string) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

function statusFallback(s?: string) {
  if (s === 'approved') return '生效中'
  if (s === 'checked_in' || s === 'checked') return '已通行'
  if (s === 'expired') return '已过期'
  if (s === 'cancelled') return '业主撤销'
  return s || '—'
}

function resultClass(r?: string) {
  if (r === 'SUCCESS') return 'ok'
  if (r === 'OPEN') return 'open'
  return 'fail'
}

async function copyLink(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.info(url)
  }
}

function recomputeKpi() {
  kpi.active = list.value.filter((r) => r.statusTone === 'green').length
  kpi.pending = list.value.filter((r) => r.statusTone === 'teal').length
  kpi.passed = list.value.filter((r) => r.status === 'checked_in' || r.statusTone === 'blue').length
}

async function load() {
  loading.value = true
  try {
    const res = await listAdminVisits({
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1],
      page: page.value,
      size: size.value,
    })
    list.value = res?.records || []
    total.value = Number(res?.total || 0)
    recomputeKpi()
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

async function openDetail(row: VisitItem) {
  try {
    cur.value = (await getAdminVisitDetail(row.id)) || row
  } catch {
    cur.value = row
  }
  dlg.value = true
}

async function onDelete(row: VisitItem) {
  await ElMessageBox.confirm(
    `确认删除访客预约「${row.visitorName}」？通行链接将立即失效，且不可恢复。`,
    '删除确认',
    { type: 'warning' },
  )
  await deleteAdminVisit(row.id)
  ElMessage.success('已删除')
  if (cur.value?.id === row.id) dlg.value = false
  load()
}

function onExport() {
  exportExcel(
    list.value.map((r) => ({
      ID: r.id,
      访客: r.visitorName,
      手机: r.visitorPhone || '',
      房屋: r.hostHouse || '',
      事由: r.visitReason || '',
      车牌: r.plateNo || '',
      到访时间: fmt(r.visitTime),
      有效至: fmt(r.expireTime),
      状态: r.statusLabel || statusFallback(r.status),
      通行时间: fmt(r.checkinTime),
    })),
    `访客预约_${new Date().toISOString().slice(0, 10)}`,
    '访客预约',
  )
}

onMounted(load)
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
.sub {
  margin: 0;
  color: #8f959e;
  font-size: 13px;
}
.hdr-actions {
  display: flex;
  gap: 8px;
}
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
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
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
.kpi[data-tone='teal']::before { background: #14b8a6; }
.kpi[data-tone='blue']::before { background: #3b82f6; }
.kpi[data-tone='gray']::before { background: #94a3b8; }
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
}
.kpi[data-tone='green'] .kpi-value { color: #16a34a; }
.kpi[data-tone='teal'] .kpi-value { color: #0f766e; }
.kpi[data-tone='blue'] .kpi-value { color: #2563eb; }
.kpi-foot {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
}
.tone-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  background: #f1f5f9;
  color: #64748b;
}
.tone-pill.green { color: #15803d; background: #dcfce7; }
.tone-pill.teal { color: #0f766e; background: #ccfbf1; }
.tone-pill.blue { color: #1d4ed8; background: #dbeafe; }
.tone-pill.orange { color: #c2410c; background: #ffedd5; }
.tone-pill.gray { color: #64748b; background: #f1f5f9; }
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.sec-hd {
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 10px;
  font-size: 14px;
}
.mt16 { margin-top: 16px; }
.qr-box {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  background: #fafbfc;
}
.qr-meta {
  flex: 1;
  font-size: 13px;
  color: #1f2329;
  line-height: 1.7;
}
.break {
  word-break: break-all;
}
.link-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 4px;
}
.muted { color: #8f959e; font-size: 12px; }
.ok { color: #16a34a; font-weight: 600; }
.open { color: #0f766e; font-weight: 600; }
.fail { color: #dc2626; font-weight: 600; }
@media (max-width: 960px) {
  .kpi-grid { grid-template-columns: 1fr 1fr; }
}
</style>
