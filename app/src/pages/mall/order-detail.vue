<template>
  <view class="page page-safe" v-if="order">
    <view class="card">
      <view class="st">{{ order.statusLabel }}</view>
      <text class="meta">{{ order.orderNo }}</text>
      <text class="meta">{{ order.deliveryTypeLabel }} · ¥{{ order.payAmount }}</text>
      <text class="meta">{{ order.receiverName }} {{ order.receiverPhone }}</text>
      <text class="meta">{{ order.receiverAddr }}</text>
    </view>
    <view class="card">
      <view class="card-title">商品明细</view>
      <view v-for="it in order.items || []" :key="it.id" class="line">
        <text>{{ it.productName }} ×{{ it.quantity }}</text>
        <text>¥{{ it.amount }}</text>
      </view>
    </view>

    <view v-if="order.status === 'UNPAID'" class="card pay-card">
      <view class="card-title">继续支付</view>
      <view class="ch" :class="{ on: channel === 'ALIPAY' }" @click="channel = 'ALIPAY'">
        <text class="ch-t">支付宝（沙盒）</text>
        <view class="radio" :class="{ on: channel === 'ALIPAY' }" />
      </view>
      <view class="ch" :class="{ on: channel === 'WECHAT' }" @click="channel = 'WECHAT'">
        <text class="ch-t">微信支付（演示模拟）</text>
        <view class="radio" :class="{ on: channel === 'WECHAT' }" />
      </view>
      <button class="btn-pay" :loading="paying" :disabled="paying" @click="onPay">
        确认支付 ¥{{ order.payAmount }}
      </button>
      <text v-if="channel === 'ALIPAY'" class="tip">将跳转支付宝沙盒收银台</text>
      <text v-else class="tip">微信渠道为本地模拟支付</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createStoreAlipayPay,
  getStoreOrder,
  openAlipayPayForm,
  payStoreOrder,
  pickMockPayAction,
} from '../../api/store'

const order = ref(null)
const channel = ref('ALIPAY')
const paying = ref(false)
let orderId = null

onLoad(async (q) => {
  orderId = q.id
  await reload()
})

async function reload() {
  order.value = (await getStoreOrder(orderId).catch(() => null)) || null
}

async function onPay() {
  if (paying.value || !order.value) return
  paying.value = true
  try {
    if (channel.value === 'ALIPAY') {
      const pay = await createStoreAlipayPay(order.value.orderNo, 'page')
      if (!pay?.payForm) throw { msg: '未拿到支付宝支付表单' }
      openAlipayPayForm(pay.payForm)
      return
    }
    const action = await pickMockPayAction()
    if (action === 'cancel') {
      uni.showToast({ title: '已取消支付', icon: 'none' })
      return
    }
    if (action === 'pending') {
      await payStoreOrder(order.value.orderNo, 'pending', channel.value)
      uni.showToast({ title: '支付处理中', icon: 'none' })
    } else if (action === 'fail') {
      try {
        await payStoreOrder(order.value.orderNo, 'fail', channel.value)
      } catch {
        /* expected */
      }
      uni.showToast({ title: '支付失败', icon: 'none' })
    } else {
      await payStoreOrder(order.value.orderNo, 'success', channel.value)
      uni.showToast({ title: '支付成功', icon: 'success' })
    }
    await reload()
  } catch (e) {
    uni.showToast({ title: e?.msg || '支付失败', icon: 'none' })
  } finally {
    paying.value = false
  }
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.st { font-size: 36rpx; font-weight: 800; color: $mint; margin-bottom: 12rpx; }
.meta { display: block; color: #5a6562; font-size: 26rpx; margin-top: 8rpx; }
.card-title { font-size: 30rpx; font-weight: 800; margin-bottom: 16rpx; color: #1f2a28; }
.line { display: flex; justify-content: space-between; padding: 12rpx 0; font-size: 26rpx; }
.pay-card { margin-top: 8rpx; }
.ch {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: #f5f7f6;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.ch.on { border-color: $mint; background: #e8f7f0; }
.ch-t { font-size: 28rpx; font-weight: 500; }
.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #c5d0cc;
}
.radio.on {
  background: $mint;
  border-color: $mint;
  box-shadow: inset 0 0 0 6rpx #fff;
}
.btn-pay {
  margin-top: 8rpx;
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  border-radius: 999rpx;
  font-weight: 700;
  border: none;
}
.tip { display: block; margin-top: 16rpx; font-size: 22rpx; color: #8a9693; }
</style>
