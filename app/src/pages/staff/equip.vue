<template>
  <view class="page">
    <view class="head card">
      <text class="h-title">设备维保</text>
      <text class="h-sub">公共设施计划性保养台账（非居民报修）</text>
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
    <view v-else-if="!list.length" class="empty">暂无设备</view>

    <view
      v-for="item in list"
      :key="item.id"
      class="card device"
      :class="{ due: item.status === 'DUE', overdue: item.status === 'OVERDUE' }"
      @click="goDetail(item.id)"
    >
      <view class="row-between">
        <text class="title">{{ item.name }}</text>
        <text class="tag" :class="tagClass(item.status)">{{ item.statusLabel }}</text>
      </view>
      <text class="meta">{{ item.deviceType }} · {{ item.location || '—' }}</text>
      <text class="meta">周期 {{ item.cycleLabel || '—' }} · 下次 {{ fmt(item.nextMaintDate) }}</text>
      <text class="meta" v-if="item.vendorName">维保单位 {{ item.vendorName }}</text>
      <text class="go">查看详情 ›</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listEquipDevices } from '../../api/equip'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const tab = ref('all')
const tabs = [
  { key: 'all', name: '全部' },
  { key: 'DUE', name: '待维保' },
  { key: 'OVERDUE', name: '已逾期' },
  { key: 'NORMAL', name: '正常' }
]

function tagClass(s) {
  if (s === 'OVERDUE') return 'danger'
  if (s === 'DUE') return 'warn'
  return 'ok'
}

function fmt(v) {
  return v ? String(v).substring(0, 10) : '—'
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff || !store.canPerm(PERMS.EQUIP_MAINT)) {
    uni.showToast({ title: '仅物业工作人员可使用设备维保', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const params = tab.value === 'all' ? {} : { status: tab.value }
    list.value = (await listEquipDevices(params)) || []
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
  if (id == null) return
  uni.navigateTo({ url: `/pages/staff/equip-detail?id=${id}` })
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
.h-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
}
.h-sub {
  display: block;
  margin: 8rpx 0 20rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.tabs {
  display: flex;
  gap: 10rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  background: #f5f7f6;
  border-radius: 14rpx;
  font-size: 24rpx;
  color: #666;
}
.tab.on {
  background: $mint;
  color: #fff;
  font-weight: 600;
}
.device.due {
  border-left: 6rpx solid #faad14;
}
.device.overdue {
  border-left: 6rpx solid #ff4d4f;
}
.row-between {
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
  flex: 1;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.go {
  margin-top: 14rpx;
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
  height: fit-content;
}
.tag.warn {
  background: #fff4e5;
  color: #d48806;
}
.tag.danger {
  background: #fff1f0;
  color: #cf1322;
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
