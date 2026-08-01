<template>
  <view class="page page-safe">
    <view class="card center">
      <view :class="['icon', statusClass]">{{ iconText }}</view>
      <text class="title">{{ title }}</text>
      <text class="sub">{{ sub }}</text>
      <view v-if="order" class="amt">¥{{ formatMoney(order.amount) }}</view>
      <view v-if="polling" class="poll">正在查询支付结果（{{ pollCount }}）...</view>
    </view>

    <view v-if="order" class="card">
      <view class="row"><text class="k">订单号</text><text class="v">{{ order.orderNo }}</text></view>
      <view class="row"><text class="k">支付方式</text><text class="v">{{ channelLabel }}</text></view>
      <view v-if="order.payTradeNo" class="row"><text class="k">支付流水号</text><text class="v">{{ order.payTradeNo }}</text></view>
      <view v-if="order.paidAt" class="row"><text class="k">支付时间</text><text class="v">{{ order.paidAt }}</text></view>
      <view class="row"><text class="k">房屋</text><text class="v">{{ order.houseLabel }}</text></view>
    </view>

    <button v-if="order?.status === 'PAID'" class="btn-primary" @click="goReceipt">查看电子凭证</button>
    <button class="btn-ghost mt" @click="goHome">返回缴费首页</button>
    <button class="btn-ghost mt" @click="goRecords">查看缴费记录</button>
  </view>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { formatMoney, getFeeOrder, statusLabel, syncAlipayPay } from '../../api/payment'

const order = ref(null)
const polling = ref(false)
const pollCount = ref(0)
let timer = null
let orderId = ''

const statusClass = computed(() => {
  const s = order.value?.status
  if (s === 'PAID') return 'ok'
  if (s === 'FAILED' || s === 'CLOSED') return 'bad'
  return 'wait'
})
const iconText = computed(() => {
  const s = order.value?.status
  if (s === 'PAID') return '✓'
  if (s === 'FAILED' || s === 'CLOSED') return '!'
  return '…'
})
const title = computed(() => {
  const s = order.value?.status
  if (s === 'PAID') return '支付成功'
  if (s === 'FAILED') return '支付失败'
  if (s === 'CLOSED') return '已取消'
  if (s === 'PAYING') return '支付处理中'
  return statusLabel(s) || '支付结果'
})
const sub = computed(() => {
  if (order.value?.status === 'PAYING') return '请勿重复提交，系统正在确认支付结果'
  if (order.value?.status === 'PAID') return '已生成缴费记录与电子凭证'
  return '可返回首页或查看缴费记录'
})
const channelLabel = computed(() =>
  order.value?.payChannel === 'ALIPAY' ? '支付宝' : '微信支付'
)

onLoad(async (q) => {
  orderId = q.orderId
  // 支付宝回跳后先主动查单落账（本地无公网异步通知时的兜底）
  try {
    await syncAlipayPay(orderId)
  } catch {
    /* ignore */
  }
  await refresh()
  if (order.value?.status === 'PAYING') startPoll()
})

onUnmounted(stopPoll)

async function refresh() {
  try {
    order.value = await getFeeOrder(orderId)
  } catch (e) {
    uni.showToast({ title: e?.msg || '查询失败', icon: 'none' })
  }
}

function startPoll() {
  stopPoll()
  polling.value = true
  pollCount.value = 0
  timer = setInterval(async () => {
    pollCount.value += 1
    try {
      await syncAlipayPay(orderId)
    } catch {
      /* ignore */
    }
    await refresh()
    if (order.value?.status !== 'PAYING' || pollCount.value >= 15) {
      stopPoll()
    }
  }, 1000)
}

function stopPoll() {
  polling.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function goReceipt() {
  uni.navigateTo({ url: `/pages/payment/receipt?orderId=${orderId}` })
}
function goHome() {
  uni.redirectTo({ url: '/pages/payment/index' })
}
function goRecords() {
  uni.redirectTo({ url: '/pages/payment/records' })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.center { text-align: center; padding: 48rpx 28rpx !important; }
.icon {
  width: 112rpx; height: 112rpx; line-height: 112rpx; margin: 0 auto 20rpx;
  border-radius: 50%; font-size: 52rpx; color: #fff; font-weight: 700;
}
.icon.ok { background: $mint; }
.icon.bad { background: #ef4444; }
.icon.wait { background: #f59e0b; }
.title { display: block; font-size: 36rpx; font-weight: 700; }
.sub { display: block; margin-top: 12rpx; font-size: 24rpx; color: #8a9693; }
.amt { display: block; margin-top: 24rpx; font-size: 48rpx; font-weight: 700; color: #e85d4c; }
.poll { margin-top: 16rpx; font-size: 22rpx; color: #f59e0b; }
.row { display: flex; justify-content: space-between; padding: 12rpx 0; font-size: 26rpx; }
.k { color: #8a9693; }
.mt { margin-top: 20rpx; }
.btn-ghost {
  background: #fff; color: $mint; border: 2rpx solid $mint; border-radius: 999rpx;
  font-size: 28rpx; height: 88rpx; line-height: 88rpx;
}
</style>
