<template>
  <view class="page">
    <view class="head card">
      <view class="head-row">
        <view>
          <text class="h-title">巡检任务</text>
          <text class="h-sub">{{ scopeTip }}</text>
        </view>
        <view class="haz-btn" @click="navHazards">隐患台账</view>
      </view>
      <view class="tabs">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ on: tab === t.key }"
          @click="switchTab(t.key)"
        >
          {{ t.name }}
        </view>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中...</view>
    <view v-else-if="!list.length" class="empty">暂无巡检任务</view>

    <view v-for="item in list" :key="item.id" class="card task" @click="goDetail(item.id)">
      <view class="row-between">
        <text class="title">{{ item.title }}</text>
        <text class="tag" :class="tagClass(item.status)">{{ item.statusLabel }}</text>
      </view>
      <view class="progress">
        <view class="bar">
          <view class="fill" :style="{ width: progressPct(item) + '%' }" />
        </view>
        <text class="pct">{{ item.pointDone || 0 }}/{{ item.pointTotal || 0 }}</text>
      </view>
      <view class="meta" v-if="store.isPropertyAdmin && item.assigneeName">
        执行人 {{ item.assigneeName }}
      </view>
      <view class="meta" v-if="item.startTime || item.endTime">
        {{ formatTime(item.startTime) }} ~ {{ formatTime(item.endTime) }}
      </view>
      <view class="meta danger" v-if="item.hazardCount">隐患 {{ item.hazardCount }} 处</view>
      <view class="go">查看详情 ›</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listMyInspectionTasks } from '../../api/inspection'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const tab = ref('todo')
const tabs = [
  { key: 'todo', name: '待执行' },
  { key: 'DONE', name: '已完成' },
  { key: 'all', name: '全部' }
]

const scopeTip = computed(() =>
  store.isPropertyAdmin ? '本社区全部巡检任务' : '仅展示分配给我的任务'
)

function tagClass(s) {
  if (s === 'DONE') return 'ok'
  if (s === 'DOING') return 'warn'
  return ''
}

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(5, 16)
}

function progressPct(item) {
  const total = Number(item.pointTotal || 0)
  if (!total) return 0
  return Math.min(100, Math.round((Number(item.pointDone || 0) / total) * 100))
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff || !store.canPerm(PERMS.INSPECTION_EXECUTE)) {
    uni.showToast({ title: '仅物业工作人员可使用巡检', icon: 'none' })
    return
  }
  loading.value = true
  try {
    if (tab.value === 'todo') {
      const [a, b] = await Promise.all([
        listMyInspectionTasks('PENDING'),
        listMyInspectionTasks('DOING')
      ])
      list.value = [...(b || []), ...(a || [])]
    } else if (tab.value === 'DONE') {
      list.value = (await listMyInspectionTasks('DONE')) || []
    } else {
      list.value = (await listMyInspectionTasks('all')) || []
    }
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(k) {
  tab.value = k
  load()
}

function goDetail(id) {
  if (id == null || id === '' || String(id) === 'undefined') {
    uni.showToast({ title: '任务参数错误', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/staff/inspection-detail?id=${id}` })
}

function navHazards() {
  uni.navigateTo({ url: '/pages/staff/inspection-hazards' })
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx 48rpx;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20rpx;
}
.h-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
}
.h-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.haz-btn {
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #e8f8f3;
  color: $mint;
  font-size: 22rpx;
  font-weight: 600;
}
.tabs {
  display: flex;
  gap: 12rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  background: #f5f7f6;
  border-radius: 16rpx;
  font-size: 26rpx;
  color: #666;
}
.tab.on {
  background: $mint;
  color: #fff;
  font-weight: 600;
}
.task .title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
  flex: 1;
}
.row-between {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  align-items: flex-start;
}
.progress {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 18rpx;
}
.bar {
  flex: 1;
  height: 12rpx;
  background: #eef2f1;
  border-radius: 999rpx;
  overflow: hidden;
}
.fill {
  height: 100%;
  background: linear-gradient(90deg, #6ed3a8, $mint);
  border-radius: 999rpx;
}
.pct {
  font-size: 22rpx;
  color: #64748b;
  min-width: 72rpx;
  text-align: right;
}
.meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.meta.danger {
  color: #e67e22;
}
.go {
  margin-top: 16rpx;
  color: $mint;
  font-size: 26rpx;
  font-weight: 600;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #eef1f0;
  color: #666;
  white-space: nowrap;
}
.tag.warn {
  background: #fff4e5;
  color: #d48806;
}
.tag.ok {
  background: #e8f8f0;
  color: #2f9e6a;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 0;
  font-size: 28rpx;
}
</style>
