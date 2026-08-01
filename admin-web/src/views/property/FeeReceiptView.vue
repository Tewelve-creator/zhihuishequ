<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>收款通知</h2>
        <p class="sub">上方记录实收缴费人员；下方管理催缴推送。催缴会写入居民 App 站内消息</p>
      </div>
      <div class="actions">
        <el-button :loading="loading || payLoading" @click="reloadAll">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate()">创建催缴推送</el-button>
      </div>
    </div>

    <!-- 实收：谁交了 -->
    <h3 class="section-title">实收缴费记录</h3>
    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
        @change="loadPayments"
      />
      <el-input
        v-model="payKeyword"
        clearable
        placeholder="缴费人/房屋/手机/订单号"
        style="width: 220px"
        @keyup.enter="loadPayments"
      />
      <el-button type="primary" :loading="payLoading" @click="loadPayments">查询</el-button>
    </div>
    <div class="summary">
      <span>实收笔数 <b>{{ payments.length }}</b></span>
      <span>实收合计 <b>¥{{ payTotal.toFixed(2) }}</b></span>
    </div>
    <el-table
      v-loading="payLoading"
      :data="payments"
      stripe
      empty-text="暂无实收记录"
      style="margin-bottom: 24px"
      max-height="360"
    >
      <el-table-column prop="payerName" label="缴费人" width="100">
        <template #default="{ row }">{{ row.payerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="payerPhone" label="手机号" width="120">
        <template #default="{ row }">{{ row.payerPhone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="room" label="房屋" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.room || row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="amount" label="实收金额" width="110">
        <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="payChannelLabel" label="支付渠道" width="100" />
      <el-table-column prop="paidAt" label="缴费时间" width="170">
        <template #default="{ row }">{{ formatTime(row.paidAt) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default>
          <el-tag type="success" size="small">已缴</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 催缴推送 -->
    <h3 class="section-title">催缴推送记录</h3>
    <el-alert
      v-if="prefillTargets.length"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      :title="`已从欠费汇总带入 ${prefillTargets.length} 位业主，可直接创建催缴推送`"
    />

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无催缴推送">
      <el-table-column prop="title" label="通知标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="scope" label="推送范围" min-width="140" show-overflow-tooltip />
      <el-table-column prop="amountHint" label="费用说明" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.amountHint || '—' }}</template>
      </el-table-column>
      <el-table-column prop="pushCount" label="推送人数" width="100" />
      <el-table-column prop="sendAt" label="发送时间" width="170">
        <template #default="{ row }">{{ formatTime(row.sendAt) || '—' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SENT' ? 'success' : 'info'" size="small">
            {{ row.status === 'SENT' ? '已发送' : row.status || '—' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="创建催缴推送" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="通知标题" required>
          <el-input v-model="form.title" maxlength="64" placeholder="如：2026年7月物业费缴纳提醒" />
        </el-form-item>
        <el-form-item label="推送范围">
          <el-input v-model="form.scope" maxlength="64" placeholder="如：指定欠费业主 / 全小区业主" />
        </el-form-item>
        <el-form-item label="费用说明">
          <el-input v-model="form.amountHint" maxlength="64" placeholder="如：按房屋面积计费 / 逾期费用" />
        </el-form-item>
        <el-form-item label="通知正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="800"
            placeholder="推送给业主的催缴内容"
          />
        </el-form-item>
        <el-form-item v-if="form.targets.length" label="推送对象">
          <div class="targets">
            <el-tag
              v-for="(t, i) in form.targets"
              :key="i"
              closable
              style="margin: 0 6px 6px 0"
              @close="form.targets.splice(i, 1)"
            >
              {{ t.room || '' }} {{ t.owner || '' }}
              <template v-if="t.unpaidAmount != null"> · ¥{{ Number(t.unpaidAmount).toFixed(2) }}</template>
            </el-tag>
          </div>
          <p class="tip">有指定对象时仅推送给上述业主的 App 账号；清空后将推送本社区全部已绑定住户。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">立即推送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createFeeReceiptNotice,
  listFeePayments,
  listFeeReceiptNotices,
  type FeePayment,
  type FeeReceiptNotice,
} from '@/api/fee'

type Target = {
  houseId?: number
  userId?: number
  owner?: string
  room?: string
  phone?: string
  unpaidAmount?: number
}

const user = useUserStore()
const route = useRoute()
const router = useRouter()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const payLoading = ref(false)
const saving = ref(false)
const visible = ref(false)
const list = ref<FeeReceiptNotice[]>([])
const payments = ref<FeePayment[]>([])
const prefillTargets = ref<Target[]>([])
const dateRange = ref<[string, string] | null>(null)
const payKeyword = ref('')

const payTotal = computed(() =>
  payments.value.reduce((s, r) => s + Number(r.amount || 0), 0),
)

const form = reactive({
  title: '',
  content: '',
  scope: '',
  amountHint: '',
  targets: [] as Target[],
})

function formatTime(v?: string) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

function readPrefill() {
  try {
    const raw = sessionStorage.getItem('fee_urge_targets')
    if (!raw) {
      prefillTargets.value = []
      return
    }
    const arr = JSON.parse(raw) as Target[]
    prefillTargets.value = Array.isArray(arr) ? arr : []
  } catch {
    prefillTargets.value = []
  }
}

function openCreate(fromPrefill = false) {
  const targets = fromPrefill || route.query.create === '1' ? [...prefillTargets.value] : []
  const total = targets.reduce((s, t) => s + Number(t.unpaidAmount || 0), 0)
  form.title = targets.length
    ? `物业费催缴提醒（${targets.length}户）`
    : '物业费缴纳提醒'
  form.content = targets.length
    ? '您好，系统检测到您名下房屋存在未缴物业费用，请尽快登录智慧社区 App 完成缴费，避免影响相关服务。'
    : '请及时缴纳本期物业相关费用，可在智慧社区 App「物业缴费」中查看账单并在线支付。'
  form.scope = targets.length ? `指定业主（${targets.length}户）` : '全小区业主'
  form.amountHint = targets.length
    ? `合计欠费 ¥${total.toFixed(2)}`
    : '按房屋面积计费'
  form.targets = targets
  visible.value = true
}

async function loadPayments() {
  payLoading.value = true
  try {
    payments.value = await listFeePayments({
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      keyword: payKeyword.value || undefined,
    })
  } catch {
    payments.value = []
  } finally {
    payLoading.value = false
  }
}

async function loadNotices() {
  loading.value = true
  try {
    list.value = await listFeeReceiptNotices()
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function reloadAll() {
  loadPayments()
  loadNotices()
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写通知标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写通知正文')
    return
  }
  saving.value = true
  try {
    const res = await createFeeReceiptNotice({
      title: form.title.trim(),
      content: form.content.trim(),
      scope: form.scope.trim() || undefined,
      amountHint: form.amountHint.trim() || undefined,
      targets: form.targets,
    })
    ElMessage.success(`已推送 ${res?.pushCount ?? 0} 人`)
    visible.value = false
    sessionStorage.removeItem('fee_urge_targets')
    prefillTargets.value = []
    if (route.query.create) {
      router.replace({ path: '/property/fee/receipt' })
    }
    loadNotices()
  } finally {
    saving.value = false
  }
}

watch(
  () => route.query.create,
  (v) => {
    readPrefill()
    if (v === '1' && canManage.value) {
      openCreate(true)
    }
  },
)

onMounted(() => {
  readPrefill()
  reloadAll()
  if (route.query.create === '1' && canManage.value) {
    openCreate(true)
  }
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.summary {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.summary b {
  color: var(--el-color-primary);
  font-size: 15px;
  margin-left: 4px;
}
.targets {
  line-height: 1.6;
}
.tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
