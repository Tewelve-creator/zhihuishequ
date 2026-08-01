<template>
  <div class="onboard-page">
    <div class="onboard-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">开通新租户</h1>
          <p class="page-desc">配置租户资料、服务套餐及管理员账号</p>
        </div>
        <el-button class="back-btn" @click="$router.push('/admin/saas/tenants')">
          <el-icon class="btn-ico"><Back /></el-icon>
          返回租户列表
        </el-button>
      </header>

      <!-- 移动端顶部紧凑步骤 -->
      <nav class="steps-mobile" aria-label="开户步骤">
        <button
          v-for="(s, i) in steps"
          :key="s.key"
          type="button"
          class="m-step"
          :class="stepClass(i)"
          :disabled="!canGoStep(i)"
          @click="goStep(i)"
        >
          <span class="m-dot">
            <el-icon v-if="i < step || result" class="m-check"><Check /></el-icon>
            <template v-else>{{ i + 1 }}</template>
          </span>
          <span class="m-label">{{ s.title }}</span>
        </button>
      </nav>

      <div class="onboard-layout">
        <!-- 桌面端左侧纵向步骤 -->
        <aside class="steps-side" aria-label="开户步骤">
          <button
            v-for="(s, i) in steps"
            :key="s.key"
            type="button"
            class="side-step"
            :class="stepClass(i)"
            :disabled="!canGoStep(i)"
            @click="goStep(i)"
          >
            <span class="side-index">
              <el-icon v-if="i < step || (result && i <= 3)" class="side-check"><Check /></el-icon>
              <template v-else>{{ i + 1 }}</template>
            </span>
            <span class="side-meta">
              <span class="side-title">{{ s.title }}</span>
              <span class="side-hint">{{ stepHint(i) }}</span>
            </span>
          </button>
        </aside>

        <section class="panel">
          <div class="panel-body">
            <div class="section-hd">
              <h2 class="section-title">{{ steps[step].title }}</h2>
              <p class="section-desc">{{ steps[step].desc }}</p>
            </div>

            <!-- step 0 租户资料 -->
            <el-form
              v-show="step === 0"
              ref="form0Ref"
              :model="form"
              :rules="rules0"
              label-position="top"
              require-asterisk-position="right"
              class="wiz-form"
              @submit.prevent
            >
              <el-form-item label="租户名称" prop="name">
                <el-input
                  v-model="form.name"
                  placeholder="如 锦江花园社区"
                  maxlength="64"
                  clearable
                />
              </el-form-item>
              <el-form-item label="所在地区">
                <el-input
                  v-model="form.region"
                  placeholder="省 / 市 / 区（选填）"
                  maxlength="64"
                  clearable
                />
              </el-form-item>
              <el-form-item label="详细地址">
                <el-input
                  v-model="form.addressDetail"
                  placeholder="街道、门牌等详细地址（选填）"
                  maxlength="128"
                  clearable
                />
              </el-form-item>
              <div class="field-row">
                <el-form-item label="联系人" class="field-half">
                  <el-input v-model="form.contactName" placeholder="选填" maxlength="32" clearable />
                </el-form-item>
                <el-form-item label="联系电话" class="field-half">
                  <el-input
                    v-model="form.contactPhone"
                    placeholder="选填"
                    maxlength="20"
                    clearable
                  />
                </el-form-item>
              </div>
              <el-form-item label="备注">
                <el-input
                  v-model="form.remark"
                  type="textarea"
                  :rows="4"
                  placeholder="选填，内部备注不对外展示"
                  maxlength="200"
                  show-word-limit
                />
              </el-form-item>
            </el-form>

            <!-- step 1 套餐与周期 -->
            <el-form
              v-show="step === 1"
              ref="form1Ref"
              :model="form"
              :rules="rules1"
              label-position="top"
              require-asterisk-position="right"
              class="wiz-form"
              @submit.prevent
            >
              <el-form-item label="服务套餐" prop="packageId">
                <el-select
                  v-model="form.packageId"
                  placeholder="选择套餐货架"
                  style="width: 100%"
                  filterable
                >
                  <el-option
                    v-for="p in packages"
                    :key="p.id"
                    :label="`${p.name}（¥${p.priceMonth ?? p.monthPrice ?? '-'} /月）`"
                    :value="p.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="租赁周期" prop="cycle">
                <el-radio-group v-model="form.cycle" class="cycle-group">
                  <el-radio-button value="month">月付</el-radio-button>
                  <el-radio-button value="quarter">季付</el-radio-button>
                  <el-radio-button value="year">年付</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <div class="info-note">
                套餐功能码将随支付生效；未支付前租户也可先开通，高级能力以网关拦截为准。
              </div>
            </el-form>

            <!-- step 2 物业账号 -->
            <el-form
              v-show="step === 2"
              ref="form2Ref"
              :model="form"
              :rules="rules2"
              label-position="top"
              require-asterisk-position="right"
              class="wiz-form"
              @submit.prevent
            >
              <el-form-item label="登录手机号" prop="adminPhone">
                <el-input
                  v-model="form.adminPhone"
                  placeholder="物业工作端登录账号"
                  maxlength="20"
                  clearable
                />
              </el-form-item>
              <el-form-item label="登录密码" prop="adminPassword">
                <el-input
                  v-model="form.adminPassword"
                  type="password"
                  show-password
                  placeholder="至少 6 位"
                  maxlength="32"
                />
              </el-form-item>
              <el-form-item label="管理员姓名">
                <el-input
                  v-model="form.adminNickname"
                  placeholder="默认使用联系人姓名"
                  maxlength="32"
                  clearable
                />
              </el-form-item>
              <div class="info-note">开通后用该账号登录管理后台，门户选择「物业工作端」。</div>
            </el-form>

            <!-- step 3 确认开通 -->
            <div v-show="step === 3" class="confirm-block">
              <dl class="review-list">
                <div class="review-row">
                  <dt>租户</dt>
                  <dd>{{ form.name || '—' }}</dd>
                </div>
                <div class="review-row">
                  <dt>地址</dt>
                  <dd>{{ composedAddress || '—' }}</dd>
                </div>
                <div class="review-row">
                  <dt>联系人</dt>
                  <dd>{{ form.contactName || '—' }} / {{ form.contactPhone || '—' }}</dd>
                </div>
                <div class="review-row">
                  <dt>套餐</dt>
                  <dd>{{ pkgLabel }} · {{ cycleLabel }}</dd>
                </div>
                <div class="review-row">
                  <dt>物业账号</dt>
                  <dd>{{ form.adminPhone || '—' }} / ******</dd>
                </div>
                <div v-if="form.remark" class="review-row">
                  <dt>备注</dt>
                  <dd>{{ form.remark }}</dd>
                </div>
              </dl>

              <el-form label-position="top" class="wiz-form pay-form">
                <el-form-item label="支付方式" required>
                  <el-radio-group v-model="form.payMethod" class="pay-group" :disabled="!!result">
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
                <p class="pay-tip">支付成功后订单计入「对账管理」；演示环境各渠道均即时到账并开通套餐。</p>
              </el-form>

              <div v-if="result" class="success-box" role="status">
                <div class="success-title">
                  <el-icon><CircleCheckFilled /></el-icon>
                  开户成功
                </div>
                <p>租户 ID：{{ result.tenantId }}；订单号：{{ result.orderNo || '—' }}；状态：{{ result.orderStatus }}</p>
                <p v-if="result.adminPhone">物业账号：{{ result.adminPhone }}（密码为刚才设置）</p>
                <div class="success-actions">
                  <el-button type="primary" @click="$router.push('/admin/saas/tenants')">查看租户</el-button>
                  <el-button @click="$router.push('/admin/saas/orders')">查看订单</el-button>
                  <el-button @click="reset">再建一个</el-button>
                </div>
              </div>
            </div>
          </div>

          <footer v-if="!result" class="panel-ft">
            <el-button class="draft-btn" :disabled="saving" @click="saveDraft">
              <el-icon class="btn-ico"><Document /></el-icon>
              保存草稿
            </el-button>
            <div class="ft-right">
              <el-button :disabled="saving" @click="onCancel">取消</el-button>
              <el-button v-if="step > 0" :disabled="saving" @click="prev">
                <el-icon class="btn-ico"><ArrowLeft /></el-icon>
                上一步
              </el-button>
              <el-button v-if="step < 3" type="primary" :disabled="saving" @click="next">
                下一步
                <el-icon class="btn-ico end"><ArrowRight /></el-icon>
              </el-button>
              <el-button
                v-else
                type="primary"
                :loading="saving"
                :disabled="saving"
                @click="submit"
              >
                <el-icon v-if="!saving" class="btn-ico"><CircleCheck /></el-icon>
                确认开通
              </el-button>
            </div>
          </footer>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Back,
  Check,
  CircleCheck,
  CircleCheckFilled,
  Document,
} from '@element-plus/icons-vue'
import { createOrder, createTenant, listPackages, payOrder } from '@/api/saas'
import { SAAS_PAY_METHODS, type SaasPayMethod } from '@/config/saasPay'

const DRAFT_KEY = 'saas_tenant_onboard_draft_v1'

const router = useRouter()
const step = ref(0)
const saving = ref(false)
const packages = ref<any[]>([])
const form0Ref = ref<FormInstance>()
const form1Ref = ref<FormInstance>()
const form2Ref = ref<FormInstance>()

const result = ref<{
  tenantId: number
  orderNo?: string
  orderStatus: string
  adminPhone: string
} | null>(null)

const steps = [
  { key: 'profile', title: '租户资料', desc: '填写社区名称、地址与联系方式' },
  { key: 'package', title: '套餐与周期', desc: '选择服务套餐与计费周期' },
  { key: 'admin', title: '物业账号', desc: '创建物业工作端管理员登录账号' },
  { key: 'confirm', title: '确认开通', desc: '核对信息并生成首单' },
] as const

const form = reactive({
  name: '',
  /** 仅前端拆分展示，提交时合并进 address */
  region: '',
  addressDetail: '',
  contactName: '',
  contactPhone: '',
  remark: '',
  packageId: undefined as number | undefined,
  cycle: 'year',
  adminPhone: '',
  adminPassword: '123456',
  adminNickname: '',
  payMethod: 'mock' as SaasPayMethod,
})

const rules0: FormRules = {
  name: [{ required: true, message: '请填写租户名称', trigger: 'blur' }],
}
const rules1: FormRules = {
  packageId: [{ required: true, message: '请选择套餐', trigger: 'change' }],
  cycle: [{ required: true, message: '请选择租赁周期', trigger: 'change' }],
}
const rules2: FormRules = {
  adminPhone: [{ required: true, message: '请填写物业登录手机号', trigger: 'blur' }],
  adminPassword: [
    { required: true, message: '请填写登录密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

const composedAddress = computed(() => {
  return [form.region.trim(), form.addressDetail.trim()].filter(Boolean).join(' ')
})

const pkgLabel = computed(() => {
  const p = packages.value.find((x) => x.id === form.packageId)
  return p?.name || '—'
})
const cycleLabel = computed(() => {
  if (form.cycle === 'month') return '月付'
  if (form.cycle === 'quarter') return '季付'
  return '年付'
})

function stepClass(i: number) {
  if (result.value || i < step.value) return 'is-done'
  if (i === step.value) return 'is-current'
  return 'is-todo'
}

function stepHint(i: number) {
  if (result.value) return '已完成'
  if (i < step.value) return '已完成，可返回修改'
  if (i === step.value) return steps[i].desc
  return '待完成'
}

function canGoStep(i: number) {
  if (result.value) return false
  return i <= step.value
}

function goStep(i: number) {
  if (!canGoStep(i)) return
  step.value = i
}

function prev() {
  if (step.value > 0) step.value--
}

function onCancel() {
  router.push('/admin/saas/tenants')
}

async function next() {
  if (step.value === 0) {
    const ok = await form0Ref.value?.validate().catch(() => false)
    if (!ok) return
  }
  if (step.value === 1) {
    const ok = await form1Ref.value?.validate().catch(() => false)
    if (!ok) return
  }
  if (step.value === 2) {
    const ok = await form2Ref.value?.validate().catch(() => false)
    if (!ok) return
  }
  step.value++
}

async function loadPackages() {
  packages.value = (await listPackages().catch(() => [])) || []
  if (!form.packageId && packages.value.length) {
    form.packageId = packages.value[0].id
  }
}

function saveDraft() {
  const payload = {
    step: step.value,
    form: { ...form },
    savedAt: Date.now(),
  }
  localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
  ElMessage.success('草稿已保存到本机')
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (!data?.form) return
    const f = data.form
    Object.assign(form, f)
    // 兼容旧草稿仅有 address 字段
    if (!form.addressDetail && !form.region && f.address) {
      form.addressDetail = String(f.address)
    }
    if (typeof data.step === 'number' && data.step >= 0 && data.step <= 3) {
      step.value = data.step
    }
  } catch {
    /* ignore */
  }
}

async function submit() {
  if (saving.value) return
  saving.value = true
  result.value = null
  try {
    const address = composedAddress.value
    const tenant = await createTenant({
      name: form.name.trim(),
      address,
      contactName: form.contactName,
      contactPhone: form.contactPhone || form.adminPhone.trim(),
      packageId: form.packageId,
      remark: form.remark,
      adminPhone: form.adminPhone.trim(),
      adminPassword: form.adminPassword,
      adminNickname: form.adminNickname.trim() || form.contactName || undefined,
    })
    const tenantId = tenant?.id
    if (!tenantId) {
      ElMessage.error('租户创建失败')
      return
    }
    let order = await createOrder({
      tenantId,
      packageId: form.packageId,
      cycle: form.cycle,
      orderType: 'new',
    })
    if (!order?.id) {
      ElMessage.error('首单创建失败')
      return
    }
    order = await payOrder(order.id, form.payMethod)
    const orderStatus = order?.status || 'paid'
    result.value = {
      tenantId,
      orderNo: order?.orderNo,
      orderStatus,
      adminPhone: form.adminPhone.trim(),
    }
    localStorage.removeItem(DRAFT_KEY)
    ElMessage.success('开户支付成功，套餐已生效，订单已进入对账管理')
  } catch {
    /* request 已 toast */
  } finally {
    saving.value = false
  }
}

function reset() {
  result.value = null
  step.value = 0
  form.name = ''
  form.region = ''
  form.addressDetail = ''
  form.contactName = ''
  form.contactPhone = ''
  form.remark = ''
  form.adminPhone = ''
  form.adminPassword = '123456'
  form.adminNickname = ''
  form.cycle = 'year'
  form.payMethod = 'mock'
  form.packageId = undefined
  loadPackages()
}

onMounted(async () => {
  restoreDraft()
  await loadPackages()
})
</script>

<style scoped>
.onboard-page {
  min-height: calc(100% - 0px);
  margin: -20px;
  padding: 24px 24px 40px;
  background: #f5f6f8;
  box-sizing: border-box;
}

.onboard-shell {
  max-width: 1180px;
  margin: 0 auto;
}

.page-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 32px;
  letter-spacing: 0;
  color: #182230;
}

.page-desc {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 22px;
  letter-spacing: 0;
  color: #667085;
}

.back-btn {
  height: 40px;
  border-radius: 6px;
  border-color: #dfe3e8;
  color: #182230;
}

.btn-ico {
  margin-right: 4px;
  vertical-align: middle;
}
.btn-ico.end {
  margin-right: 0;
  margin-left: 4px;
}

/* —— 移动端步骤 —— */
.steps-mobile {
  display: none;
  gap: 4px;
  margin-bottom: 16px;
  padding: 12px;
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  overflow-x: auto;
}

.m-step {
  flex: 1;
  min-width: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 4px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: #667085;
}
.m-step:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.m-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid #dfe3e8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #fff;
  color: #667085;
}
.m-check {
  font-size: 14px;
}
.m-label {
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}
.m-step.is-current .m-dot {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}
.m-step.is-current .m-label {
  color: #2563eb;
  font-weight: 600;
}
.m-step.is-done .m-dot {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

.onboard-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

/* —— 左侧步骤 —— */
.steps-side {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 12px;
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
}

.side-step {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: #667085;
}
.side-step:hover:not(:disabled) {
  background: #f5f6f8;
}
.side-step:disabled {
  cursor: not-allowed;
}
.side-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid #dfe3e8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: #fff;
  color: #667085;
}
.side-check {
  font-size: 14px;
}
.side-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 2px;
}
.side-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  letter-spacing: 0;
  color: #182230;
}
.side-hint {
  font-size: 12px;
  line-height: 18px;
  color: #667085;
}
.side-step.is-current {
  background: #eff6ff;
}
.side-step.is-current .side-index {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}
.side-step.is-current .side-title {
  color: #2563eb;
}
.side-step.is-done .side-index {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

/* —— 右侧内容面板 —— */
.panel {
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.panel-body {
  padding: 32px 40px 24px;
}

.section-hd {
  margin-bottom: 24px;
  max-width: 680px;
}
.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  letter-spacing: 0;
  color: #182230;
}
.section-desc {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 22px;
  color: #667085;
}

.wiz-form {
  max-width: 680px;
}

.wiz-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.wiz-form :deep(.el-form-item__label) {
  margin-bottom: 8px !important;
  height: auto;
  line-height: 20px;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0;
  color: #182230;
}
.wiz-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
  line-height: 18px;
}
.wiz-form :deep(.el-input__wrapper),
.wiz-form :deep(.el-textarea__inner) {
  border-radius: 6px;
  box-shadow: 0 0 0 1px #dfe3e8 inset;
}
.wiz-form :deep(.el-input__wrapper:hover),
.wiz-form :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #c5cad3 inset;
}
.wiz-form :deep(.el-input__wrapper.is-focus),
.wiz-form :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #2563eb inset !important;
}
.wiz-form :deep(.el-input__inner) {
  height: 40px;
  font-size: 14px;
  color: #182230;
}
.wiz-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background: #f5f6f8;
}
.wiz-form :deep(.el-select .el-input__wrapper) {
  min-height: 40px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.field-half {
  margin-bottom: 20px;
}

.cycle-group :deep(.el-radio-button__inner) {
  min-width: 88px;
  height: 40px;
  line-height: 38px;
  border-radius: 0;
  font-size: 14px;
}
.cycle-group :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 6px 0 0 6px;
}
.cycle-group :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 6px 6px 0;
}
.cycle-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #2563eb;
  border-color: #2563eb;
  box-shadow: -1px 0 0 0 #2563eb;
}

.info-note {
  margin-top: 4px;
  padding: 12px 16px;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  background: #f5f6f8;
  font-size: 13px;
  line-height: 20px;
  color: #667085;
  max-width: 680px;
}

.confirm-block {
  max-width: 680px;
}

.review-list {
  margin: 0;
  border: 1px solid #dfe3e8;
  border-radius: 6px;
  overflow: hidden;
}
.review-row {
  display: grid;
  grid-template-columns: 112px 1fr;
  gap: 0;
  border-bottom: 1px solid #dfe3e8;
}
.review-row:last-child {
  border-bottom: 0;
}
.review-row dt,
.review-row dd {
  margin: 0;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 22px;
  letter-spacing: 0;
}
.review-row dt {
  background: #f5f6f8;
  color: #667085;
  border-right: 1px solid #dfe3e8;
  font-weight: 500;
}
.review-row dd {
  color: #182230;
  word-break: break-word;
}

.pay-form {
  margin-top: 24px;
}
.pay-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  width: 100%;
}
.pay-radio {
  width: 100%;
  margin: 0 !important;
  height: auto !important;
  padding: 12px 14px !important;
}
.pay-label {
  display: block;
  font-weight: 600;
  color: #182230;
}
.pay-desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
}
.pay-tip {
  margin: 0;
  font-size: 12px;
  color: #98a2b3;
}

.success-box {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  background: #f0fdf4;
  color: #166534;
  font-size: 14px;
  line-height: 22px;
}
.success-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #15803d;
}
.success-box p {
  margin: 0 0 4px;
}
.success-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.panel-ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 40px;
  border-top: 1px solid #dfe3e8;
  background: #fafbfc;
  border-radius: 0 0 8px 8px;
}
.ft-right {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.draft-btn {
  border-color: #dfe3e8;
  color: #182230;
}
.panel-ft :deep(.el-button) {
  height: 40px;
  border-radius: 6px;
  padding: 0 16px;
}
.panel-ft :deep(.el-button--primary) {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
}

/* —— 响应式 —— */
@media (max-width: 1024px) {
  .onboard-layout {
    grid-template-columns: 200px minmax(0, 1fr);
    gap: 16px;
  }
  .panel-body,
  .panel-ft {
    padding-left: 24px;
    padding-right: 24px;
  }
}

@media (max-width: 768px) {
  .onboard-page {
    padding: 16px 12px 32px;
  }
  .steps-mobile {
    display: flex;
  }
  .steps-side {
    display: none;
  }
  .onboard-layout {
    grid-template-columns: 1fr;
  }
  .page-hd {
    flex-direction: column;
    align-items: stretch;
  }
  .back-btn {
    align-self: flex-start;
  }
  .field-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .panel-ft {
    flex-direction: column-reverse;
    align-items: stretch;
  }
  .ft-right {
    width: 100%;
  }
  .ft-right :deep(.el-button),
  .draft-btn {
    flex: 1;
  }
  .review-row {
    grid-template-columns: 1fr;
  }
  .review-row dt {
    border-right: 0;
    border-bottom: 1px solid #dfe3e8;
    padding-bottom: 4px;
  }
}

@media (max-width: 375px) {
  .page-title {
    font-size: 22px;
  }
  .m-label {
    font-size: 11px;
  }
  .panel-body {
    padding: 20px 16px 16px;
  }
  .panel-ft {
    padding: 12px 16px;
  }
  .ft-right {
    gap: 6px;
  }
}
</style>
