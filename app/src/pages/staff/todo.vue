<template>
  <view class="page">
    <view class="card tabs-wrap">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ on: tab === t.key }"
        @click="switchTab(t.key)"
      >
        <text>{{ t.name }}</text>
        <text v-if="t.count > 0" class="badge">{{ t.count > 99 ? '99+' : t.count }}</text>
      </view>
    </view>

    <!-- 工单 -->
    <view v-if="tab === 'repair'">
      <view v-if="!repairs.length" class="empty">暂无待办工单</view>
      <view
        v-for="r in repairs"
        :key="'r' + r.id"
        class="card item"
        @click="nav('/pages/repair/detail?id=' + r.id)"
      >
        <view class="row-between">
          <text class="title">{{ r.title || '报修工单' }}</text>
          <text class="tag warn">{{ statusLab(r.status) }}</text>
        </view>
        <text class="meta">{{ r.facilityType || r.aiTag || '' }} · {{ r.roomNo || r.address || '' }}</text>
        <text class="go">去处理 ›</text>
      </view>
    </view>

    <!-- 访客 -->
    <view v-if="tab === 'visit'">
      <view v-if="!visits.length" class="empty">暂无待核销访客</view>
      <view
        v-for="v in visits"
        :key="'v' + v.id"
        class="card item"
        @click="nav('/pages/visitor/list')"
      >
        <view class="row-between">
          <text class="title">{{ v.visitorName || v.name || '访客预约' }}</text>
          <text class="tag warn">待核销</text>
        </view>
        <text class="meta">{{ v.hostName || v.host || '' }} · {{ v.visitTime || v.createTime || '' }}</text>
        <text class="go">去核销 ›</text>
      </view>
    </view>

    <!-- 巡检 -->
    <view v-if="tab === 'insp'">
      <view v-if="!insps.length" class="empty">暂无待执行巡检</view>
      <view
        v-for="item in insps"
        :key="'i' + item.id"
        class="card item"
        @click="nav('/pages/staff/inspection-detail?id=' + item.id)"
      >
        <view class="row-between">
          <text class="title">{{ item.title }}</text>
          <text class="tag" :class="item.status === 'DOING' ? 'warn' : ''">{{ item.statusLabel }}</text>
        </view>
        <text class="meta">进度 {{ item.pointDone || 0 }}/{{ item.pointTotal || 0 }}</text>
        <text class="meta" v-if="item.endTime">截止 {{ formatTime(item.endTime) }}</text>
        <text class="go">去打卡 ›</text>
      </view>
    </view>

    <!-- 设备维保 -->
    <view v-if="tab === 'equip'">
      <view v-if="!equips.length" class="empty">暂无待维保/逾期设备</view>
      <view
        v-for="d in equips"
        :key="'e' + d.id"
        class="card item"
        @click="nav('/pages/staff/equip-detail?id=' + d.id)"
      >
        <view class="row-between">
          <text class="title">{{ d.name }}</text>
          <text class="tag" :class="d.status === 'OVERDUE' ? 'danger' : 'warn'">{{ d.statusLabel }}</text>
        </view>
        <text class="meta">{{ d.deviceType }} · {{ d.location || '' }}</text>
        <text class="meta">下次维保 {{ String(d.nextMaintDate || '').substring(0, 10) }}</text>
        <text class="go">去登记 ›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listRepair, listVisit } from '../../api/biz'
import { listMyInspectionTasks } from '../../api/inspection'
import { listEquipDue } from '../../api/equip'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const tab = ref('repair')
const repairs = ref([])
const visits = ref([])
const insps = ref([])
const equips = ref([])

const tabs = computed(() => [
  { key: 'repair', name: '工单', count: repairs.value.length },
  { key: 'visit', name: '访客', count: visits.value.length },
  { key: 'insp', name: '巡检', count: insps.value.length },
  { key: 'equip', name: '维保', count: equips.value.length }
])

function nav(url) {
  uni.navigateTo({ url })
}

function formatTime(v) {
  return String(v || '').replace('T', ' ').substring(0, 16)
}

function statusLab(s) {
  if (s === 'done') return '已完成'
  if (s === 'processing') return '处理中'
  if (s === 'pending') return '待处理'
  return s || '待处理'
}

function switchTab(k) {
  tab.value = k
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff) {
    uni.showToast({ title: '仅工作人员可查看', icon: 'none' })
    return
  }
  const [mine, pending, visitList, a, b, dueEquip] = await Promise.all([
    listRepair({ mine: true }).catch(() => []),
    listRepair('pending').catch(() => []),
    listVisit().catch(() => []),
    listMyInspectionTasks('PENDING', 'assigned').catch(() => []),
    listMyInspectionTasks('DOING', 'assigned').catch(() => []),
    listEquipDue().catch(() => [])
  ])
  const mineOpen = (Array.isArray(mine) ? mine : []).filter((r) => r.status !== 'done')
  const pendingList = Array.isArray(pending) ? pending : []
  const map = new Map()
  ;[...mineOpen, ...pendingList].forEach((r) => map.set(r.id, r))
  repairs.value = [...map.values()]
  visits.value = (Array.isArray(visitList) ? visitList : []).filter((v) => v.status === 'approved')
  insps.value = [...(b || []), ...(a || [])]
  equips.value = Array.isArray(dueEquip) ? dueEquip : []
}

onLoad((q) => {
  if (q.tab === 'visit' || q.tab === 'insp' || q.tab === 'repair' || q.tab === 'equip') {
    tab.value = q.tab
  }
})

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
.tabs-wrap {
  display: flex;
  gap: 8rpx;
  padding: 16rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 4rpx;
  border-radius: 16rpx;
  background: #f5f7f6;
  font-size: 24rpx;
  color: #666;
  position: relative;
}
.tab.on {
  background: $mint;
  color: #fff;
  font-weight: 600;
}
.badge {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  min-width: 28rpx;
  padding: 0 8rpx;
  height: 28rpx;
  line-height: 28rpx;
  border-radius: 999rpx;
  background: #ff4d4f;
  color: #fff;
  font-size: 18rpx;
}
.tab.on .badge {
  background: #fff;
  color: #ff4d4f;
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
  flex: 1;
}
.meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.go {
  display: block;
  margin-top: 14rpx;
  color: $mint;
  font-size: 26rpx;
  font-weight: 600;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #eef1f0;
  color: #666;
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
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 0;
  font-size: 28rpx;
}
</style>
