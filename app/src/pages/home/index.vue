<template>
  <view class="home">
    <scroll-view scroll-y class="scroll" :style="{ height: '100vh' }">
      <!-- 顶栏 -->
      <view class="hero" :class="{ staff: store.isStaff }">
        <image v-if="!store.isStaff" class="hero-img" :src="heroBanner" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="nav" :style="{ paddingTop: statusBarPx + 'px' }">
          <view class="nav-left" @click="showCommunity = true">
            <text class="community-name">{{ communityName }}</text>
            <u-icon name="arrow-down" color="#ffffff" size="12" />
          </view>
          <view class="nav-right" @click="nav('/pages/message/list')">
            <u-icon name="bell" color="#ffffff" size="22" />
            <view v-if="unreadCount > 0" class="red-dot" />
          </view>
        </view>
        <view v-if="store.isStaff" class="hero-role">
          <text>{{ store.isGridWorker ? '网格员工作台' : '物业管理工作台' }}</text>
          <text class="hero-sub">{{ store.nickname }} · {{ store.roleName }}</text>
        </view>
      </view>

      <view class="body">
        <!-- ========== 物业端首页 ========== -->
        <template v-if="store.isStaff">
          <view class="card banner-card" @click="onActivityTap">
            <text class="ban-tag">活动</text>
            <text class="ban-title">{{ activityTitle }}</text>
            <text class="ban-desc">{{ activityDesc }}</text>
          </view>

          <view class="card grid-8">
            <view
              v-for="item in staffShortcuts"
              :key="item.name"
              class="g8-item"
              @click="go(item)"
            >
              <view class="g8-icon" :style="{ background: item.bg }">{{ item.letter }}</view>
              <text class="g8-name">{{ item.name }}</text>
            </view>
          </view>

          <view class="card todo-card">
            <view class="todo-head" @click="nav('/pages/repair/list?mine=1')">
              <text class="todo-title">待办工单提醒</text>
              <text class="todo-more">查看全部 ›</text>
            </view>
            <view class="todo-nums">
              <view class="todo-n" @click="nav('/pages/repair/list?mine=1')">
                <text class="num warn">{{ assignedMine }}</text>
                <text class="lab">指派给我</text>
              </view>
              <view class="todo-n" @click="nav('/pages/repair/list')">
                <text class="num">{{ pendingRepair }}</text>
                <text class="lab">待接单报修</text>
              </view>
              <view class="todo-n" @click="nav('/pages/visitor/list')">
                <text class="num">{{ pendingVisit }}</text>
                <text class="lab">待审访客</text>
              </view>
            </view>
            <view v-if="assignedList.length" class="assign-box">
              <text class="assign-h">指派给我的报修</text>
              <view
                v-for="r in assignedList"
                :key="r.id"
                class="assign-row"
                @click="nav('/pages/repair/detail?id=' + r.id)"
              >
                <view class="assign-dot" />
                <view class="assign-body">
                  <text class="assign-t">{{ r.title || '报修工单' }}</text>
                  <text class="assign-m">{{ r.facilityType || r.aiTag || '待处理' }} · {{ statusLab(r.status) }}</text>
                </view>
                <text class="assign-go">去处理</text>
              </view>
            </view>
            <view v-else class="assign-empty">暂无指派给您的未完工单</view>
          </view>

          <view v-if="store.canPerm(PERMS.NOTICE_MANAGE)" class="card">
            <view class="sec-h">
              <text class="sec-t">社区公告</text>
              <text class="sec-m" @click="nav('/pages/notice/list')">更多 ›</text>
            </view>
            <view v-if="!noticeList.length" class="empty-sm">暂无公告</view>
            <view
              v-for="n in noticeList"
              :key="n.id"
              class="notice-row"
              @click="nav('/pages/notice/detail?id=' + n.id)"
            >
              <text class="nr-t">{{ n.title }}</text>
              <text class="nr-d">{{ n.summary || '' }}</text>
            </view>
          </view>
        </template>

        <!-- ========== 居民端首页 ========== -->
        <template v-else>
          <view class="card grid-card">
            <view class="func-row primary">
              <view v-for="item in primaryShortcuts" :key="item.path" class="func-item" @click="go(item)">
                <view class="func-block" :style="{ background: item.bg }">
                  <image class="func-svg" :src="item.icon" mode="aspectFit" />
                </view>
                <text class="func-name">{{ item.name }}</text>
              </view>
            </view>
            <view class="func-row secondary">
              <view v-for="item in secondaryShortcuts" :key="item.path" class="func-item" @click="go(item)">
                <view class="func-soft">
                  <image class="func-svg-sm" :src="item.icon" mode="aspectFit" />
                </view>
                <text class="func-name">{{ item.name }}</text>
              </view>
            </view>
          </view>

          <view class="card butler-card">
            <view class="butler-row">
              <view class="butler-avatar">AI</view>
              <view class="butler-info">
                <text class="butler-name">AI智能管家</text>
                <text class="butler-comm">{{ communityName }} · 智能管家</text>
              </view>
              <view class="btn-solid" @click="onCallButler">问管家</view>
            </view>
          </view>

          <view class="card notice-card" @click="onNoticeTap">
            <text class="notice-label-text">物业公告</text>
            <text class="notice-title">{{ latestNoticeTitle }}</text>
          </view>

          <view class="card activity-banner" @click="onActivityTap">
            <text class="act-title">{{ activityTitle }}</text>
            <text class="act-desc">{{ activityDesc }}</text>
          </view>
        </template>

        <view class="safe-bottom" />
      </view>
    </scroll-view>

    <u-popup :show="showCommunity" mode="bottom" round="24" @close="showCommunity = false">
      <view class="sheet">
        <text class="sheet-title">选择社区（仅展示）</text>
        <view v-for="t in tenants" :key="t.id" class="sheet-item" @click="pickCommunity(t)">
          <text class="sheet-name">{{ t.name }}</text>
          <text class="sheet-addr">{{ t.address }}</text>
        </view>
      </view>
    </u-popup>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { getMessages } from '../../api/auth'
import { listNotice, listActivity, listRepair, listVisit, listAppeal } from '../../api/biz'
import { FEATURES, DEMO_TENANTS } from '../../utils/constants'
import { PERMS, staffHomeShortcuts } from '../../utils/roleAccess'
import { whiteIcon, colorIcon, hopeBannerSvg } from '../../utils/hopeIcons'

const store = useUserStore()
const notices = ref([])
const messages = ref([])
const activities = ref([])
const showCommunity = ref(false)
const communityName = ref(uni.getStorageSync('communityName') || '阳光花园社区')
const tenants = DEMO_TENANTS
const statusBarPx = ref(20)
const heroBanner = hopeBannerSvg()
const pendingRepair = ref(0)
const pendingVisit = ref(0)
const pendingAppeal = ref(0)
const assignedMine = ref(0)
const assignedList = ref([])

const staffShortcuts = computed(() => staffHomeShortcuts(store.role))
const noticeList = computed(() => asList(notices.value).slice(0, 5))

const primaryShortcuts = computed(() => [
  { name: '智能管家', icon: whiteIcon('ai'), bg: 'linear-gradient(165deg,#6ed3a8,#47b686)', path: '/pages/ai/chat', tab: true, perm: PERMS.AI_CHAT },
  { name: '拍照报修', icon: whiteIcon('repair'), bg: 'linear-gradient(165deg,#7eb6f7,#4f8fe8)', path: '/pages/repair/create', feature: FEATURES.BASIC_REPAIR },
  { name: '访客预约', icon: whiteIcon('visitor'), bg: 'linear-gradient(165deg,#6ec8f0,#3aaedc)', path: '/pages/visitor/create' },
  { name: '物业缴费', icon: whiteIcon('pay'), bg: 'linear-gradient(165deg,#ffc85a,#f5a623)', path: '/pages/payment/index' }
])

const secondaryShortcuts = computed(() => [
  { name: '活动报名', icon: colorIcon('activity'), path: '/pages/activity/list' },
  { name: '社区公告', icon: colorIcon('notice'), path: '/pages/notice/list' },
  { name: '失物招领', icon: colorIcon('lost'), path: '/pages/lost/index' },
  { name: '邻里倾诉', icon: colorIcon('appeal'), path: '/pages/appeal/index' }
])

const unreadCount = computed(() => asList(messages.value).filter((m) => !(m.read_flag || m.readFlag)).length)
const latestNoticeTitle = computed(() => asList(notices.value)[0]?.title || '暂无公告')
const activityTitle = computed(() => asList(activities.value)[0]?.title || '温暖秋意 · 社区邻里日')
const activityDesc = computed(() => asList(activities.value)[0]?.summary || '报名参加精彩社区活动')

function asList(v) {
  if (Array.isArray(v)) return v
  if (v?.records) return v.records
  if (v?.list) return v.list
  return []
}

function nav(url) {
  uni.navigateTo({ url })
}

function go(item) {
  if (item.perm && !store.guardPerm(item.perm)) return
  if (item.tab) uni.switchTab({ url: item.path })
  else uni.navigateTo({ url: item.path })
}

function pickCommunity(t) {
  communityName.value = t.name
  uni.setStorageSync('communityName', t.name)
  showCommunity.value = false
  if (t.id != null && store.tenantId != null && String(t.id) !== String(store.tenantId)) {
    uni.showToast({ title: '展示已切换，数据仍属登录社区', icon: 'none' })
  }
}

function onNoticeTap() {
  const n = asList(notices.value)[0]
  if (n?.id) nav('/pages/notice/detail?id=' + n.id)
  else nav('/pages/notice/list')
}

function onActivityTap() {
  const a = asList(activities.value)[0]
  if (a?.id) nav('/pages/activity/detail?id=' + a.id)
  else nav('/pages/activity/list')
}

function onCallButler() {
  uni.switchTab({ url: '/pages/ai/chat' })
}

async function load() {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  try {
    statusBarPx.value = uni.getSystemInfoSync().statusBarHeight || 20
  } catch (e) {
    statusBarPx.value = 20
  }

  const tasks = [
    getMessages().catch(() => []),
    listActivity().catch(() => [])
  ]
  // 网格员不拉公告（后端也会 403）
  if (!store.isGridWorker) {
    tasks.push(listNotice().catch(() => []))
  }
  if (store.isStaff) {
    tasks.push(listRepair('pending').catch(() => []))
    tasks.push(listRepair({ mine: true }).catch(() => []))
    tasks.push(listVisit().catch(() => []))
    tasks.push(listAppeal().catch(() => []))
  }

  const results = await Promise.all(tasks)
  let i = 0
  messages.value = results[i++] || []
  activities.value = results[i++] || []
  if (!store.isGridWorker) {
    notices.value = results[i++] || []
  } else {
    notices.value = []
  }
  if (store.isStaff) {
    const repairs = results[i++] || []
    const mineRepairs = results[i++] || []
    const visits = results[i++] || []
    const appeals = results[i++] || []
    pendingRepair.value = asList(repairs).length
    const mineOpen = asList(mineRepairs).filter((r) => r.status !== 'done')
    assignedMine.value = mineOpen.length
    assignedList.value = mineOpen.slice(0, 5)
    pendingVisit.value = asList(visits).filter((v) => v.status === 'pending').length
    pendingAppeal.value = asList(appeals).filter((a) => a.status === 'pending' || !a.status).length
  }
}

function statusLab(s) {
  if (s === 'done') return '已完成'
  if (s === 'processing') return '处理中'
  if (s === 'pending') return '待处理'
  return s || '待处理'
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.home { min-height: 100vh; background: #f5f7f6; }
.scroll { width: 100%; }
.hero {
  position: relative;
  height: 360rpx;
  background: linear-gradient(165deg, #6ed3a8, $mint 60%, #3fad7d);
  overflow: hidden;
}
.hero.staff { height: 320rpx; }
.hero-img { width: 100%; height: 100%; }
.hero-mask {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,.15), rgba(47,182,134,.55));
}
.nav {
  position: absolute; left: 0; right: 0; top: 0;
  z-index: 2; display: flex; justify-content: space-between;
  align-items: center; padding: 12rpx 28rpx;
}
.nav-left, .nav-right { display: flex; align-items: center; gap: 8rpx; }
.community-name { color: #fff; font-size: 30rpx; font-weight: 700; }
.nav-right { position: relative; width: 56rpx; height: 56rpx; justify-content: center; }
.red-dot {
  position: absolute; right: 4rpx; top: 4rpx;
  width: 14rpx; height: 14rpx; border-radius: 50%; background: #ff4d4f;
}
.hero-role {
  position: absolute; left: 32rpx; bottom: 36rpx; z-index: 2; color: #fff;
  display: flex; flex-direction: column; gap: 8rpx;
  font-size: 34rpx; font-weight: 700;
}
.hero-sub { font-size: 24rpx; font-weight: 400; opacity: .9; }
.body { margin-top: -48rpx; padding: 0 28rpx; position: relative; z-index: 3; }
.card {
  background: #fff; border-radius: 28rpx; padding: 28rpx;
  margin-bottom: 24rpx; box-shadow: 0 8rpx 28rpx rgba(31,42,40,.05);
}
.banner-card {
  background: linear-gradient(135deg, #e8f8f3, #fff);
  border: 2rpx solid #d4efe6;
}
.ban-tag {
  display: inline-block; background: $mint; color: #fff;
  font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 8rpx; margin-bottom: 12rpx;
}
.ban-title { display: block; font-size: 32rpx; font-weight: 700; color: #1f2a28; }
.ban-desc { display: block; margin-top: 8rpx; font-size: 24rpx; color: #8a9693; }
.grid-8 { display: flex; flex-wrap: wrap; }
.g8-item {
  width: 25%; display: flex; flex-direction: column; align-items: center;
  gap: 12rpx; margin-bottom: 24rpx;
}
.g8-icon {
  width: 88rpx; height: 88rpx; border-radius: 24rpx; color: #fff;
  font-size: 30rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.g8-name { font-size: 22rpx; color: #3a4543; text-align: center; }
.todo-head { display: flex; justify-content: space-between; margin-bottom: 20rpx; }
.todo-title { font-size: 30rpx; font-weight: 700; }
.todo-more { font-size: 24rpx; color: $mint; }
.todo-nums { display: flex; justify-content: space-between; }
.todo-n { flex: 1; text-align: center; }
.num { display: block; font-size: 40rpx; font-weight: 700; color: $mint; }
.num.warn { color: #e67e22; }
.lab { font-size: 22rpx; color: #8a9693; }
.assign-box { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid #eef2f1; }
.assign-h { display: block; font-size: 24rpx; color: #64748b; margin-bottom: 12rpx; }
.assign-row {
  display: flex; align-items: center; gap: 12rpx;
  padding: 16rpx 0; border-bottom: 1rpx solid #f5f7f6;
}
.assign-row:last-child { border-bottom: none; }
.assign-dot {
  width: 14rpx; height: 14rpx; border-radius: 50%;
  background: #e67e22; flex-shrink: 0;
}
.assign-body { flex: 1; min-width: 0; }
.assign-t {
  display: block; font-size: 28rpx; color: #1f2a28; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.assign-m { display: block; margin-top: 4rpx; font-size: 22rpx; color: #8a9693; }
.assign-go { font-size: 22rpx; color: $mint; flex-shrink: 0; }
.assign-empty {
  margin-top: 16rpx; text-align: center; font-size: 24rpx; color: #94a3b8;
}
.sec-h { display: flex; justify-content: space-between; margin-bottom: 16rpx; }
.sec-t { font-size: 30rpx; font-weight: 700; }
.sec-m { font-size: 24rpx; color: #8a9693; }
.notice-row { padding: 16rpx 0; border-bottom: 1rpx solid #f0f3f2; }
.nr-t { display: block; font-size: 28rpx; color: #1f2a28; font-weight: 600; }
.nr-d {
  display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.empty-sm { text-align: center; color: #8a9693; padding: 24rpx; font-size: 26rpx; }
.func-row { display: flex; justify-content: space-between; margin-bottom: 16rpx; }
.func-item { width: 22%; display: flex; flex-direction: column; align-items: center; gap: 10rpx; }
.func-block {
  width: 96rpx; height: 96rpx; border-radius: 28rpx;
  display: flex; align-items: center; justify-content: center;
}
.func-svg { width: 48rpx; height: 48rpx; }
.func-soft { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; }
.func-svg-sm { width: 44rpx; height: 44rpx; }
.func-name { font-size: 22rpx; color: #3a4543; }
.butler-row { display: flex; align-items: center; gap: 16rpx; }
.butler-avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%;
  background: linear-gradient(145deg,#6ed3a8,$mint); color: #fff;
  font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.butler-info { flex: 1; }
.butler-name { display: block; font-size: 30rpx; font-weight: 700; }
.butler-comm { display: block; font-size: 22rpx; color: #8a9693; margin-top: 6rpx; }
.btn-solid {
  padding: 12rpx 24rpx; border-radius: 999rpx; background: $mint; color: #fff; font-size: 24rpx;
}
.notice-card, .activity-banner { display: flex; flex-direction: column; gap: 10rpx; }
.notice-label-text { font-size: 24rpx; color: $mint; font-weight: 600; }
.notice-title, .act-title { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.act-desc { font-size: 24rpx; color: #8a9693; }
.sheet { padding: 32rpx; }
.sheet-title { font-size: 32rpx; font-weight: 700; display: block; margin-bottom: 20rpx; }
.sheet-item { padding: 24rpx 0; border-bottom: 1rpx solid #f0f3f2; }
.sheet-name { display: block; font-size: 28rpx; font-weight: 600; }
.sheet-addr { font-size: 22rpx; color: #8a9693; }
.safe-bottom { height: calc(40rpx + env(safe-area-inset-bottom) + 100rpx); }
</style>
