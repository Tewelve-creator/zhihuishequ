<template>
  <view class="page page-safe" v-if="item">
    <view class="card">
      <view class="section-title">{{ item.title }}</view>
      <view class="muted">地点：{{ item.location || '-' }}</view>
      <view class="muted">时间：{{ item.startTime || '-' }} ~ {{ item.endTime || '-' }}</view>
      <view class="muted">名额：{{ item.signedCount || 0 }} / {{ item.maxPeople || '-' }}</view>
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
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { signupActivity, listSignups, checkSignup } from '../../api/biz'
import { statusText } from '../../utils/constants'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const item = ref(null)
const signups = ref([])
const loading = ref(false)
let id = null

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
    await signupActivity(id)
    uni.showToast({ title: '报名成功', icon: 'success' })
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function check(signupId) {
  await checkSignup(signupId)
  uni.showToast({ title: '已核销', icon: 'success' })
  signups.value = (await listSignups(id).catch(() => [])) || []
}
</script>

<style lang="scss" scoped>
.content {
  margin: 24rpx 0 32rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
