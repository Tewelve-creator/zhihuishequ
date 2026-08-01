<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">商户营收</h1>
          <p class="page-desc">
            按社区查看各商户总营收与社区提成 · 平台不抽成 · 近 {{ days }} 天 · 已完成订单
          </p>
        </div>
        <div class="page-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button @click="exportRows">导出</el-button>
        </div>
      </header>

      <el-alert
        class="mb"
        type="success"
        :closable="false"
        show-icon
        title="平台抽成比例为 0%。下列「社区提成」为物业/社区向商户计提金额，仅供监管查看，不进入平台账务。"
      />

      <div class="toolbar panel">
        <el-select v-model="days" style="width: 120px" @change="onFilterChange">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-select
          v-model="tenantId"
          clearable
          filterable
          placeholder="所属社区"
          style="width: 180px"
          @change="onTenantChange"
        >
          <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button v-if="selectedShop" link type="primary" @click="clearShop">返回商户列表</el-button>
      </div>

      <div v-loading="loading" class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">总营收</span>
          <span class="kpi-value">{{ money(data.gmv) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">社区提成</span>
          <span class="kpi-value">{{ money(data.commissionAmount) }}</span>
          <span class="kpi-sub">平台抽成 ¥0</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">商户实收</span>
          <span class="kpi-value">{{ money(data.merchantNet) }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">订单 / 商户</span>
          <span class="kpi-value">{{ data.orderCount || 0 }} / {{ data.merchantCount || 0 }}</span>
        </div>
      </div>

      <div class="grid-2">
        <section class="panel">
          <div class="panel-hd">
            <h2>社区汇总</h2>
            <span class="meta">点击行筛选该社区商户</span>
          </div>
          <el-table
            :data="byTenant"
            height="320"
            empty-text="暂无社区营收"
            highlight-current-row
            @row-click="onTenantRow"
          >
            <el-table-column prop="tenantName" label="社区" min-width="120" show-overflow-tooltip />
            <el-table-column label="商户数" width="80" align="right">
              <template #default="{ row }">{{ row.merchantCount }}</template>
            </el-table-column>
            <el-table-column label="订单" width="72" align="right">
              <template #default="{ row }">{{ row.orderCount }}</template>
            </el-table-column>
            <el-table-column label="总营收" width="110" align="right">
              <template #default="{ row }">{{ money(row.gmv) }}</template>
            </el-table-column>
            <el-table-column label="社区提成" width="110" align="right">
              <template #default="{ row }">{{ money(row.commissionAmount) }}</template>
            </el-table-column>
            <el-table-column label="商户实收" width="110" align="right">
              <template #default="{ row }">{{ money(row.merchantNet) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="panel">
          <div class="panel-hd">
            <h2>{{ selectedShop ? '订单明细' : '商户明细' }}</h2>
            <span class="meta">{{ selectedShop ? selectedShop.shopName : '点击商户查看订单' }}</span>
          </div>

          <el-table
            v-if="!selectedShop"
            :data="byMerchant"
            height="320"
            empty-text="暂无商户营收"
            @row-click="onMerchantRow"
          >
            <el-table-column prop="shopName" label="商户" min-width="120" show-overflow-tooltip />
            <el-table-column prop="tenantName" label="社区" width="110" show-overflow-tooltip />
            <el-table-column label="订单" width="72" align="right">
              <template #default="{ row }">{{ row.orderCount }}</template>
            </el-table-column>
            <el-table-column label="总营收" width="110" align="right">
              <template #default="{ row }">{{ money(row.gmv) }}</template>
            </el-table-column>
            <el-table-column label="提成%" width="72" align="right">
              <template #default="{ row }">
                {{ row.commissionRate != null ? `${row.commissionRate}%` : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="社区提成" width="110" align="right">
              <template #default="{ row }">{{ money(row.commissionAmount) }}</template>
            </el-table-column>
            <el-table-column label="商户实收" width="110" align="right">
              <template #default="{ row }">{{ money(row.merchantNet) }}</template>
            </el-table-column>
          </el-table>

          <el-table v-else :data="orders" height="320" empty-text="暂无订单">
            <el-table-column prop="orderNo" label="订单号" min-width="140" show-overflow-tooltip />
            <el-table-column label="成交额" width="110" align="right">
              <template #default="{ row }">{{ money(row.gmv) }}</template>
            </el-table-column>
            <el-table-column label="提成%" width="72" align="right">
              <template #default="{ row }">
                {{ row.commissionRate != null ? `${row.commissionRate}%` : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="社区提成" width="110" align="right">
              <template #default="{ row }">{{ money(row.commissionAmount) }}</template>
            </el-table-column>
            <el-table-column label="商户实收" width="110" align="right">
              <template #default="{ row }">{{ money(row.merchantNet) }}</template>
            </el-table-column>
            <el-table-column label="结算" width="90">
              <template #default="{ row }">{{ row.settleStatusLabel || '--' }}</template>
            </el-table-column>
            <el-table-column label="完成时间" width="168">
              <template #default="{ row }">{{ fmtTime(row.completeTime) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageTenants } from '@/api/saas'
import { getMerchantRevenueReport } from '@/api/platformHub'
import { exportExcel } from '@/utils/exportExcel'
import { fmtTime, money } from '@/utils/opsFormat'

const loading = ref(false)
const days = ref(30)
const tenantId = ref<number | undefined>()
const tenants = ref<any[]>([])
const data = ref<any>({})
const selectedShop = ref<any>(null)

const byTenant = computed(() => data.value.byTenant || [])
const byMerchant = computed(() => data.value.byMerchant || [])
const orders = computed(() => data.value.orders || [])

function onFilterChange() {
  selectedShop.value = null
  load()
}

function onTenantChange() {
  selectedShop.value = null
  load()
}

function onTenantRow(row: any) {
  tenantId.value = row.tenantId
  selectedShop.value = null
  load()
}

async function onMerchantRow(row: any) {
  if (!row.shopId) {
    ElMessage.warning('该行未绑定店铺，无法下钻订单')
    return
  }
  selectedShop.value = row
  tenantId.value = row.tenantId
  await load()
}

function clearShop() {
  selectedShop.value = null
  load()
}

async function load() {
  loading.value = true
  try {
    data.value =
      (await getMerchantRevenueReport({
        days: days.value,
        tenantId: tenantId.value,
        shopId: selectedShop.value?.shopId,
      })) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

function exportRows() {
  const rows = selectedShop.value ? orders.value : byMerchant.value
  if (!rows.length) {
    ElMessage.warning('暂无数据')
    return
  }
  if (selectedShop.value) {
    exportExcel(
      rows.map((r: any) => ({
        订单号: r.orderNo,
        成交额: r.gmv,
        提成比例: r.commissionRate,
        社区提成: r.commissionAmount,
        商户实收: r.merchantNet,
        结算: r.settleStatusLabel,
        完成时间: r.completeTime,
      })),
      `商户营收-订单-${selectedShop.value.shopName}`,
      '订单',
    )
  } else {
    exportExcel(
      rows.map((r: any) => ({
        社区: r.tenantName,
        商户: r.shopName,
        订单数: r.orderCount,
        总营收: r.gmv,
        提成比例: r.commissionRate,
        社区提成: r.commissionAmount,
        商户实收: r.merchantNet,
        平台抽成: 0,
      })),
      '商户营收',
      '商户',
    )
  }
}

onMounted(async () => {
  try {
    const res = await pageTenants({ page: 1, size: 500 })
    tenants.value = res?.records || []
  } catch {
    tenants.value = []
  }
  await load()
})
</script>

<style scoped>
.ops-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.ops-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
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
.mb {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-label {
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  font-size: 22px;
  font-weight: 650;
  color: #182230;
}
.kpi-sub {
  font-size: 12px;
  color: #98a2b3;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 12px;
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
@media (max-width: 1100px) {
  .kpi-row,
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
