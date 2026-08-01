<template>
  <view class="page page-safe">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.v || 'all'"
        class="tab"
        :class="{ on: status === t.v }"
        @click="status = t.v; load()"
      >{{ t.l }}</view>
    </view>
    <view v-for="o in list" :key="o.id" class="card item" @click="go(o.id)">
      <view class="row">
        <text class="no">{{ o.orderNo }}</text>
        <text class="st">{{ o.statusLabel }}</text>
      </view>
      <text class="meta">{{ o.deliveryTypeLabel }} · ¥{{ o.payAmount }}</text>
      <text class="meta">{{ o.receiverAddr }}</text>
      <view class="ops" @click.stop>
        <button
          v-if="o.status === 'UNPAID'"
          class="pay-btn"
          size="mini"
          type="primary"
          :loading="payingId === o.id"
          @click="onPay(o)"
        >去支付</button>
        <button v-if="o.status === 'UNPAID' || o.status === 'PENDING_ACCEPT'" size="mini" @click="onCancel(o)">取消</button>
        <button
          v-if="['PENDING_ACCEPT','PREPARING','DELIVERING','COMPLETED'].includes(o.status) && o.refundStatus !== 'APPLYING' && o.status !== 'REFUNDED'"
          size="mini"
          @click="onRefund(o)"
        >退款</button>
      </view>
    </view>
    <view v-if="!list.length" class="empty">暂无订单</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  cancelStoreOrder,
  listStoreOrders,
  payStoreOrder,
  pickMockPayAction,
  refundStoreOrder,
} from '../../api/store'

const status = ref('')
const list = ref([])
const payingId = ref(null)
const tabs = [
  { v: '', l: '全部' },
  { v: 'UNPAID', l: '待支付' },
  { v: 'PENDING_ACCEPT', l: '待接单' },
  { v: 'DELIVERING', l: '配送中' },
  { v: 'COMPLETED', l: '已完成' },
]

onLoad((q) => {
  if (q?.tab) status.value = q.tab
})
onShow(load)

async function load() {
  list.value = (await listStoreOrders(status.value || undefined).catch(() => [])) || []
}
function go(id) {
  uni.navigateTo({ url: '/pages/mall/order-detail?id=' + id })
}

async function onPay(o) {
  if (payingId.value) return
  const action = await pickMockPayAction()
  if (action === 'cancel') {
    uni.showToast({ title: '已取消支付', icon: 'none' })
    return
  }
  payingId.value = o.id
  try {
    if (action === 'pending') {
      await payStoreOrder(o.orderNo, 'pending')
      uni.showToast({ title: '支付处理中', icon: 'none' })
    } else if (action === 'fail') {
      try {
        await payStoreOrder(o.orderNo, 'fail')
      } catch {
        /* expected */
      }
      uni.showToast({ title: '支付失败', icon: 'none' })
    } else {
      await payStoreOrder(o.orderNo, 'success')
      uni.showToast({ title: '支付成功', icon: 'success' })
    }
    await load()
  } catch (e) {
    uni.showToast({ title: e?.msg || '支付失败', icon: 'none' })
  } finally {
    payingId.value = null
  }
}

async function onCancel(o) {
  await cancelStoreOrder(o.id, '居民取消')
  uni.showToast({ title: '已取消', icon: 'success' })
  load()
}
async function onRefund(o) {
  uni.showModal({
    title: '申请退款',
    editable: true,
    placeholderText: '退款原因',
    success: async (res) => {
      if (!res.confirm) return
      await refundStoreOrder(o.id, res.content || '居民申请退款')
      uni.showToast({ title: '已提交', icon: 'success' })
      load()
    },
  })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.tabs { display: flex; gap: 12rpx; padding: 16rpx 0; flex-wrap: wrap; }
.tab { padding: 10rpx 20rpx; border-radius: 999rpx; background: #fff; font-size: 24rpx; color: #5a6562; }
.tab.on { background: $mint; color: #fff; }
.item { margin-bottom: 16rpx; padding: 24rpx; }
.row { display: flex; justify-content: space-between; }
.no { font-weight: 700; font-size: 26rpx; }
.st { color: $mint; font-size: 24rpx; }
.meta { display: block; margin-top: 8rpx; color: #8a9693; font-size: 24rpx; }
.ops { display: flex; gap: 12rpx; margin-top: 16rpx; justify-content: flex-end; align-items: center; }
.pay-btn { background: $mint !important; color: #fff !important; }
.empty { text-align: center; color: #8a9693; padding: 80rpx; }
</style>
