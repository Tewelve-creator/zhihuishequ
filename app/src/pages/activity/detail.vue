<template>
  <view class="page page-safe" v-if="item">
    <image
      v-if="coverSrc"
      class="hero-cover"
      :src="coverSrc"
      mode="aspectFill"
    />
    <view class="card">
      <view class="section-title">{{ item.title }}</view>
      <view class="muted" v-if="item.category">分类：{{ item.category }}</view>
      <view class="muted">地点：{{ item.location || '-' }}</view>
      <view class="muted">时间：{{ item.startTime || '-' }} ~ {{ item.endTime || '-' }}</view>
      <view class="muted" v-if="item.signupEnd">报名截止：{{ item.signupEnd }}</view>
      <view class="muted">名额：{{ item.signedCount || 0 }} / {{ item.maxPeople || '-' }}</view>
      <view class="summary" v-if="item.summary">{{ item.summary }}</view>
      <view class="tags" v-if="item.tags">
        <text v-for="t in tagList(item.tags)" :key="t" class="tag-item">{{ t }}</text>
      </view>
      <view class="content">{{ item.content }}</view>
      <button class="btn-primary" :loading="loading" @click="signup">立即报名</button>
    </view>

    <view v-if="store.isStaff" class="card">
      <view class="card-title">报名名单（核销）</view>
      <view v-for="s in signups" :key="s.id" class="list-item">
        <view class="flex-1">
          <text>报名 #{{ s.id }} · 用户 {{ s.userId }}</text>
          <view class="muted">{{ statusText(s.status) }}</view>
        </view>
        <button
          v-if="s.status !== 'checked'"
          size="mini"
          class="btn-primary"
          @click="check(s.id)"
        >
          核销
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { signupActivity, listSignups, checkSignup } from '../../api/biz'
import { statusText } from '../../utils/constants'
import { useUserStore } from '../../store/user'
import { getBaseUrl } from '../../utils/request'

const store = useUserStore()
const item = ref(null)
const signups = ref([])
const loading = ref(false)
let id = null

const coverSrc = computed(() => {
  const u = item.value?.coverUrl || item.value?.cover_url
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || u.startsWith('data:') || u.startsWith('blob:')) return u
  const base = getBaseUrl() || ''
  return u.startsWith('/') ? base + u : `${base}/${u}`
})

onLoad(async (q) => {
  id = q.id
  item.value = uni.getStorageSync('activity_cache_' + id) || { title: '活动', id }
  if (store.isStaff) {
    signups.value = (await listSignups(id).catch(() => [])) || []
  }
})

async function signup() {
  loading.value = true
  try {
    await signupActivity(id, {
      userName: store.nickname || undefined,
      phone: uni.getStorageSync('phone') || undefined,
    })
    uni.showToast({ title: '报名成功，已同步日程', icon: 'success' })
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function tagList(tags) {
  return String(tags || '')
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function check(signupId) {
  await checkSignup(signupId)
  uni.showToast({ title: '已核销', icon: 'success' })
  signups.value = (await listSignups(id).catch(() => [])) || []
}
</script>

<style lang="scss" scoped>
.hero-cover {
  width: 100%;
  height: 360rpx;
  border-radius: 0 0 24rpx 24rpx;
  margin-bottom: 8rpx;
  background: #e8f7f0;
}
.summary {
  margin-top: 16rpx;
  padding: 16rpx;
  background: #f8fafc;
  border-radius: 12rpx;
  color: #475569;
  font-size: 26rpx;
  line-height: 1.6;
}
.tags {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}
.tag-item {
  font-size: 22rpx;
  color: #2563eb;
  background: #eff6ff;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.content {
  margin: 24rpx 0 32rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
