<template>
  <!-- 服务管理 Tab：物业全流程；居民便民 -->
  <view class="page page-safe mint">
    <view class="banner">
      <text class="banner-title">{{ store.isStaff ? '服务管理' : '社区服务' }}</text>
      <text class="banner-sub">{{ bannerSub }}</text>
    </view>

    <!-- 物业公共：报修 + 访客 -->
    <view v-if="store.isStaff" class="sec">
      <view class="sec-title">工单与通行</view>
      <view class="svc" @click="go('/pages/repair/list', PERMS.REPAIR_MANAGE)">
        <view>
          <text class="t">报修工单全流程</text>
          <text class="d">接单 · 派单 · 完工上传维修照 · AI标签</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/visitor/list', PERMS.VISITOR_VERIFY)">
        <view>
          <text class="t">访客审核核销</text>
          <text class="d">审核预约 · 线下核销</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/appeal/index', PERMS.APPEAL_MEDIATE)">
        <view>
          <text class="t">邻里倾诉处理</text>
          <text class="d">矛盾调解 · 诉求跟进</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 仅物业管理员 -->
    <view v-if="store.isPropertyAdmin" class="sec">
      <view class="sec-title">社区运营（管理员）</view>
      <view class="svc" @click="go('/pages/notice/list', PERMS.NOTICE_MANAGE)">
        <view>
          <text class="t">公告发布</text>
          <text class="d">发布 / 管理社区公告</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/activity/list', PERMS.ACTIVITY_MANAGE)">
        <view>
          <text class="t">活动管理</text>
          <text class="d">活动发布与报名核销</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/payment/index', PERMS.PAYMENT)">
        <view>
          <text class="t">物业费账单</text>
          <text class="d">本社区缴费账单</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/lost/index', PERMS.LOST_AUDIT)">
        <view>
          <text class="t">失物招领审核</text>
          <text class="d">失物信息与 AI 匹配</text>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="store.isGridWorker" class="tip-card">
      网格员岗位已隐藏：公告发布、活动管理、物业费账单、失物招领审核、数据大屏
    </view>

    <!-- 居民 -->
    <view v-if="store.isResident" class="sec">
      <view class="sec-title">便民服务</view>
      <view class="svc" @click="go('/pages/repair/list')">
        <view><text class="t">报修进度</text><text class="d">查看我的工单</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/payment/index')">
        <view><text class="t">物业缴费</text><text class="d">在线缴纳</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/activity/list')">
        <view><text class="t">活动报名</text><text class="d">社区活动</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="svc" @click="go('/pages/visitor/create')">
        <view><text class="t">访客预约</text><text class="d">预约通行</text></view>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()

const bannerSub = computed(() => {
  if (store.isGridWorker) return '报修 · 访客核销 · 邻里调解'
  if (store.isStaff) return '工单通行 · 公告活动 · 账单失物'
  return '报修缴费 · 活动访客'
})

onShow(() => {
  if (!store.token) uni.reLaunch({ url: '/pages/login/login' })
})

function go(url, perm) {
  if (perm && !store.guardPerm(perm)) return
  uni.navigateTo({ url })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.mint { background: #f5f7f6; min-height: 100vh; padding: 24rpx 28rpx; }
.banner {
  padding: 36rpx 32rpx; border-radius: 24rpx; margin-bottom: 24rpx;
  background: linear-gradient(135deg, #6ed3a8, $mint 55%, #3fad7d);
}
.banner-title { display: block; font-size: 40rpx; font-weight: 700; color: #fff; }
.banner-sub { display: block; margin-top: 10rpx; font-size: 24rpx; color: rgba(255,255,255,.9); }
.sec {
  background: #fff; border-radius: 24rpx; padding: 8rpx 28rpx; margin-bottom: 24rpx;
}
.sec-title {
  font-size: 28rpx; font-weight: 700; color: #1f2a28;
  padding: 24rpx 0 8rpx;
}
.svc {
  display: flex; justify-content: space-between; align-items: center;
  padding: 28rpx 0; border-bottom: 1rpx solid #f0f3f2;
}
.svc:last-child { border-bottom: none; }
.t { display: block; font-size: 30rpx; font-weight: 600; color: #1f2a28; }
.d { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a9693; }
.arrow { font-size: 36rpx; color: #c5cecb; }
.tip-card {
  background: #fff8e8; color: #8a5a00; border-radius: 20rpx;
  padding: 24rpx; font-size: 24rpx; line-height: 1.5;
}
</style>
