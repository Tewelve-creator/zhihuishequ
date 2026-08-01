<template>
  <view class="page page-safe">
    <view v-if="list.length" class="card list-card">
      <view
        v-for="a in list"
        :key="a.id"
        class="row"
        @click="go(a)"
      >
        <image
          v-if="coverSrc(a)"
          class="cover"
          :src="coverSrc(a)"
          mode="aspectFill"
        />
        <view v-else class="cover cover-ph">
          <text class="ph-t">活动</text>
        </view>

        <view class="main">
          <text class="title">{{ a.title }}</text>
          <text v-if="a.summary" class="summary">{{ a.summary }}</text>
          <text class="meta">
            {{ a.category || '活动' }}
            <text v-if="a.location"> · {{ a.location }}</text>
            · 已报名 {{ a.signedCount || 0 }}/{{ a.maxPeople || '-' }}
          </text>
          <view v-if="tagList(a.tags).length" class="tags">
            <text v-for="t in tagList(a.tags)" :key="t" class="tag-item">{{ t }}</text>
          </view>
        </view>

        <text class="phase" :class="phaseClass(a.phase)">{{ a.phase || '报名中' }}</text>
      </view>
    </view>
    <view v-else class="empty">暂无活动</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listActivity } from '../../api/biz'
import { getBaseUrl } from '../../utils/request'

const list = ref([])

onShow(async () => {
  list.value = (await listActivity().catch(() => [])) || []
})

function coverSrc(a) {
  const u = a?.coverUrl || a?.cover_url
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || u.startsWith('data:') || u.startsWith('blob:')) return u
  const base = getBaseUrl() || ''
  return u.startsWith('/') ? base + u : `${base}/${u}`
}

function tagList(tags) {
  return String(tags || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 4)
}

function phaseClass(phase) {
  if (phase === '已结束') return 'done'
  if (phase === '进行中') return 'doing'
  return 'pending'
}

function go(a) {
  uni.setStorageSync('activity_cache_' + a.id, a)
  uni.navigateTo({ url: `/pages/activity/detail?id=${a.id}` })
}
</script>

<style lang="scss" scoped>
.list-card {
  padding: 0 28rpx;
  overflow: hidden;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #eef2f1;
}
.row:last-child {
  border-bottom: none;
}
.cover {
  width: 140rpx;
  height: 140rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
  background: #e8f7f0;
}
.cover-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #dff5ea, #b8e6d0);
}
.ph-t {
  color: #47b686;
  font-size: 24rpx;
  font-weight: 700;
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding-top: 2rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary {
  font-size: 24rpx;
  color: #64748b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.4;
}
.tags {
  margin-top: 4rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}
.tag-item {
  font-size: 20rpx;
  color: #2563eb;
  background: #eff6ff;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.phase {
  flex-shrink: 0;
  align-self: center;
  font-size: 22rpx;
  font-weight: 600;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #047857;
}
.phase.doing {
  background: #eff6ff;
  color: #1d4ed8;
}
.phase.done {
  background: #f1f5f9;
  color: #64748b;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 120rpx 0;
  font-size: 28rpx;
}
</style>
