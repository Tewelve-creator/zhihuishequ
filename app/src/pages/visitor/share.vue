<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">转发通行链接</view>
      <view class="tip">
        可将下方链接或链接二维码发给访客。访客扫码/打开后进入通行页，门岗用的通行码由服务端实时签发并定时刷新。
      </view>

      <view class="info" v-if="meta.visitorName">
        <view class="row"><text class="k">访客</text><text class="v">{{ meta.visitorName }}</text></view>
        <view class="row" v-if="meta.visitorCount">
          <text class="k">人数</text><text class="v">{{ meta.visitorCount }} 人</text>
        </view>
        <view class="row"><text class="k">事由</text><text class="v">{{ meta.visitReason || '—' }}</text></view>
        <view class="row"><text class="k">状态</text><text class="v">{{ meta.statusLabel || '—' }}</text></view>
      </view>

      <!-- 链接二维码：扫码即可打开通行页（不是门岗短时通行码） -->
      <view v-if="passUrl" class="qr-wrap">
        <image class="qr" :src="linkQrSrc" mode="aspectFit" />
        <view class="qr-tip">扫码打开通行页</view>
      </view>

      <view class="link-box" @longpress="copyLink">
        <text class="link-text">{{ passUrl || '生成中…' }}</text>
      </view>

      <button class="btn-primary" :disabled="!passUrl" @click="copyLink">复制链接给访客</button>
      <button class="btn-ghost mt" @click="goList">返回预约列表</button>
      <view class="warn">撤销预约 / 过期 / 单次通行后，访客通行页将不再显示门岗二维码。</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listVisit } from '../../api/biz'

const token = ref('')
const passUrl = ref('')
const meta = ref({})

const linkQrSrc = computed(() => {
  if (!passUrl.value) return ''
  return `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(passUrl.value)}`
})

function buildUrl(t) {
  // #ifdef H5
  const base = `${window.location.origin}${window.location.pathname || '/'}#/pages/visitor/pass?token=`
  return base + encodeURIComponent(t)
  // #endif
  // #ifndef H5
  return `http://localhost:5180/#/pages/visitor/pass?token=${encodeURIComponent(t)}`
  // #endif
}

async function loadMeta() {
  const rows = (await listVisit().catch(() => [])) || []
  const hit = rows.find((r) => r.passToken === token.value)
  if (hit) {
    meta.value = hit
    if (hit.passUrl) passUrl.value = hit.passUrl
  }
  if (!passUrl.value && token.value) {
    passUrl.value = buildUrl(token.value)
  }
}

function copyLink() {
  if (!passUrl.value) return
  uni.setClipboardData({
    data: passUrl.value,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' })
  })
}

function goList() {
  uni.redirectTo({ url: '/pages/visitor/list' })
}

onLoad((q) => {
  token.value = (q && q.token) || ''
  if (token.value) passUrl.value = buildUrl(token.value)
  loadMeta()
})
</script>

<style lang="scss" scoped>
.tip {
  font-size: 24rpx;
  color: #64748b;
  line-height: 1.55;
  margin-bottom: 24rpx;
}
.info {
  margin-bottom: 20rpx;
  padding: 16rpx;
  background: #f8fafc;
  border-radius: 12rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 8rpx 0;
  font-size: 28rpx;
}
.k { color: #94a3b8; flex-shrink: 0; }
.v { color: #0f172a; text-align: right; word-break: break-all; }
.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 8rpx 0 24rpx;
}
.qr {
  width: 360rpx;
  height: 360rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1px solid #e2e8f0;
}
.qr-tip {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #64748b;
}
.link-box {
  padding: 20rpx;
  background: #ecfdf5;
  border: 1px dashed #6ee7b7;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.link-text {
  font-size: 24rpx;
  color: #065f46;
  word-break: break-all;
  line-height: 1.5;
  user-select: text;
}
.mt { margin-top: 16rpx; }
.warn {
  margin-top: 24rpx;
  font-size: 22rpx;
  color: #b45309;
  line-height: 1.5;
}
</style>
