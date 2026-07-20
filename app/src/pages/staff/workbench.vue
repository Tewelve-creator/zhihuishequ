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
        @click="go(t.path)"
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
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const pendingRepair = ref(0)
const pendingVisit = ref(0)
const pendingAppeal = ref(0)

/** 管理员：全社区业务；网格员：调解 / 访客核销 / 巡查（屏蔽公告发布、大屏） */
const menus = computed(() => {
  if (store.isGridWorker) {
    return [
      { name: '矛盾调解', letter: '调', path: '/pages/appeal/index', perm: PERMS.APPEAL_MEDIATE },
      { name: '访客核销', letter: '访', path: '/pages/visitor/list', perm: PERMS.VISITOR_VERIFY },
      { name: '巡查上报', letter: '巡', path: '/pages/staff/patrol', perm: PERMS.PATROL_REPORT },
      { name: '工单AI', letter: 'AI', tab: true, path: '/pages/ai/chat', perm: PERMS.REPAIR_AI_AUDIT },
      { name: '消息', letter: '讯', path: '/pages/message/list' }
    ]
  }
  return [
    { name: '报修工单', letter: '工', path: '/pages/repair/list', perm: PERMS.REPAIR_MANAGE },
    { name: '访客审核', letter: '访', path: '/pages/visitor/list', perm: PERMS.VISITOR_VERIFY },
    { name: '活动管理', letter: '活', path: '/pages/activity/list', perm: PERMS.ACTIVITY_MANAGE },
    { name: '情绪诉求', letter: '诉', path: '/pages/appeal/index', perm: PERMS.APPEAL_MEDIATE },
    { name: '知识库', letter: 'AI', tab: true, path: '/pages/ai/chat', perm: PERMS.KB_MANAGE },
    { name: '公告管理', letter: '告', path: '/pages/notice/list', perm: PERMS.NOTICE_MANAGE },
    { name: '数据大屏', letter: '屏', path: '/pages/staff/dashboard', perm: PERMS.DATA_DASHBOARD },
    { name: '消息', letter: '讯', path: '/pages/message/list' }
  ]
})

const todos = computed(() => {
  if (store.isGridWorker) {
    return [
      { name: '待处理诉求', count: pendingAppeal.value, path: '/pages/appeal/index' },
      { name: '待核销访客', count: pendingVisit.value, path: '/pages/visitor/list' }
    ]
  }
  return [
    { name: '待处理报修', count: pendingRepair.value, path: '/pages/repair/list' },
    { name: '待审核访客', count: pendingVisit.value, path: '/pages/visitor/list' },
    { name: '待处理诉求', count: pendingAppeal.value, path: '/pages/appeal/index' }
  ]
})

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff) return
  const [repairs, visits, appeals] = await Promise.all([
    store.canPerm(PERMS.REPAIR_MANAGE) ? listRepair('pending').catch(() => []) : Promise.resolve([]),
    listVisit().catch(() => []),
    listAppeal().catch(() => [])
  ])
  pendingRepair.value = (repairs || []).length
  pendingVisit.value = (visits || []).filter((v) => v.status === 'pending').length
  pendingAppeal.value = (appeals || []).filter((a) => a.status === 'pending' || !a.status).length
})

function go(url) {
  uni.navigateTo({ url })
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
