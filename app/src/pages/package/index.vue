<template>
  <!-- App 端不提供 SaaS 套餐；误入时引导回首页 -->
  <view class="page page-safe">
    <view class="card empty-box">
      <text class="title">暂无内容</text>
      <text class="muted">套餐管理仅在运营/物业后台使用，居民端不展示。</text>
      <button class="btn-primary" @click="goHome">返回首页</button>
    </view>
  </view>
</template>

<script setup>
function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style lang="scss" scoped>
.empty-box {
  margin-top: 80rpx;
  padding: 48rpx 32rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  background: #fff;
  border-radius: 28rpx;
}
.title {
  font-size: 32rpx;
  font-weight: 700;
}
.muted {
  color: #8a9693;
  font-size: 26rpx;
}
.btn-primary {
  background: #47b686 !important;
  color: #fff !important;
  border: none;
  border-radius: 40rpx;
}
</style>
