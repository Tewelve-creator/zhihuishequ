<template>
  <div class="login-page">
    <div class="shell">
      <section class="form-pane">
        <header class="hero-text">
          <h1>欢迎登录智慧社区管理后台</h1>
          <p class="en">SYSTEM MANAGEMENT BACKGROUND</p>
        </header>

        <h2 class="form-title">登录</h2>

        <form class="login-form" @submit.prevent="onSubmit">
          <label class="field">
            <span>请输入账号</span>
            <input v-model="form.username" type="text" autocomplete="username" placeholder="手机号或账号" />
          </label>
          <label class="field">
            <span>请输入密码</span>
            <input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <label v-if="needTenant" class="field">
            <span>所属社区</span>
            <select v-model="form.tenantId">
              <option v-for="t in tenants" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </label>
          <button class="login-btn" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : 'LOGIN' }}
          </button>
        </form>

        <p class="hint">
          物业真实账号：13800000001 / 123456（选阳光花园）可看 App 报修；演示：admin1/admin1 · 运营 admin2/admin2
        </p>
      </section>

      <aside class="art-pane" aria-hidden="true">
        <svg class="wave" viewBox="0 0 80 640" preserveAspectRatio="none">
          <path
            d="M80,0 C40,80 10,160 35,240 C60,320 5,400 30,480 C55,560 20,600 0,640 L80,640 Z"
            fill="#ffffff"
          />
        </svg>
        <div class="art-inner">
          <svg class="illus" viewBox="0 0 420 380" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- plant -->
            <ellipse cx="62" cy="300" rx="28" ry="10" fill="#0f766e" opacity=".25" />
            <rect x="54" y="250" width="16" height="50" rx="4" fill="#5eead4" />
            <path d="M62 250 C40 220 28 200 38 180 C52 210 58 230 62 250Z" fill="#99f6e4" />
            <path d="M62 248 C84 218 98 198 90 178 C78 208 68 230 62 248Z" fill="#5eead4" />

            <!-- phone base -->
            <rect x="90" y="250" width="260" height="90" rx="18" fill="#0f172a" opacity=".2" />
            <rect x="100" y="220" width="240" height="100" rx="16" fill="#ecfdf5" stroke="#fff" stroke-width="3" />
            <rect x="118" y="236" width="56" height="10" rx="5" fill="#99f6e4" />
            <rect x="118" y="256" width="90" height="8" rx="4" fill="#a7f3d0" />
            <rect x="118" y="274" width="70" height="8" rx="4" fill="#a7f3d0" />

            <!-- floating dashboard card -->
            <g transform="translate(200 70)">
              <rect width="170" height="120" rx="14" fill="#fff" filter="url(#shadow)" />
              <path d="M18 78 L48 52 L78 62 L112 34 L150 48" stroke="#14b8a6" stroke-width="3" fill="none" stroke-linecap="round" />
              <circle cx="130" cy="70" r="28" fill="#ccfbf1" />
              <path d="M130 70 L130 48 A22 22 0 0 1 148 78Z" fill="#14b8a6" />
              <circle cx="36" cy="28" r="10" fill="#fde68a" />
            </g>

            <!-- profile chip -->
            <g transform="translate(150 150)">
              <rect width="88" height="40" rx="12" fill="#fff" />
              <circle cx="22" cy="20" r="10" fill="#5eead4" />
              <rect x="38" y="12" width="36" height="6" rx="3" fill="#99f6e4" />
              <rect x="38" y="24" width="26" height="5" rx="2.5" fill="#d1fae5" />
            </g>

            <!-- person -->
            <g transform="translate(250 165)">
              <circle cx="28" cy="18" r="14" fill="#fcd34d" />
              <path d="M10 40 C10 28 46 28 46 40 L42 78 L14 78 Z" fill="#0d9488" />
              <rect x="16" y="78" width="10" height="28" rx="4" fill="#134e4a" />
              <rect x="30" y="78" width="10" height="28" rx="4" fill="#134e4a" />
              <rect x="44" y="48" width="22" height="8" rx="4" fill="#fbbf24" />
            </g>

            <!-- wifi badge -->
            <g transform="translate(320 36)">
              <circle cx="24" cy="24" r="24" fill="#fbbf24" />
              <path d="M12 28 C18 20 30 20 36 28" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" />
              <path d="M16 32 C20 27 28 27 32 32" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" />
              <circle cx="24" cy="36" r="2.5" fill="#fff" />
            </g>

            <defs>
              <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
                <feDropShadow dx="0" dy="10" stdDeviation="12" flood-color="#0f766e" flood-opacity=".18" />
              </filter>
            </defs>
          </svg>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore, type PortalType } from '@/stores/user'
import { DEMO_FEATURES } from '@/config/features'
import { loginApi } from '@/api/auth'

const ACCOUNTS: Record<string, { password: string; portal: PortalType; home: string }> = {
  admin1: { password: 'admin1', portal: 'property', home: '/property/dashboard' },
  admin2: { password: 'admin2', portal: 'admin', home: '/admin/cockpit' },
  degraded1: { password: '123456', portal: 'property', home: '/property/dashboard' },
}

const tenants = [
  { id: 1, name: '阳光花园社区' },
  { id: 2, name: '星河湾社区' },
]

const router = useRouter()
const route = useRoute()
const user = useUserStore()
const loading = ref(false)
const form = reactive({
  username: '13800000001',
  password: '123456',
  tenantId: 1,
})

const needTenant = computed(() => {
  const u = form.username.trim()
  return !ACCOUNTS[u] && /^1\d{10}$/.test(u)
})

function featuresToStr(f: unknown) {
  if (Array.isArray(f)) return f.join(',')
  return typeof f === 'string' ? f : ''
}

function goHome(portal: PortalType, home: string) {
  const redirect = route.query.redirect as string | undefined
  const okRedirect =
    redirect &&
    ((portal === 'admin' && redirect.startsWith('/admin')) ||
      (portal === 'property' && redirect.startsWith('/property')))
  router.replace(okRedirect ? redirect : home)
}

async function loginDemo(username: string) {
  const account = ACCOUNTS[username]
  if (!account || form.password !== account.password) {
    ElMessage.error('账号或密码错误')
    return
  }
  const demo = DEMO_FEATURES[username]
  user.login({
    token: `token-${Date.now()}`,
    username,
    portal: account.portal,
    features: demo?.features,
    tenantStatus: demo?.tenantStatus,
    role: demo?.role,
  })
  ElMessage.success(
    demo?.tenantStatus === 'degraded' ? '登录成功（已降级：仅公告/报修）' : '登录成功（演示）',
  )
  goHome(account.portal, account.home)
}

async function loginReal(phone: string) {
  const data = await loginApi({
    phone,
    password: form.password,
    tenantId: form.tenantId,
    clientType: 'ADMIN',
  })
  const role = data.role || ''
  const portal: PortalType =
    role === 'platform_admin' ? 'admin' : 'property'
  const home = portal === 'admin' ? '/admin/cockpit' : '/property/dashboard'
  const tenant = tenants.find((t) => t.id === Number(data.tenantId))
  user.login({
    token: data.token,
    username: data.nickname || phone,
    portal,
    features: featuresToStr(data.features),
    tenantStatus: data.tenantStatus || 'active',
    role,
    tenantId: data.tenantId,
    userId: data.userId,
    communityName: tenant?.name || '当前社区',
  })
  ElMessage.success('登录成功')
  goHome(portal, home)
}

async function onSubmit() {
  const username = form.username.trim()
  if (!username || !form.password) {
    ElMessage.error('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    if (ACCOUNTS[username]) {
      await loginDemo(username)
      return
    }
    await loginReal(username)
  } catch {
    /* request 已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@500;700&family=Noto+Sans+SC:wght@400;500;700&display=swap');

.login-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 32px 16px;
  font-family: 'Noto Sans SC', 'DM Sans', sans-serif;
  background:
    radial-gradient(circle at 12% 18%, rgba(153, 246, 228, 0.55), transparent 42%),
    radial-gradient(circle at 88% 78%, rgba(45, 212, 191, 0.28), transparent 40%),
    linear-gradient(145deg, #e8faf6 0%, #d5f5ee 45%, #c7efe6 100%);
}

.shell {
  width: min(980px, 96vw);
  min-height: 560px;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: #fff;
  border-radius: 28px;
  overflow: hidden;
  box-shadow:
    0 30px 80px rgba(15, 118, 110, 0.14),
    0 8px 24px rgba(15, 23, 42, 0.06);
}

.form-pane {
  padding: 56px 56px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero-text h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #0f766e;
  line-height: 1.35;
}

.hero-text .en {
  margin: 10px 0 0;
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: #94a3b8;
  font-weight: 500;
}

.form-title {
  margin: 42px 0 28px;
  font-size: 22px;
  font-weight: 700;
  color: #334155;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 360px;
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

.field input::placeholder {
  color: #cbd5e1;
}

.field input:focus,
.field select:focus {
  border-bottom-color: #14b8a6;
  box-shadow: 0 1px 0 #14b8a6;
}

.login-btn {
  margin-top: 8px;
  height: 48px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #14b8a6, #0d9488);
  color: #fff;
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
  box-shadow: 0 12px 28px rgba(13, 148, 136, 0.28);
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.05);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: wait;
}

.hint {
  margin: 28px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.art-pane {
  position: relative;
  background:
    radial-gradient(circle at 70% 30%, rgba(255, 255, 255, 0.22), transparent 45%),
    linear-gradient(160deg, #2dd4bf 0%, #14b8a6 42%, #0f766e 100%);
  display: grid;
  place-items: center;
}

.wave {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 56px;
  z-index: 2;
}

.art-inner {
  position: relative;
  z-index: 1;
  padding: 24px 28px 24px 40px;
  animation: float-in 0.8s ease both;
}

.illus {
  width: min(360px, 88%);
  height: auto;
  filter: drop-shadow(0 18px 40px rgba(15, 23, 42, 0.12));
}

@keyframes float-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 860px) {
  .shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .art-pane {
    display: none;
  }

  .form-pane {
    padding: 40px 28px 32px;
  }

  .hero-text h1 {
    font-size: 22px;
  }
}
</style>
