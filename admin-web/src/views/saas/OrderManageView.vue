<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">订单管理</h1>
          <p class="page-desc">运营创建订单 · 处理物业端续费/升级申请 · 确认生效后同步租户套餐</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
          <el-button type="primary" class="primary-btn" @click="openCreate()">创建订单</el-button>
        </div>
      </header>

      <el-tabs v-model="typeTab" class="type-tabs" @tab-change="onTypeTab">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待处理申请" name="pending_apply" />
        <el-tab-pane label="新购" name="new" />
        <el-tab-pane label="续费" name="renew" />
        <el-tab-pane label="升级" name="upgrade" />
        <el-tab-pane label="退款" name="refunded" />
      </el-tabs>

      <div class="panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            clearable
            class="kw-input"
            placeholder="订单号或租户名称"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-select
            v-if="typeTab === 'all'"
            v-model="orderType"
            clearable
            placeholder="订单类型"
            class="f-select"
            @change="onSearch"
          >
            <el-option label="新购" value="new" />
            <el-option label="续费" value="renew" />
            <el-option label="升级" value="upgrade" />
          </el-select>
          <el-select
            v-if="typeTab !== 'refunded' && typeTab !== 'pending_apply'"
            v-model="status"
            clearable
            placeholder="支付状态"
            class="f-select"
            @change="onSearch"
          >
            <el-option label="待处理" value="pending" />
            <el-option label="已生效" value="paid" />
            <el-option label="已取消" value="cancelled" />
            <el-option label="已退款" value="refunded" />
          </el-select>
          <el-select
            v-model="packageId"
            clearable
            filterable
            placeholder="套餐"
            class="f-select wide"
            @change="onSearch"
          >
            <el-option v-for="p in packages" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select
            v-model="tenantId"
            clearable
            filterable
            placeholder="租户"
            class="f-select wide"
            @change="onSearch"
          >
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
        </div>

        <div class="table-wrap">
          <el-table v-loading="loading" :data="displayList" class="saas-table" empty-text="暂无订单">
            <el-table-column label="订单号" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <button type="button" class="link-btn" @click="openDetail(row)">
                  {{ row.orderNo || '--' }}
                </button>
              </template>
            </el-table-column>
            <el-table-column label="租户" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ tenantName(row.tenantId) }}</template>
            </el-table-column>
            <el-table-column label="订单类型" width="88">
              <template #default="{ row }">
                <span class="type-pill">{{ typeLabel(row.orderType) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="原套餐 → 新套餐" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="pkg-change">
                  <span class="muted">{{ fromPackageHint(row) }}</span>
                  <span class="arrow">→</span>
                  <span>{{ row.packageName || '--' }}</span>
                  <span class="cycle">{{ cycleLabel(row.cycle) }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="应付金额" width="110" align="right">
              <template #default="{ row }">
                <span class="money">{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="支付方式" width="110">
              <template #default="{ row }">{{ saasPayMethodLabel(row.payMethod) }}</template>
            </el-table-column>
            <el-table-column label="支付状态" width="96">
              <template #default="{ row }">
                <span class="status-pill" :class="statusTone(row.status)">
                  {{ statusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="生效时间" width="158">
              <template #default="{ row }">{{ formatTime(row.startTime || row.payTime) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="158">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button
                    v-if="row.status === 'pending'"
                    link
                    class="ops-link"
                    @click="onPay(row)"
                  >
                    确认生效
                  </el-button>
                  <el-button link class="ops-link" @click="openDetail(row)">详情</el-button>
                  <el-dropdown
                    v-if="row.status === 'pending' || row.status === 'paid'"
                    trigger="click"
                    @command="(cmd: string) => onMore(row, cmd)"
                  >
                    <el-button link class="ops-more" aria-label="更多">
                      <el-icon><MoreFilled /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="row.status === 'pending'" command="cancel">
                          取消订单
                        </el-dropdown-item>
                        <el-dropdown-item
                          v-if="row.status === 'paid'"
                          command="refund"
                          class="danger-item"
                        >
                          退款
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            background
            layout="total, prev, pager, next"
            :total="keyword.trim() || packageId ? displayList.length : total"
            @current-change="onPageChange"
            @size-change="onSearch"
          />
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="订单详情" size="420px" destroy-on-close>
      <template v-if="detail">
        <dl class="detail-dl">
          <div><dt>订单号</dt><dd>{{ detail.orderNo }}</dd></div>
          <div><dt>租户</dt><dd>{{ tenantName(detail.tenantId) }}</dd></div>
          <div><dt>类型</dt><dd>{{ typeLabel(detail.orderType) }}</dd></div>
          <div><dt>套餐</dt><dd>{{ detail.packageName || '--' }}（{{ cycleLabel(detail.cycle) }}）</dd></div>
          <div><dt>金额</dt><dd>{{ formatMoney(detail.amount) }}</dd></div>
          <div><dt>支付方式</dt><dd>{{ saasPayMethodLabel(detail.payMethod) }}</dd></div>
          <div><dt>支付状态</dt><dd>{{ statusLabel(detail.status) }}</dd></div>
          <div><dt>支付时间</dt><dd>{{ formatTime(detail.payTime) }}</dd></div>
          <div><dt>生效起止</dt><dd>{{ formatTime(detail.startTime) }} ~ {{ formatTime(detail.endTime) }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatTime(detail.createTime) }}</dd></div>
          <div v-if="detail.refundReason"><dt>退款原因</dt><dd>{{ detail.refundReason }}</dd></div>
          <div v-if="detail.refundTime"><dt>退款时间</dt><dd>{{ formatTime(detail.refundTime) }}</dd></div>
        </dl>
        <p class="detail-hint">支付流水与操作日志以订单字段为准；当前接口未提供独立日志列表。</p>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" title="创建订单" width="480px" destroy-on-close>
      <el-form :model="form" label-position="top" class="create-form">
        <el-form-item label="租户" required>
          <el-select v-model="form.tenantId" filterable style="width: 100%" placeholder="选择租户">
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="套餐" required>
          <el-select v-model="form.packageId" style="width: 100%" placeholder="选择套餐">
            <el-option v-for="p in packages" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期" required>
          <el-select v-model="form.cycle" style="width: 100%">
            <el-option label="月付" value="month" />
            <el-option label="季付" value="quarter" />
            <el-option label="年付" value="year" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.orderType" style="width: 100%">
            <el-option label="新购" value="new" />
            <el-option label="续费" value="renew" />
            <el-option label="升级" value="upgrade" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Refresh } from '@element-plus/icons-vue'
import {
  pageOrders,
  createOrder,
  payOrder,
  cancelOrder,
  refundOrder,
  pageTenants,
  listPackages,
} from '@/api/saas'
import { saasPayMethodLabel } from '@/config/saasPay'

const STATUS_LABEL: Record<string, string> = {
  pending: '待支付',
  paid: '已生效',
  cancelled: '已取消',
  refunded: '已退款',
}
const TYPE_LABEL: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }
const CYCLE_LABEL: Record<string, string> = { month: '月付', quarter: '季付', year: '年付' }

const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const status = ref('')
const orderType = ref('')
const typeTab = ref('all')
const tenantId = ref<number | undefined>()
const packageId = ref<number | undefined>()
const keyword = ref('')
const list = ref<any[]>([])
const tenants = ref<any[]>([])
const packages = ref<any[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const detail = ref<any>(null)
const form = reactive({
  tenantId: undefined as number | undefined,
  packageId: undefined as number | undefined,
  cycle: 'year',
  orderType: 'new',
})

const tenantMap = computed(() => {
  const m: Record<number, any> = {}
  for (const t of tenants.value) m[t.id] = t
  return m
})

const displayList = computed(() => {
  let rows = list.value
  if (typeTab.value === 'pending_apply') {
    rows = rows.filter((r) => r.orderType === 'renew' || r.orderType === 'upgrade')
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    rows = rows.filter((r) => {
      const no = String(r.orderNo || '').toLowerCase()
      const name = tenantName(r.tenantId).toLowerCase()
      return no.includes(kw) || name.includes(kw)
    })
  }
  if (packageId.value) {
    rows = rows.filter((r) => Number(r.packageId) === Number(packageId.value))
  }
  return rows
})

function statusLabel(s: string) {
  return STATUS_LABEL[s] || s || '--'
}
function statusTone(s: string) {
  if (s === 'paid') return 'ok'
  if (s === 'pending') return 'warn'
  if (s === 'refunded') return 'info'
  return 'off'
}
function typeLabel(t: string) {
  return TYPE_LABEL[t] || t || '--'
}
function cycleLabel(c: string) {
  return CYCLE_LABEL[c] || c || ''
}
function tenantName(id: number) {
  return tenantMap.value[id]?.name || (id != null ? `租户#${id}` : '--')
}
function formatMoney(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
}
function formatTime(v: unknown) {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 19)
}
/** 订单实体无原套餐字段：续费/升级展示租户当前套餐作参考，其余为 -- */
function fromPackageHint(row: any) {
  if (row.orderType === 'new') return '--'
  const t = tenantMap.value[row.tenantId]
  if (!t?.packageId) return '--'
  const p = packages.value.find((x) => x.id === t.packageId)
  return p?.name || '--'
}

function onTypeTab(name: string | number) {
  const tab = String(name)
  if (tab === 'all') {
    orderType.value = ''
    if (status.value === 'refunded' || status.value === 'pending') status.value = ''
  } else if (tab === 'pending_apply') {
    // 物业续费/升级申请：待处理
    orderType.value = ''
    status.value = 'pending'
  } else if (tab === 'refunded') {
    orderType.value = ''
    status.value = 'refunded'
  } else {
    orderType.value = tab
    if (status.value === 'refunded') status.value = ''
  }
  onSearch()
}

async function loadRefs() {
  try {
    const [t, p] = await Promise.all([pageTenants({ page: 1, size: 500 }), listPackages()])
    tenants.value = t?.records || []
    packages.value = p || []
  } catch {
    tenants.value = []
    packages.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageOrders({
      page: page.value,
      size: keyword.value.trim() || packageId.value ? 100 : size.value,
      tenantId: tenantId.value,
      status: status.value || undefined,
      orderType: orderType.value || undefined,
    })
    list.value = data?.records || []
    total.value = Number(data?.total || 0)
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

function onPageChange() {
  if (keyword.value.trim() || packageId.value) return
  load()
}

function openDetail(row: any) {
  detail.value = row
  detailVisible.value = true
}

function openCreate(preset?: Partial<typeof form>) {
  form.tenantId = preset?.tenantId
  form.packageId = preset?.packageId
  form.cycle = preset?.cycle || 'year'
  form.orderType = preset?.orderType || 'new'
  visible.value = true
}

async function saveCreate() {
  if (!form.tenantId || !form.packageId) {
    ElMessage.error('请选择租户与套餐')
    return
  }
  saving.value = true
  try {
    await createOrder({ ...form })
    ElMessage.success('订单已创建')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onPay(row: any) {
  const typeName = typeLabel(row.orderType)
  try {
    await ElMessageBox.confirm(
      `确认生效订单 ${row.orderNo}（${typeName}）？\n生效后将同步更新该社区套餐、功能权限、AI 额度与到期时间。`,
      '确认生效',
      {
        type: 'warning',
        confirmButtonText: '确认生效',
      },
    )
  } catch {
    return
  }
  try {
    await payOrder(row.id, 'mock')
    ElMessage.success('已模拟支付生效，租户套餐已同步，订单已进入对账')
    await load()
  } catch {
    /* interceptor */
  }
}

async function onMore(row: any, cmd: string) {
  if (cmd === 'cancel') {
    try {
      await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？`, '取消订单', { type: 'warning' })
    } catch {
      return
    }
    try {
      await cancelOrder(row.id)
      ElMessage.success('已取消')
      await load()
    } catch {
      /* */
    }
    return
  }
  if (cmd === 'refund') {
    try {
      const { value } = await ElMessageBox.prompt('请填写退款原因（可选）', '确认退款', {
        confirmButtonText: '确认退款',
        cancelButtonText: '取消',
        inputPlaceholder: '退款原因',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      })
      await refundOrder(row.id, value || undefined)
      ElMessage.success('已退款')
      await load()
    } catch {
      /* cancel or error */
    }
  }
}

onMounted(async () => {
  await loadRefs()
  const q = useRoute().query
  if (q.tenantId) {
    const id = Number(q.tenantId)
    if (Number.isFinite(id)) tenantId.value = id
  }
  if (q.orderType && ['new', 'renew', 'upgrade'].includes(String(q.orderType))) {
    typeTab.value = String(q.orderType)
    orderType.value = String(q.orderType)
  }
  if (q.action === 'renew' && tenantId.value) {
    openCreate({ tenantId: tenantId.value, orderType: 'renew' })
  }
  await load()
})
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
  line-height: 28px;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}
.page-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.icon-btn {
  width: 36px;
  padding: 8px;
  border-color: #e4e7ec;
  background: #fff;
  color: #667085;
}
.primary-btn {
  background: #2563eb;
  border-color: #2563eb;
}
.type-tabs {
  margin-bottom: 12px;
}
.type-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.type-tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.type-tabs :deep(.el-tabs__active-bar) {
  background: #2563eb;
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
.kw-input {
  width: 200px;
}
.f-select {
  width: 130px;
}
.f-select.wide {
  width: 160px;
}
.saas-table {
  width: 100%;
}
.saas-table :deep(.el-table__header th) {
  background: #f9fafb;
  color: #667085;
  font-weight: 550;
}
.link-btn {
  border: 0;
  background: none;
  padding: 0;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
}
.pkg-change {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.pkg-change .muted {
  color: #98a2b3;
}
.pkg-change .arrow {
  color: #98a2b3;
}
.pkg-change .cycle {
  margin-left: 4px;
  font-size: 12px;
  color: #667085;
}
.money {
  font-variant-numeric: tabular-nums;
  color: #182230;
  font-weight: 550;
}
.type-pill {
  font-size: 12px;
  color: #344054;
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
.status-pill.info {
  background: #eff8ff;
  color: #175cd3;
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
:deep(.danger-item) {
  color: #b42318 !important;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.detail-dl {
  margin: 0;
  display: grid;
  gap: 12px;
}
.detail-dl > div {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px;
  font-size: 13px;
}
.detail-dl dt {
  color: #667085;
  margin: 0;
}
.detail-dl dd {
  margin: 0;
  color: #182230;
  word-break: break-all;
}
.detail-hint {
  margin: 20px 0 0;
  font-size: 12px;
  color: #98a2b3;
  line-height: 1.5;
}
.create-form :deep(.el-form-item__label) {
  color: #182230;
  font-weight: 500;
}
@media (max-width: 768px) {
  .saas-page {
    padding: 16px 12px;
  }
  .page-hd {
    flex-direction: column;
  }
  .kw-input,
  .f-select,
  .f-select.wide {
    width: 100%;
  }
}
</style>
