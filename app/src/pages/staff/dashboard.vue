<template>
  <view class="page page-safe">
    <view class="card">
      <view class="section-title">社区数据大屏</view>
      <view class="muted">物业管理员 · 本社区公共业务概览</view>
      <view v-if="!store.canPerm(PERMS.DATA_DASHBOARD)" class="empty">
        网格员无数据大屏权限
      </view>
    </view>

    <view v-if="store.canPerm(PERMS.DATA_DASHBOARD)" class="card stats">
      <view class="stat">
        <text class="num">{{ stats.repair }}</text>
        <text class="label">待处理报修</text>
      </view>
      <view class="stat">
        <text class="num">{{ stats.visit }}</text>
        <text class="label">待核销访客</text>
      </view>
      <view class="stat">
        <text class="num">{{ stats.appeal }}</text>
        <text class="label">待处理诉求</text>
      </view>
      <view class="stat">
        <text class="num">{{ stats.notice }}</text>
        <text class="label">公告数</text>
      </view>
    </view>

    <view v-if="store.canPerm(PERMS.DATA_DASHBOARD)" class="card">
      <view class="card-title">快捷管理</view>
      <view class="menu" @click="go('/pages/repair/list')">报修工单 ›</view>
      <view class="menu" @click="go('/pages/notice/list')">公告管理 ›</view>
      <view class="menu" @click="go('/pages/activity/list')">活动管理 ›</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listRepair, listVisit, listAppeal, listNotice } from '../../api/biz'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const stats = reactive({ repair: 0, visit: 0, appeal: 0, notice: 0 })

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.guardPerm(PERMS.DATA_DASHBOARD, '仅物业管理员可查看数据大屏')) return
  const [repairs, visits, appeals, notices] = await Promise.all([
    listRepair('pending').catch(() => []),
    listVisit().catch(() => []),
    listAppeal().catch(() => []),
    listNotice().catch(() => [])
  ])
  stats.repair = (repairs || []).length
  stats.visit = (visits || []).filter((v) => v.status === 'approved').length
  stats.appeal = (appeals || []).filter((a) => a.status === 'pending' || !a.status).length
  stats.notice = Array.isArray(notices) ? notices.length : notices?.records?.length || 0
})

function go(url) {
  uni.navigateTo({ url })
}
</script>

<style lang="scss" scoped>
.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}
.stat {
  width: calc(50% - 10rpx);
  background: #f5faf8;
  border-radius: 16rpx;
  padding: 28rpx;
  box-sizing: border-box;
}
.num {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #47b686;
}
.label {
  font-size: 24rpx;
  color: #8a9693;
}
.menu {
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
  font-size: 28rpx;
}
</style>
