<template>
  <view class="page">
    <view v-if="loading" class="empty">加载中...</view>
    <template v-else-if="device">
      <view class="card" :class="{ due: device.status === 'DUE', overdue: device.status === 'OVERDUE' }">
        <view class="row-between">
          <text class="title">{{ device.name }}</text>
          <text class="tag" :class="tagClass(device.status)">{{ device.statusLabel }}</text>
        </view>
        <text class="meta">类型 {{ device.deviceType }}</text>
        <text class="meta">位置 {{ device.location || '—' }}</text>
        <text class="meta">周期 {{ device.cycleLabel }}</text>
        <text class="meta">上次维保 {{ fmt(device.lastMaintDate) }}</text>
        <text class="meta highlight">下次维保 {{ fmt(device.nextMaintDate) }}</text>
        <text class="meta" v-if="device.vendorName">服务商 {{ device.vendorName }}</text>
        <text class="meta" v-if="device.contactName">
          联系人 {{ device.contactName }} {{ device.contactPhone || '' }}
        </text>
        <text class="meta" v-if="device.remark">备注 {{ device.remark }}</text>
      </view>

      <view class="card">
        <view class="sec-h">
          <text class="sec-t">维保历史</text>
          <view class="add-btn" @click="goAdd">新增维保记录</view>
        </view>
        <view v-if="!records.length" class="empty-sm">暂无维保记录</view>
        <view v-for="r in records" :key="r.id" class="rec">
          <text class="rec-d">{{ fmt(r.maintDate) }} · {{ r.workerName || r.createByName || '—' }}</text>
          <text class="rec-c">{{ r.content }}</text>
          <view v-if="photosOf(r.photoUrls).length" class="photos">
            <image
              v-for="(u, i) in photosOf(r.photoUrls)"
              :key="i"
              :src="mediaUrl(u)"
              mode="aspectFill"
              class="ph"
              @click="preview(photosOf(r.photoUrls), i)"
            />
          </view>
        </view>
      </view>
    </template>
    <view v-else class="empty">设备不存在</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getEquipDevice, listEquipRecords } from '../../api/equip'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const id = ref(null)
const loading = ref(false)
const device = ref(null)
const records = ref([])

function tagClass(s) {
  if (s === 'OVERDUE') return 'danger'
  if (s === 'DUE') return 'warn'
  return 'ok'
}

function fmt(v) {
  return v ? String(v).substring(0, 10) : '—'
}

function photosOf(s) {
  if (!s) return []
  return String(s)
    .split(/[,;]/)
    .map((x) => x.trim())
    .filter((u) => u && !u.startsWith('blob:'))
}

function mediaUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url) || url.startsWith('data:')) return url
  // H5 开发代理 /api
  return `/api${url.startsWith('/') ? url : '/' + url}`
}

function preview(list, i) {
  uni.previewImage({
    urls: list.map(mediaUrl),
    current: i
  })
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.canPerm(PERMS.EQUIP_MAINT)) {
    uni.showToast({ title: '无权限', icon: 'none' })
    return
  }
  if (!id.value) return
  loading.value = true
  try {
    device.value = await getEquipDevice(id.value)
    records.value = (await listEquipRecords(id.value)) || []
  } catch (e) {
    device.value = null
    records.value = []
  } finally {
    loading.value = false
  }
}

function goAdd() {
  uni.navigateTo({ url: `/pages/staff/equip-record?deviceId=${id.value}` })
}

onLoad((q) => {
  id.value = q.id
})

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx 48rpx;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}
.card.due {
  border-left: 6rpx solid #faad14;
}
.card.overdue {
  border-left: 6rpx solid #ff4d4f;
}
.row-between {
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
}
.title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
  flex: 1;
}
.meta {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #64748b;
}
.meta.highlight {
  color: #cf1322;
  font-weight: 600;
}
.sec-h {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.sec-t {
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
}
.add-btn {
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #e8f8f3;
  color: $mint;
  font-size: 24rpx;
  font-weight: 600;
}
.rec {
  padding: 20rpx 0;
  border-top: 1rpx solid #f0f3f2;
}
.rec-d {
  display: block;
  font-size: 24rpx;
  color: #8a9693;
}
.rec-c {
  display: block;
  margin-top: 8rpx;
  font-size: 28rpx;
  color: #1f2a28;
}
.photos {
  display: flex;
  gap: 12rpx;
  margin-top: 12rpx;
  flex-wrap: wrap;
}
.ph {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  background: #f0f3f2;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
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
.empty,
.empty-sm {
  text-align: center;
  color: #94a3b8;
  padding: 40rpx 0;
  font-size: 26rpx;
}
</style>
