<template>
  <view class="page page-safe">
    <view class="card">
      <view class="section-title">{{ store.isGridWorker ? '网格员工作台' : '物业管理工作台' }}</view>
      <view class="muted">{{ store.nickname }} · {{ store.roleName }}</view>
      <view v-if="!store.isStaff" class="empty">当前账号非工作人员角色</view>
    </view>

    <view v-if="store.isStaff" class="card">
      <view class="grid-4">
        <view
          v-for="item in menus"
          :key="item.name"
          class="grid-item"
          @click="onMenu(item)"
        >
          <view class="grid-icon">{{ item.letter }}</view>
          <text class="grid-text">{{ item.name }}</text>
        </view>
      </view>
    </view>

    <view v-if="store.isStaff" class="card">
      <view class="card-title">待办摘要</view>
      <view
        v-for="t in todos"
        :key="t.name"
        class="list-item"
        @click="go(t.path, t.tab)"
      >
        <text>{{ t.name }}</text>
        <text class="tag warn">{{ t.count }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listRepair, listVisit, listAppeal } from '../../api/biz'
import { listMyInspectionTasks } from '../../api/inspection'
import { getEquipTodoSummary } from '../../api/equip'
import { getStoreTodo } from '../../api/store'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const pendingRepair = ref(0)
const pendingVisit = ref(0)
const pendingAppeal = ref(0)
const pendingInsp = ref(0)
const pendingEquip = ref(0)
const pendingStore = ref(0)

/** 管理员：全社区业务；网格员：调解 / 访客核销 / 巡查（屏蔽公告发布、大屏） */
const menus = computed(() => {
  if (store.isGridWorker) {
    return [
      { name: '矛盾调解', letter: '调', tab: true, path: '/pages/appeal/index', perm: PERMS.APPEAL_MEDIATE },
      { name: '访客核销', letter: '访', path: '/pages/visitor/list', perm: PERMS.VISITOR_VERIFY },
      { name: '巡检任务', letter: '检', path: '/pages/staff/inspection', perm: PERMS.INSPECTION_EXECUTE },
      { name: '设备维保', letter: '保', path: '/pages/staff/equip', perm: PERMS.EQUIP_MAINT },
      { name: '车辆台账', letter: '车', path: '/pages/staff/resident-cars' },
      { name: '超市订单', letter: '超', tab: true, path: '/pages/mall/index' },
      { name: '邻里倾诉', letter: '诉', tab: true, path: '/pages/appeal/index', perm: PERMS.APPEAL_MEDIATE },
      { name: '失物招领', letter: '失', path: '/pages/lost/index' },
      { name: '智能管家', letter: '智', tab: true, path: '/pages/ai/chat', perm: PERMS.KB_MANAGE },
      { name: '巡查上报', letter: '巡', path: '/pages/staff/patrol', perm: PERMS.PATROL_REPORT },
      { name: '工单AI', letter: 'AI', tab: true, path: '/pages/ai/chat', perm: PERMS.REPAIR_AI_AUDIT },
      { name: '消息', letter: '讯', path: '/pages/message/list' }
    ]
  }
  return [
    { name: '报修工单', letter: '工', path: '/pages/repair/list', perm: PERMS.REPAIR_MANAGE },
    { name: '巡检任务', letter: '检', path: '/pages/staff/inspection', perm: PERMS.INSPECTION_EXECUTE },
    { name: '设备维保', letter: '保', path: '/pages/staff/equip', perm: PERMS.EQUIP_MAINT },
    { name: '车辆台账', letter: '车', path: '/pages/staff/resident-cars' },
    { name: '超市订单', letter: '超', tab: true, path: '/pages/mall/index' },
    { name: '访客核销', letter: '访', path: '/pages/visitor/list', perm: PERMS.VISITOR_VERIFY },
    { name: '活动管理', letter: '活', path: '/pages/activity/list', perm: PERMS.ACTIVITY_MANAGE },
    { name: '邻里倾诉', letter: '诉', tab: true, path: '/pages/appeal/index', perm: PERMS.APPEAL_MEDIATE },
    { name: '失物招领', letter: '失', path: '/pages/lost/index', perm: PERMS.LOST_AUDIT },
    { name: '智能管家', letter: '智', tab: true, path: '/pages/ai/chat', perm: PERMS.KB_MANAGE },
    { name: '公告管理', letter: '告', path: '/pages/notice/list', perm: PERMS.NOTICE_MANAGE },
    { name: '数据大屏', letter: '屏', path: '/pages/staff/dashboard', perm: PERMS.DATA_DASHBOARD },
    { name: '消息', letter: '讯', path: '/pages/message/list' }
  ]
})

const todos = computed(() => {
  if (store.isGridWorker) {
    return [
      { name: '待处理诉求', count: pendingAppeal.value, path: '/pages/appeal/index', tab: true },
      { name: '待核销访客', count: pendingVisit.value, path: '/pages/visitor/list' },
      { name: '待接单超市', count: pendingStore.value, path: '/pages/mall/index', tab: true },
      { name: '待执行巡检', count: pendingInsp.value, path: '/pages/staff/inspection' },
      { name: '待维保设备', count: pendingEquip.value, path: '/pages/staff/equip' }
    ]
  }
  return [
    { name: '待处理报修', count: pendingRepair.value, path: '/pages/repair/list' },
    { name: '待核销访客', count: pendingVisit.value, path: '/pages/visitor/list' },
    { name: '待接单超市', count: pendingStore.value, path: '/pages/mall/index', tab: true },
    { name: '待处理诉求', count: pendingAppeal.value, path: '/pages/appeal/index', tab: true },
    { name: '待执行巡检', count: pendingInsp.value, path: '/pages/staff/inspection' },
    { name: '待维保设备', count: pendingEquip.value, path: '/pages/staff/equip' }
  ]
})

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff) return
  const [repairs, visits, appeals, inspPending, inspDoing, equipSum, storeSum] = await Promise.all([
    store.canPerm(PERMS.REPAIR_MANAGE) ? listRepair('pending').catch(() => []) : Promise.resolve([]),
    listVisit().catch(() => []),
    listAppeal().catch(() => []),
    listMyInspectionTasks('PENDING').catch(() => []),
    listMyInspectionTasks('DOING').catch(() => []),
    getEquipTodoSummary().catch(() => ({ todoCount: 0 })),
    getStoreTodo().catch(() => ({ pendingAccept: 0 }))
  ])
  pendingRepair.value = (repairs || []).length
  pendingVisit.value = (visits || []).filter((v) => v.status === 'approved').length
  pendingAppeal.value = (appeals || []).filter((a) => a.status === 'pending' || !a.status).length
  pendingInsp.value = (inspPending || []).length + (inspDoing || []).length
  pendingEquip.value = Number(equipSum?.todoCount ?? 0)
  pendingStore.value = Number(storeSum?.pendingAccept ?? 0)
})

function go(url, tab) {
  if (tab) uni.switchTab({ url })
  else uni.navigateTo({ url })
}

function onMenu(item) {
  if (item.perm && !store.guardPerm(item.perm)) return
  if (item.tab) {
    uni.switchTab({ url: item.path })
  } else {
    go(item.path)
  }
}
</script>

<style lang="scss" scoped></style>
