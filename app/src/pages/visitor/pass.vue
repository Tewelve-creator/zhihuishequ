<template>
  <view class="page page-safe pass-page">
    <view class="hero">
      <view class="community">{{ info.communityName || '智慧社区' }}</view>
      <view class="sub">访客通行凭证</view>
    </view>

    <view class="card main-card" v-if="loaded">
      <view class="status-row">
        <text class="tone-pill" :class="info.statusTone || 'gray'">{{ info.statusLabel || '—' }}</text>
      </view>

      <view class="info-grid">
        <view class="row"><text class="k">拜访房屋</text><text class="v">{{ info.hostHouse || '—' }}</text></view>
        <view class="row"><text class="k">业主</text><text class="v">{{ info.hostNickname || '—' }}</text></view>
        <view class="row"><text class="k">访客</text><text class="v">{{ info.visitorName || '—' }}</text></view>
        <view class="row"><text class="k">事由</text><text class="v">{{ info.visitReason || '—' }}</text></view>
        <view class="row"><text class="k">通行时间</text><text class="v">{{ fmtRange }}</text></view>
      </view>

      <!-- 预留：手机号校验 -->
      <view v-if="info.phoneVerifyRequired || (info.phoneVerifyEnabled && !phoneVerified)" class="phone-box">
        <input
          v-model="phoneInput"
          class="phone-input"
          type="number"
          maxlength="11"
          placeholder="请输入预约手机号以显示二维码"
        />
        <button class="btn-primary mini" size="mini" :loading="loading" @click="load(true)">校验并显示</button>
      </view>

      <view v-if="info.showQr && qrPayload" class="qr-wrap">
        <image
          class="qr"
          :src="qrSrc"
          mode="aspectFit"
          :show-menu-by-longpress="false"
        />
        <view class="qr-tip">出示给门岗扫码 · {{ refreshLeft }}s 后自动刷新</view>
      </view>
      <view v-else class="qr-hide">
        <view class="fail-title">二维码未显示</view>
        <view class="fail-reason">{{ info.failReason || '当前不可通行' }}</view>
      </view>

      <view class="tip">{{ info.tip || '撤销 / 过期 / 单次通行后二维码立即失效' }}</view>
      <view class="tip muted">{{ info.ownerHint || '请勿截图转发二维码，截图将随刷新失效' }}</view>

      <button v-if="canDemoVerify" class="btn-primary mt" :loading="verifying" @click="demoVerify">
        模拟门岗扫码通行
      </button>
    </view>
    <view v-else class="empty">加载中…</view>
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue'
import { onLoad, onHide, onShow } from '@dcloudio/uni-app'
import { getVisitPassInfo, refreshVisitPassQr, verifyVisitPass } from '../../api/biz'

const token = ref('')
const loaded = ref(false)
const loading = ref(false)
const verifying = ref(false)
const info = ref({})
const qrPayload = ref('')
const phoneInput = ref('')
const phoneVerified = ref(false)
const refreshLeft = ref(0)
let refreshTimer = null
let countdownTimer = null

const fmtRange = computed(() => {
  const a = fmt(info.value.visitTime)
  const b = fmt(info.value.expireTime)
  if (!a && !b) return '—'
  return `${a || '—'} ~ ${b || '—'}`
})

const qrSrc = computed(() => {
  if (!qrPayload.value) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(qrPayload.value)}`
})

const canDemoVerify = computed(() => !!info.value.showQr && !!qrPayload.value)

function fmt(v) {
  return String(v || '').replace('T', ' ').substring(0, 16)
}

function clearTimers() {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function scheduleRefresh(sec) {
  clearTimers()
  const n = Math.max(Number(sec) || 60, 15)
  refreshLeft.value = n
  countdownTimer = setInterval(() => {
    refreshLeft.value = Math.max(0, refreshLeft.value - 1)
  }, 1000)
  refreshTimer = setTimeout(() => refreshQr(), n * 1000)
}

async function load(fromPhone) {
  if (!token.value) {
    info.value = { showQr: false, failReason: '缺少通行 token', statusLabel: '无效', statusTone: 'gray' }
    loaded.value = true
    return
  }
  loading.value = true
  try {
    const phone = phoneInput.value.trim() || undefined
    const data = (await getVisitPassInfo(token.value, phone)) || {}
    info.value = data
    if (data.showQr && data.qrPayload) {
      qrPayload.value = data.qrPayload
      phoneVerified.value = true
      scheduleRefresh(data.qrRefreshSeconds)
    } else {
      qrPayload.value = ''
      clearTimers()
    }
    if (fromPhone && data.phoneVerifyRequired) {
      uni.showToast({ title: data.failReason || '校验未通过', icon: 'none' })
    }
  } catch (e) {
    info.value = { showQr: false, failReason: '加载失败', statusLabel: '无效', statusTone: 'gray' }
    qrPayload.value = ''
    clearTimers()
  } finally {
    loaded.value = true
    loading.value = false
  }
}

async function refreshQr() {
  if (!token.value) return
  try {
    const phone = phoneInput.value.trim() || undefined
    const data = (await refreshVisitPassQr(token.value, phone)) || {}
    if (data.showQr && data.qrPayload) {
      info.value = { ...info.value, ...data, showQr: true, valid: true }
      qrPayload.value = data.qrPayload
      scheduleRefresh(data.qrRefreshSeconds)
    } else {
      info.value = { ...info.value, ...data, showQr: false, valid: false }
      qrPayload.value = ''
      clearTimers()
    }
  } catch (e) {
    // 刷新失败时重新拉 info
    load(false)
  }
}

async function demoVerify() {
  verifying.value = true
  try {
    const res = await verifyVisitPass({ qrPayload: qrPayload.value })
    if (res?.ok) {
      uni.showToast({ title: res.msg || '通行成功', icon: 'success' })
    } else {
      uni.showToast({ title: res?.msg || '通行失败', icon: 'none' })
    }
    await load(false)
  } catch (e) {
  } finally {
    verifying.value = false
  }
}

onLoad((q) => {
  token.value = (q && q.token) || ''
  load(false)
})
onShow(() => {
  if (loaded.value && info.value.showQr) scheduleRefresh(info.value.qrRefreshSeconds || 60)
})
onHide(clearTimers)
onUnmounted(clearTimers)
</script>

<style lang="scss" scoped>
.pass-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #e8f7f0 0%, #f5f7fa 42%, #f5f7fa 100%);
}
.hero {
  padding: 40rpx 32rpx 12rpx;
}
.community {
  font-size: 40rpx;
  font-weight: 700;
  color: #0f172a;
}
.sub {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #64748b;
}
.main-card {
  margin-top: 8rpx;
}
.status-row {
  margin-bottom: 20rpx;
}
.tone-pill {
  display: inline-flex;
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  background: #f1f5f9;
  color: #64748b;
}
.tone-pill.green { color: #15803d; background: #dcfce7; }
.tone-pill.teal { color: #0f766e; background: #ccfbf1; }
.tone-pill.blue { color: #1d4ed8; background: #dbeafe; }
.tone-pill.orange { color: #c2410c; background: #ffedd5; }
.tone-pill.gray { color: #64748b; background: #f1f5f9; }
.info-grid .row {
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
  padding: 14rpx 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 28rpx;
}
.k { color: #94a3b8; flex-shrink: 0; }
.v { color: #0f172a; text-align: right; }
.phone-box {
  margin-top: 20rpx;
  display: flex;
  gap: 12rpx;
  align-items: center;
}
.phone-input {
  flex: 1;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 28rpx;
}
.qr-wrap {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 降低长按保存引导（H5 仍可能系统截图，靠短时载荷失效） */
  -webkit-touch-callout: none;
  user-select: none;
}
.qr {
  width: 360rpx;
  height: 360rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1px solid #e2e8f0;
  pointer-events: none;
}
.qr-tip {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #64748b;
}
.qr-hide {
  margin-top: 32rpx;
  padding: 40rpx 24rpx;
  text-align: center;
  background: #f8fafc;
  border-radius: 16rpx;
  border: 1px dashed #cbd5e1;
}
.fail-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #334155;
}
.fail-reason {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #dc2626;
}
.tip {
  margin-top: 28rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.6;
}
.tip.muted { margin-top: 8rpx; color: #b45309; }
.mt { margin-top: 28rpx; }
</style>
