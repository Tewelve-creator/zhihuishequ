<template>
  <view class="page page-safe">
    <view v-if="store.canPerm(PERMS.NOTICE_PUBLISH)" class="card">
      <view class="section-title">发布公告</view>
      <input v-model="form.title" class="input" placeholder="公告标题" />
      <textarea v-model="form.content" class="area" placeholder="公告正文" />
      <view class="mode-row">
        <text
          class="mode"
          :class="{ on: form.mode === 'now' }"
          @click="form.mode = 'now'"
        >立即发布</text>
        <text
          class="mode"
          :class="{ on: form.mode === 'schedule' }"
          @click="form.mode = 'schedule'"
        >定时发布</text>
      </view>
      <picker
        v-if="form.mode === 'schedule'"
        mode="multiSelector"
        :range="pickerRange"
        :value="pickerIndex"
        @change="onPickerChange"
        @columnchange="onColumnChange"
      >
        <view class="picker-val">
          {{ form.publishTimeLabel || '选择发布时间' }}
        </view>
      </picker>
      <button class="btn-primary" size="mini" :loading="publishing" @click="onPublish">
        {{ form.mode === 'schedule' ? '定时发布' : '立即发布' }}
      </button>
    </view>

    <view class="card" v-if="list.length">
      <view v-for="n in list" :key="n.id" class="list-item" @click="go(n)">
        <view class="flex-1">
          <view class="text-ellipsis">{{ n.title }}</view>
          <view class="muted text-ellipsis">{{ n.summary || n.content }}</view>
          <view class="muted time">{{ formatTime(n.publishTime || n.createTime) }}</view>
        </view>
        <text class="tag" :class="tagClass(n)">{{ n.publishState || '公告' }}</text>
      </view>
    </view>
    <view v-else class="empty">暂无公告</view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listNotice, createNotice } from '../../api/biz'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const list = ref([])
const publishing = ref(false)
const form = reactive({
  title: '',
  content: '',
  mode: 'now',
  publishTime: '',
  publishTimeLabel: ''
})

const years = []
const months = []
const days = []
const hours = []
const minutes = []
const now = new Date()
for (let y = now.getFullYear(); y <= now.getFullYear() + 1; y++) years.push(String(y))
for (let m = 1; m <= 12; m++) months.push(String(m).padStart(2, '0'))
for (let d = 1; d <= 31; d++) days.push(String(d).padStart(2, '0'))
for (let h = 0; h <= 23; h++) hours.push(String(h).padStart(2, '0'))
for (let m = 0; m <= 59; m += 5) minutes.push(String(m).padStart(2, '0'))

const pickerRange = computed(() => [years, months, days, hours, minutes])
const pickerIndex = ref([0, now.getMonth(), now.getDate() - 1, now.getHours(), Math.floor(now.getMinutes() / 5)])

function syncPickerLabel() {
  const [yi, mi, di, hi, mni] = pickerIndex.value
  const y = years[yi]
  const m = months[mi]
  const d = days[di]
  const h = hours[hi]
  const mn = minutes[mni]
  form.publishTime = `${y}-${m}-${d}T${h}:${mn}:00`
  form.publishTimeLabel = `${y}-${m}-${d} ${h}:${mn}`
}

function onColumnChange(e) {
  const { column, value } = e.detail
  const next = [...pickerIndex.value]
  next[column] = value
  pickerIndex.value = next
}

function onPickerChange(e) {
  pickerIndex.value = e.detail.value
  syncPickerLabel()
}

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.guardFeature('basic_notice')) return
  const manage = store.canPerm(PERMS.NOTICE_PUBLISH)
  const data = await listNotice(manage).catch(() => [])
  list.value = Array.isArray(data) ? data : []
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function tagClass(n) {
  if (n.publishState === '定时待发') return 'warn'
  if (n.publishState === '已下架') return 'off'
  return ''
}

function go(n) {
  uni.setStorageSync('notice_cache_' + n.id, n)
  uni.navigateTo({ url: `/pages/notice/detail?id=${n.id}` })
}

async function onPublish() {
  if (!store.guardPerm(PERMS.NOTICE_PUBLISH, '网格员不可发布公告')) return
  if (!form.title.trim() || !form.content.trim()) {
    uni.showToast({ title: '请填写标题和正文', icon: 'none' })
    return
  }
  let publishTime
  if (form.mode === 'schedule') {
    if (!form.publishTime) syncPickerLabel()
    if (!form.publishTime) {
      uni.showToast({ title: '请选择发布时间', icon: 'none' })
      return
    }
    if (new Date(form.publishTime.replace('T', ' ')).getTime() <= Date.now()) {
      uni.showToast({ title: '定时时间需晚于现在', icon: 'none' })
      return
    }
    publishTime = form.publishTime
  }
  publishing.value = true
  try {
    const body = { title: form.title.trim(), content: form.content.trim() }
    if (publishTime) body.publishTime = publishTime
    await createNotice(body)
    form.title = ''
    form.content = ''
    form.mode = 'now'
    form.publishTime = ''
    form.publishTimeLabel = ''
    uni.showToast({ title: publishTime ? '已定时' : '已发布', icon: 'success' })
    const data = await listNotice(true).catch(() => [])
    list.value = Array.isArray(data) ? data : []
  } finally {
    publishing.value = false
  }
}
</script>

<style lang="scss" scoped>
.input, .area {
  background: #f5f7f6; border-radius: 12rpx; padding: 18rpx; margin: 12rpx 0; width: 100%; box-sizing: border-box;
}
.area { min-height: 160rpx; }
.mode-row { display: flex; gap: 16rpx; margin: 12rpx 0; }
.mode {
  padding: 10rpx 24rpx; border-radius: 999rpx; background: #eef2f1; color: #64748b; font-size: 24rpx;
}
.mode.on { background: rgba(71,182,134,.18); color: #2f8f66; font-weight: 600; }
.picker-val {
  background: #f5f7f6; border-radius: 12rpx; padding: 18rpx; margin: 8rpx 0 16rpx; color: #334155; font-size: 26rpx;
}
.btn-primary { background: #47b686; color: #fff; border: none; margin-top: 8rpx; }
.list-item { display: flex; gap: 16rpx; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f0f3f2; }
.time { margin-top: 6rpx; font-size: 22rpx; }
.tag { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 999rpx; background: #ecfdf5; color: #047857; }
.tag.warn { background: #fff7ed; color: #c2410c; }
.tag.off { background: #f1f5f9; color: #64748b; }
.empty { text-align: center; color: #94a3b8; padding: 60rpx 0; }
</style>
