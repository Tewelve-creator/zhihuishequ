<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">提现审核</h1>
          <p class="page-desc">商户营销资金提现申请 · 通过后结转，驳回退回余额</p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </header>

      <div class="kpi-row">
        <div class="kpi">
          <span class="kpi-label">待审核</span>
          <span class="kpi-value warn">{{ pendingCount }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">本列表合计</span>
          <span class="kpi-value">{{ list.length }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">待审金额</span>
          <span class="kpi-value">{{ money(pendingAmount) }}</span>
        </div>
      </div>

      <div class="panel">
        <div class="toolbar">
          <el-select v-model="status" clearable placeholder="审核状态" style="width: 140px" @change="load">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>

        <el-table v-loading="loading" :data="list" empty-text="暂无提现申请">
          <el-table-column label="提现单号" width="100">
            <template #default="{ row }">{{ row.id }}</template>
          </el-table-column>
          <el-table-column prop="tenantName" label="社区" min-width="110" show-overflow-tooltip />
          <el-table-column prop="merchantName" label="商户" min-width="130" show-overflow-tooltip />
          <el-table-column label="申请金额" width="120" align="right">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="手续费" width="100" align="right">
            <template #default>
              <span class="muted" title="接口未返回手续费">--</span>
            </template>
          </el-table-column>
          <el-table-column label="实际到账" width="120" align="right">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="收款账户" width="140">
            <template #default="{ row }">
              {{ maskAccount(row.accountNo || row.bankAccount || row.payAccount) }}
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="168">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="审核状态" width="100">
            <template #default="{ row }">
              <span class="pill" :class="statusTone(row.status)">{{ row.statusLabel || row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link class="ok-link" @click="openAudit(row, 'APPROVED')">通过</el-button>
                <el-button link class="danger-link" @click="openAudit(row, 'REJECTED')">拒绝</el-button>
              </template>
              <el-button v-else link class="ops-link" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="auditVisible" :title="auditTitle" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="社区">{{ current?.tenantName }}</el-form-item>
        <el-form-item label="商户">{{ current?.merchantName }}</el-form-item>
        <el-form-item label="金额">{{ money(current?.amount) }}</el-form-item>
        <el-form-item label="审核意见" :required="auditStatus === 'REJECTED'">
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            :placeholder="auditStatus === 'REJECTED' ? '驳回须填写意见' : '选填'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button
          :type="auditStatus === 'APPROVED' ? 'success' : 'danger'"
          :loading="saving"
          @click="submitAudit"
        >
          确认{{ auditStatus === 'APPROVED' ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="提现详情" size="420px">
      <dl v-if="current" class="detail-dl">
        <div><dt>单号</dt><dd>{{ current.id }}</dd></div>
        <div><dt>商户</dt><dd>{{ current.merchantName }}</dd></div>
        <div><dt>金额</dt><dd>{{ money(current.amount) }}</dd></div>
        <div><dt>状态</dt><dd>{{ current.statusLabel }}</dd></div>
        <div><dt>申请人</dt><dd>{{ current.applicantName || '--' }}</dd></div>
        <div><dt>申请备注</dt><dd>{{ current.applyRemark || '--' }}</dd></div>
        <div><dt>审核人</dt><dd>{{ current.auditorName || '--' }}</dd></div>
        <div><dt>审核意见</dt><dd>{{ current.auditRemark || '--' }}</dd></div>
        <div><dt>审核时间</dt><dd>{{ fmtTime(current.auditTime) }}</dd></div>
      </dl>
      <p class="hint">可提现余额、关联账单等需钱包明细接口，当前提现列表未返回。</p>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditMerchantWithdraw, listMerchantWithdraws } from '@/api/store'
import { fmtTime, maskAccount, money } from '@/utils/opsFormat'

const loading = ref(false)
const list = ref<any[]>([])
const status = ref<string | undefined>('PENDING')
const pendingCount = computed(() => list.value.filter((r) => r.status === 'PENDING').length)
const pendingAmount = computed(() =>
  list.value
    .filter((r) => r.status === 'PENDING')
    .reduce((s, r) => s + (Number(r.amount) || 0), 0),
)

function statusTone(st: string) {
  if (st === 'PENDING') return 'warn'
  if (st === 'APPROVED') return 'ok'
  return 'off'
}

async function load() {
  loading.value = true
  try {
    list.value = (await listMerchantWithdraws({ status: status.value || undefined })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const auditVisible = ref(false)
const detailVisible = ref(false)
const saving = ref(false)
const current = ref<any>(null)
const auditStatus = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const auditRemark = ref('')
const auditTitle = computed(() =>
  auditStatus.value === 'APPROVED' ? '通过提现申请' : '拒绝提现申请',
)

function openAudit(row: any, st: 'APPROVED' | 'REJECTED') {
  current.value = row
  auditStatus.value = st
  auditRemark.value = ''
  auditVisible.value = true
}

function openDetail(row: any) {
  current.value = row
  detailVisible.value = true
}

async function submitAudit() {
  if (!current.value) return
  if (auditStatus.value === 'REJECTED' && !auditRemark.value.trim()) {
    ElMessage.warning('拒绝须填写审核意见')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认${auditStatus.value === 'APPROVED' ? '通过' : '拒绝'}「${current.value.merchantName}」提现 ${money(current.value.amount)}？`,
      '二次确认',
      {
        type: 'warning',
        confirmButtonText: '确认',
        confirmButtonClass: auditStatus.value === 'REJECTED' ? 'el-button--danger' : undefined,
      },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await auditMerchantWithdraw(current.value.id, auditStatus.value, auditRemark.value || undefined)
    ElMessage.success(auditStatus.value === 'APPROVED' ? '已通过' : '已拒绝')
    auditVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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
  gap: 8px;
  margin-bottom: 12px;
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
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.muted {
  color: #98a2b3;
}
.ok-link {
  color: #067647 !important;
}
.danger-link {
  color: #b42318 !important;
}
.ops-link {
  color: #2563eb !important;
}
.detail-dl {
  margin: 0;
  display: grid;
  gap: 12px;
}
.detail-dl > div {
  display: grid;
  grid-template-columns: 88px 1fr;
  font-size: 13px;
}
.detail-dl dt {
  margin: 0;
  color: #667085;
}
.detail-dl dd {
  margin: 0;
  color: #182230;
}
.hint {
  margin-top: 16px;
  font-size: 12px;
  color: #98a2b3;
}
@media (max-width: 768px) {
  .ops-page {
    padding: 16px 12px;
  }
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
