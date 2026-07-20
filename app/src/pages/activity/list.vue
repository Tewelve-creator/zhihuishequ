<template>
  <view class="page page-safe">
    <view class="card" v-if="list.length">
      <view
        v-for="a in list"
        :key="a.id"
        class="list-item"
        @click="go(a)"
      >
        <view class="flex-1">
          <view class="text-ellipsis">{{ a.title }}</view>
          <view class="muted">{{ a.location || '' }} · 已报名 {{ a.signedCount || 0 }}/{{ a.maxPeople || '-' }}</view>
        </view>
        <text class="tag ok">活动</text>
      </view>
    </view>
    <view v-else class="empty">暂无活动</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listActivity } from '../../api/biz'

const list = ref([])

onShow(async () => {
  list.value = (await listActivity().catch(() => [])) || []
})

function go(a) {
  uni.setStorageSync('activity_cache_' + a.id, a)
  uni.navigateTo({ url: `/pages/activity/detail?id=${a.id}` })
}
</script>
