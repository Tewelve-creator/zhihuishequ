<template>
  <view class="page page-safe">
    <view class="tip">正在进入社区商城订单…</view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'

const store = useUserStore()
onShow(() => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (store.isStaff) {
    uni.switchTab({ url: '/pages/mall/index' })
  } else {
    uni.switchTab({ url: '/pages/mall/index' })
  }
})
</script>

<style scoped>
.tip { text-align: center; color: #8a9693; padding: 80rpx; }
</style>
