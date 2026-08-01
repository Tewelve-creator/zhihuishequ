<template>
  <view class="page page-safe">
    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="!receipt" class="state">暂无凭证</view>
    <template v-else>
      <view class="card receipt">
        <text class="brand">优邻智慧社区 · 电子缴费凭证</text>
        <text class="no">凭证号 {{ receipt.receiptNo }}</text>
        <view class="amt">¥{{ formatMoney(receipt.amount) }}</view>
        <view class="row"><text class="k">社区</text><text class="v">{{ receipt.communityName || '—' }}</text></view>
        <view class="row"><text class="k">房屋</text><text class="v">{{ receipt.houseLabel }}</text></view>
        <view class="row"><text class="k">缴费人</text><text class="v">{{ receipt.payer }}</text></view>
        <view class="row"><text class="k">订单号</text><text class="v">{{ receipt.orderNo }}</text></view>
        <view class="row"><text class="k">支付流水号</text><text class="v">{{ receipt.payTradeNo }}</text></view>
        <view class="row"><text class="k">支付方式</text><text class="v">{{ receipt.payChannelLabel }}</text></view>
        <view class="row"><text class="k">支付时间</text><text class="v">{{ receipt.paidAt }}</text></view>
        <view class="split" />
        <view class="card-title">账单明细</view>
        <view v-for="b in receipt.bills || []" :key="b.id" class="row">
          <text class="k">{{ b.title }}</text>
          <text class="v">¥{{ formatMoney(b.payable) }}</text>
        </view>
        <text class="foot">本凭证由系统自动生成，仅供参考</text>
      </view>
      <button class="btn-primary" @click="onSave">保存/分享凭证（演示）</button>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { formatMoney, getFeeReceipt } from '../../api/payment'

const receipt = ref(null)
const loading = ref(true)

onLoad(async (q) => {
  loading.value = true
  try {
    receipt.value = await getFeeReceipt(q.orderId)
  } catch (e) {
    receipt.value = null
    uni.showToast({ title: e?.msg || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
})

function onSave() {
  uni.showToast({ title: '演示环境已生成凭证', icon: 'success' })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.receipt { position: relative; }
.brand { display: block; text-align: center; color: $mint; font-weight: 700; font-size: 30rpx; }
.no { display: block; text-align: center; margin-top: 8rpx; font-size: 22rpx; color: #8a9693; }
.amt {
  text-align: center; margin: 28rpx 0; font-size: 56rpx; font-weight: 700; color: #1f2a28;
}
.row { display: flex; justify-content: space-between; gap: 20rpx; padding: 12rpx 0; font-size: 26rpx; }
.k { color: #8a9693; }
.v { text-align: right; max-width: 420rpx; }
.split { height: 1rpx; background: #eef2f1; margin: 16rpx 0; }
.foot { display: block; margin-top: 28rpx; text-align: center; font-size: 22rpx; color: #94a3b8; }
.state { text-align: center; color: #8a9693; padding: 80rpx 0; }
</style>
