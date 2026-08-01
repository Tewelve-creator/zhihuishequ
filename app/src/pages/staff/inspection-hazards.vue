<template>
  <view class="page">
    <view class="card head">
      <text class="h-title">巡检隐患台账</text>
      <text class="h-sub">{{ scopeTip }}</text>
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
    <view v-else-if="!list.length" class="empty">暂无隐患记录</view>

    <view v-for="item in list" :key="item.id" class="card item">
      <view class="row-between">
        <text class="title">{{ item.pointName || '点位隐患' }}</text>
        <text class="tag" :class="item.hazardStatus === 'FIXED' ? 'ok' : 'warn'">
          {{ item.hazardStatus === 'FIXED' ? '已整改' : '待跟进' }}
        </text>
      </view>
      <text class="desc">{{ item.hazardDesc || '—' }}</text>
      <text class="meta">任务：{{ item.taskTitle || '—' }}</text>
      <text class="meta" v-if="store.isPropertyAdmin">上报人：{{ item.userName || '—' }}</text>
      <text class="meta">时间：{{ formatTime(item.checkTime) }}</text>
      <view v-if="photosOf(item).length" class="photos">
        <image
          v-for="(u, i) in photosOf(item)"
          :key="i"
          :src="u"
          class="ph"
          mode="aspectFill"
          @click="preview(item, i)"
        />
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listMyInspectionHazards } from '../../api/inspection'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const tab = ref('')
const tabs = [
  { key: '', name: '全部' },
  { key: 'OPEN', name: '待跟进' },
  { key: 'FIXED', name: '已整改' }
]

const scopeTip = computed(() =>
  store.isPropertyAdmin ? '本社区全部隐患记录' : '本人上报的巡检隐患'
)

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 19)
}

function photosOf(item) {
  const s = item.photoUrls || ''
  return s
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
}

function preview(item, i) {
  const urls = photosOf(item)
  uni.previewImage({ urls, current: urls[i] })
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff || !store.canPerm(PERMS.INSPECTION_EXECUTE)) {
    uni.showToast({ title: '无权限', icon: 'none' })
    return
  }
  loading.value = true
  try {
    list.value = (await listMyInspectionHazards(tab.value || undefined)) || []
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
}
.h-sub {
  display: block;
  margin: 8rpx 0 20rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.tabs {
  display: flex;
  gap: 12rpx;
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
.row-between {
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
}
.desc {
  display: block;
  margin-top: 14rpx;
  font-size: 26rpx;
  color: #3a4543;
  line-height: 1.5;
}
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.photos {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}
.ph {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  background: #f5f7f6;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #eef1f0;
  height: fit-content;
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
}
</style>
