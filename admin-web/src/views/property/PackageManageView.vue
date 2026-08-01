<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p class="sub">{{ pageDesc }}</p>
      </div>
      <el-button :loading="loading || ordersLoading || applying" @click="reload">刷新</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="物业端只读订阅中心。开通、变更由平台运营确认；升级申请将生成待支付订单出现在 SaaS 订单管理。"
      style="margin-bottom: 12px"
    />

    <div v-loading="loading && tab !== 'orders'" class="content">
      <!-- 订阅概览 -->
      <div v-if="tab === 'overview'">
        <div class="kpi-row">
          <div class="kpi-card">
            <div class="kpi-label">当前套餐</div>
            <div class="kpi-value text">{{ lease?.packageName || '未绑定套餐' }}</div>
            <div class="kpi-hint">{{ lease?.packageCode ? `编码 ${lease.packageCode}` : '—' }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">套餐到期时间</div>
            <div class="kpi-value text">{{ formatExpire(lease?.expireTime) }}</div>
            <div class="kpi-hint" :class="{ warn: lease?.expireWarning }">{{ expireHint }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">租户状态</div>
            <div class="kpi-value text">
              <el-tag :type="statusTagType(lease?.status)" size="small" round>
                {{ statusText(lease?.status) }}
              </el-tag>
            </div>
            <div class="kpi-hint">{{ lease?.tenantName || '当前社区' }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">AI 剩余额度</div>
            <div class="kpi-value" :class="remainTone">{{ remainQuotaLabel }}</div>
            <div class="kpi-hint">
              额度 {{ formatNum(lease?.aiQuota) }} · 今日已用 {{ formatNum(lease?.todayAiCalls) }}
            </div>
          </div>
        </div>

        <div class="overview-grid">
          <div class="panel-box">
            <div class="sec-title">订阅摘要</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="社区">{{ lease?.tenantName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="套餐">
                {{ lease?.packageName || '—' }}
                <span v-if="lease?.packageCode" class="code">（{{ lease.packageCode }}）</span>
              </el-descriptions-item>
              <el-descriptions-item label="到期">{{ formatExpire(lease?.expireTime) }}</el-descriptions-item>
              <el-descriptions-item label="已开通功能">{{ featureRows.length }} 项</el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="panel-box">
            <div class="sec-title">子模块</div>
            <div class="quick-list">
              <button type="button" class="quick-item" @click="go('features')">功能权限 →</button>
              <button type="button" class="quick-item" @click="go('quota')">用量与额度 →</button>
              <button type="button" class="quick-item" @click="go('upgrade')">升级与办理 →</button>
              <button type="button" class="quick-item" @click="go('orders')">订购记录 →</button>
            </div>
            <el-alert
              v-if="lease?.expireWarning"
              type="warning"
              :closable="false"
              show-icon
              class="mt"
              :title="`套餐即将到期（${expireHint}），可在「升级与办理」提交续费/升级申请。`"
            />
          </div>
        </div>
      </div>

      <!-- 功能权限 -->
      <div v-else-if="tab === 'features'">
        <p class="sec-desc">
          数据来自运营端租户授权（features）；若为空则回退当前套餐默认功能。本页不可编辑，变更请运营在「租户授权」操作。
        </p>
        <template v-if="featureGroupsView.length">
          <div v-for="g in featureGroupsView" :key="g.key" class="group-block">
            <div class="group-title">
              {{ g.label }}
              <span class="group-count">{{ g.items.length }}</span>
            </div>
            <div class="feature-grid">
              <div v-for="f in g.items" :key="f.code" class="feature-item">
                <span class="dot" />
                <div>
                  <div class="fname">{{ f.label }}</div>
                  <div class="fcode">{{ f.code }}</div>
                </div>
                <el-tag size="small" type="success" effect="plain" round>已开通</el-tag>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="暂无已开通功能（或套餐功能码为空）" :image-size="72" />
      </div>

      <!-- 用量与额度 -->
      <div v-else-if="tab === 'quota'">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb"
          title="剩余额度按「租户 AI 额度 − 今日调用次数」估算；扩容请联系运营或在升级与办理中提交申请。"
        />
        <div class="kpi-row three">
          <div class="kpi-card">
            <div class="kpi-label">AI 调用额度</div>
            <div class="kpi-value">{{ formatNum(lease?.aiQuota) }}</div>
            <div class="kpi-hint">租户分配额度</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">今日已用</div>
            <div class="kpi-value warn">{{ formatNum(lease?.todayAiCalls) }}</div>
            <div class="kpi-hint">来自 ai_call_log 当日统计</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">剩余额度</div>
            <div class="kpi-value" :class="remainTone">{{ remainQuotaLabel }}</div>
            <div class="kpi-hint">使用率 {{ usageRate }}%</div>
          </div>
        </div>
        <div class="panel-box">
          <div class="sec-title">使用情况</div>
          <el-progress
            :percentage="Math.min(100, usageRate)"
            :stroke-width="10"
            :status="usageRate >= 100 ? 'exception' : usageRate >= 90 ? 'warning' : undefined"
          />
        </div>
      </div>

      <!-- 升级与办理 -->
      <div v-else-if="tab === 'upgrade'">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          class="mb"
          title="选择套餐并支付后立即升级生效，无需平台审核。支付成功订单同步至 SaaS「对账管理」。"
        />
        <div class="toolbar">
          <el-button type="primary" plain @click="openRenew">续费当前套餐</el-button>
        </div>
        <el-table :data="upgradeOptions" stripe empty-text="暂无可升级套餐（或已是最高档）">
          <el-table-column prop="name" label="套餐名称" min-width="140" />
          <el-table-column prop="code" label="编码" width="110" />
          <el-table-column label="档位" width="80">
            <template #default="{ row }">{{ row.sortNo ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="年费参考" width="110">
            <template #default="{ row }">{{ formatPrice(row.priceYear) }}</template>
          </el-table-column>
          <el-table-column label="AI 额度" width="100">
            <template #default="{ row }">{{ formatNum(row.aiQuota) }}</template>
          </el-table-column>
          <el-table-column label="功能数" width="80">
            <template #default="{ row }">{{ (row.featureCodes || []).length }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :loading="applying" @click="openUpgrade(row)">升级</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 订购记录 -->
      <div v-else>
        <p class="sec-desc">本社区订购与升级记录；待支付订单可继续支付或撤销。</p>
        <el-table v-loading="ordersLoading" :data="orders" stripe empty-text="暂无订购记录">
          <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ orderTypeText(row.orderType) }}</template>
          </el-table-column>
          <el-table-column prop="packageName" label="套餐" min-width="120" show-overflow-tooltip />
          <el-table-column label="周期" width="80">
            <template #default="{ row }">{{ cycleText(row.cycle) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ formatPrice(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="支付方式" width="110">
            <template #default="{ row }">{{ saasPayMethodLabel(row.payMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="orderStatusTag(row.status)" size="small" round>
                {{ orderStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatExpire(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'pending'">
                <el-button link type="primary" @click="openPayPending(row)">去支付</el-button>
                <el-button link type="danger" @click="onCancelOrder(row)">撤销</el-button>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="orderPage"
            background
            layout="total, prev, pager, next"
            :total="orderTotal"
            :page-size="10"
            @current-change="loadOrders"
          />
        </div>
      </div>
    </div>

    <el-dialog v-model="applyVisible" :title="applyTitle" width="480px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="目标套餐">
          <el-input :model-value="applyForm.packageName" disabled />
        </el-form-item>
        <el-form-item label="类型">
          <el-input :model-value="orderTypeText(applyForm.orderType)" disabled />
        </el-form-item>
        <el-form-item label="计费周期" required>
          <el-select v-model="applyForm.cycle" style="width: 100%" :disabled="!!pendingPayOrderId">
            <el-option label="按月" value="month" />
            <el-option label="按季" value="quarter" />
            <el-option label="按年" value="year" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付方式" required>
          <el-radio-group v-model="applyForm.payMethod" class="pay-methods">
            <el-radio
              v-for="m in SAAS_PAY_METHODS"
              :key="m.value"
              :value="m.value"
              border
              class="pay-radio"
            >
              <span class="pay-label">{{ m.label }}</span>
              <span class="pay-desc">{{ m.desc }}</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="支付成功后立即生效，无需平台审核；订单同步至对账管理。"
        />
      </el-form>
      <template #footer>
        <el-button @click="closeApply">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">确认支付并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyLease, listPublicPackages } from '@/api/auth'
import { cancelOrder, createOrder, pageOrders, payOrder } from '@/api/saas'
import { SAAS_PAY_METHODS, saasPayMethodLabel, type SaasPayMethod } from '@/config/saasPay'
import { FEATURE_GROUPS, type FeatureCode } from '@/config/features'

const FEATURE_LABEL: Record<string, string> = {
  basic_repair: '基础报修',
  basic_notice: '基础公告',
  ai_chat: 'AI 问答',
  ai_image_repair: '识图报修',
  ai_emotion: '情绪倾诉',
  ai_lost_match: '失物匹配',
  data_dashboard: '数据大屏',
  data_export: '数据导出',
  vendor_manage: '社区商超',
}

const TAB_META: Record<string, { title: string; desc: string; path: string }> = {
  overview: {
    title: '订阅概览',
    desc: '当前社区套餐、到期与状态总览',
    path: '/property/package',
  },
  features: {
    title: '功能权限',
    desc: '已开通功能模块清单（按分组只读）',
    path: '/property/package/features',
  },
  quota: {
    title: '用量与额度',
    desc: 'AI 接口额度、今日用量与剩余',
    path: '/property/package/quota',
  },
  upgrade: {
    title: '升级与办理',
    desc: '可升级套餐参考，申请后同步 SaaS 运营端',
    path: '/property/package/upgrade',
  },
  orders: {
    title: '订购记录',
    desc: '本社区 SaaS 订单与升级申请记录',
    path: '/property/package/orders',
  },
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const ordersLoading = ref(false)
const applying = ref(false)
const lease = ref<Awaited<ReturnType<typeof getMyLease>> | null>(null)
const packages = ref<Awaited<ReturnType<typeof listPublicPackages>>>([])
const orders = ref<any[]>([])
const orderPage = ref(1)
const orderTotal = ref(0)
const applyVisible = ref(false)
const pendingPayOrderId = ref<number | null>(null)
const applyForm = reactive({
  packageId: 0,
  packageName: '',
  orderType: 'upgrade' as 'upgrade' | 'renew',
  cycle: 'year',
  payMethod: 'mock' as SaasPayMethod,
})

const tab = computed(() => {
  const t = String(route.meta.packageTab || 'overview')
  return TAB_META[t] ? t : 'overview'
})
const pageTitle = computed(() => TAB_META[tab.value]?.title || '套餐管理')
const pageDesc = computed(() => TAB_META[tab.value]?.desc || '')

const featureRows = computed(() => {
  const codes = lease.value?.features || []
  return codes.map((code) => ({ code, label: FEATURE_LABEL[code] || code }))
})

const featureGroupsView = computed(() => {
  const opened = new Set(lease.value?.features || [])
  const groups = FEATURE_GROUPS.map((g) => ({
    key: g.key,
    label: g.label,
    items: g.codes
      .filter((c: FeatureCode) => opened.has(c))
      .map((code) => ({ code, label: FEATURE_LABEL[code] || code })),
  })).filter((g) => g.items.length)
  const known = new Set(FEATURE_GROUPS.flatMap((g) => g.codes))
  const extra = [...opened]
    .filter((c) => !known.has(c as FeatureCode))
    .map((code) => ({ code, label: FEATURE_LABEL[code] || code }))
  if (extra.length) groups.push({ key: 'other', label: '其他能力', items: extra })
  return groups
})

const remainQuota = computed(() => {
  const q = Number(lease.value?.aiQuota)
  if (!Number.isFinite(q)) return null
  return Math.max(0, q - (Number(lease.value?.todayAiCalls) || 0))
})
const remainQuotaLabel = computed(() =>
  remainQuota.value == null ? '—' : remainQuota.value.toLocaleString('zh-CN'),
)
const usageRate = computed(() => {
  const q = Number(lease.value?.aiQuota) || 0
  if (q <= 0) return Number(lease.value?.todayAiCalls) > 0 ? 100 : 0
  return Math.round(((Number(lease.value?.todayAiCalls) || 0) / q) * 100)
})
const remainTone = computed(() => {
  if (usageRate.value >= 90) return 'danger'
  if (usageRate.value >= 70) return 'warn'
  return 'ok'
})
const expireHint = computed(() => {
  if (!lease.value?.expireTime) return '未设置到期时间'
  const days = lease.value.daysLeft
  if (days == null) return '—'
  if (days < 0) return `已过期 ${Math.abs(days)} 天`
  if (lease.value.expireWarning) return `剩余 ${days} 天（即将到期）`
  return `剩余 ${days} 天`
})
const upgradeOptions = computed(() => {
  const curId = lease.value?.packageId
  const cur = packages.value.find((p) => p.id === curId)
  // 与运营端 OrderService.assertUpgradeAllowed 一致：按 sortNo 判断更高档
  const curSort = Number(cur?.sortNo ?? lease.value?.packageSortNo ?? 0)
  return packages.value
    .filter((p) => p.id !== curId)
    .filter((p) => Number(p.sortNo ?? 0) > curSort)
    .sort((a, b) => (Number(a.sortNo) || 0) - (Number(b.sortNo) || 0))
})
const applyTitle = computed(() =>
  pendingPayOrderId.value
    ? '支付待付订单'
    : applyForm.orderType === 'renew'
      ? '续费并支付'
      : '升级并支付',
)

function go(name: string) {
  const path = TAB_META[name]?.path
  if (path) router.push(path)
}

function formatNum(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n.toLocaleString('zh-CN')
}
function formatPrice(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return `¥${n.toLocaleString('zh-CN')}`
}
function formatExpire(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}
function statusText(s?: string) {
  const map: Record<string, string> = {
    active: '正常',
    degraded: '已降级',
    frozen: '已冻结',
    disabled: '已停用',
  }
  return map[String(s || '')] || s || '—'
}
function statusTagType(s?: string) {
  if (s === 'active') return 'success'
  if (s === 'degraded') return 'warning'
  if (s === 'frozen' || s === 'disabled') return 'danger'
  return 'info'
}
function orderTypeText(t?: string) {
  const map: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }
  return map[String(t || '')] || t || '—'
}
function cycleText(c?: string) {
  const map: Record<string, string> = { month: '月', quarter: '季', year: '年' }
  return map[String(c || '')] || c || '—'
}
function orderStatusText(s?: string) {
  const map: Record<string, string> = {
    pending: '待支付',
    paid: '已支付',
    cancelled: '已取消',
    refunded: '已退款',
  }
  return map[String(s || '')] || s || '—'
}
function orderStatusTag(s?: string) {
  if (s === 'paid') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'refunded') return 'danger'
  return 'info'
}

function openUpgrade(row: any) {
  pendingPayOrderId.value = null
  applyForm.packageId = Number(row.id)
  applyForm.packageName = row.name
  applyForm.orderType = 'upgrade'
  applyForm.cycle = 'year'
  applyForm.payMethod = 'mock'
  applyVisible.value = true
}

function openRenew() {
  if (!lease.value?.packageId) {
    ElMessage.warning('当前未绑定套餐，无法续费')
    return
  }
  pendingPayOrderId.value = null
  applyForm.packageId = Number(lease.value.packageId)
  applyForm.packageName = lease.value.packageName || `套餐#${lease.value.packageId}`
  applyForm.orderType = 'renew'
  applyForm.cycle = 'year'
  applyForm.payMethod = 'mock'
  applyVisible.value = true
}

function openPayPending(row: any) {
  pendingPayOrderId.value = Number(row.id)
  applyForm.packageId = Number(row.packageId || 0)
  applyForm.packageName = row.packageName || '—'
  applyForm.orderType = row.orderType === 'renew' ? 'renew' : 'upgrade'
  applyForm.cycle = row.cycle || 'year'
  applyForm.payMethod = 'mock'
  applyVisible.value = true
}

function closeApply() {
  applyVisible.value = false
  pendingPayOrderId.value = null
}

async function submitApply() {
  if (!applyForm.packageId && !pendingPayOrderId.value) return
  applying.value = true
  try {
    let orderId = pendingPayOrderId.value
    if (!orderId) {
      const order = await createOrder({
        packageId: applyForm.packageId,
        cycle: applyForm.cycle,
        orderType: applyForm.orderType,
      })
      orderId = order?.id
      if (!orderId) {
        ElMessage.error('订单创建失败')
        return
      }
    }
    await payOrder(orderId, applyForm.payMethod)
    ElMessage.success(
      `${orderTypeText(applyForm.orderType)}支付成功，套餐已立即生效`,
    )
    closeApply()
    await loadLeaseAndPackages()
    await router.push('/property/package/orders')
    orderPage.value = 1
    await loadOrders()
  } catch {
    /* request 已提示 */
  } finally {
    applying.value = false
  }
}

async function onCancelOrder(row: any) {
  try {
    await ElMessageBox.confirm(`确认撤销申请「${row.orderNo}」？`, '撤销申请', { type: 'warning' })
  } catch {
    return
  }
  await cancelOrder(row.id)
  ElMessage.success('已撤销')
  await loadOrders()
}

async function loadLeaseAndPackages() {
  loading.value = true
  try {
    const [leaseData, pkgData] = await Promise.all([
      getMyLease(),
      listPublicPackages().catch(() => []),
    ])
    lease.value = leaseData
    packages.value = pkgData || []
  } catch {
    lease.value = null
    packages.value = []
  } finally {
    loading.value = false
  }
}

async function loadOrders() {
  ordersLoading.value = true
  try {
    const data = await pageOrders({ page: orderPage.value, size: 10 })
    orders.value = data?.records || []
    orderTotal.value = Number(data?.total || 0)
  } catch {
    orders.value = []
    orderTotal.value = 0
  } finally {
    ordersLoading.value = false
  }
}

async function reload() {
  if (tab.value === 'orders') await loadOrders()
  else if (tab.value === 'upgrade') {
    await loadLeaseAndPackages()
  } else await loadLeaseAndPackages()
}

watch(
  tab,
  (t) => {
    if (t === 'orders') loadOrders()
    else if (!lease.value) loadLeaseAndPackages()
  },
  { immediate: true },
)

onMounted(() => {
  if (tab.value !== 'orders') loadLeaseAndPackages()
})
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
.toolbar {
  margin-bottom: 12px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.kpi-row.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  line-height: 1.15;
}
.kpi-value.text {
  font-size: 18px;
  font-weight: 650;
}
.kpi-value.warn {
  color: #d48806;
}
.kpi-value.ok {
  color: #389e0d;
}
.kpi-value.danger {
  color: #cf1322;
}
.kpi-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
}
.kpi-hint.warn {
  color: #d48806;
}
.overview-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 12px;
}
.panel-box {
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fff;
}
.sec-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}
.sec-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: #8f959e;
  line-height: 1.5;
}
.code {
  color: #8f959e;
  font-size: 12px;
}
.muted {
  color: #8f959e;
  font-size: 13px;
}
.mb {
  margin-bottom: 12px;
}
.mt {
  margin-top: 12px;
}
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quick-item {
  text-align: left;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  background: #f7f8fa;
  padding: 10px 12px;
  font-size: 13px;
  color: #1f2329;
  cursor: pointer;
}
.quick-item:hover {
  border-color: #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
}
.group-block {
  margin-bottom: 16px;
}
.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}
.group-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.feature-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  background: #fff;
}
.feature-item .el-tag {
  margin-left: auto;
  flex-shrink: 0;
}
.dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: #0d9488;
  flex-shrink: 0;
}
.fname {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}
.fcode {
  margin-top: 2px;
  font-size: 12px;
  color: #8f959e;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.pay-methods {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
}
.pay-radio {
  width: 100%;
  margin: 0 !important;
  height: auto !important;
  padding: 10px 12px !important;
}
.pay-label {
  display: block;
  font-weight: 600;
  color: #1f2329;
}
.pay-desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #8f959e;
}
@media (max-width: 1100px) {
  .kpi-row,
  .kpi-row.three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .kpi-row,
  .kpi-row.three,
  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
