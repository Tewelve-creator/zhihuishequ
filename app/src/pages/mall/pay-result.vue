<template>
  <view class="page page-safe">
    <view class="card center">
      <view :class="['icon', statusClass]">{{ iconText }}</view>
      <text class="title">{{ title }}</text>
      <text class="sub">{{ sub }}</text>
      <view v-if="order" class="amt">¥{{ Number(order.payAmount || 0).toFixed(2) }}</view>
      <view v-if="polling" class="poll">正在查询支付结果（{{ pollCount }}）...</view>
    </view>

    <view v-if="order" class="card">
      <view class="row"><text class="k">订单号</text><text class="v">{{ order.orderNo }}</text></view>
      <view class="row"><text class="k">状态</text><text class="v">{{ order.statusLabel || order.status }}</text></view>
      <view class="row"><text class="k">配送</text><text class="v">{{ order.deliveryTypeLabel || '—' }}</text></view>
    </view>

    <button v-if="order?.id" class="btn-primary" @click="goDetail">查看订单详情</button>
    <button class="btn-ghost mt" @click="goOrders">我的订单</button>
    <button class="btn-ghost mt" @click="goMall">返回商城</button>
  </view>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { syncStoreAlipayPay } from '../../api/store'

const order = ref(null)
const polling = ref(false)
const pollCount = ref(0)
let timer = null
let orderNo = ''

const paid = computed(() => {
  const s = order.value?.status
  return s && s !== 'UNPAID' && s !== 'CANCELLED'
})
const statusClass = computed(() => {
  if (paid.value) return 'ok'
  if (order.value?.status === 'CANCELLED') return 'bad'
  return 'wait'
})
const iconText = computed(() => {
  if (paid.value) return '✓'
  if (order.value?.status === 'CANCELLED') return '!'
  return '…'
})
const title = computed(() => {
  if (paid.value) return '支付成功'
  if (order.value?.status === 'CANCELLED') return '已取消'
  return '支付处理中'
})
const sub = computed(() => {
  if (paid.value) return '商家即将接单，可在订单中查看进度'
  if (order.value?.status === 'CANCELLED') return '可返回商城重新下单'
  return '请勿重复提交，系统正在确认支付结果'
})

onLoad(async (q) => {
  orderNo = q.orderId || q.orderNo || ''
  if (!orderNo) {
    uni.showToast({ title: '缺少订单号', icon: 'none' })
    return
  }
  try {
    order.value = await syncStoreAlipayPay(orderNo)
  } catch {
    /* ignore */
  }
  if (!paid.value) startPoll()
})

onUnmounted(stopPoll)

function startPoll() {
  stopPoll()
  polling.value = true
  pollCount.value = 0
  timer = setInterval(async () => {
    pollCount.value += 1
    try {
      order.value = await syncStoreAlipayPay(orderNo)
    } catch {
      /* ignore */
    }
    if (paid.value || pollCount.value >= 15) stopPoll()
  }, 2000)
}

function stopPoll() {
  polling.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function goDetail() {
  if (!order.value?.id) return
  uni.redirectTo({ url: `/pages/mall/order-detail?id=${order.value.id}` })
}
function goOrders() {
  uni.redirectTo({ url: '/pages/mall/orders' })
}
function goMall() {
  uni.switchTab({ url: '/pages/mall/index' })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100%;
  padding: 32rpx 28rpx;
  background: linear-gradient(180deg, #eaf7f1 0%, #f5f7f6 240rpx);
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 10rpx 28rpx rgba(47, 158, 111, 0.06);
}
.center { text-align: center; }
.icon {
  width: 96rpx;
  height: 96rpx;
  line-height: 96rpx;
  margin: 8rpx auto 20rpx;
  border-radius: 50%;
  font-size: 48rpx;
  font-weight: 800;
  color: #fff;
  background: #c5d0cc;
}
.icon.ok { background: $mint; }
.icon.bad { background: #e85d4c; }
.icon.wait { background: #f5a623; }
.title { display: block; font-size: 36rpx; font-weight: 800; color: #1f2a28; }
.sub { display: block; margin-top: 12rpx; font-size: 24rpx; color: #8a9693; line-height: 1.5; }
.amt { margin-top: 20rpx; font-size: 44rpx; font-weight: 800; color: #e85d4c; }
.poll { margin-top: 16rpx; font-size: 22rpx; color: #8a9693; }
.row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  font-size: 26rpx;
  border-bottom: 1rpx solid #f0f3f2;
}
.row:last-child { border-bottom: none; }
.k { color: #8a9693; }
.v { color: #1f2a28; font-weight: 600; max-width: 70%; text-align: right; word-break: break-all; }
.btn-primary {
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  border-radius: 999rpx;
  font-weight: 700;
  border: none;
}
.btn-ghost {
  background: #fff;
  color: #2f9e6f;
  border: 2rpx solid #cfe9dc;
  border-radius: 999rpx;
  font-weight: 600;
}
.mt { margin-top: 16rpx; }
</style>
