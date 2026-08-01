<template>
  <view class="page page-safe">
    <view class="toolbar">
      <view class="tabs">
        <text
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ on: tab === t.key }"
          @click="tab = t.key"
        >{{ t.label }}</text>
      </view>
      <button v-if="!store.isStaff" class="btn-primary add-btn" size="mini" @click="goCreate">+ 预约</button>
    </view>

    <view v-if="filtered.length" class="list">
      <view v-for="v in filtered" :key="v.id" class="item" @click="openItem(v)">
        <view class="item-hd">
          <view class="title-wrap">
            <text class="name">{{ v.visitorName }}</text>
            <text v-if="v.visitorCount > 1" class="count">{{ v.visitorCount }}人</text>
          </view>
          <text class="tag" :class="tagClass(v)">{{ statusText(v) }}</text>
        </view>

        <view class="meta">
          <text v-if="v.visitorPhone">{{ v.visitorPhone }}</text>
          <text v-if="v.visitorPhone && (v.driveFlag === 1 || v.plateNo)"> · </text>
          <text v-if="v.driveFlag === 1 || v.plateNo">{{ v.plateNo || '驾车' }}</text>
        </view>
        <view class="meta">{{ v.visitReason || '来访' }} · {{ formatTime(v.visitTime) }}</view>
        <view class="meta" v-if="v.expireTime">有效至 {{ formatTime(v.expireTime) }}
          <text v-if="remainText(v)" class="remain"> · 剩余 {{ remainText(v) }}</text>
        </view>

        <view class="item-ft" @click.stop>
          <button
            v-if="!store.isStaff && v.passToken && v.status === 'approved'"
            class="act"
            size="mini"
            @click="goShare(v)"
          >转发链接</button>
          <button
            v-if="!store.isStaff && isHistory(v) && v.passToken"
            class="act"
            size="mini"
            @click="goShare(v)"
          >查看</button>
          <button
            v-if="store.isStaff && v.status === 'approved' && v.statusTone === 'green'"
            class="act primary"
            size="mini"
            @click="checkin(v.id)"
          >核销</button>
          <button
            v-if="!store.isStaff && v.canCancel"
            class="act ghost"
            size="mini"
            @click="cancel(v.id)"
          >撤销</button>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text>{{ emptyText }}</text>
      <button v-if="!store.isStaff" class="btn-primary mt" size="mini" @click="goCreate">去预约访客</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { listVisit, checkinVisit, cancelVisit } from '../../api/biz'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const list = ref([])
const tab = ref('all')
let tickTimer = null

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '有效中' },
  { key: 'history', label: '历史记录' },
]

const filtered = computed(() => {
  const rows = list.value || []
  if (tab.value === 'active') return rows.filter((v) => v.status === 'approved')
  if (tab.value === 'history') return rows.filter((v) => isHistory(v))
  return rows
})

const emptyText = computed(() => {
  if (store.isStaff) {
    if (tab.value === 'history') return '暂无历史访客记录'
    if (tab.value === 'active') return '暂无待核销访客'
    return '暂无访客预约'
  }
  if (tab.value === 'history') return '暂无历史预约记录'
  if (tab.value === 'active') return '暂无有效预约'
  return '暂无预约，点击上方新增'
})

function isHistory(v) {
  return ['checked_in', 'checked', 'expired', 'cancelled'].includes(v.status)
}

function formatTime(v) {
  return String(v || '').replace('T', ' ').substring(0, 16)
}

function statusText(v) {
  if (v.statusLabel) return v.statusLabel
  if (v.status === 'approved' || v.status === 'pending') return '生效中'
  if (v.status === 'checked_in' || v.status === 'checked') return '已通行'
  if (v.status === 'expired') return '已过期'
  if (v.status === 'cancelled') return '业主撤销'
  return v.status || '-'
}

function tagClass(v) {
  const t = v.statusTone
  if (t === 'green') return 'ok'
  if (t === 'teal') return 'teal'
  if (t === 'blue') return 'ok'
  if (t === 'orange') return 'warn'
  return 'warn'
}

function remainText(v) {
  if (v.status !== 'approved') return ''
  const sec = Number(v.remainSeconds || 0)
  if (sec <= 0) {
    if (v.statusTone === 'teal' && v.visitTime) {
      const start = new Date(String(v.visitTime).replace(' ', 'T')).getTime()
      const left = Math.floor((start - Date.now()) / 1000)
      if (left > 0) return fmtDuration(left)
    }
    return ''
  }
  return fmtDuration(sec)
}

function fmtDuration(sec) {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  if (h > 0) return `${h}小时${m}分`
  if (m > 0) return `${m}分钟`
  return `${sec}秒`
}

async function load() {
  const rows = (await listVisit().catch(() => [])) || []
  list.value = Array.isArray(rows) ? rows : []
}

function startTick() {
  stopTick()
  tickTimer = setInterval(() => {
    list.value = list.value.map((v) => {
      if (v.status !== 'approved') return v
      const next = { ...v }
      if (Number(next.remainSeconds) > 0) {
        next.remainSeconds = Number(next.remainSeconds) - 1
      }
      return next
    })
  }, 1000)
}

function stopTick() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

onShow(async () => {
  await load()
  startTick()
})
onHide(stopTick)
onUnmounted(stopTick)

function goCreate() {
  uni.navigateTo({ url: '/pages/visitor/create' })
}

function goShare(v) {
  const token = v.passToken
  if (!token) {
    uni.showToast({ title: '暂无通行链接', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/visitor/share?token=${encodeURIComponent(token)}` })
}

function openItem(v) {
  if (v.passToken) goShare(v)
}

async function checkin(id) {
  uni.showModal({
    title: '线下核销',
    content: '确认该访客已到访并核销？核销后单次通行码失效。',
    success: async (res) => {
      if (!res.confirm) return
      await checkinVisit(id)
      uni.showToast({ title: '已核销', icon: 'success' })
      load()
    }
  })
}

async function cancel(id) {
  uni.showModal({
    title: '撤销预约',
    content: '仅未生效前可撤销。确认撤销该预约？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await cancelVisit(id)
        uni.showToast({ title: '已撤销', icon: 'success' })
        load()
      } catch (e) {}
    }
  })
}
</script>

<style lang="scss" scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}
.tabs {
  flex: 1;
  display: flex;
  gap: 4rpx;
  padding: 6rpx;
  background: #fff;
  border-radius: 999rpx;
  border: 1px solid #e8eaed;
  min-width: 0;
}
.tab {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  color: #64748b;
  padding: 12rpx 4rpx;
  border-radius: 999rpx;
  white-space: nowrap;
}
.tab.on {
  background: #ecfdf5;
  color: #0f766e;
  font-weight: 600;
}
.add-btn {
  flex-shrink: 0;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.item {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, 0.04);
}
.item-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 10rpx;
}
.title-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  flex: 1;
}
.name {
  font-size: 30rpx;
  font-weight: 600;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.count {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #0f766e;
  background: #ecfdf5;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
}
.tag {
  flex-shrink: 0;
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  line-height: 1.4;
  background: #f1f5f9;
  color: #64748b;
}
.tag.ok {
  background: #dcfce7;
  color: #15803d;
}
.tag.teal {
  background: #ccfbf1;
  color: #0f766e;
}
.tag.warn {
  background: #ffedd5;
  color: #c2410c;
}
.meta {
  font-size: 24rpx;
  color: #94a3b8;
  line-height: 1.55;
}
.remain {
  color: #0f766e;
}
.item-ft {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #f1f5f9;
}
.act {
  margin: 0 !important;
  padding: 0 22rpx !important;
  height: 52rpx !important;
  line-height: 52rpx !important;
  font-size: 24rpx !important;
  border-radius: 999rpx !important;
  background: #ecfdf5 !important;
  color: #0f766e !important;
  border: 1px solid #99f6e4 !important;
}
.act.primary {
  background: #14b8a6 !important;
  color: #fff !important;
  border-color: #14b8a6 !important;
}
.act.ghost {
  background: #fff !important;
  color: #64748b !important;
  border-color: #e2e8f0 !important;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  padding: 80rpx 32rpx;
  color: #94a3b8;
  font-size: 28rpx;
}
.mt {
  margin-top: 8rpx;
}
</style>
