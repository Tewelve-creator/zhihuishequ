<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="title">{{ store.isStaff ? '社区报修工单' : '我的报修' }}</text>
        <text class="sub">{{ headSub }}</text>
      </view>
      <button v-if="!store.isStaff" class="btn-mini" size="mini" @click="goCreate">+ 拍照报修</button>
    </view>

    <view v-if="store.isStaff" class="tabs">
      <view class="tab" :class="{ on: tab === 'all' }" @click="switchTab('all')">全部</view>
      <view class="tab" :class="{ on: tab === 'mine' }" @click="switchTab('mine')">
        指派给我
        <text v-if="mineCount" class="badge">{{ mineCount }}</text>
      </view>
    </view>

    <view v-if="list.length" class="list">
      <view
        v-for="item in list"
        :key="item.id"
        class="card item"
        @click="goDetail(item.id)"
      >
        <view class="row">
          <text class="item-title text-ellipsis">{{ item.title || '未命名工单' }}</text>
          <text class="tag" :class="tagClass(item.status)">{{ statusText(item.status) }}</text>
        </view>
        <view class="meta">
          <text>{{ item.facilityType || item.aiTag || '设施待识别' }}</text>
          <text v-if="item.damageLevel"> · {{ item.damageLevel }}</text>
        </view>
        <view v-if="store.isStaff" class="handler">
          处理人：{{ item.handlerName || '未指派' }}
        </view>
        <view v-if="item.aiTag" class="ai-chip">AI {{ item.aiTag }}</view>
      </view>
    </view>
    <view v-else class="empty">
      <text class="empty-title">暂无内容</text>
      <text class="muted">{{ emptyTip }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listRepair } from '../../api/biz'
import { statusText } from '../../utils/constants'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const list = ref([])
const tab = ref('all')
const mineCount = ref(0)

const headSub = computed(() => {
  if (!store.isStaff) return '仅展示本人提交的工单'
  return tab.value === 'mine' ? '仅显示指派给您的工单' : '本社区全部工单'
})

const emptyTip = computed(() => {
  if (!store.isStaff) return '您还没有提交过报修'
  return tab.value === 'mine' ? '暂无指派给您的工单' : '本社区暂无报修工单'
})

onLoad((q) => {
  if (q?.mine === '1' || q?.mine === 1) tab.value = 'mine'
})

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  await load()
})

async function load() {
  const mine = store.isStaff && tab.value === 'mine'
  const data = await listRepair(mine ? { mine: true } : undefined).catch(() => [])
  list.value = Array.isArray(data) ? data : []
  if (store.isStaff) {
    const mineData = await listRepair({ mine: true }).catch(() => [])
    mineCount.value = asList(mineData).filter((r) => r.status !== 'done').length
  }
}

function asList(v) {
  return Array.isArray(v) ? v : []
}

function switchTab(t) {
  tab.value = t
  load()
}

function tagClass(s) {
  if (s === 'done') return 'ok'
  if (s === 'pending') return 'warn'
  if (s === 'processing') return 'ing'
  return ''
}

function goCreate() {
  if (store.isStaff) return
  if (!store.guardFeature('basic_repair')) return
  uni.navigateTo({ url: '/pages/repair/create' })
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/repair/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100vh;
  padding: 28rpx;
  background: #f5f7f6;
  box-sizing: border-box;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
}
.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
}
.sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.btn-mini {
  background: $mint !important;
  color: #fff !important;
  border: none;
  border-radius: 28rpx;
}
.tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.tab {
  position: relative;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 24rpx;
  color: #64748b;
}
.tab.on {
  background: rgba(71, 182, 134, 0.15);
  color: #2f8f66;
  font-weight: 600;
}
.badge {
  margin-left: 8rpx;
  background: #ef4444;
  color: #fff;
  border-radius: 20rpx;
  padding: 0 10rpx;
  font-size: 20rpx;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  align-items: center;
}
.item-title {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
}
.meta {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.handler {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #475569;
}
.ai-chip {
  margin-top: 12rpx;
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #047857;
  font-size: 20rpx;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #f1f5f9;
  color: #64748b;
}
.tag.warn { background: #fff7ed; color: #c2410c; }
.tag.ing { background: #eff6ff; color: #1d4ed8; }
.tag.ok { background: #ecfdf5; color: #047857; }
.empty {
  padding: 80rpx 20rpx;
  text-align: center;
}
.empty-title { display: block; font-size: 30rpx; color: #334155; margin-bottom: 8rpx; }
.muted { color: #94a3b8; font-size: 24rpx; }
</style>
