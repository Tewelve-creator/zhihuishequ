<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">用量与额度</h1>
          <p class="page-desc">基于今日调用量与租户 AI 额度监控使用情况（接口暂无独立月度用量字段）</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="stats-bar">
        <div class="stat-item">
          <span class="stat-label">今日调用量</span>
          <span class="stat-value">{{ kpis.used.toLocaleString('zh-CN') }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-label">总分配额度</span>
          <span class="stat-value">{{ kpis.quota.toLocaleString('zh-CN') }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-label">使用率</span>
          <span class="stat-value" :class="rateTone(kpis.rate)">{{ kpis.rate }}%</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-label">预计超额租户</span>
          <span class="stat-value" :class="kpis.overCount ? 'danger' : ''">
            {{ kpis.overCount }}
          </span>
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索租户"
            style="width: 200px"
          />
          <el-select v-model="band" clearable placeholder="使用状态" style="width: 140px">
            <el-option label="正常 &lt;70%" value="ok" />
            <el-option label="提醒 70–90%" value="warn" />
            <el-option label="预警 &gt;90%" value="alert" />
            <el-option label="超额 &gt;100%" value="over" />
          </el-select>
        </div>

        <el-table v-loading="loading" :data="filtered" class="saas-table" empty-text="暂无数据">
          <el-table-column label="租户" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.name || '--' }}</template>
          </el-table-column>
          <el-table-column label="套餐" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.packageName || '--' }}</template>
          </el-table-column>
          <el-table-column label="月度额度" width="110" align="right">
            <template #default="{ row }">{{ formatNum(row.aiQuota) }}</template>
          </el-table-column>
          <el-table-column label="已使用" width="100" align="right">
            <template #default="{ row }">
              <span :class="{ danger: usageRaw(row) > 100 }">{{ formatNum(usedOf(row)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余额度" width="100" align="right">
            <template #default="{ row }">{{ formatNum(remainOf(row)) }}</template>
          </el-table-column>
          <el-table-column label="使用率" min-width="180">
            <template #default="{ row }">
              <div class="rate-cell">
                <el-progress
                  :percentage="Math.min(100, Math.round(usageRaw(row)))"
                  :stroke-width="8"
                  :show-text="false"
                  :color="progressColor(row)"
                />
                <span class="rate-text" :class="bandClass(row)">
                  {{ Math.round(usageRaw(row)) }}% · {{ bandLabel(row) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <span class="status-pill" :class="statusTone(row.status)">
                {{ statusLabel(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link class="ops-link" @click="openEdit(row)">调整额度</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="visible" title="调整 AI 额度" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="租户">
          <span class="readonly">{{ editing?.name }}</span>
        </el-form-item>
        <el-form-item label="AI 额度（次/月）">
          <el-input-number v-model="quota" :min="0" style="width: 100%" />
        </el-form-item>
        <p class="form-hint">接口仅支持调整额度数值，不支持有效期、原因与操作人字段。</p>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listAiQuotaOverview, listPackages, pageTenants, updateTenantAiQuota } from '@/api/saas'

const STATUS_LABEL: Record<string, string> = {
  active: '正常',
  degraded: '降级',
  frozen: '冻结',
  disabled: '停用',
}

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const band = ref('')
const visible = ref(false)
const editing = ref<any>(null)
const quota = ref(0)

const kpis = computed(() => {
  const used = list.value.reduce((s, r) => s + usedOf(r), 0)
  const q = list.value.reduce((s, r) => s + (Number(r.aiQuota) || 0), 0)
  const rate = q > 0 ? Math.round((used / q) * 100) : 0
  const overCount = list.value.filter((r) => usageRaw(r) > 100).length
  return { used, quota: q, rate, overCount }
})

const filtered = computed(() => {
  let rows = list.value
  const kw = keyword.value.trim()
  if (kw) rows = rows.filter((r) => String(r.name || '').includes(kw))
  if (band.value) rows = rows.filter((r) => bandOf(r) === band.value)
  return rows
})

function usedOf(row: any) {
  return Number(row.todayAiCalls ?? row.todayCalls ?? 0)
}
function remainOf(row: any) {
  const q = Number(row.aiQuota) || 0
  return Math.max(0, q - usedOf(row))
}
function usageRaw(row: any) {
  const q = Number(row.aiQuota) || 0
  if (q <= 0) return usedOf(row) > 0 ? 101 : 0
  return (usedOf(row) / q) * 100
}
function bandOf(row: any) {
  const p = usageRaw(row)
  if (p > 100) return 'over'
  if (p > 90) return 'alert'
  if (p >= 70) return 'warn'
  return 'ok'
}
function bandLabel(row: any) {
  const m: Record<string, string> = {
    ok: '正常',
    warn: '提醒',
    alert: '预警',
    over: '超额',
  }
  return m[bandOf(row)]
}
function bandClass(row: any) {
  return `tone-${bandOf(row)}`
}
function progressColor(row: any) {
  const b = bandOf(row)
  if (b === 'over' || b === 'alert') return '#b42318'
  if (b === 'warn') return '#b54708'
  return '#2563eb'
}
function rateTone(rate: number) {
  if (rate > 90) return 'danger'
  if (rate >= 70) return 'warn'
  return ''
}
function formatNum(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return n.toLocaleString('zh-CN')
}
function statusLabel(s: string) {
  return STATUS_LABEL[s] || s || '--'
}
function statusTone(s: string) {
  if (s === 'active') return 'ok'
  if (s === 'degraded') return 'warn'
  return 'off'
}

async function load() {
  loading.value = true
  try {
    const [quotaRows, tenants, pkgs] = await Promise.all([
      listAiQuotaOverview(),
      pageTenants({ page: 1, size: 500 }).catch(() => ({ records: [] })),
      listPackages().catch(() => []),
    ])
    const pkgMap: Record<number, string> = {}
    for (const p of pkgs || []) pkgMap[p.id] = p.name
    const tenantPkg: Record<number, number> = {}
    for (const t of tenants?.records || []) tenantPkg[t.id] = t.packageId
    list.value = (quotaRows || []).map((r: any) => ({
      ...r,
      packageName: pkgMap[tenantPkg[r.id]] || '--',
    }))
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openEdit(row: any) {
  editing.value = row
  quota.value = Number(row.aiQuota) || 0
  visible.value = true
}

async function save() {
  if (!editing.value) return
  saving.value = true
  try {
    await updateTenantAiQuota(editing.value.id, quota.value)
    ElMessage.success('额度已更新')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.saas-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.saas-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
  max-width: 560px;
}
.icon-btn {
  width: 36px;
  padding: 8px;
  border-color: #e4e7ec;
  background: #fff;
  color: #667085;
}
.stats-bar {
  display: flex;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: auto;
}
.stat-item {
  flex: 1;
  min-width: 120px;
  padding: 14px 16px;
}
.stat-label {
  display: block;
  font-size: 12px;
  color: #667085;
}
.stat-value {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  font-weight: 650;
  color: #182230;
  font-variant-numeric: tabular-nums;
}
.stat-value.danger {
  color: #b42318;
}
.stat-value.warn {
  color: #b54708;
}
.stat-divider {
  width: 1px;
  background: #e4e7ec;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 16px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.saas-table :deep(.el-table__header th) {
  background: #f9fafb;
  color: #667085;
}
.rate-cell {
  display: grid;
  gap: 4px;
}
.rate-text {
  font-size: 12px;
  color: #667085;
}
.rate-text.tone-ok {
  color: #067647;
}
.rate-text.tone-warn {
  color: #b54708;
}
.rate-text.tone-alert,
.rate-text.tone-over {
  color: #b42318;
}
.danger {
  color: #b42318;
  font-weight: 550;
}
.status-pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
}
.status-pill.ok {
  background: #ecfdf3;
  color: #067647;
}
.status-pill.warn {
  background: #fffaeb;
  color: #b54708;
}
.status-pill.off {
  background: #f2f4f7;
  color: #667085;
}
.ops-link {
  color: #2563eb !important;
  font-weight: 500;
}
.readonly {
  color: #182230;
  font-weight: 550;
}
.form-hint {
  margin: 0;
  font-size: 12px;
  color: #98a2b3;
  line-height: 1.5;
}
@media (max-width: 768px) {
  .saas-page {
    padding: 16px 12px;
  }
  .stats-bar {
    flex-wrap: wrap;
  }
  .stat-divider {
    display: none;
  }
  .stat-item {
    flex: 1 1 45%;
  }
}
</style>
