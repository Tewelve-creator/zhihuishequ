<template>
  <view class="page">
    <view class="hero">
      <text class="h-title">AI 智能日程</text>
      <text class="h-sub">同步公告/活动/缴费，检测冲突并推荐便民服务</text>
      <view class="hero-actions">
        <button class="btn" size="mini" :loading="genLoading" @click="onGenerate">生成我的日程</button>
        <button class="btn ghost" size="mini" @click="goButler">问管家</button>
      </view>
    </view>

    <view v-if="conflictCount > 0" class="conflict-banner" @click="onConflict">
      检测到 {{ conflictCount }} 条日程冲突，点此复核
    </view>

    <view class="card">
      <view class="card-hd">
        <text class="card-title">我的日程</text>
        <text class="link" @click="load">刷新</text>
      </view>
      <view v-if="!items.length" class="empty">暂无日程，点击上方「生成我的日程」</view>
      <view v-for="it in items" :key="it.id" class="item" @click="jump(it.jumpPath)">
        <view class="item-top">
          <text class="item-title">{{ it.title }}</text>
          <text v-if="it.conflictFlag === 1" class="badge">冲突</text>
        </view>
        <text class="item-meta">{{ it.sourceType || 'AI' }} · {{ fmt(it.startTime || it.remindTime) }}</text>
        <text class="item-desc">{{ it.content }}</text>
      </view>
    </view>

    <view class="card">
      <view class="card-hd">
        <text class="card-title">便民推荐</text>
      </view>
      <view v-for="(r, i) in recommends" :key="i" class="rec" @click="jump(r.jumpPath)">
        <view>
          <text class="rec-title">{{ r.title }}</text>
          <text class="rec-desc">{{ r.desc }}</text>
        </view>
        <text class="go">预约 →</text>
      </view>
    </view>

    <view class="card">
      <view class="card-hd"><text class="card-title">我的画像</text></view>
      <picker :range="ageLabels" @change="onAge">
        <view class="field">年龄段：{{ ageLabel }}</view>
      </picker>
      <view class="field">
        <text>兴趣标签</text>
        <input v-model="profile.interests" class="input" placeholder="如：亲子,家政,健身" />
      </view>
      <view class="field row" @click="profile.hasChild = profile.hasChild ? 0 : 1">
        <text>有孩家庭</text>
        <text>{{ profile.hasChild ? '是' : '否' }}</text>
      </view>
      <button class="btn-primary" size="mini" @click="saveProf">保存画像</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  conflictCheck,
  generateSchedule,
  getProfile,
  listSchedule,
  recommendServices,
  saveProfile,
} from '../../api/ai'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const items = ref([])
const recommends = ref([])
const conflictCount = ref(0)
const genLoading = ref(false)
const profile = reactive({ ageBand: 'adult', interests: '', hasChild: 0, livingNeeds: '', building: '' })
const ageOpts = [
  { v: 'youth', l: '青年' },
  { v: 'adult', l: '中年' },
  { v: 'senior', l: '老年' },
]
const ageLabels = ageOpts.map((x) => x.l)
const ageLabel = computed(() => ageOpts.find((x) => x.v === profile.ageBand)?.l || '中年')

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function jump(path) {
  if (!path) return
  if (path.includes('/pages/ai/chat') || path.includes('/pages/mall/index') || path.includes('/pages/home')) {
    uni.switchTab({ url: path.split('?')[0], fail: () => uni.navigateTo({ url: path }) })
    return
  }
  uni.navigateTo({ url: path, fail: () => uni.showToast({ title: '页面打开失败', icon: 'none' }) })
}

function goButler() {
  uni.switchTab({ url: '/pages/ai/chat' })
}

async function load() {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  try {
    items.value = (await listSchedule()) || []
    conflictCount.value = items.value.filter((x) => x.conflictFlag === 1).length
    recommends.value = (await recommendServices()) || []
    const p = await getProfile()
    if (p) Object.assign(profile, {
      ageBand: p.ageBand || 'adult',
      interests: p.interests || '',
      hasChild: Number(p.hasChild) || 0,
      livingNeeds: p.livingNeeds || '',
      building: p.building || '',
    })
  } catch (e) {
    /* request tips */
  }
}

async function onGenerate() {
  genLoading.value = true
  try {
    const res = await generateSchedule()
    items.value = res?.items || []
    conflictCount.value = res?.conflicts || 0
    uni.showToast({ title: `新增${res?.created || 0}条`, icon: 'none' })
  } finally {
    genLoading.value = false
  }
}

async function onConflict() {
  const res = await conflictCheck()
  conflictCount.value = res?.conflictCount || 0
  await load()
}

function onAge(e) {
  const i = Number(e.detail.value)
  profile.ageBand = ageOpts[i]?.v || 'adult'
}

async function saveProf() {
  await saveProfile({ ...profile })
  uni.showToast({ title: '画像已保存', icon: 'success' })
}

onMounted(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page { min-height: 100vh; padding: 24rpx; background: #f5f7f6; box-sizing: border-box; }
.hero {
  background: linear-gradient(145deg, #47b686, #2f9a6e);
  color: #fff; border-radius: 20rpx; padding: 28rpx; margin-bottom: 20rpx;
}
.h-title { font-size: 34rpx; font-weight: 700; display: block; }
.h-sub { font-size: 24rpx; opacity: 0.9; margin-top: 8rpx; display: block; }
.hero-actions { margin-top: 20rpx; display: flex; gap: 16rpx; }
.btn { background: #fff; color: $mint; border: none; }
.btn.ghost { background: rgba(255,255,255,.18); color: #fff; }
.conflict-banner {
  background: #fff7ed; color: #c2410c; padding: 18rpx 22rpx; border-radius: 12rpx;
  margin-bottom: 16rpx; font-size: 26rpx;
}
.card { background: #fff; border-radius: 16rpx; padding: 22rpx; margin-bottom: 16rpx; }
.card-hd { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.card-title { font-weight: 600; font-size: 28rpx; }
.link { color: $mint; font-size: 24rpx; }
.empty { color: #94a3b8; font-size: 24rpx; padding: 20rpx 0; }
.item { padding: 16rpx 0; border-bottom: 1px solid #f1f5f9; }
.item-top { display: flex; justify-content: space-between; gap: 12rpx; }
.item-title { font-size: 28rpx; font-weight: 500; }
.badge { color: #dc2626; font-size: 22rpx; }
.item-meta, .item-desc { display: block; color: #64748b; font-size: 22rpx; margin-top: 6rpx; }
.rec { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 0; border-bottom: 1px solid #f1f5f9; }
.rec-title { display: block; font-size: 28rpx; }
.rec-desc { display: block; font-size: 22rpx; color: #94a3b8; }
.go { color: $mint; font-size: 24rpx; }
.field { padding: 14rpx 0; font-size: 26rpx; color: #334155; }
.field.row { display: flex; justify-content: space-between; }
.input { margin-top: 8rpx; border-bottom: 1px solid #e2e8f0; padding: 8rpx 0; font-size: 26rpx; }
.btn-primary { margin-top: 12rpx; background: $mint; color: #fff; }
</style>
