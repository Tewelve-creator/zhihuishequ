<template>
  <el-container class="admin-layout" :class="{ 'is-screen': isScreen }">
    <el-aside v-if="!isScreen" :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span v-if="!collapsed">{{ portalTitle }}</span>
        <span v-else>智</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          background-color="#001529"
          text-color="#ffffffa6"
          active-text-color="#fff"
          router
          :unique-opened="true"
        >
          <template v-for="item in menus" :key="item.title">
            <el-sub-menu v-if="item.children?.length" :index="item.title">
              <template #title>
                <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path!">
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path!">
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header v-if="!isScreen" class="header">
        <div class="left">
          <el-icon class="fold" @click="collapsed = !collapsed"><Fold /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ portalTitle }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right">
          <el-tag v-if="leaseTip" type="warning" size="small">{{ leaseTip }}</el-tag>
          <el-tag size="small">{{ user.communityName }}</el-tag>
          <span class="user">{{ user.username }}</span>
          <el-button type="danger" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main" :class="{ 'main-screen': isScreen }">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { adminMenus, filterMenusByFeature, propertyMenus } from '@/config/menus'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const collapsed = ref(false)

const menus = computed(() => {
  const raw = user.portal === 'admin' ? adminMenus : propertyMenus
  // 运营端看全量；物业端按套餐隐藏无权限菜单
  if (user.portal === 'admin') return raw
  return filterMenusByFeature(raw, (code) => user.can(code))
})
const portalTitle = computed(() => (user.portal === 'admin' ? '运营管理端' : '物业工作端'))
const isScreen = computed(() => Boolean(route.meta.screen))
const leaseTip = computed(() => {
  if (user.tenantStatus === 'degraded') return '套餐已到期降级：仅公告/报修可用'
  if (user.tenantStatus === 'frozen') return '租户已冻结，请续费解锁'
  return ''
})

function onLogout() {
  user.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { height: 100%; }
.aside {
  background: #001529;
  transition: width .2s;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: .04em;
  border-bottom: 1px solid #ffffff14;
  flex-shrink: 0;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
}
.left, .right { display: flex; align-items: center; gap: 12px; }
.fold { cursor: pointer; font-size: 18px; }
.user { color: #666; }
.main { background: #f0f2f5; }
.main-screen {
  padding: 0 !important;
  background: #020b1a;
  overflow: hidden;
}
:deep(.el-menu) { border-right: none; }
</style>
