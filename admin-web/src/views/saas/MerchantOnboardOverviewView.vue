<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">{{ isPlatform ? '商户管理' : '本小区超市' }}</h1>
          <p class="page-desc">
            {{ isPlatform ? '跨社区超市入驻与结算概览' : '本小区超市入驻与佣金应收' }}
            · 与 SaaS 套餐账务独立
          </p>
        </div>
        <div class="page-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button @click="exportList">导出</el-button>
          <el-button v-if="!isPlatform && canManage" type="primary" @click="openSubmit">
            提交入驻申请
          </el-button>
        </div>
      </header>

      <div class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">已入驻</span>
          <span class="kpi-value">{{ stats.approved }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">待平台审核</span>
          <span class="kpi-value warn">{{ stats.pendingPlatform }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">待物业初审</span>
          <span class="kpi-value warn">{{ stats.pendingProperty }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">已驳回</span>
          <span class="kpi-value">{{ stats.rejected }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">待结算金额</span>
          <span class="kpi-value">{{ money(stats.pendingAmount) }}</span>
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <el-select
            v-if="isPlatform"
            v-model="tenantId"
            clearable
            filterable
            placeholder="所属社区"
            style="width: 160px"
            @change="load"
          >
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="商户 / 社区"
            style="width: 180px"
            @keyup.enter="load"
          />
          <el-select
            v-model="onboardStatus"
            clearable
            placeholder="经营/入驻状态"
            style="width: 150px"
            @change="load"
          >
            <el-option label="待物业初审" value="PENDING_PROPERTY" />
            <el-option label="待平台审核" value="PENDING_PLATFORM" />
            <el-option label="已入驻" value="APPROVED" />
            <el-option label="物业已驳回" value="REJECTED_PROPERTY" />
            <el-option label="平台已驳回" value="REJECTED_PLATFORM" />
          </el-select>
          <el-select
            v-model="settleStatus"
            clearable
            placeholder="结算状态"
            style="width: 130px"
            @change="load"
          >
            <el-option label="有待结算" value="PENDING" />
            <el-option label="已结清" value="SETTLED" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>

        <el-table v-loading="loading" :data="list" empty-text="暂无商户数据">
          <el-table-column
            v-if="isPlatform"
            prop="tenantName"
            label="所属社区"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column prop="merchantName" label="商户名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="shopType" label="经营类目" width="110" />
          <el-table-column label="联系人" width="100">
            <template #default="{ row }">{{ row.contactName || '--' }}</template>
          </el-table-column>
          <el-table-column label="在售商品" width="90" align="right">
            <template #default>
              <span class="muted" title="接口未返回">--</span>
            </template>
          </el-table-column>
          <el-table-column label="累计佣金" width="120" align="right">
            <template #default="{ row }">{{ money(row.totalCommission) }}</template>
          </el-table-column>
          <el-table-column label="待结算" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ danger: Number(row.pendingAmount) > 0 }">{{ money(row.pendingAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="经营状态" width="120">
            <template #default="{ row }">
              <span class="pill" :class="statusTone(row.onboardStatus)">
                {{ row.onboardStatusLabel || row.onboardStatus }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="openProfile(row)">详情</el-button>
                <el-dropdown trigger="click" @command="(c: string) => onMore(row, c)">
                  <el-button link class="ops-more"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="bills" :disabled="!row.merchantId">结算账单</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-drawer v-model="profileVisible" title="商户详情" size="560px" destroy-on-close>
      <el-tabs v-if="profile">
        <el-tab-pane label="基本资料">
          <el-descriptions :column="1" border>
            <el-descriptions-item v-if="isPlatform" label="所属社区">
              {{ profile.tenantName }}
            </el-descriptions-item>
            <el-descriptions-item label="商户名称">{{ profile.merchantName }}</el-descriptions-item>
            <el-descriptions-item label="经营类目">{{ profile.shopType }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ profile.contactName || '--' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ maskPhone(profile.contactPhone) }}</el-descriptions-item>
            <el-descriptions-item label="经营地址">{{ profile.address || '--' }}</el-descriptions-item>
            <el-descriptions-item label="营业执照号">{{ profile.businessLicense || '--' }}</el-descriptions-item>
            <el-descriptions-item label="佣金比例">{{ profile.commissionRate ?? 5 }}%</el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ profile.onboardStatusLabel || profile.statusLabel }}
            </el-descriptions-item>
            <el-descriptions-item v-if="profile.rejectReason" label="驳回原因">
              <span class="danger">{{ profile.rejectReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="简介">{{ profile.intro || '--' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="结算信息">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="累计应收佣金">{{ money(profile.totalCommission) }}</el-descriptions-item>
            <el-descriptions-item label="已结算">{{ money(profile.settledAmount) }}</el-descriptions-item>
            <el-descriptions-item label="待结算">{{ money(profile.pendingAmount) }}</el-descriptions-item>
            <el-descriptions-item label="结算状态">{{ profile.settleStatusLabel || '--' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="操作记录">
          <el-timeline v-if="logs.length">
            <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="fmtTime(l.createTime)" placement="top">
              【{{ l.stageLabel }}】{{ l.actionLabel }} · {{ l.operatorName || '--' }}
              <div class="muted">{{ l.remark || '' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审核日志" :image-size="56" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-drawer v-model="billVisible" :title="billTitle" size="760px" destroy-on-close>
      <div class="toolbar">
        <el-select v-model="billSettle" clearable placeholder="结算状态" style="width: 120px" @change="loadBills">
          <el-option label="待结算" value="PENDING" />
          <el-option label="已结算" value="SETTLED" />
        </el-select>
        <el-button type="primary" @click="loadBills">筛选</el-button>
        <el-button @click="exportBills">导出明细</el-button>
        <el-button
          v-if="canManage && selectedBills.length"
          type="success"
          :loading="settling"
          @click="doSettle"
        >
          标记已结算({{ selectedBills.length }})
        </el-button>
      </div>
      <el-table
        v-loading="billLoading"
        :data="billRows"
        height="480"
        empty-text="暂无佣金明细"
        @selection-change="(rows: any[]) => (selectedBills = rows)"
      >
        <el-table-column
          v-if="canManage"
          type="selection"
          width="42"
          :selectable="(r: any) => r.settleStatus === 'PENDING'"
        />
        <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
        <el-table-column label="成交额" width="110" align="right">
          <template #default="{ row }">{{ money(row.gmv) }}</template>
        </el-table-column>
        <el-table-column label="佣金率" width="80" align="right">
          <template #default="{ row }">{{ row.commissionRate }}%</template>
        </el-table-column>
        <el-table-column label="平台佣金" width="110" align="right">
          <template #default="{ row }">{{ money(row.commissionAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settleStatusLabel" label="结算状态" width="90" />
        <el-table-column label="产生时间" width="168">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="billPage"
          v-model:page-size="billSize"
          :total="billTotal"
          layout="total, prev, pager, next"
          @current-change="loadBills"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="submitVisible" title="提交社区超市入驻申请" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="超市名称" required>
          <el-input v-model="form.merchantName" maxlength="64" />
        </el-form-item>
        <el-form-item label="店铺类型">
          <el-input model-value="社区超市" disabled />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="经营地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="营业执照号"><el-input v-model="form.businessLicense" /></el-form-item>
        <el-form-item label="佣金比例%">
          <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.intro" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { pageTenants } from '@/api/saas'
import { exportExcel } from '@/utils/exportExcel'
import { fmtTime, maskPhone, money } from '@/utils/opsFormat'
import {
  getMerchantApply,
  listMerchantApplyLogs,
  listMerchantOnboardOverview,
  pageMerchantCommissionBills,
  settleMerchantCommissionBills,
  submitMerchantApply,
} from '@/api/store'

const user = useUserStore()
const isPlatform = computed(() => user.portal === 'admin' || user.role === 'platform_admin')
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<any[]>([])
const tenants = ref<any[]>([])
const tenantId = ref<number | undefined>()
const keyword = ref('')
const onboardStatus = ref<string | undefined>()
const settleStatus = ref<string | undefined>()

const stats = computed(() => {
  const rows = list.value
  return {
    approved: rows.filter((r) => r.onboardStatus === 'APPROVED').length,
    pendingPlatform: rows.filter((r) => r.onboardStatus === 'PENDING_PLATFORM').length,
    pendingProperty: rows.filter((r) => r.onboardStatus === 'PENDING_PROPERTY').length,
    rejected: rows.filter((r) => String(r.onboardStatus || '').startsWith('REJECTED')).length,
    pendingAmount: rows.reduce((s, r) => s + (Number(r.pendingAmount) || 0), 0),
  }
})

function statusTone(st: string) {
  if (st === 'APPROVED') return 'ok'
  if (st === 'PENDING_PROPERTY' || st === 'PENDING_PLATFORM') return 'warn'
  if (String(st).startsWith('REJECTED')) return 'danger'
  return 'off'
}

async function loadTenants() {
  if (!isPlatform.value) return
  try {
    const res = await pageTenants({ page: 1, size: 500 })
    tenants.value = res?.records || []
  } catch {
    tenants.value = []
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listMerchantOnboardOverview({
        tenantId: tenantId.value,
        keyword: keyword.value || undefined,
        onboardStatus: onboardStatus.value,
        settleStatus: settleStatus.value,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function exportList() {
  if (!list.value.length) {
    ElMessage.warning('暂无数据')
    return
  }
  exportExcel(
    list.value.map((r) => ({
      归属小区: r.tenantName,
      商户名称: r.merchantName,
      店铺类型: r.shopType,
      入驻状态: r.onboardStatusLabel,
      累计应收佣金: r.totalCommission,
      已结算金额: r.settledAmount,
      待结算金额: r.pendingAmount,
      结算状态: r.settleStatusLabel,
    })),
    '商户管理',
    '商户',
  )
}

const profileVisible = ref(false)
const profile = ref<any>(null)
const logs = ref<any[]>([])

async function openProfile(row: any) {
  profileVisible.value = true
  profile.value = row
  logs.value = []
  if (!row.applyId) return
  try {
    profile.value = { ...(await getMerchantApply(row.applyId)), ...row }
    logs.value = (await listMerchantApplyLogs(row.applyId)) || []
  } catch {
    profile.value = row
  }
}

function onMore(row: any, cmd: string) {
  if (cmd === 'bills') openBills(row)
}

const billVisible = ref(false)
const billLoading = ref(false)
const billRows = ref<any[]>([])
const billPage = ref(1)
const billSize = ref(20)
const billTotal = ref(0)
const billSettle = ref<string | undefined>()
const current = ref<any>(null)
const selectedBills = ref<any[]>([])
const settling = ref(false)
const billTitle = computed(() =>
  current.value ? `结算账单 · ${current.value.merchantName}` : '结算账单',
)

function openBills(row: any) {
  current.value = row
  billSettle.value = undefined
  billPage.value = 1
  billVisible.value = true
  loadBills()
}

async function loadBills() {
  if (!current.value?.merchantId) return
  billLoading.value = true
  try {
    const res = await pageMerchantCommissionBills({
      merchantId: current.value.merchantId,
      settleStatus: billSettle.value,
      page: billPage.value,
      size: billSize.value,
    })
    billRows.value = res?.records || []
    billTotal.value = res?.total || 0
  } catch {
    billRows.value = []
    billTotal.value = 0
  } finally {
    billLoading.value = false
  }
}

function exportBills() {
  if (!billRows.value.length) {
    ElMessage.warning('暂无明细')
    return
  }
  exportExcel(
    billRows.value.map((r) => ({
      小区: r.tenantName,
      商户: r.merchantName,
      订单号: r.orderNo,
      成交额: r.gmv,
      佣金率: r.commissionRate,
      应收佣金: r.commissionAmount,
      结算状态: r.settleStatusLabel,
      产生时间: r.createTime,
      结算时间: r.settleTime || '',
    })),
    `佣金明细_${current.value?.merchantName || ''}`,
    '明细',
  )
}

async function doSettle() {
  const ids = selectedBills.value.map((r) => r.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认将 ${ids.length} 笔账单标记为已结算？`, '结算确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  settling.value = true
  try {
    const res = await settleMerchantCommissionBills(ids)
    ElMessage.success(`已结算 ${res?.settledCount ?? 0} 笔`)
    await loadBills()
    await load()
  } finally {
    settling.value = false
  }
}

const submitVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  merchantName: '',
  shopType: '社区超市',
  contactName: '',
  contactPhone: '',
  address: '',
  businessLicense: '',
  commissionRate: 5,
  intro: '',
})

function openSubmit() {
  Object.assign(form, {
    merchantName: '',
    shopType: '社区超市',
    contactName: '',
    contactPhone: '',
    address: '',
    businessLicense: '',
    commissionRate: 5,
    intro: '',
  })
  submitVisible.value = true
}

async function doSubmit() {
  if (!form.merchantName.trim()) {
    ElMessage.warning('请填写超市名称')
    return
  }
  submitting.value = true
  try {
    await submitMerchantApply({ ...form })
    ElMessage.success('已提交，等待物业初审')
    submitVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadTenants()
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
  gap: 12px;
  margin-bottom: 14px;
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
  flex-wrap: wrap;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 12px 14px;
}
.kpi-label {
  display: block;
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  font-weight: 650;
  color: #182230;
}
.kpi-value.warn {
  color: #b54708;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #067647;
}
.pill.warn {
  background: #fffaeb;
  color: #b54708;
}
.pill.danger {
  background: #fef3f2;
  color: #b42318;
}
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.danger {
  color: #b42318;
  font-weight: 550;
}
.muted {
  color: #98a2b3;
  font-size: 12px;
}
.ops {
  display: inline-flex;
  gap: 2px;
}
.ops-link {
  color: #2563eb !important;
}
.ops-more {
  color: #667085 !important;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 1024px) {
  .kpi-row {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .ops-page {
    padding: 16px 12px;
  }
  .page-hd {
    flex-direction: column;
  }
  .kpi-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
