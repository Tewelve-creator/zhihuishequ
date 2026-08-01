<template>
  <view class="page page-safe">
    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="!bill" class="state">账单不存在</view>
    <template v-else>
      <view class="card">
        <view class="head">
          <text class="title">{{ bill.title }}</text>
          <text :class="['tag', bill.status]">{{ statusLabel(bill.status) }}</text>
        </view>
        <view class="row"><text class="k">账单编号</text><text class="v">{{ bill.billNo }}</text></view>
        <view class="row"><text class="k">房屋信息</text><text class="v">{{ bill.houseLabel }}</text></view>
        <view class="row"><text class="k">收费单位</text><text class="v">{{ bill.chargeUnit }}</text></view>
        <view class="row"><text class="k">计费周期</text><text class="v">{{ bill.period }}</text></view>
        <view class="row"><text class="k">截止时间</text><text class="v">{{ bill.dueDate }}</text></view>
      </view>

      <view class="card">
        <view class="card-title">收费明细</view>
        <view v-for="(it, i) in bill.items || []" :key="i" class="row">
          <text class="k">{{ it.name }}</text>
          <text class="v">¥{{ formatMoney(it.amount) }}</text>
        </view>
        <view class="row"><text class="k">计费依据</text><text class="v wrap">{{ bill.chargeBasis || '—' }}</text></view>
        <view class="row"><text class="k">优惠减免</text><text class="v">¥{{ formatMoney(bill.discount) }}</text></view>
        <view class="row"><text class="k">滞纳金</text><text class="v">¥{{ formatMoney(bill.lateFee) }}</text></view>
        <view class="row total">
          <text class="k">应缴金额</text>
          <text class="pay">¥{{ formatMoney(bill.payable) }}</text>
        </view>
      </view>

      <button
        v-if="bill.status === 'UNPAID' || bill.status === 'OVERDUE'"
        class="btn-primary"
        @click="payOne"
      >立即缴费</button>
      <button
        v-else-if="bill.status === 'PAYING'"
        class="btn-primary"
        :loading="acting"
        @click="payOne"
      >继续支付</button>
      <button
        v-if="bill.status === 'PAYING'"
        class="btn-ghost mt"
        :loading="acting"
        @click="cancelPaying"
      >取消支付中状态</button>
      <button class="btn-ghost mt" @click="onQuestion">对账单有疑问</button>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  cancelPayingBill,
  createBillQuestion,
  formatMoney,
  getBillDetail,
  statusLabel
} from '../../api/payment'
import { useHouseStore } from '../../store/house'

const houseStore = useHouseStore()
const bill = ref(null)
const loading = ref(true)
const acting = ref(false)
let billId = null

onLoad(async (q) => {
  billId = q.id
  if (!houseStore.guardBound()) return
  await reload()
})

async function reload() {
  loading.value = true
  try {
    bill.value = await getBillDetail(billId)
  } catch (e) {
    bill.value = null
  } finally {
    loading.value = false
  }
}

function payOne() {
  uni.navigateTo({ url: `/pages/payment/confirm?billIds=${billId}` })
}

async function cancelPaying() {
  uni.showModal({
    title: '取消支付中',
    content: '将关闭未完成的支付订单，账单恢复为待缴，可重新发起缴费',
    success: async (res) => {
      if (!res.confirm) return
      acting.value = true
      try {
        await cancelPayingBill(billId)
        uni.showToast({ title: '已恢复待缴', icon: 'success' })
        await reload()
      } catch (e) {
        uni.showToast({ title: e?.msg || '操作失败', icon: 'none' })
      } finally {
        acting.value = false
      }
    }
  })
}

async function onQuestion() {
  uni.showModal({
    title: '提交咨询',
    content: `将向物业创建咨询工单，自动携带账单编号 ${bill.value?.billNo}`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await createBillQuestion(bill.value.billNo)
        uni.showToast({ title: '已提交咨询', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: e?.msg || '提交失败', icon: 'none' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16rpx; }
.title { font-size: 32rpx; font-weight: 700; flex: 1; padding-right: 16rpx; }
.tag { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx; background: #f1f5f4; }
.tag.OVERDUE { background: #fee2e2; color: #ef4444; }
.tag.UNPAID { background: #e8f7f0; color: $mint; }
.tag.PAYING { background: #fff7ed; color: #ea580c; }
.tag.PAID { background: #e0f2fe; color: #0284c7; }
.row { display: flex; justify-content: space-between; gap: 24rpx; padding: 12rpx 0; font-size: 26rpx; }
.k { color: #8a9693; flex-shrink: 0; }
.v { color: #1f2a28; text-align: right; }
.v.wrap { max-width: 420rpx; }
.total { border-top: 1rpx solid #eef2f1; margin-top: 8rpx; padding-top: 20rpx; }
.pay { font-size: 34rpx; font-weight: 700; color: #e85d4c; }
.state { text-align: center; color: #8a9693; padding: 80rpx 0; }
.mt { margin-top: 20rpx; }
.btn-ghost {
  background: #fff; color: $mint; border: 2rpx solid $mint; border-radius: 999rpx;
  font-size: 28rpx; height: 88rpx; line-height: 88rpx;
}
</style>
