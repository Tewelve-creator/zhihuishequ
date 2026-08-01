<template>
  <view class="login-page">
    <!-- 顶部插画区 -->
    <view class="hero">
      <view class="hero-blobs">
        <view class="blob b1" />
        <view class="blob b2" />
      </view>
      <view class="illus-wrap">
        <!-- 简化社区场景：楼栋 + 手机 + AI 气泡 -->
        <view class="scene">
          <view class="building b-left" />
          <view class="building b-mid" />
          <view class="building b-right" />
          <view class="tree t1" />
          <view class="tree t2" />
          <view class="phone">
            <view class="phone-screen">
              <view class="dot" />
              <view class="bar" />
              <view class="bar short" />
            </view>
          </view>
          <view class="float-card">
            <view class="avatar" />
            <view class="lines">
              <view class="line" />
              <view class="line thin" />
            </view>
          </view>
          <view class="ai-bubble">AI</view>
          <view class="person" />
        </view>
      </view>
    </view>

    <!-- 玻璃拟态登录卡 -->
    <view class="glass-card">
      <view class="titles">
        <text class="title">AI智慧社区居民端</text>
        <text class="subtitle">登录账号为 11 位手机号 · 报修缴费 · 智能提醒</text>
      </view>

      <view class="field">
        <input
          v-model="phone"
          type="number"
          maxlength="11"
          placeholder="请输入手机号（登录账号）"
          placeholder-class="ph"
        />
      </view>

      <view class="field">
        <input
          v-model="password"
          password
          placeholder="请输入密码"
          placeholder-class="ph"
        />
      </view>

      <view class="field tenant" @click="showTenant = true">
        <text :class="tenantName ? 'tenant-val' : 'ph'">{{ tenantName || '请选择所属社区（推荐）' }}</text>
        <text class="chev">›</text>
      </view>

      <view class="agree" @click="agreed = !agreed">
        <view class="check" :class="{ on: agreed }" />
        <text class="agree-text">
          同意
          <text class="link">《社区用户服务协议》</text>
          <text class="link">《隐私政策》</text>
        </text>
      </view>

      <button class="login-btn" :loading="loading" @click="onLogin">登录</button>

      <view class="footer-links">
        <text class="muted" @click="fillDemo">填入演示账号</text>
        <text class="link" @click="goRegister">注册居民账号</text>
      </view>

      <view class="staff-entry" @click="fillStaffHint">物业工作人员登录入口</view>
    </view>

    <view v-if="showTenant" class="mask" @click="showTenant = false">
      <view class="sheet" @click.stop>
        <view class="sheet-title">选择租赁社区</view>
        <view v-for="t in tenants" :key="t.id" class="sheet-item" @click="pickTenant(t)">
          <text class="name">{{ t.name }}</text>
          <text class="addr">{{ t.address }}</text>
        </view>
        <view class="sheet-item" @click="pickTenant(null)">
          <text class="name">暂不选择</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { login, listPublicTenants } from '../../api/auth'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'
import { DEMO_TENANTS } from '../../utils/constants'

const store = useUserStore()
const houseStore = useHouseStore()
const phone = ref('')
const password = ref('')
const tenantId = ref(null)
const tenantName = ref('')
const tenants = ref([...DEMO_TENANTS])
const showTenant = ref(false)
const loading = ref(false)
const agreed = ref(true)

async function loadTenants() {
  try {
    const list = await listPublicTenants()
    if (Array.isArray(list) && list.length) {
      tenants.value = list
    }
  } catch {
    /* 保留演示兜底 */
  }
}

function pickTenant(t) {
  if (!t) {
    tenantId.value = null
    tenantName.value = ''
  } else {
    tenantId.value = t.id
    tenantName.value = t.name
  }
  showTenant.value = false
}

function fillDemo() {
  phone.value = '13800000021'
  password.value = '123456'
  const first = tenants.value[0]
  tenantId.value = first?.id ?? 1
  tenantName.value = first?.name || '阳光花园社区'
  agreed.value = true
}

function fillStaffHint() {
  phone.value = '13800000001'
  password.value = '123456'
  const first = tenants.value[0]
  tenantId.value = first?.id ?? 1
  tenantName.value = first?.name || '阳光花园社区'
  agreed.value = true
  uni.showToast({ title: '已填入物业演示账号', icon: 'none' })
}

function goRegister() {
  uni.navigateTo({ url: '/pages/login/register' })
}

/** 登录返回的功能码归一化 */
function normalizeFeatures(raw) {
  if (Array.isArray(raw) && raw.length) return raw.join(',')
  if (typeof raw === 'string' && raw.trim()) return raw.trim()
  // 库未写 features 时按免费版保底，避免误开高级能力
  return 'basic_repair,basic_notice'
}

async function onLogin() {
  const mobile = String(phone.value || '').trim()
  if (!mobile || !password.value) {
    uni.showToast({ title: '请填写手机号和密码', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(mobile)) {
    uni.showToast({ title: '请输入正确的 11 位手机号', icon: 'none' })
    return
  }
  if (!agreed.value) {
    uni.showToast({ title: '请先同意服务协议', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const body = {
      phone: mobile,
      password: password.value,
      clientType: 'APP'
    }
    if (tenantId.value != null) body.tenantId = tenantId.value
    const data = await login(body)
    // 兼容 features / effectiveFeatureCodes；空则免费版保底
    const feat = normalizeFeatures(data?.effectiveFeatureCodes || data?.features)
    store.setSession({
      ...data,
      features: feat,
      tenantStatus: data?.tenantStatus || 'active'
    })
    if (phone.value) {
      uni.setStorageSync('phone', phone.value)
    }
    if (tenantName.value) {
      uni.setStorageSync('communityName', tenantName.value)
    }
    // 居民：写入房屋上下文；未绑定则引导绑定
    if (!store.isStaff) {
      houseStore.applyLogin(data || {})
    }
    const tip = store.isGridWorker
      ? '网格员登录成功'
      : store.isStaff
        ? '物业管理员登录成功'
        : '登录成功'
    uni.showToast({ title: tip, icon: 'success' })
    setTimeout(() => {
      store.enterHome()
      if (!store.isStaff && (data?.needBindHouse || !houseStore.hasHouse)) {
        setTimeout(() => {
          uni.showModal({
            title: '绑定房屋',
            content: '首次使用请先绑定您的房屋，以便报修、缴费等服务精准到户。',
            confirmText: '去绑定',
            showCancel: true,
            success: (r) => {
              if (r.confirm) {
                uni.navigateTo({ url: '/pages/house/bind' })
              }
            }
          })
        }, 500)
      }
    }, 400)
  } catch (e) {
    // toast 已在 request 中处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTenants()
})
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(165deg, #d8f5ec 0%, #e8f8f3 38%, #f3fbf8 70%, #ffffff 100%);
  box-sizing: border-box;
  width: 100%;
}

.hero {
  position: relative;
  height: 42vh;
  min-height: 380rpx;
  max-height: 520rpx;
  overflow: hidden;
  flex-shrink: 0;
}

.hero-blobs {
  position: absolute;
  inset: 0;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}
.b1 {
  width: 280rpx;
  height: 280rpx;
  background: rgba(52, 199, 89, 0.18);
  top: -40rpx;
  right: -40rpx;
}
.b2 {
  width: 200rpx;
  height: 200rpx;
  background: rgba(20, 184, 166, 0.16);
  bottom: 40rpx;
  left: -30rpx;
}

.illus-wrap {
  height: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 24rpx;
}

.scene {
  position: relative;
  width: 560rpx;
  height: 360rpx;
}

.building {
  position: absolute;
  bottom: 70rpx;
  background: linear-gradient(180deg, #ffffff 0%, #e8f5f1 100%);
  border: 2rpx solid rgba(255, 255, 255, 0.9);
  border-radius: 12rpx 12rpx 4rpx 4rpx;
  box-shadow: 0 12rpx 28rpx rgba(15, 118, 110, 0.1);
}
.b-left {
  left: 40rpx;
  width: 90rpx;
  height: 160rpx;
}
.b-mid {
  left: 150rpx;
  width: 110rpx;
  height: 210rpx;
}
.b-right {
  left: 280rpx;
  width: 86rpx;
  height: 140rpx;
}
.building::after {
  content: '';
  position: absolute;
  inset: 16rpx 14rpx auto;
  height: 70%;
  background:
    linear-gradient(#99f6e4 0 0) 0 0 / 28% 22%,
    linear-gradient(#99f6e4 0 0) 50% 0 / 28% 22%,
    linear-gradient(#99f6e4 0 0) 100% 0 / 28% 22%,
    linear-gradient(#99f6e4 0 0) 0 40% / 28% 22%,
    linear-gradient(#99f6e4 0 0) 50% 40% / 28% 22%,
    linear-gradient(#99f6e4 0 0) 100% 40% / 28% 22%;
  background-repeat: no-repeat;
  opacity: 0.85;
}

.tree {
  position: absolute;
  bottom: 70rpx;
  width: 36rpx;
  height: 56rpx;
  border-radius: 40rpx 40rpx 8rpx 8rpx;
  background: #34c759;
}
.t1 { left: 380rpx; }
.t2 { left: 420rpx; height: 44rpx; background: #30d158; }

.phone {
  position: absolute;
  left: 120rpx;
  bottom: 20rpx;
  width: 280rpx;
  height: 90rpx;
  border-radius: 20rpx;
  background: #1c1c1e;
  padding: 10rpx;
  box-sizing: border-box;
  transform: perspective(400rpx) rotateX(52deg);
  box-shadow: 0 20rpx 40rpx rgba(0, 0, 0, 0.18);
}
.phone-screen {
  height: 100%;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #e8fff2, #ffffff);
  padding: 16rpx 20rpx;
  box-sizing: border-box;
}
.phone-screen .dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #34c759;
  margin-bottom: 10rpx;
}
.phone-screen .bar {
  height: 8rpx;
  width: 70%;
  border-radius: 8rpx;
  background: #a7f3d0;
  margin-bottom: 8rpx;
}
.phone-screen .bar.short { width: 45%; }

.float-card {
  position: absolute;
  right: 36rpx;
  top: 50rpx;
  width: 150rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12rpx 30rpx rgba(15, 118, 110, 0.14);
  display: flex;
  align-items: center;
  padding: 0 14rpx;
  gap: 12rpx;
}
.avatar {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #34c759;
}
.lines { flex: 1; }
.line {
  height: 8rpx;
  border-radius: 8rpx;
  background: #d1fae5;
  margin-bottom: 8rpx;
}
.line.thin { width: 60%; background: #ecfdf5; }

.ai-bubble {
  position: absolute;
  left: 56rpx;
  top: 70rpx;
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #34c759, #30d158);
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(52, 199, 89, 0.35);
}

.person {
  position: absolute;
  right: 160rpx;
  bottom: 88rpx;
  width: 36rpx;
  height: 70rpx;
  border-radius: 18rpx 18rpx 10rpx 10rpx;
  background: linear-gradient(180deg, #fbbf24 0 28%, #0f766e 28% 68%, #134e4a 68%);
}

.glass-card {
  margin: -48rpx 0 0;
  padding: 48rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  border-radius: 36rpx 36rpx 0 0;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(24px);
  box-shadow: 0 -8rpx 40rpx rgba(15, 118, 110, 0.08);
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  position: relative;
  z-index: 2;
}

.titles {
  margin-bottom: 40rpx;
}
.title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #111827;
  letter-spacing: 1rpx;
}
.subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9ca3af;
}

.field {
  height: 92rpx;
  border-radius: 20rpx;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  padding: 0 28rpx;
  margin-bottom: 24rpx;
}
.field input {
  flex: 1;
  font-size: 28rpx;
  color: #111827;
}
.ph {
  color: #9ca3af;
  font-size: 28rpx;
}
.tenant {
  justify-content: space-between;
}
.tenant-val {
  color: #111827;
  font-size: 28rpx;
}
.chev {
  color: #d1d5db;
  font-size: 40rpx;
  line-height: 1;
}

.agree {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin: 8rpx 0 32rpx;
}
.check {
  width: 32rpx;
  height: 32rpx;
  border-radius: 8rpx;
  border: 2rpx solid #d1d5db;
  margin-top: 4rpx;
  box-sizing: border-box;
  flex-shrink: 0;
}
.check.on {
  background: #34c759;
  border-color: #34c759;
  position: relative;
}
.check.on::after {
  content: '';
  position: absolute;
  left: 9rpx;
  top: 4rpx;
  width: 8rpx;
  height: 14rpx;
  border: solid #fff;
  border-width: 0 3rpx 3rpx 0;
  transform: rotate(45deg);
}
.agree-text {
  font-size: 24rpx;
  color: #6b7280;
  line-height: 1.5;
}
.link {
  color: #34c759;
}

.login-btn {
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 20rpx;
  border: none;
  background: #34c759;
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  letter-spacing: 4rpx;
}
.login-btn::after {
  border: none;
}

.footer-links {
  margin-top: 28rpx;
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
}
.muted {
  color: #9ca3af;
}

.staff-entry {
  margin-top: 36rpx;
  text-align: center;
  font-size: 26rpx;
  color: #6b7280;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 99;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 36rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  max-height: 70vh;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
  color: #111827;
}
.sheet-item {
  padding: 28rpx 8rpx;
  border-bottom: 1rpx solid #f3f4f6;
}
.sheet-item .name {
  display: block;
  font-size: 30rpx;
  color: #111827;
}
.sheet-item .addr {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #9ca3af;
}
</style>
