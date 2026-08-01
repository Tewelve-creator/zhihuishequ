<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">{{ isPlatform ? '入驻审核' : '入驻初审' }}</h1>
          <p class="page-desc">
            <template v-if="isPlatform">审核物业初审通过的超市入驻申请 · 通过后正式入驻</template>
            <template v-else>审核本小区超市入驻申请 · 通过后流转平台审核</template>
          </p>
        </div>
        <div class="page-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button v-if="!isPlatform && canManage" type="primary" @click="openSubmit">
            代提交申请
          </el-button>
        </div>
      </header>

      <div class="panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            clearable
            placeholder="商户名称"
            style="width: 200px"
            @keyup.enter="load"
          />
          <el-select
            v-if="!pendingOnly"
            v-model="status"
            clearable
            placeholder="审核状态"
            style="width: 150px"
            @change="load"
          >
            <el-option v-if="!isPlatform" label="待物业初审" value="PENDING_PROPERTY" />
            <el-option v-if="isPlatform" label="待平台审核" value="PENDING_PLATFORM" />
            <el-option label="已入驻" value="APPROVED" />
            <el-option label="物业已驳回" value="REJECTED_PROPERTY" />
            <el-option label="平台已驳回" value="REJECTED_PLATFORM" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
          <el-tag type="warning" effect="plain">待审 {{ pendingCount }}</el-tag>
        </div>

        <el-table v-loading="loading" :data="list" empty-text="暂无待审申请">
          <el-table-column label="申请编号" width="100">
            <template #default="{ row }">{{ row.id }}</template>
          </el-table-column>
          <el-table-column
            v-if="isPlatform"
            prop="tenantName"
            label="申请社区"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column prop="merchantName" label="商户名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="shopType" label="经营类目" width="110" />
          <el-table-column prop="contactName" label="申请人" width="100" />
          <el-table-column label="提交时间" width="168">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="审核状态" width="120">
            <template #default="{ row }">
              <span class="pill" :class="statusTone(row.status)">{{ row.statusLabel }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="openDetail(row)">审核</el-button>
                <template v-if="canAudit(row)">
                  <el-button link class="ok-link" @click="openAudit(row, true)">通过</el-button>
                  <el-button link class="danger-link" @click="openAudit(row, false)">驳回</el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="入驻审核" size="720px" destroy-on-close class="audit-drawer">
      <div v-if="current" class="audit-layout">
        <section class="audit-left">
          <h3>申请资料</h3>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-if="isPlatform" label="申请社区">
              {{ current.tenantName }}
            </el-descriptions-item>
            <el-descriptions-item label="商户名称">{{ current.merchantName }}</el-descriptions-item>
            <el-descriptions-item label="经营类目">{{ current.shopType }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ current.contactName || '--' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ maskPhone(current.contactPhone) }}</el-descriptions-item>
            <el-descriptions-item label="地址">{{ current.address || '--' }}</el-descriptions-item>
            <el-descriptions-item label="执照号">{{ current.businessLicense || '--' }}</el-descriptions-item>
            <el-descriptions-item label="简介">{{ current.intro || '--' }}</el-descriptions-item>
            <el-descriptions-item label="佣金比例">{{ current.commissionRate ?? 5 }}%</el-descriptions-item>
          </el-descriptions>
          <p class="hint">营业执照影像等附件字段当前接口未返回，仅展示文字资料。</p>
        </section>
        <section class="audit-right">
          <h3>审核结论</h3>
          <p class="status-line">
            当前状态：
            <span class="pill" :class="statusTone(current.status)">{{ current.statusLabel }}</span>
          </p>
          <div v-if="canAudit(current)" class="audit-actions">
            <el-button type="success" @click="openAudit(current, true)">通过</el-button>
            <el-button type="danger" @click="openAudit(current, false)">驳回</el-button>
          </div>
          <h3 class="sec">历史记录</h3>
          <el-timeline v-if="logs.length">
            <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="fmtTime(l.createTime)" placement="top">
              【{{ l.stageLabel }}】{{ l.actionLabel }} · {{ l.operatorName }}
              <div class="muted">{{ l.remark }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审核日志" :image-size="48" />
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="auditVisible" :title="auditApprove ? '审核通过' : '审核驳回'" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="商户">{{ current?.merchantName }}</el-form-item>
        <el-form-item :label="auditApprove ? '审核意见' : '驳回原因'" :required="!auditApprove">
          <el-input
            v-model="auditRemark"
            type="textarea"
            :rows="3"
            :placeholder="auditApprove ? '选填' : '必填驳回原因'"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button :type="auditApprove ? 'success' : 'danger'" :loading="saving" @click="doAudit">
          确认{{ auditApprove ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submitVisible" title="代提交入驻申请" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="超市名称" required>
          <el-input v-model="form.merchantName" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="经营地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="佣金比例%">
          <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" />
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
import { useUserStore } from '@/stores/user'
import { fmtTime, maskPhone } from '@/utils/opsFormat'
import {
  listMerchantApplies,
  listMerchantApplyLogs,
  listPlatformOnboardPending,
  listPropertyOnboardPending,
  platformAuditMerchantApply,
  propertyAuditMerchantApply,
  submitMerchantApply,
} from '@/api/store'

const props = withDefaults(
  defineProps<{ mode?: 'platform' | 'property'; pendingOnly?: boolean }>(),
  { mode: undefined, pendingOnly: true },
)

const user = useUserStore()
const isPlatform = computed(
  () =>
    props.mode === 'platform' ||
    (props.mode !== 'property' && (user.portal === 'admin' || user.role === 'platform_admin')),
)
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const status = ref<string | undefined>()
const pendingCount = computed(() =>
  list.value.filter((r) =>
    isPlatform.value ? r.status === 'PENDING_PLATFORM' : r.status === 'PENDING_PROPERTY',
  ).length,
)

function canAudit(row: any) {
  if (!canManage.value) return false
  if (isPlatform.value) return row.status === 'PENDING_PLATFORM'
  return row.status === 'PENDING_PROPERTY'
}

function statusTone(st: string) {
  if (st === 'APPROVED') return 'ok'
  if (String(st).startsWith('REJECTED')) return 'danger'
  return 'warn'
}

async function load() {
  loading.value = true
  try {
    if (props.pendingOnly !== false && !status.value) {
      list.value = isPlatform.value
        ? (await listPlatformOnboardPending(keyword.value || undefined)) || []
        : (await listPropertyOnboardPending(keyword.value || undefined)) || []
    } else {
      list.value =
        (await listMerchantApplies({
          keyword: keyword.value || undefined,
          status: status.value || (isPlatform.value ? 'PENDING_PLATFORM' : 'PENDING_PROPERTY'),
        })) || []
    }
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const detailVisible = ref(false)
const current = ref<any>(null)
const logs = ref<any[]>([])

async function openDetail(row: any) {
  current.value = row
  detailVisible.value = true
  try {
    logs.value = (await listMerchantApplyLogs(row.id)) || []
  } catch {
    logs.value = []
  }
}

const auditVisible = ref(false)
const auditApprove = ref(true)
const auditRemark = ref('')
const saving = ref(false)

function openAudit(row: any, approve: boolean) {
  current.value = row
  auditApprove.value = approve
  auditRemark.value = ''
  auditVisible.value = true
}

async function doAudit() {
  if (!current.value) return
  if (!auditApprove.value && !auditRemark.value.trim()) {
    ElMessage.warning('驳回须填写原因')
    return
  }
  try {
    await ElMessageBox.confirm(
      auditApprove.value
        ? `确认通过「${current.value.merchantName}」的入驻申请？`
        : `确认驳回「${current.value.merchantName}」？`,
      '二次确认',
      {
        type: 'warning',
        confirmButtonText: auditApprove.value ? '确认通过' : '确认驳回',
        confirmButtonClass: auditApprove.value ? undefined : 'el-button--danger',
      },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    if (isPlatform.value) {
      await platformAuditMerchantApply(
        current.value.id,
        auditApprove.value,
        auditRemark.value || undefined,
      )
    } else {
      await propertyAuditMerchantApply(
        current.value.id,
        auditApprove.value,
        auditRemark.value || undefined,
      )
    }
    ElMessage.success(auditApprove.value ? '已通过' : '已驳回')
    auditVisible.value = false
    detailVisible.value = false
    await load()
  } finally {
    saving.value = false
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
  commissionRate: 5,
})

function openSubmit() {
  Object.assign(form, {
    merchantName: '',
    shopType: '社区超市',
    contactName: '',
    contactPhone: '',
    address: '',
    commissionRate: 5,
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
    ElMessage.success('已提交入驻申请')
    submitVisible.value = false
    await load()
  } finally {
    submitting.value = false
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
.ops {
  display: inline-flex;
  gap: 2px;
  flex-wrap: wrap;
}
.ops-link {
  color: #2563eb !important;
}
.ok-link {
  color: #067647 !important;
}
.danger-link {
  color: #b42318 !important;
}
.audit-layout {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}
.audit-left h3,
.audit-right h3 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #182230;
}
.sec {
  margin-top: 20px !important;
}
.status-line {
  margin: 0 0 12px;
  font-size: 13px;
  color: #667085;
}
.audit-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.hint,
.muted {
  font-size: 12px;
  color: #98a2b3;
}
.hint {
  margin-top: 12px;
}
@media (max-width: 768px) {
  .ops-page {
    padding: 16px 12px;
  }
  .audit-layout {
    grid-template-columns: 1fr;
  }
}
</style>
