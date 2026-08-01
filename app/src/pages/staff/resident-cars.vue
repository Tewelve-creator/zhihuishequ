<template>
  <view class="page">
    <view class="card head">
      <text class="title">住户车辆台账</text>
      <text class="sub">查看本社区常驻车辆；网格员只读</text>
      <view class="tabs">
        <view class="tab" :class="{ on: tab === 'car' }" @click="tab = 'car'; load()">常驻车辆</view>
        <view class="tab" :class="{ on: tab === 'visit' }" @click="tab = 'visit'; load()">访客车牌</view>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中...</view>

    <template v-else-if="tab === 'car'">
      <view v-if="!cars.length" class="empty">暂无常驻车辆</view>
      <view v-for="c in cars" :key="c.id" class="card item">
        <view class="row">
          <text class="plate">{{ c.plateNo }}</text>
          <text class="tag">{{ c.status === 'ACTIVE' ? '正常' : '停用' }}</text>
        </view>
        <text class="meta">{{ c.houseLabel || '—' }} · {{ c.ownerName || '' }} {{ c.ownerPhone || '' }}</text>
        <text class="meta">{{ c.brandModel || '' }} {{ c.color || '' }}</text>
      </view>
    </template>

    <template v-else>
      <view v-if="!visits.length" class="empty">暂无访客预约</view>
      <view v-for="v in visits" :key="v.id" class="card item">
        <view class="row">
          <text class="plate">{{ v.plateNo || '未填车牌' }}</text>
          <text class="tag">{{ statusText(v.status) }}</text>
        </view>
        <text class="meta">{{ v.visitorName }} · {{ v.visitorPhone }}</text>
        <text class="meta">{{ v.visitReason }} · {{ v.visitTime || '' }}</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listResidentCars } from '../../api/residentCar'
import { listVisit } from '../../api/biz'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const loading = ref(false)
const tab = ref('car')
const cars = ref([])
const visits = ref([])

function statusText(s) {
  if (s === 'pending' || s === 'approved') return '已通过'
  if (s === 'checked_in' || s === 'checked') return '已核销'
  if (s === 'expired') return '已过期'
  if (s === 'cancelled') return '已撤销'
  return s || ''
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff) {
    uni.showToast({ title: '仅物业人员可查看', icon: 'none' })
    return
  }
  loading.value = true
  try {
    if (tab.value === 'car') {
      cars.value = (await listResidentCars({})) || []
    } else {
      visits.value = (await listVisit().catch(() => [])) || []
    }
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}
.title {
  font-size: 34rpx;
  font-weight: 700;
  display: block;
}
.sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.tabs {
  display: flex;
  gap: 12rpx;
  margin-top: 20rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  background: #f5f7f6;
  border-radius: 14rpx;
  font-size: 26rpx;
}
.tab.on {
  background: $mint;
  color: #fff;
  font-weight: 600;
}
.plate {
  font-size: 32rpx;
  font-weight: 700;
}
.row {
  display: flex;
  justify-content: space-between;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.tag {
  font-size: 22rpx;
  color: $mint;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 0;
}
</style>
