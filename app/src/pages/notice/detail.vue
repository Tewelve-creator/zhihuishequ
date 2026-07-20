<template>
  <view class="page page-safe" v-if="item">
    <view class="card">
      <view class="section-title">{{ item.title }}</view>
      <view v-if="item.publishState" class="tag" :class="tagClass">{{ item.publishState }}</view>
      <view v-if="item.summary" class="tag soft" style="margin-bottom: 16rpx">AI摘要：{{ item.summary }}</view>
      <view class="content">{{ item.content }}</view>
      <view class="muted" style="margin-top: 24rpx">
        发布时间：{{ formatTime(item.publishTime || item.createTime) }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getNotice, listNotice } from '../../api/biz'

const item = ref(null)

const tagClass = computed(() => {
  if (item.value?.publishState === '定时待发') return 'warn'
  if (item.value?.publishState === '已下架') return 'off'
  return ''
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onLoad(async (q) => {
  const cached = uni.getStorageSync('notice_cache_' + q.id)
  if (cached) item.value = cached
  try {
    const detail = await getNotice(q.id)
    if (detail) item.value = detail
  } catch (e) {
    if (!item.value) {
      const list = (await listNotice().catch(() => [])) || []
      item.value = list.find((n) => String(n.id) === String(q.id)) || {
        title: '公告',
        content: '未找到或尚未到发布时间'
      }
    }
  }
})
</script>

<style lang="scss" scoped>
.content {
  font-size: 30rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
.tag {
  display: inline-block;
  margin-bottom: 12rpx;
  margin-right: 12rpx;
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #ecfdf5;
  color: #047857;
}
.tag.soft { background: #f0fdf4; }
.tag.warn { background: #fff7ed; color: #c2410c; }
.tag.off { background: #f1f5f9; color: #64748b; }
</style>
