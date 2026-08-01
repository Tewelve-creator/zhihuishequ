<template>
  <view class="page page-safe">
    <view class="filters card">
      <picker :range="statusOptions" range-key="label" @change="onStatus">
        <view class="picker">状态：{{ statusLabelText }}</view>
      </picker>
      <picker mode="date" @change="onFrom">
        <view class="picker">起：{{ from || '不限' }}</view>
      </picker>
      <picker mode="date" @change="onTo">
        <view class="picker">止：{{ to || '不限' }}</view>
      </picker>
      <text class="link" @click="load">查询</text>
    </view>

    <view v-if="loading" class="state">加载中...</view>
    <view v-else-if="!list.length" class="state">暂无缴费记录</view>
    <view v-for="o in list" :key="o.orderId" class="card item" @click="open(o)">
      <view class="top">
        <text class="t">{{ o.orderNo }}</text>
        <text :class="['tag', o.status]">{{ statusLabel(o.status) }}</text>
      </view>
      <text class="d">{{ o.houseLabel }}</text>
      <view class="bot">
        <text class="time">{{ o.paidAt || o.createTime }}</text>
        <text class="amt">¥{{ formatMoney(o.amount) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useHouseStore } from '../../store/house'
import { formatMoney, listFeeOrders, statusLabel } from '../../api/payment'

const houseStore = useHouseStore()
const list = ref([])
const loading = ref(false)
const status = ref('')
const from = ref('')
const to = ref('')

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PAID', label: '已缴' },
  { value: 'PAYING', label: '支付中' },
  { value: 'FAILED', label: '失败' },
  { value: 'CLOSED', label: '已取消' }
]
const statusLabelText = computed(
  () => statusOptions.find((x) => x.value === status.value)?.label || '全部'
)

function onStatus(e) {
  status.value = statusOptions[e.detail.value].value
  load()
}
function onFrom(e) {
  from.value = e.detail.value
}
function onTo(e) {
  to.value = e.detail.value
}

function open(o) {
  if (o.status === 'PAID') {
    uni.navigateTo({ url: `/pages/payment/receipt?orderId=${o.orderId}` })
  } else {
    uni.navigateTo({ url: `/pages/payment/result?orderId=${o.orderId}` })
  }
}

async function load() {
  if (!houseStore.guardBound()) return
  loading.value = true
  try {
    list.value =
      (await listFeeOrders({
        houseId: houseStore.currentHouseId,
        status: status.value || undefined,
        from: from.value || undefined,
        to: to.value || undefined
      })) || []
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.filters { display: flex; flex-wrap: wrap; gap: 12rpx; align-items: center; }
.picker {
  background: #f5f7f6; padding: 12rpx 20rpx; border-radius: 12rpx; font-size: 24rpx; color: #475569;
}
.link { color: $mint; font-size: 26rpx; margin-left: auto; }
.item { padding: 28rpx !important; }
.top { display: flex; justify-content: space-between; align-items: center; }
.t { font-size: 28rpx; font-weight: 600; }
.tag { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; background: #f1f5f4; }
.tag.PAID { background: #e8f7f0; color: $mint; }
.tag.PAYING { background: #fff7ed; color: #ea580c; }
.tag.FAILED, .tag.CLOSED { background: #fee2e2; color: #ef4444; }
.d { display: block; margin-top: 10rpx; font-size: 24rpx; color: #8a9693; }
.bot { display: flex; justify-content: space-between; margin-top: 16rpx; }
.time { font-size: 22rpx; color: #94a3b8; }
.amt { font-size: 30rpx; font-weight: 700; color: #e85d4c; }
.state { text-align: center; color: #8a9693; padding: 80rpx 0; }
</style>
