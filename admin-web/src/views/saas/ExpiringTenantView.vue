<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">到期预警</h1>
          <p class="page-desc">按真实到期数据跟踪续费风险，支持发起续费与到期扫描</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
          <el-button type="warning" :loading="scanning" @click="onScan">执行到期扫描</el-button>
        </div>
      </header>

      <div class="stats-bar" aria-label="到期概览">
        <button
          type="button"
          class="stat-item"
          :class="{ active: filterKey === 'd7' }"
          @click="filterKey = 'd7'"
        >
          <span class="stat-label">7天内到期</span>
          <span class="stat-value danger">{{ stats.d7 }}</span>
        </button>
        <div class="stat-divider" />
        <button
          type="button"
          class="stat-item"
          :class="{ active: filterKey === 'd30' }"
          @click="filterKey = 'd30'"
        >
          <span class="stat-label">30天内到期</span>
          <span class="stat-value warn">{{ stats.d30 }}</span>
        </button>
        <div class="stat-divider" />
        <button
          type="button"
          class="stat-item"
          :class="{ active: filterKey === 'expired' }"
          @click="filterKey = 'expired'"
        >
          <span class="stat-label">已过期</span>
          <span class="stat-value danger">{{ stats.expired }}</span>
        </button>
        <div class="stat-divider" />
        <div class="stat-item muted-card" title="接口未返回自动续费失败数据">
          <span class="stat-label">自动续费失败</span>
          <span class="stat-value muted">--</span>
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <span class="toolbar-label">数据范围</span>
          <el-select v-model="withinDays" style="width: 120px" @change="load">
            <el-option label="30 天内" :value="30" />
            <el-option label="60 天内" :value="60" />
            <el-option label="90 天内" :value="90" />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索租户"
            style="width: 180px"
          />
          <el-button link type="primary" @click="filterKey = 'all'">清除筛选</el-button>
        </div>

        <div class="table-wrap">
          <el-table
            v-loading="loading"
            :data="filtered"
            class="saas-table"
            empty-text="暂无到期租户"
          >
            <el-table-column label="租户" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="name-cell">
                  <span>{{ row.name || '--' }}</span>
                  <span class="risk-pill" :class="riskTone(row)">{{ riskLabel(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="当前套餐" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.packageName || '--' }}</template>
            </el-table-column>
            <el-table-column label="到期时间" width="158">
              <template #default="{ row }">{{ formatTime(row.expireTime) }}</template>
            </el-table-column>
            <el-table-column label="剩余天数" width="100" align="right">
              <template #default="{ row }">
                <span :class="daysClass(row.daysLeft)">
                  {{ row.daysLeft == null ? '--' : row.daysLeft }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="联系人" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contactName || '--' }}</template>
            </el-table-column>
            <el-table-column label="联系电话" width="120">
              <template #default="{ row }">{{ maskPhone(row.contactPhone) }}</template>
            </el-table-column>
            <el-table-column label="续费状态" width="96">
              <template #default="{ row }">
                <span class="status-pill" :class="statusTone(row.status)">
                  {{ statusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="负责人" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.ownerName || '--' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button link class="ops-link" @click="goRenew(row)">发起续费</el-button>
                  <el-dropdown trigger="click" @command="(cmd: string) => onMore(row, cmd)">
                    <el-button link class="ops-more" aria-label="更多">
                      <el-icon><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="order">创建订单</el-dropdown-item>
                        <el-dropdown-item command="tenant">查看租户</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MoreFilled, Refresh } from '@element-plus/icons-vue'
import { listExpiringTenants, pageTenants, runExpireScan } from '@/api/saas'

const STATUS_LABEL: Record<string, string> = {
  active: '正常',
  degraded: '降级',
  frozen: '冻结',
  disabled: '停用',
}

const router = useRouter()
const loading = ref(false)
const scanning = ref(false)
const withinDays = ref(60)
const list = ref<any[]>([])
const keyword = ref('')
const filterKey = ref<'all' | 'd7' | 'd30' | 'expired'>('all')

const stats = computed(() => {
  const rows = list.value
  return {
    d7: rows.filter((r) => daysOf(r) != null && daysOf(r)! >= 0 && daysOf(r)! <= 7).length,
    d30: rows.filter((r) => daysOf(r) != null && daysOf(r)! >= 0 && daysOf(r)! <= 30).length,
    expired: rows.filter((r) => daysOf(r) != null && daysOf(r)! < 0).length,
  }
})

const filtered = computed(() => {
  let rows = list.value
  const kw = keyword.value.trim()
  if (kw) rows = rows.filter((r) => String(r.name || '').includes(kw))
  if (filterKey.value === 'd7') {
    rows = rows.filter((r) => daysOf(r) != null && daysOf(r)! >= 0 && daysOf(r)! <= 7)
  } else if (filterKey.value === 'd30') {
    rows = rows.filter((r) => daysOf(r) != null && daysOf(r)! >= 0 && daysOf(r)! <= 30)
  } else if (filterKey.value === 'expired') {
    rows = rows.filter((r) => daysOf(r) != null && daysOf(r)! < 0)
  }
  return rows
})

function daysOf(row: any): number | null {
  if (row.daysLeft == null || row.daysLeft === '') return null
  const n = Number(row.daysLeft)
  return Number.isFinite(n) ? n : null
}

function riskLabel(row: any) {
  const d = daysOf(row)
  if (d == null) return '未知'
  if (d < 0 || d <= 7) return '紧急'
  if (d <= 30) return '较高'
  if (d <= 60) return '一般'
  return '关注'
}

function riskTone(row: any) {
  const d = daysOf(row)
  if (d == null) return 'tone-off'
  if (d < 0 || d <= 7) return 'tone-urgent'
  if (d <= 30) return 'tone-high'
  return 'tone-mid'
}

function daysClass(d: unknown) {
  const n = Number(d)
  if (!Number.isFinite(n)) return ''
  if (n < 0 || n <= 7) return 'days danger'
  if (n <= 30) return 'days warn'
  return 'days'
}

function statusLabel(s: string) {
  return STATUS_LABEL[s] || s || '--'
}
function statusTone(s: string) {
  if (s === 'active') return 'ok'
  if (s === 'degraded') return 'warn'
  return 'off'
}
function formatTime(v: unknown) {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}
function maskPhone(p?: string) {
  if (!p) return '--'
  const s = String(p)
  if (s.length < 7) return s
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

function goRenew(row: any) {
  router.push({
    path: '/admin/saas/orders',
    query: { tenantId: String(row.id), orderType: 'renew', action: 'renew' },
  })
}

function onMore(row: any, cmd: string) {
  if (cmd === 'order') {
    router.push({
      path: '/admin/saas/orders',
      query: { tenantId: String(row.id), action: 'renew' },
    })
    return
  }
  if (cmd === 'tenant') {
    router.push({ path: '/admin/saas/tenants', query: { keyword: row.name || '' } })
  }
}

async function load() {
  loading.value = true
  try {
    const [expiring, tenants] = await Promise.all([
      listExpiringTenants(withinDays.value),
      pageTenants({ page: 1, size: 500 }),
    ])
    const contactMap: Record<number, any> = {}
    for (const t of tenants?.records || []) contactMap[t.id] = t
    list.value = (expiring || []).map((r: any) => {
      const t = contactMap[r.id] || {}
      return {
        ...r,
        contactName: t.contactName || r.contactName,
        contactPhone: t.contactPhone || r.contactPhone,
        ownerName: t.ownerName || t.followUpBy || null,
      }
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onScan() {
  scanning.value = true
  try {
    const n = await runExpireScan()
    ElMessage.success(`扫描完成，处理 ${n ?? 0} 条`)
    await load()
  } finally {
    scanning.value = false
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
  align-items: flex-start;
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
}
.page-actions {
  display: flex;
  gap: 8px;
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
  align-items: stretch;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}
.stat-item {
  flex: 1;
  border: 0;
  background: transparent;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
}
.stat-item.active {
  background: #eff6ff;
}
.stat-item.muted-card {
  cursor: default;
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
.stat-value.muted {
  color: #98a2b3;
}
.stat-divider {
  width: 1px;
  background: #e4e7ec;
  align-self: stretch;
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
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.toolbar-label {
  font-size: 13px;
  color: #667085;
}
.saas-table :deep(.el-table__header th) {
  background: #f9fafb;
  color: #667085;
}
.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.risk-pill {
  display: inline-flex;
  height: 20px;
  align-items: center;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 550;
}
.risk-pill.tone-urgent {
  background: #fef3f2;
  color: #b42318;
}
.risk-pill.tone-high {
  background: #fffaeb;
  color: #b54708;
}
.risk-pill.tone-mid {
  background: #f2f4f7;
  color: #667085;
}
.risk-pill.tone-off {
  background: #f2f4f7;
  color: #98a2b3;
}
.days {
  font-variant-numeric: tabular-nums;
  font-weight: 550;
}
.days.danger {
  color: #b42318;
}
.days.warn {
  color: #b54708;
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
.ops {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.ops-link {
  color: #2563eb !important;
  font-weight: 500;
}
.ops-more {
  color: #667085 !important;
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
