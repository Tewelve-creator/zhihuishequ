<template>
  <div class="login-page">
    <header class="brand">
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 40 40" fill="none">
          <rect width="40" height="40" rx="12" fill="url(#bm)" />
          <path d="M8 24 L20 12 L32 24 V30 H8 Z" fill="#fff" opacity=".95" />
          <rect x="16" y="22" width="8" height="8" rx="1.5" fill="#0d9488" />
          <defs>
            <linearGradient id="bm" x1="0" y1="0" x2="40" y2="40">
              <stop stop-color="#2dd4bf" />
              <stop offset="1" stop-color="#0f766e" />
            </linearGradient>
          </defs>
        </svg>
      </span>
      <div class="brand-text">
        <strong>智慧社区平台</strong>
        <em>Smart Community</em>
      </div>
    </header>

    <div class="shell" :class="{ chosen: !!portal, triple: !portal }">
      <!-- 选择入口 -->
      <template v-if="!portal">
        <button type="button" class="pick property" @click="choose('property')">
          <span class="pick-icon" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none">
              <path d="M8 22 L24 8 L40 22 V40 H8 Z" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" />
              <rect x="18" y="26" width="12" height="14" rx="2" fill="currentColor" opacity=".2" />
            </svg>
          </span>
          <p class="badge">PROPERTY</p>
          <h1>物业端登录</h1>
          <p class="sub">社区物业工作台<br />工单 · 缴费 · 巡检 · 商超</p>
          <span class="cta"><span>点击进入</span><i>→</i></span>
        </button>
        <button type="button" class="pick merchant" @click="choose('merchant')">
          <span class="pick-icon" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none">
              <path d="M10 18 H38 L36 38 H12 Z" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" />
              <path d="M16 18 V14 A8 8 0 0 1 32 14 V18" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
            </svg>
          </span>
          <p class="badge">MERCHANT</p>
          <h1>商户端登录</h1>
          <p class="sub">社区商超独立后台<br />店铺 · 商品 · 订单</p>
          <span class="cta"><span>点击进入</span><i>→</i></span>
        </button>
        <button type="button" class="pick admin" @click="choose('admin')">
          <span class="pick-icon" aria-hidden="true">
            <svg viewBox="0 0 48 48" fill="none">
              <rect x="8" y="10" width="32" height="22" rx="4" stroke="currentColor" stroke-width="2.4" />
              <path d="M14 38 H34 M24 32 V38" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
              <circle cx="18" cy="21" r="2.2" fill="currentColor" />
              <circle cx="24" cy="21" r="2.2" fill="currentColor" />
              <circle cx="30" cy="21" r="2.2" fill="currentColor" />
            </svg>
          </span>
          <p class="badge">OPERATIONS</p>
          <h1>运营端登录</h1>
          <p class="sub">SaaS 平台运营<br />租户 · 套餐 · 驾驶舱</p>
          <span class="cta"><span>点击进入</span><i>→</i></span>
        </button>
      </template>

      <!-- 物业端表单 -->
      <section v-else-if="portal === 'property'" class="pane property">
        <button type="button" class="back" @click="portal = null">‹ 返回选择</button>
        <header class="pane-head">
          <p class="badge">PROPERTY</p>
          <h1>物业端登录</h1>
          <p class="sub">请输入账号与密码</p>
        </header>

        <form class="login-form" @submit.prevent="onSubmitProperty">
          <label class="field">
            <span>账号</span>
            <input
              v-model="propertyForm.username"
              type="text"
              autocomplete="username"
              placeholder="物业手机号或账号"
              @input="onPropertyUsernameInput"
              @blur="resolvePropertyCommunity"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="propertyForm.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <div
            class="community-box"
            :class="{ ready: !!propertyCommunityName || propertyCommunities.length > 0, miss: propertyCommunityMiss }"
          >
            <span class="community-label">所属社区</span>
            <span v-if="resolvingCommunity" class="community-value muted">识别中…</span>
            <template v-else-if="propertyCommunities.length > 1">
              <select v-model="propertyForm.tenantId" class="community-select-login">
                <option v-for="c in propertyCommunities" :key="c.id" :value="c.id">
                  {{ c.name }}{{ c.packageName ? ` · ${c.packageName}` : '' }}
                </option>
              </select>
            </template>
            <span v-else-if="propertyCommunityName" class="community-value">
              {{ propertyCommunityName
              }}{{ selectedPropertyPkg ? ` · ${selectedPropertyPkg}` : '' }}
            </span>
            <span v-else-if="propertyCommunityMiss" class="community-value muted">未匹配到社区，请确认账号</span>
            <span v-else class="community-value muted">输入账号后自动识别</span>
          </div>
          <button class="login-btn" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : 'LOGIN' }}
          </button>
        </form>

        <p class="hint">
          演示：admin1 / admin1<br />
          真实：13800000001 / 123456
        </p>
      </section>

      <!-- 商户端表单 -->
      <section v-else-if="portal === 'merchant'" class="pane merchant">
        <button type="button" class="back" @click="portal = null">‹ 返回选择</button>
        <header class="pane-head">
          <p class="badge">MERCHANT</p>
          <h1>商户端登录</h1>
          <p class="sub">仅可管理本店数据，与物业后台分离</p>
        </header>

        <form class="login-form" @submit.prevent="onSubmitMerchant">
          <label class="field">
            <span>商户账号</span>
            <input
              v-model="merchantForm.username"
              type="text"
              autocomplete="username"
              placeholder="物业开通的商户登录账号"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="merchantForm.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <label class="field">
            <span>所属社区</span>
            <select v-model="merchantForm.tenantId">
              <option v-if="!tenants.length" disabled value="">暂无社区</option>
              <option v-for="t in tenants" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </label>
          <button class="login-btn merchant-btn" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : 'LOGIN' }}
          </button>
        </form>

        <p class="hint">请使用物业在「社区商超 → 商户列表」开通的账号登录</p>
      </section>

      <!-- 运营端表单 -->
      <section v-else class="pane admin">
        <button type="button" class="back light" @click="portal = null">‹ 返回选择</button>
        <header class="pane-head light">
          <p class="badge">OPERATIONS</p>
          <h1>运营端登录</h1>
          <p class="sub">请输入账号与密码</p>
        </header>

        <form class="login-form light" @submit.prevent="onSubmitAdmin">
          <label class="field">
            <span>账号</span>
            <input
              v-model="adminForm.username"
              type="text"
              autocomplete="username"
              placeholder="运营账号或手机号"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="adminForm.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <button class="login-btn ghost" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : 'LOGIN' }}
          </button>
        </form>

        <p class="hint light">
          演示：admin2 / admin2<br />
          真实：13900000000 / 123456
        </p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore, type PortalType } from '@/stores/user'
import { DEMO_COMMUNITY_PACKAGES, DEMO_FEATURES } from '@/config/features'
import { loginApi, listPublicTenants, resolveCommunitiesByPhone } from '@/api/auth'

type CommunityOpt = {
  id: number
  name: string
  packageCode?: string
  packageName?: string
  role?: string
}

const ACCOUNTS: Record<string, { password: string; portal: PortalType; home: string }> = {
  admin1: { password: 'admin1', portal: 'property', home: '/property/dashboard' },
  admin2: { password: 'admin2', portal: 'admin', home: '/admin/saas/cockpit' },
  degraded1: { password: '123456', portal: 'property', home: '/property/dashboard' },
}

const DEMO_COMMUNITY: Record<string, string> = {
  admin1: '阳光花园社区',
  degraded1: '翠湖湾社区',
}

const PLATFORM_PHONES = new Set(['13900000000'])

type PortalPick = 'property' | 'admin' | 'merchant' | null

const tenants = ref<Array<{ id: number; name: string; address?: string }>>([])
const router = useRouter()
const route = useRoute()
const user = useUserStore()
const portal = ref<PortalPick>(null)
const loading = ref(false)
const resolvingCommunity = ref(false)
const propertyCommunityName = ref('')
const propertyCommunityMiss = ref(false)
const propertyCommunities = ref<CommunityOpt[]>([])
let resolveTimer: ReturnType<typeof setTimeout> | null = null

const propertyForm = reactive({
  username: '13800000001',
  password: '',
  tenantId: undefined as number | undefined,
})

const selectedPropertyPkg = computed(() => {
  const hit = propertyCommunities.value.find((c) => c.id === propertyForm.tenantId)
  return hit?.packageName || ''
})

const merchantForm = reactive({
  username: '',
  password: '',
  tenantId: undefined as number | undefined,
})

const adminForm = reactive({
  username: '13900000000',
  password: '',
})

function choose(p: 'property' | 'admin' | 'merchant') {
  portal.value = p
  if (p === 'property') {
    propertyForm.password = ''
    resolvePropertyCommunity()
  } else if (p === 'merchant') merchantForm.password = ''
  else adminForm.password = ''
}

function onPropertyUsernameInput() {
  propertyCommunityName.value = ''
  propertyCommunityMiss.value = false
  propertyCommunities.value = []
  propertyForm.tenantId = undefined
  if (resolveTimer) clearTimeout(resolveTimer)
  resolveTimer = setTimeout(() => {
    resolvePropertyCommunity()
  }, 400)
}

async function resolvePropertyCommunity() {
  const username = propertyForm.username.trim()
  if (!username) {
    propertyCommunityName.value = ''
    propertyCommunityMiss.value = false
    propertyCommunities.value = []
    propertyForm.tenantId = undefined
    return
  }
  if (DEMO_COMMUNITY[username]) {
    propertyCommunityName.value = DEMO_COMMUNITY[username]
    propertyCommunities.value = []
    propertyCommunityMiss.value = false
    propertyForm.tenantId = undefined
    return
  }
  if (PLATFORM_PHONES.has(username)) {
    propertyCommunityName.value = ''
    propertyCommunities.value = []
    propertyCommunityMiss.value = true
    propertyForm.tenantId = undefined
    return
  }
  resolvingCommunity.value = true
  try {
    const list = ((await resolveCommunitiesByPhone(username, 'property')) || []) as CommunityOpt[]
    propertyCommunities.value = list
    if (list.length) {
      const keep = list.find((t) => t.id === propertyForm.tenantId)
      propertyForm.tenantId = keep?.id ?? list[0].id
      const cur = list.find((t) => t.id === propertyForm.tenantId) || list[0]
      propertyCommunityName.value = cur.name
      propertyCommunityMiss.value = false
    } else {
      propertyForm.tenantId = undefined
      propertyCommunityName.value = ''
      propertyCommunityMiss.value = true
    }
  } catch {
    propertyForm.tenantId = undefined
    propertyCommunities.value = []
    propertyCommunityName.value = ''
    propertyCommunityMiss.value = true
  } finally {
    resolvingCommunity.value = false
  }
}

async function loadTenants() {
  try {
    const list = (await listPublicTenants()) || []
    tenants.value = list
    if (list.length) {
      if (merchantForm.tenantId == null || !list.some((t) => t.id === merchantForm.tenantId)) {
        merchantForm.tenantId = list[0].id
      }
    }
  } catch {
    tenants.value = []
  }
}

function featuresToStr(f: unknown) {
  if (Array.isArray(f)) return f.join(',')
  return typeof f === 'string' ? f : ''
}

function portalMismatchMsg(expect: PortalType) {
  if (expect === 'property') return '该账号不属于物业端，请返回选择正确入口登录'
  if (expect === 'merchant') return '该账号不属于商户端，请返回选择商户端登录'
  return '该账号不属于运营端，请返回选择运营端登录'
}

async function goHome(p: PortalType, home: string) {
  const redirect = route.query.redirect as string | undefined
  const okRedirect =
    redirect &&
    ((p === 'admin' && redirect.startsWith('/admin')) ||
      (p === 'property' && redirect.startsWith('/property')) ||
      (p === 'merchant' && redirect.startsWith('/merchant')))
  try {
    await router.replace(okRedirect ? redirect : home)
  } catch (e) {
    console.error('登录后跳转失败', e)
    ElMessage.error('进入系统失败，请刷新后重试')
  }
}

async function loginDemo(username: string, password: string, expectPortal: PortalType) {
  const account = ACCOUNTS[username]
  if (!account || password !== account.password) {
    ElMessage.error('账号或密码错误')
    return
  }
  if (account.portal !== expectPortal) {
    ElMessage.error(portalMismatchMsg(expectPortal))
    return
  }
  const demo = DEMO_FEATURES[username]
  const communityName =
    account.portal === 'property'
      ? DEMO_COMMUNITY[username] || '阳光花园社区'
      : account.portal === 'admin'
        ? 'SaaS 运营平台'
        : undefined
  const pkg = communityName ? DEMO_COMMUNITY_PACKAGES[communityName] : undefined
  const demoTenantId =
    account.portal === 'property' && communityName
      ? Object.keys(DEMO_COMMUNITY_PACKAGES)
          .filter((n) => !n.includes('演示'))
          .indexOf(communityName) + 1 || undefined
      : undefined
  user.login({
    token: `token-${Date.now()}`,
    username,
    portal: account.portal,
    features: pkg?.features || demo?.features,
    tenantStatus: demo?.tenantStatus,
    role: demo?.role || (account.portal === 'admin' ? 'platform_admin' : 'property_admin'),
    communityName,
    packageCode: pkg?.packageCode,
    packageName: pkg?.packageName,
    tenantId: demoTenantId || undefined,
  })
  ElMessage.success(
    demo?.tenantStatus === 'degraded' ? '登录成功（已降级：仅公告/报修）' : '登录成功（演示）',
  )
  goHome(account.portal, account.home)
}

async function loginPropertyReal(phone: string) {
  if (PLATFORM_PHONES.has(phone)) {
    ElMessage.error('该账号属于运营端，请返回选择运营端登录')
    return
  }
  if (!propertyCommunityName.value && propertyForm.tenantId == null) {
    await resolvePropertyCommunity()
  }
  const data = await loginApi({
    phone,
    password: propertyForm.password,
    tenantId: propertyForm.tenantId,
    clientType: 'ADMIN',
  })
  const role = data.role || ''
  if (role === 'platform_admin') {
    ElMessage.error('该账号属于运营端，请返回选择运营端登录')
    return
  }
  if (role === 'store_merchant') {
    ElMessage.error('该账号属于商户端，请返回选择商户端登录')
    return
  }
  const tenant =
    propertyCommunities.value.find((t) => t.id === Number(data.tenantId || propertyForm.tenantId)) ||
    tenants.value.find((t) => t.id === Number(data.tenantId || propertyForm.tenantId)) ||
    null
  user.login({
    token: data.token,
    username: data.nickname || phone,
    phone: data.phone || phone,
    portal: 'property',
    features: featuresToStr(data.effectiveFeatureCodes || data.features),
    effectiveFeatureCodes: data.effectiveFeatureCodes,
    tenantStatus: data.tenantStatus || 'active',
    role,
    tenantId: data.tenantId ?? propertyForm.tenantId,
    userId: data.userId,
    communityName: data.tenantName || propertyCommunityName.value || tenant?.name || '当前社区',
    packageCode: data.packageCode || tenant?.packageCode,
    packageName: data.packageName || tenant?.packageName,
  })
  ElMessage.success('登录成功')
  goHome('property', '/property/dashboard')
}

async function loginMerchantReal(phone: string) {
  if (merchantForm.tenantId == null) {
    ElMessage.error('请选择所属社区')
    return
  }
  const data = await loginApi({
    phone,
    password: merchantForm.password,
    tenantId: merchantForm.tenantId,
    clientType: 'ADMIN',
  })
  const role = data.role || ''
  if (role !== 'store_merchant') {
    ElMessage.error(portalMismatchMsg('merchant'))
    return
  }
  if (!data.shopId) {
    ElMessage.error('商户店铺未绑定，请联系物业')
    return
  }
  const tenant = tenants.value.find((t) => t.id === Number(data.tenantId || merchantForm.tenantId))
  user.login({
    token: data.token,
    username: data.nickname || phone,
    portal: 'merchant',
    features: featuresToStr(data.features),
    tenantStatus: data.tenantStatus || 'active',
    role,
    tenantId: data.tenantId ?? merchantForm.tenantId,
    userId: data.userId,
    shopId: data.shopId,
    communityName: tenant?.name || '当前社区',
  })
  ElMessage.success('登录成功')
  goHome('merchant', '/merchant/home')
}

async function loginAdminReal(phone: string) {
  const data = await loginApi({
    phone,
    password: adminForm.password,
    clientType: 'ADMIN',
  })
  const role = data.role || ''
  if (role !== 'platform_admin') {
    ElMessage.error('该账号不属于运营端，请返回选择物业端登录')
    return
  }
  user.login({
    token: data.token,
    username: data.nickname || phone,
    portal: 'admin',
    features: featuresToStr(data.features),
    tenantStatus: data.tenantStatus || 'active',
    role,
    tenantId: data.tenantId,
    userId: data.userId,
    communityName: 'SaaS 运营平台',
  })
  ElMessage.success('登录成功')
  goHome('admin', '/admin/saas/cockpit')
}

async function onSubmitProperty() {
  const username = propertyForm.username.trim()
  if (!username || !propertyForm.password) {
    ElMessage.error('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    if (ACCOUNTS[username]) {
      await loginDemo(username, propertyForm.password, 'property')
      return
    }
    await loginPropertyReal(username)
  } catch {
    /* request 已提示 */
  } finally {
    loading.value = false
  }
}

async function onSubmitMerchant() {
  const username = merchantForm.username.trim()
  if (!username || !merchantForm.password) {
    ElMessage.error('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await loginMerchantReal(username)
  } catch {
    /* request 已提示 */
  } finally {
    loading.value = false
  }
}

async function onSubmitAdmin() {
  const username = adminForm.username.trim()
  if (!username || !adminForm.password) {
    ElMessage.error('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    if (ACCOUNTS[username]) {
      await loginDemo(username, adminForm.password, 'admin')
      return
    }
    await loginAdminReal(username)
  } catch {
    /* request 已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTenants()
})

onUnmounted(() => {
  if (resolveTimer) clearTimeout(resolveTimer)
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@500;700&family=Noto+Sans+SC:wght@400;500;700&display=swap');

.login-page {
  position: relative;
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 88px 20px 40px;
  font-family: 'Noto Sans SC', 'DM Sans', sans-serif;
  background:
    radial-gradient(ellipse 70% 50% at 8% 0%, rgba(45, 212, 191, 0.35), transparent 55%),
    radial-gradient(ellipse 55% 45% at 100% 100%, rgba(15, 118, 110, 0.18), transparent 50%),
    linear-gradient(165deg, #f0fdf9 0%, #e0f7f1 48%, #d5f0e9 100%);
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(15, 118, 110, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 118, 110, 0.035) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, #000 20%, transparent 75%);
  pointer-events: none;
}

.brand {
  position: absolute;
  top: 28px;
  left: 36px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 14px;
  animation: brand-in 0.6s ease both;
}

.brand-mark {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  filter: drop-shadow(0 8px 16px rgba(13, 148, 136, 0.28));
}

.brand-mark svg {
  width: 100%;
  height: 100%;
  display: block;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-text strong {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #0f766e;
  line-height: 1.2;
}

.brand-text em {
  font-style: normal;
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #5eead4;
}

.shell {
  position: relative;
  z-index: 1;
  width: min(920px, 94vw);
  min-height: 460px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
  border-radius: 28px;
  overflow: hidden;
  box-shadow:
    0 40px 90px rgba(15, 118, 110, 0.16),
    0 10px 28px rgba(15, 23, 42, 0.06);
  animation: shell-in 0.55s ease both;
}

.shell.triple {
  width: min(1080px, 96vw);
  grid-template-columns: 1fr 1fr 1fr;
}

.shell.chosen {
  grid-template-columns: 1fr;
  max-width: 440px;
  width: min(440px, 94vw);
  min-height: 540px;
}

.pick {
  position: relative;
  border: none;
  text-align: left;
  cursor: pointer;
  padding: 52px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 460px;
  transition: transform 0.25s ease, background 0.25s ease;
}

.pick::after {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.25s ease;
  pointer-events: none;
}

.pick.property::after {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.06), transparent 60%);
}

.pick.admin::after {
  background: radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.16), transparent 50%);
}

.pick:hover {
  transform: translateY(-2px);
}

.pick:hover::after {
  opacity: 1;
}

.pick:active {
  transform: scale(0.992);
}

.pick.property {
  background: #fff;
  border-right: 1px solid #e8f5f1;
}

.pick.merchant {
  background: linear-gradient(180deg, #f8fffc 0%, #eefaf6 100%);
  border-right: 1px solid #e8f5f1;
  color: #0f766e;
}

.pick.admin {
  background:
    radial-gradient(circle at 78% 18%, rgba(255, 255, 255, 0.2), transparent 42%),
    linear-gradient(155deg, #2dd4bf 0%, #14b8a6 40%, #0f766e 100%);
  color: #fff;
}

.pick-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  margin-bottom: 28px;
  color: #0d9488;
  background: linear-gradient(145deg, #ecfdf5, #d1fae5);
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.12);
}

.pick.admin .pick-icon {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  box-shadow: none;
  backdrop-filter: blur(6px);
}

.pick-icon svg {
  width: 30px;
  height: 30px;
}

.pick h1 {
  margin: 10px 0 0;
  font-size: 30px;
  font-weight: 700;
  color: #0f766e;
  letter-spacing: 0.02em;
}

.pick.admin h1 {
  color: #fff;
}

.pick .badge {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  letter-spacing: 0.22em;
  font-weight: 700;
  color: #14b8a6;
}

.pick.admin .badge {
  color: rgba(255, 255, 255, 0.72);
}

.pick .sub {
  margin: 14px 0 0;
  font-size: 14px;
  color: #64748b;
  line-height: 1.65;
}

.pick.admin .sub {
  color: rgba(255, 255, 255, 0.78);
}

.pick .cta {
  margin-top: auto;
  padding-top: 40px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #0d9488;
}

.pick .cta i {
  font-style: normal;
  transition: transform 0.25s ease;
}

.pick:hover .cta i {
  transform: translateX(4px);
}

.pick.admin .cta {
  color: #fff;
}

.pane {
  padding: 40px 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.pane.property,
.pane.merchant {
  background: #fff;
}

.pane.admin {
  background:
    radial-gradient(circle at 78% 18%, rgba(255, 255, 255, 0.2), transparent 42%),
    linear-gradient(155deg, #2dd4bf 0%, #14b8a6 40%, #0f766e 100%);
  color: #fff;
}

.back {
  align-self: flex-start;
  border: none;
  background: rgba(13, 148, 136, 0.08);
  color: #0d9488;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 8px 14px;
  border-radius: 999px;
  margin-bottom: 24px;
  transition: background 0.2s ease;
}

.back:hover {
  background: rgba(13, 148, 136, 0.14);
}

.back.light {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
}

.back.light:hover {
  background: rgba(255, 255, 255, 0.24);
}

.pane-head h1 {
  margin: 8px 0 0;
  font-size: 26px;
  font-weight: 700;
  color: #0f766e;
}

.pane-head.light h1 {
  color: #fff;
}

.pane-head .badge {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  letter-spacing: 0.22em;
  font-weight: 700;
  color: #14b8a6;
}

.pane-head.light .badge {
  color: rgba(255, 255, 255, 0.72);
}

.pane-head .sub {
  margin: 10px 0 0;
  font-size: 13px;
  color: #94a3b8;
}

.pane-head.light .sub {
  color: rgba(255, 255, 255, 0.78);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 22px;
  margin-top: 28px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field span {
  font-size: 13px;
  color: #94a3b8;
}

.login-form.light .field span {
  color: rgba(255, 255, 255, 0.72);
}

.field input,
.field select {
  border: none;
  border-bottom: 1px solid #cbd5e1;
  border-radius: 0;
  padding: 8px 2px 10px;
  font-size: 16px;
  color: #0f172a;
  background: transparent;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.login-form.light .field input,
.login-form.light .field select {
  color: #fff;
  border-bottom-color: rgba(255, 255, 255, 0.35);
}

.login-form.light .field select option {
  color: #0f172a;
}

.field input::placeholder {
  color: #cbd5e1;
}

.login-form.light .field input::placeholder {
  color: rgba(255, 255, 255, 0.45);
}

.field input:focus,
.field select:focus {
  border-bottom-color: #14b8a6;
  box-shadow: 0 1px 0 #14b8a6;
}

.login-form.light .field input:focus,
.login-form.light .field select:focus {
  border-bottom-color: #fff;
  box-shadow: 0 1px 0 #fff;
}

.community-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.community-box.ready {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.community-box.miss {
  border-color: #fecaca;
  background: #fef2f2;
}

.community-label {
  font-size: 13px;
  color: #94a3b8;
}

.community-select-login {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
  cursor: pointer;
}
.community-value {
  font-size: 15px;
  font-weight: 600;
  color: #0f766e;
  line-height: 1.4;
  word-break: break-word;
}

.community-value.muted {
  font-weight: 500;
  color: #94a3b8;
}

.login-btn {
  margin-top: 10px;
  height: 48px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff;
  font-family: 'DM Sans', sans-serif;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.14em;
  cursor: pointer;
  box-shadow: 0 12px 28px rgba(13, 148, 136, 0.28);
  transition: transform 0.2s ease, filter 0.2s ease;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.04);
}

.login-btn.ghost {
  background: #fff;
  color: #0f766e;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
}

.login-btn.merchant-btn {
  background: linear-gradient(135deg, #0d9488, #0f766e);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: wait;
}

.hint {
  margin: 24px 0 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.65;
}

.hint.light {
  color: rgba(255, 255, 255, 0.68);
}

@keyframes brand-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes shell-in {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 860px) {
  .login-page {
    padding: 76px 14px 28px;
  }

  .brand {
    top: 18px;
    left: 18px;
    gap: 10px;
  }

  .brand-mark {
    width: 36px;
    height: 36px;
  }

  .brand-text strong {
    font-size: 18px;
  }

  .shell:not(.chosen) {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .shell.triple:not(.chosen) {
    grid-template-columns: 1fr;
  }

  .pick {
    padding: 36px 28px;
    min-height: 240px;
  }

  .pick.property,
  .pick.merchant {
    border-right: none;
    border-bottom: 1px solid #e8f5f1;
  }

  .pane {
    padding: 28px 24px;
  }
}
</style>
