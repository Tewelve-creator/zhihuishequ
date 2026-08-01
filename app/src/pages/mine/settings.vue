<template>
  <view class="page page-safe">
    <view class="card">
      <view class="section-title">系统设置</view>
      <view class="mi" @click="nav('/pages/mine/profile')">
        <text>个人资料</text>
        <text class="ar">›</text>
      </view>
      <view class="mi" @click="toggleFont">
        <text>大字模式</text>
        <text class="v">{{ store.largeFont ? '已开启' : '已关闭' }}</text>
      </view>
      <view class="mi" @click="nav('/pages/message/list')">
        <text>消息通知</text>
        <text class="ar">›</text>
      </view>
      <view class="mi" @click="about">
        <text>关于智慧社区</text>
        <text class="ar">›</text>
      </view>
    </view>

    <view class="logout-wrap">
      <button class="logout-btn" @click="onLogout">退出账号</button>
    </view>
  </view>
</template>

<script setup>
import { useUserStore } from '../../store/user'

const store = useUserStore()

function toggleFont() {
  store.toggleLargeFont()
  uni.showToast({ title: store.largeFont ? '已开启大字' : '已关闭大字', icon: 'none' })
}

function nav(url) {
  uni.navigateTo({ url })
}

function about() {
  uni.showModal({
    title: '关于',
    content: 'AI智慧社区 App · 薄荷绿物业端',
    showCancel: false
  })
}

function onLogout() {
  uni.showModal({
    title: '退出账号',
    content: '确定退出当前账号吗？',
    confirmText: '退出',
    confirmColor: '#e85d4c',
    success: (res) => {
      if (res.confirm) store.logout()
    }
  })
}
</script>

<style lang="scss" scoped>
.mi {
  display: flex; justify-content: space-between; padding: 28rpx 0;
  border-bottom: 1rpx solid #f0f3f2; font-size: 30rpx;
}
.mi:last-child { border-bottom: none; }
.v { color: #47b686; font-size: 26rpx; }
.ar { color: #c5cecb; }
.logout-wrap { margin-top: 40rpx; }
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #fff;
  color: #e85d4c;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 28rpx;
  border: 2rpx solid #ffd4ce;
}
.logout-btn::after { border: none; }
</style>
