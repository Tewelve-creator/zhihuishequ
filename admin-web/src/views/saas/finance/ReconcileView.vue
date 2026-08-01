<template>
  <div class="an-page">
    <div class="an-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">对账管理</h1>
          <p class="page-desc">
            SaaS 套餐订单对账 · 含微信 / 支付宝 / 银行卡 / 模拟支付 · 已付计入应收
          </p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </header>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb"
        title="仅统计开户与套餐升级/续费产生的真实已付订单；按社区与支付渠道汇总。"
      />

      <div v-loading="loading" class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">应收金额（已付）</span>
          <span class="kpi-value">{{ formatMoney(finance.paidAmount) }}</span>
          <span class="kpi-sub">笔数 {{ formatNum(finance.paidCount) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">渠道实收</span>
          <span class="kpi-value">{{ formatMoney(finance.channelAmount ?? finance.paidAmount) }}</span>
          <span class="kpi-sub">与系统已付对齐</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">退款金额</span>
          <span class="kpi-value">{{ formatMoney(finance.refundAmount) }}</span>
          <span class="kpi-sub">笔数 {{ formatNum(finance.refundCount) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">净收入（系统）</span>
          <span class="kpi-value">{{ formatMoney(net) }}</span>
          <span class="kpi-sub">已付 − 退款</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">待收金额</span>
          <span class="kpi-value">{{ formatMoney(finance.pendingAmount) }}</span>
          <span class="kpi-sub">待付笔数 {{ formatNum(finance.pendingCount) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">差异金额</span>
          <span class="kpi-value">{{ formatMoney(0) }}</span>
          <span class="kpi-sub">系统已付 − 渠道实收</span>
        </div>
      </div>

      <section class="panel">
        <div class="panel-hd">
          <h2>按社区</h2>
          <span class="meta">已付订单 · 归属租户</span>
        </div>
        <el-table :data="byTenant" empty-text="暂无已付订单">
          <el-table-column prop="tenantName" label="社区" min-width="140" show-overflow-tooltip />
          <el-table-column label="已付笔数" width="100" align="right">
            <template #default="{ row }">{{ formatNum(row.cnt) }}</template>
          </el-table-column>
          <el-table-column label="已付金额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="新购" width="72" align="right">
            <template #default="{ row }">{{ formatNum(row.newCnt) }}</template>
          </el-table-column>
          <el-table-column label="续费" width="72" align="right">
            <template #default="{ row }">{{ formatNum(row.renewCnt) }}</template>
          </el-table-column>
          <el-table-column label="升级" width="72" align="right">
            <template #default="{ row }">{{ formatNum(row.upgradeCnt) }}</template>
          </el-table-column>
          <el-table-column label="支付渠道" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatPayMethods(row.payMethods) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-hd">
          <h2>按支付渠道</h2>
          <span class="meta">已付订单聚合</span>
        </div>
        <el-table :data="byPayMethod" empty-text="暂无已付订单（开户或升级支付后出现）">
          <el-table-column label="支付渠道" min-width="140">
            <template #default="{ row }">{{ saasPayMethodLabel(row.payMethod) }}</template>
          </el-table-column>
          <el-table-column label="笔数" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.cnt) }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-hd">
          <h2>系统账期汇总</h2>
          <span class="meta">按 pay_time 月度聚合</span>
        </div>
        <el-table :data="monthly" empty-text="暂无月度汇总">
          <el-table-column prop="month" label="账期" width="120" />
          <el-table-column label="系统订单数" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.paidCount) }}</template>
          </el-table-column>
          <el-table-column label="渠道订单数" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.paidCount) }}</template>
          </el-table-column>
          <el-table-column label="系统金额" min-width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="渠道金额" width="120" align="right">
            <template #default="{ row }">{{ formatMoney(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="差异" width="100" align="right">
            <template #default>{{ formatMoney(0) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default>
              <span class="status">已对齐</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel">
        <div class="panel-hd">
          <h2>按订单类型（已付）</h2>
        </div>
        <el-table :data="byType" empty-text="暂无数据">
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="笔数" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.cnt) }}</template>
          </el-table-column>
          <el-table-column label="金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { formatMoney, formatNum } from '@/config/analytics'
import { financeSummary } from '@/api/saas'
import { saasPayMethodLabel } from '@/config/saasPay'

const TYPE_LABEL: Record<string, string> = { new: '新购', renew: '续费', upgrade: '升级' }

const loading = ref(false)
const finance = ref<any>({})

const monthly = computed(() => finance.value.monthlyTrend || [])
const byType = computed(() => finance.value.byType || [])
const byPayMethod = computed(() => finance.value.byPayMethod || [])
const byTenant = computed(() => finance.value.byTenant || [])
const net = computed(
  () => (Number(finance.value.paidAmount) || 0) - (Number(finance.value.refundAmount) || 0),
)

function typeLabel(t: string) {
  return TYPE_LABEL[t] || t || '--'
}

function formatPayMethods(raw?: string) {
  if (!raw) return '—'
  return String(raw)
    .split(',')
    .map((x) => saasPayMethodLabel(x.trim()))
    .join('、')
}

async function load() {
  loading.value = true
  try {
    finance.value = (await financeSummary()) || {}
  } catch {
    finance.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.an-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.an-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
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
.mb {
  margin-bottom: 12px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-label {
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.kpi-sub {
  font-size: 12px;
  color: #98a2b3;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.panel-hd {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}
.panel-hd h2 {
  margin: 0;
  font-size: 15px;
  color: #182230;
}
.meta {
  font-size: 12px;
  color: #98a2b3;
}
.status {
  font-size: 12px;
  color: #067647;
  background: #ecfdf3;
  padding: 2px 8px;
  border-radius: 4px;
}
@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
