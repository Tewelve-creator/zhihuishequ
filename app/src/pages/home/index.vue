<template>
  <view class="home">
    <scroll-view scroll-y class="scroll">
      <!-- 顶栏 -->
      <view class="hero" :class="{ staff: store.isStaff }">
        <image class="hero-img" :src="heroBanner" mode="aspectFill" />
        <view class="hero-mask" :class="{ staff: store.isStaff }" />
        <view class="nav" :style="{ paddingTop: statusBarPx + 'px' }">
          <view class="nav-left">
            <HouseSwitcher v-if="!store.isStaff" @change="onHouseChange" />
            <view v-else class="community-row" @click="showCommunity = true">
              <text class="community-name">{{ communityName }}</text>
              <u-icon name="arrow-down" color="#ffffff" size="12" />
            </view>
          </view>
          <view class="nav-right" @click="nav('/pages/message/list')">
            <u-icon name="bell" color="#ffffff" size="22" />
            <view v-if="unreadCount > 0" class="red-dot" />
          </view>
        </view>
        <view v-if="store.isStaff" class="hero-role">
          <text class="hero-hello">你好，{{ store.nickname || '同事' }}</text>
          <text class="hero-sub">{{ store.isGridWorker ? '网格员工作台' : '物业管理工作台' }} · {{ store.roleName }}</text>
        </view>
      </view>

      <view class="body" :class="{ staff: store.isStaff }">
        <!-- ========== 物业端首页 ========== -->
        <template v-if="store.isStaff">
          <!-- 待办总览 -->
          <view class="card staff-todo-card">
            <view class="todo-head" @click="nav('/pages/staff/todo')">
              <view>
                <text class="todo-title">今日待办</text>
                <text class="todo-sub">优先处理指派与临近事项</text>
              </view>
              <text class="todo-more">全部 ›</text>
            </view>
            <view class="todo-chips">
              <view class="todo-chip warn" @click="nav('/pages/staff/todo?tab=repair')">
                <text class="chip-num">{{ assignedMine }}</text>
                <text class="chip-lab">指派给我</text>
              </view>
              <view class="todo-chip" @click="nav('/pages/staff/todo?tab=repair')">
                <text class="chip-num">{{ pendingRepair }}</text>
                <text class="chip-lab">待接报修</text>
              </view>
              <view class="todo-chip" @click="nav('/pages/staff/todo?tab=visit')">
                <text class="chip-num">{{ pendingVisit }}</text>
                <text class="chip-lab">待核销访客</text>
              </view>
              <view class="todo-chip" :class="{ warn: pendingInsp > 0 }" @click="nav('/pages/staff/todo?tab=insp')">
                <text class="chip-num">{{ pendingInsp }}</text>
                <text class="chip-lab">待执行巡检</text>
              </view>
              <view class="todo-chip" :class="{ warn: pendingEquip > 0 }" @click="nav('/pages/staff/todo?tab=equip')">
                <text class="chip-num">{{ pendingEquip }}</text>
                <text class="chip-lab">设备维保</text>
              </view>
              <view class="todo-chip" @click="go({ path: '/pages/appeal/index', tab: true })">
                <text class="chip-num">{{ pendingAppeal }}</text>
                <text class="chip-lab">邻里诉求</text>
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
            <view v-else-if="inspTodoList.length" class="assign-box">
              <text class="assign-h">待执行巡检</text>
              <view
                v-for="t in inspTodoList"
                :key="'insp' + (t.id || t.title)"
                class="assign-row"
                @click="goInspDetail(t)"
              >
                <view class="assign-dot mint" />
                <view class="assign-body">
                  <text class="assign-t">{{ t.title || '巡检任务' }}</text>
                  <text class="assign-m">
                    {{ t.statusLabel }} · {{ t.pointDone || 0 }}/{{ t.pointTotal || 0 }} 点位
                  </text>
                </view>
                <text class="assign-go">去打卡</text>
              </view>
            </view>
            <view v-else-if="equipTodoList.length" class="assign-box">
              <text class="assign-h">临近/逾期设备维保</text>
              <view
                v-for="d in equipTodoList"
                :key="'eq' + d.id"
                class="assign-row"
                @click="nav('/pages/staff/equip-detail?id=' + d.id)"
              >
                <view class="assign-dot" :class="{ mint: d.status !== 'OVERDUE' }" />
                <view class="assign-body">
                  <text class="assign-t">{{ d.name }}</text>
                  <text class="assign-m">
                    {{ d.statusLabel }} · 下次 {{ String(d.nextMaintDate || '').substring(0, 10) }}
                  </text>
                </view>
                <text class="assign-go">去登记</text>
              </view>
            </view>
            <view v-else class="assign-empty">当前没有紧急待办，继续保持～</view>
          </view>

          <!-- 工作台入口 -->
          <view class="card staff-menu-card">
            <view class="sec-h">
              <text class="sec-t">工作台</text>
              <text class="sec-m">常用业务入口</text>
            </view>
            <view class="staff-grid">
              <view
                v-for="item in staffShortcuts"
                :key="item.name"
                class="staff-item"
                @click="go(item)"
              >
                <view class="func-block" :style="{ background: item.bg }">
                  <image class="func-svg" :src="item.icon" mode="aspectFit" />
                </view>
                <text class="func-name">{{ item.name }}</text>
              </view>
            </view>
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
          <!-- 物业催缴站内提醒 -->
          <view
            v-if="feeRemindMsg"
            class="card fee-remind-card"
            @click="openFeeRemind"
          >
            <view class="fee-remind-left">
              <view class="fee-remind-icon">催</view>
              <view class="fee-remind-info">
                <text class="fee-remind-title">{{ feeRemindMsg.title || '缴费催缴提醒' }}</text>
                <text class="fee-remind-desc">{{ feeRemindPreview }}</text>
              </view>
            </view>
            <view class="fee-remind-btn">去查看</view>
          </view>

          <!-- 欠费提醒 -->
          <view
            v-if="feeSummary.unpaidCount > 0"
            class="card arrears-card"
            :class="{ overdue: feeSummary.overdueCount > 0 }"
            @click="goPay"
          >
            <view class="arrears-left">
              <view class="arrears-icon">欠</view>
              <view class="arrears-info">
                <text class="arrears-title">
                  {{ feeSummary.overdueCount > 0 ? '您有逾期未缴费用' : '您有待缴物业费' }}
                </text>
                <text class="arrears-desc">
                  {{ feeSummary.unpaidCount }} 笔 · 合计 ¥{{ formatFee(feeSummary.unpaidAmount) }}
                  <text v-if="feeSummary.earliestDueDate"> · 最早 {{ feeSummary.earliestDueDate }}</text>
                </text>
              </view>
            </view>
            <view class="arrears-btn">去缴费</view>
          </view>

          <!-- 功能宫格 3×4 -->
          <view class="card grid-card">
            <view class="func-grid">
              <view
                v-for="item in residentShortcuts"
                :key="item.name + item.path"
                class="func-item"
                @click="go(item)"
              >
                <view class="func-block" :style="{ background: item.bg }">
                  <image class="func-svg" :src="item.icon" mode="aspectFit" />
                  <view v-if="item.path === '/pages/payment/index' && feeSummary.unpaidCount > 0" class="func-badge">
                    {{ feeSummary.unpaidCount > 99 ? '99+' : feeSummary.unpaidCount }}
                  </view>
                </view>
                <text class="func-name">{{ item.name }}</text>
              </view>
            </view>
          </view>

          <!-- 活动通知 -->
          <view class="sec-divider">
            <view class="sec-line" />
            <text class="sec-lab">活动通知</text>
            <view class="sec-line" />
          </view>

          <view class="notify-row">
            <view class="act-card" @click="onActivityTap">
              <image
                v-if="activityCover"
                class="act-cover"
                :src="activityCover"
                mode="aspectFill"
              />
              <view v-else class="act-cover act-cover-ph">
                <text class="act-ph-t">活动</text>
              </view>
              <view class="act-body">
                <text class="act-title">{{ activityTitle }}</text>
                <text class="act-meta">{{ activityMeta }}</text>
                <view class="act-btn" @click.stop="onActivityTap">立即参与</view>
              </view>
            </view>
            <view class="notice-side" @click="nav('/pages/notice/list')">
              <text class="ns-h">公告</text>
              <text class="ns-t">{{ noticeList[0]?.title || '物业公告' }}</text>
              <text class="ns-t sub">{{ noticeList[1]?.title || '点击查看更多' }}</text>
            </view>
          </view>

          <view class="card notice-banner" @click="onNoticeTap">
            <text class="nb-lab">公告</text>
            <text class="nb-txt">{{ latestNoticeTitle }}</text>
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
import { useHouseStore } from '../../store/house'
import HouseSwitcher from '../../components/HouseSwitcher.vue'
import { getMessages, listPublicTenants, readMessage } from '../../api/auth'
import { listNotice, listActivity, listRepair, listVisit, listAppeal } from '../../api/biz'
import { getInspectionTodoSummary, listMyInspectionTasks } from '../../api/inspection'
import { getEquipTodoSummary, listEquipDue } from '../../api/equip'
import { getFeeSummary, formatMoney } from '../../api/payment'
import { FEATURES, DEMO_TENANTS } from '../../utils/constants'
import { PERMS, staffHomeShortcuts } from '../../utils/roleAccess'
import { whiteIcon, hopeBannerSvg } from '../../utils/hopeIcons'
import { getBaseUrl } from '../../utils/request'

const store = useUserStore()
const houseStore = useHouseStore()
const notices = ref([])
const messages = ref([])
const activities = ref([])
const showCommunity = ref(false)
const communityName = ref(uni.getStorageSync('communityName') || '阳光花园社区')
const tenants = ref([...DEMO_TENANTS])
const statusBarPx = ref(20)
const heroBanner = hopeBannerSvg()
const pendingRepair = ref(0)
const pendingVisit = ref(0)
const pendingAppeal = ref(0)
const pendingInsp = ref(0)
const pendingEquip = ref(0)
const assignedMine = ref(0)
const assignedList = ref([])
const inspTodoList = ref([])
const equipTodoList = ref([])
const feeSummary = ref({
  unpaidAmount: 0,
  unpaidCount: 0,
  earliestDueDate: null,
  overdueCount: 0
})

function formatFee(n) {
  return formatMoney(n)
}

function goPay() {
  if (!houseStore.guardBound()) return
  nav('/pages/payment/index')
}

async function openFeeRemind() {
  const m = feeRemindMsg.value
  if (m?.id && !(m.readFlag || m.read_flag)) {
    await readMessage(m.id).catch(() => {})
    m.readFlag = 1
    m.read_flag = 1
  }
  if (!houseStore.guardBound()) return
  nav('/pages/payment/index')
}

async function loadTenants() {
  try {
    const list = await listPublicTenants()
    if (Array.isArray(list) && list.length) {
      tenants.value = list
      // 用真实租户名纠正本地缓存（如新开通社区）
      const cur = list.find((t) => String(t.id) === String(store.tenantId))
      if (cur?.name) {
        communityName.value = cur.name
        uni.setStorageSync('communityName', cur.name)
      }
    }
  } catch {
    /* ignore */
  }
}

const houseLabel = computed(() => houseStore.label || communityName.value)

/** 需绑定房屋后才能进入的居民功能 */
const HOUSE_REQUIRED = [
  '/pages/repair/create',
  '/pages/repair/list',
  '/pages/visitor/create',
  '/pages/visitor/list',
  '/pages/payment/index',
  '/pages/feedback/index',
  '/pages/neighbor/index',
]

const staffShortcuts = computed(() =>
  staffHomeShortcuts(store.role).map((i) => ({
    ...i,
    icon: whiteIcon(i.iconKey || 'ai'),
  })),
)
const noticeList = computed(() => asList(notices.value).slice(0, 5))

/** 居民首页 3×4 功能入口（对齐设计稿） */
const residentShortcuts = computed(() => [
  { name: '智能管家', icon: whiteIcon('ai'), bg: 'linear-gradient(165deg,#6ed3a8,#47b686)', path: '/pages/ai/chat', tab: true, perm: PERMS.AI_CHAT, feature: FEATURES.AI_CHAT },
  { name: '访客预约', icon: whiteIcon('visitor'), bg: 'linear-gradient(165deg,#6ec8f0,#3aaedc)', path: '/pages/visitor/list' },
  { name: '物业缴费', icon: whiteIcon('pay'), bg: 'linear-gradient(165deg,#ffc85a,#f5a623)', path: '/pages/payment/index' },
  { name: '拍照报修', icon: whiteIcon('camera'), bg: 'linear-gradient(165deg,#7eb6f7,#4f8fe8)', path: '/pages/repair/create', feature: FEATURES.BASIC_REPAIR },
  { name: '情绪调解', icon: whiteIcon('emotion'), bg: 'linear-gradient(165deg,#8aa4f0,#5b7de0)', path: '/pages/emotion/index', feature: FEATURES.AI_EMOTION },
  { name: '失物招领', icon: whiteIcon('lost'), bg: 'linear-gradient(165deg,#6ed3a8,#47b686)', path: '/pages/lost/index', feature: FEATURES.AI_LOST_MATCH },
  { name: '公告通知', icon: whiteIcon('notice'), bg: 'linear-gradient(165deg,#7eb6f7,#4f8fe8)', path: '/pages/notice/list', feature: FEATURES.BASIC_NOTICE },
  { name: '邻里互助', icon: whiteIcon('help'), bg: 'linear-gradient(165deg,#6ed3a8,#3fad7d)', path: '/pages/neighbor/index' },
  { name: '社区商城', icon: whiteIcon('mall'), bg: 'linear-gradient(165deg,#ffb86c,#f08a3a)', path: '/pages/mall/index', tab: true, feature: FEATURES.VENDOR_MANAGE },
  { name: '邻里圈', icon: whiteIcon('appeal'), bg: 'linear-gradient(165deg,#7ed6b0,#47b686)', path: '/pages/appeal/index', tab: true },
  { name: '活动报名', icon: whiteIcon('activity'), bg: 'linear-gradient(165deg,#ffc85a,#f0a04b)', path: '/pages/activity/list' },
  { name: '意见反馈', icon: whiteIcon('feedback'), bg: 'linear-gradient(165deg,#6ed3a8,#47b686)', path: '/pages/feedback/index' },
])

const unreadCount = computed(() => asList(messages.value).filter((m) => !(m.read_flag || m.readFlag)).length)
const feeRemindMsg = computed(() =>
  asList(messages.value).find((m) => {
    if (m.read_flag || m.readFlag) return false
    const typ = m.msgType || m.msg_type || ''
    const title = String(m.title || '')
    return typ === 'fee_remind' || title.includes('催缴') || title.includes('缴纳提醒') || title.includes('物业费')
  }) || null,
)
const feeRemindPreview = computed(() => {
  const c = String(feeRemindMsg.value?.content || '').replace(/\s+/g, ' ').trim()
  return c.length > 36 ? c.slice(0, 36) + '…' : c || '物业已向您发送缴费催缴通知'
})
const latestNoticeTitle = computed(() => asList(notices.value)[0]?.title || '暂无公告，点击查看公告列表')
const activityTitle = computed(() => asList(activities.value)[0]?.title || '温暖秋意 · 社区邻里日')
const activityDesc = computed(() => asList(activities.value)[0]?.summary || '报名参加精彩社区活动')
const activityCover = computed(() => {
  const a = asList(activities.value)[0]
  const u = a?.coverUrl || a?.cover_url
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || u.startsWith('data:')) return u
  const base = getBaseUrl() || ''
  return u.startsWith('/') ? base + u : base + '/' + u
})
const activityMeta = computed(() => {
  const a = asList(activities.value)[0]
  if (!a) return '精彩活动等你来'
  const t = String(a.startTime || a.start_time || '').replace('T', ' ').slice(5, 16)
  const loc = a.location || '社区活动室'
  return [t || '近期', loc].filter(Boolean).join(' · ')
})

function asList(v) {
  if (Array.isArray(v)) return v
  if (v?.records) return v.records
  if (v?.list) return v.list
  return []
}

function nav(url) {
  uni.navigateTo({ url })
}

function goInspDetail(t) {
  const id = t?.id ?? t?.taskId
  if (id == null || id === '' || String(id) === 'undefined') {
    uni.showToast({ title: '任务数据异常，请下拉刷新', icon: 'none' })
    return
  }
  nav('/pages/staff/inspection-detail?id=' + id)
}

function go(item) {
  if (item.perm && !store.guardPerm(item.perm)) return
  if (item.feature && store.guardFeature && !store.guardFeature(item.feature)) return
  if (
    !store.isStaff &&
    HOUSE_REQUIRED.some((p) => item.path && item.path.startsWith(p.replace(/\?.*$/, '')))
  ) {
    if (!houseStore.guardBound()) return
  }
  if (item.tab) uni.switchTab({ url: item.path })
  else uni.navigateTo({ url: item.path })
}

function onHouseChange() {
  communityName.value =
    houseStore.currentHouse?.communityName || uni.getStorageSync('communityName') || communityName.value
  load()
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

  await loadTenants()

  if (!store.isStaff) {
    await houseStore.refresh()
    const cn = houseStore.currentHouse?.communityName
    if (cn && !/[?？]/.test(String(cn))) {
      communityName.value = cn
      uni.setStorageSync('communityName', cn)
    } else if (/[?？]/.test(String(communityName.value || ''))) {
      const hit = tenants.value.find((t) => String(t.id) === String(store.tenantId))
      communityName.value = hit?.name || '智慧社区'
      uni.setStorageSync('communityName', communityName.value)
    }
    // 欠费汇总：未绑定房屋时清空提醒
    if (houseStore.hasHouse) {
      try {
        const sum = await getFeeSummary(houseStore.currentHouseId || houseStore.currentHouse?.id)
        feeSummary.value = {
          unpaidAmount: Number(sum?.unpaidAmount) || 0,
          unpaidCount: Number(sum?.unpaidCount) || 0,
          earliestDueDate: sum?.earliestDueDate || null,
          overdueCount: Number(sum?.overdueCount) || 0
        }
      } catch {
        feeSummary.value = { unpaidAmount: 0, unpaidCount: 0, earliestDueDate: null, overdueCount: 0 }
      }
    } else {
      feeSummary.value = { unpaidAmount: 0, unpaidCount: 0, earliestDueDate: null, overdueCount: 0 }
    }
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
    tasks.push(getInspectionTodoSummary().catch(() => ({ todoCount: 0 })))
    tasks.push(listMyInspectionTasks('PENDING', 'assigned').catch(() => []))
    tasks.push(listMyInspectionTasks('DOING', 'assigned').catch(() => []))
    tasks.push(getEquipTodoSummary().catch(() => ({ todoCount: 0 })))
    tasks.push(listEquipDue().catch(() => []))
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
    const inspSummary = results[i++] || {}
    const inspPending = results[i++] || []
    const inspDoing = results[i++] || []
    const equipSummary = results[i++] || {}
    const equipDue = results[i++] || []
    pendingRepair.value = asList(repairs).length
    const mineOpen = asList(mineRepairs).filter((r) => r.status !== 'done')
    assignedMine.value = mineOpen.length
    assignedList.value = mineOpen.slice(0, 5)
    pendingVisit.value = asList(visits).filter((v) => v.status === 'approved').length
    pendingAppeal.value = asList(appeals).filter((a) => a.status === 'pending' || !a.status).length
    pendingInsp.value = Number(inspSummary.todoCount ?? 0)
    const merged = [...asList(inspDoing), ...asList(inspPending)].filter(
      (t) => t && (t.id != null || t.taskId != null)
    )
    inspTodoList.value = merged.slice(0, 5)
    pendingEquip.value = Number(equipSummary.todoCount ?? 0)
    equipTodoList.value = asList(equipDue).slice(0, 5)
  } else {
    pendingInsp.value = 0
    inspTodoList.value = []
    pendingEquip.value = 0
    equipTodoList.value = []
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
/* Tab 页：高度扣掉底栏，避免快捷入口被裁切；H5 预览框已让出 Tab 时 --window-bottom=0 */
.home {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: var(--window-bottom, 50px);
  background: #f5f7f6;
  overflow: hidden;
  box-sizing: border-box;
}
.scroll { width: 100%; height: 100%; }
.hero {
  position: relative;
  height: 380rpx;
  background: linear-gradient(165deg, #7ed6b0, $mint 60%, #3fad7d);
  overflow: hidden;
}
.hero.staff { height: 380rpx; }
.hero-img { width: 100%; height: 100%; }
.hero-mask {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(71,182,134,.08), rgba(71,182,134,.28));
}
.hero-mask.staff {
  background: linear-gradient(180deg, rgba(20,40,36,.35), rgba(31,90,70,.72));
}
.nav {
  position: absolute; left: 0; right: 0; top: 0;
  z-index: 2; display: flex; justify-content: space-between;
  align-items: center; padding: 12rpx 28rpx;
}
.nav-left, .nav-right { display: flex; align-items: center; gap: 8rpx; }
.nav-left { flex: 1; min-width: 0; }
.community-row { display: flex; align-items: center; gap: 8rpx; }
.community-name { color: #fff; font-size: 30rpx; font-weight: 700; }
.nav-right { position: relative; width: 56rpx; height: 56rpx; justify-content: center; }
.red-dot {
  position: absolute; right: 4rpx; top: 4rpx;
  width: 14rpx; height: 14rpx; border-radius: 50%; background: #ff4d4f;
}
.hero-role {
  position: absolute; left: 32rpx; right: 32rpx; bottom: 56rpx; z-index: 2; color: #fff;
  display: flex; flex-direction: column; gap: 8rpx;
}
.hero-hello {
  font-size: 40rpx;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 1rpx;
}
.hero-sub {
  font-size: 24rpx;
  font-weight: 400;
  opacity: .9;
  line-height: 1.4;
}
.body { margin-top: -48rpx; padding: 0 28rpx; position: relative; z-index: 3; }
.body.staff { margin-top: -36rpx; }
.card {
  background: #fff; border-radius: 28rpx; padding: 28rpx;
  margin-bottom: 24rpx; box-shadow: 0 8rpx 28rpx rgba(31,42,40,.05);
}
.arrears-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff7ed, #fffbeb);
  border: 2rpx solid #fed7aa;
}
.fee-remind-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #fff1f2, #fdf2f8);
  border: 2rpx solid #fecdd3;
}
.fee-remind-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex: 1;
  min-width: 0;
}
.fee-remind-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: linear-gradient(165deg, #fb7185, #e11d48);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.fee-remind-info { flex: 1; min-width: 0; }
.fee-remind-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #9f1239;
}
.fee-remind-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #be123c;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fee-remind-btn {
  flex-shrink: 0;
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
  background: #e11d48;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}
.arrears-card.overdue {
  background: linear-gradient(135deg, #fff1f2, #fff7ed);
  border-color: #fecaca;
}
.arrears-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex: 1;
  min-width: 0;
}
.arrears-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: linear-gradient(165deg, #fb923c, #ea580c);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.arrears-card.overdue .arrears-icon {
  background: linear-gradient(165deg, #fb7185, #e11d48);
}
.arrears-info { flex: 1; min-width: 0; }
.arrears-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #9a3412;
}
.arrears-card.overdue .arrears-title { color: #9f1239; }
.arrears-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #c2410c;
  line-height: 1.35;
}
.arrears-card.overdue .arrears-desc { color: #be123c; }
.arrears-btn {
  flex-shrink: 0;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: linear-gradient(165deg, #fb923c, #ea580c);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
}
.arrears-card.overdue .arrears-btn {
  background: linear-gradient(165deg, #fb7185, #e11d48);
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
.grid-9 {
  display: flex;
  flex-wrap: wrap;
  padding-bottom: 8rpx;
  overflow: visible;
  box-sizing: border-box;
}
.g9-item {
  width: 33.333%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
  padding: 0 6rpx;
}
.g9-item:nth-last-child(-n + 3) { margin-bottom: 8rpx; }
.g9-icon {
  width: 92rpx; height: 92rpx; border-radius: 26rpx; color: #fff;
  font-size: 30rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8rpx 18rpx rgba(47, 182, 134, 0.12);
}
.g9-name {
  font-size: 22rpx;
  color: #3a4543;
  text-align: center;
  line-height: 1.3;
  width: 100%;
}
.todo-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20rpx; }
.todo-title { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.todo-more { font-size: 24rpx; color: $mint; padding-top: 4rpx; }
.todo-nums { display: flex; justify-content: space-between; }
.todo-nums-4 .todo-n { padding: 0 4rpx; }
.todo-nums-4 .num { font-size: 36rpx; }
.todo-nums-4 .lab { font-size: 20rpx; }
.todo-nums-5 .todo-n { padding: 0 2rpx; flex: 1; }
.todo-nums-5 .num { font-size: 32rpx; }
.todo-nums-5 .lab { font-size: 18rpx; }
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
.assign-dot.mint { background: $mint; }
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

/* 物业工作台 */
.staff-todo-card { padding-bottom: 20rpx; }
.todo-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a9693;
  font-weight: 400;
}
.todo-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 8rpx;
}
.todo-chip {
  width: calc((100% - 32rpx) / 3);
  box-sizing: border-box;
  padding: 20rpx 12rpx;
  border-radius: 20rpx;
  background: #f4faf7;
  text-align: center;
}
.todo-chip.warn {
  background: linear-gradient(160deg, #fff7ed, #ffedd5);
}
.chip-num {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: $mint;
  line-height: 1.2;
}
.todo-chip.warn .chip-num { color: #ea580c; }
.chip-lab {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #64748b;
}
.staff-menu-card .sec-m {
  color: #94a3b8;
  font-size: 22rpx;
}
.staff-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 8rpx -6rpx 0;
}
.staff-item {
  width: 25%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 6rpx 20rpx;
}
.staff-item .func-name {
  text-align: center;
  line-height: 1.3;
}

.func-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -8rpx;
}
.func-item {
  width: 25%;
  box-sizing: border-box;
  padding: 12rpx 8rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}
.func-block {
  position: relative;
  width: 96rpx; height: 96rpx; border-radius: 28rpx;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8rpx 18rpx rgba(47, 182, 134, 0.12);
}
.func-badge {
  position: absolute;
  top: -8rpx;
  right: -10rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #ef4444;
  color: #fff;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
  box-sizing: border-box;
  border: 2rpx solid #fff;
}
.func-svg { width: 48rpx; height: 48rpx; }
.func-name { font-size: 22rpx; color: #3a4543; text-align: center; line-height: 1.3; }

.sec-divider {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 8rpx 0 20rpx;
  padding: 0 12rpx;
}
.sec-line { flex: 1; height: 1rpx; background: #d8e8e1; }
.sec-lab { font-size: 26rpx; color: #64748b; font-weight: 600; letter-spacing: 2rpx; }

.notify-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.act-card {
  flex: 1.35;
  background: #fff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(31,42,40,.05);
  display: flex;
  flex-direction: column;
}
.act-cover { width: 100%; height: 160rpx; background: #e8f7f0; }
.act-cover-ph {
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(145deg, #dff5ea, #b8e6d0);
}
.act-ph-t { color: #47b686; font-size: 28rpx; font-weight: 700; }
.act-body { padding: 16rpx 18rpx 20rpx; display: flex; flex-direction: column; gap: 8rpx; }
.act-title {
  font-size: 28rpx; font-weight: 700; color: #1f2a28;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.act-meta { font-size: 20rpx; color: #8a9693; line-height: 1.4; }
.act-btn {
  margin-top: 8rpx;
  align-self: flex-start;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #47b686;
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
}
.notice-side {
  flex: 1;
  background: #fff;
  border-radius: 24rpx;
  padding: 20rpx 18rpx;
  box-shadow: 0 8rpx 24rpx rgba(31,42,40,.05);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.ns-h { font-size: 26rpx; font-weight: 700; color: #1f2a28; }
.ns-t {
  font-size: 22rpx; color: #475569; line-height: 1.4;
  overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.ns-t.sub { color: #94a3b8; -webkit-line-clamp: 2; }

.notice-banner {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 22rpx 24rpx !important;
}
.nb-lab {
  flex-shrink: 0;
  font-size: 22rpx;
  font-weight: 700;
  color: #47b686;
  background: #ecfdf5;
  padding: 6rpx 12rpx;
  border-radius: 8rpx;
}
.nb-txt {
  flex: 1;
  font-size: 26rpx;
  color: #1f2a28;
  line-height: 1.45;
  font-weight: 500;
}

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
.sheet { padding: 32rpx; }
.sheet-title { font-size: 32rpx; font-weight: 700; display: block; margin-bottom: 20rpx; }
.sheet-item { padding: 24rpx 0; border-bottom: 1rpx solid #f0f3f2; }
.sheet-name { display: block; font-size: 28rpx; font-weight: 600; }
.sheet-addr { font-size: 22rpx; color: #8a9693; }
/* 内容区已扣 Tab 高度，这里只留底部呼吸间距 */
.safe-bottom { height: calc(40rpx + constant(safe-area-inset-bottom)); height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
