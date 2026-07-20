<template>
  <view class="page page-safe">
    <view v-if="store.isGridWorker" class="card">
      <view class="empty">网格员无物业费账单权限</view>
    </view>

    <template v-else-if="store.isStaff">
      <view class="card">
        <view class="card-title">本社区物业费账单</view>
        <view class="muted">仅展示当前登录社区数据</view>
      </view>
      <view class="card" v-if="bills.length">
        <view v-for="b in bills" :key="b.id" class="bill">
          <view>
            <text class="t">{{ b.title }}</text>
            <text class="d">{{ b.period || '' }} · {{ b.houseNo || '-' }}</text>
          </view>
          <view class="right">
            <text class="amt">¥{{ b.amount }}</text>
            <text class="st" :class="b.status">{{ b.status === 'paid' ? '已缴' : '待缴' }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">暂无账单</view>
    </template>

    <template v-else>
      <view class="card">
        <view class="card-title">物业费缴纳</view>
        <view class="muted">演示环境：确认后模拟缴费成功</view>
        <view class="input-row">
          <text class="label">金额</text>
          <input v-model="feeAmount" type="digit" placeholder="如 320.00" />
        </view>
        <button class="btn-primary" @click="mockFee">确认缴费（演示）</button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { listFeeBills } from '../../api/biz'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const feeAmount = ref('320.00')
const bills = ref([])

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (store.isGridWorker) return
  if (store.isStaff) {
    if (!store.canPerm(PERMS.PAYMENT)) return
    bills.value = (await listFeeBills().catch(() => [])) || []
  }
})

function mockFee() {
  uni.showModal({
    title: '缴费成功（演示）',
    content: `已模拟缴纳物业费 ¥${feeAmount.value}`,
    showCancel: false
  })
}
</script>

<style lang="scss" scoped>
.bill {
  display: flex; justify-content: space-between; padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.t { display: block; font-size: 28rpx; font-weight: 600; }
.d { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693; }
.right { text-align: right; }
.amt { display: block; font-size: 30rpx; font-weight: 700; color: #e85d4c; }
.st { font-size: 22rpx; color: #8a9693; }
.st.paid { color: #47b686; }
.input-row { display: flex; align-items: center; gap: 16rpx; margin: 20rpx 0; }
.label { width: 100rpx; }
</style>
