<template>
  <el-container class="admin-layout" :class="{ 'is-screen': isScreen, 'is-merchant': isMerchant }">
    <el-aside v-if="!isScreen" :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span v-if="!collapsed">{{ portalTitle }}</span>
        <span v-else>智</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :background-color="isMerchant ? '#0f766e' : '#001529'"
          :text-color="isMerchant ? '#ecfdf5' : '#ffffffa6'"
          :active-text-color="isMerchant ? '#fff' : '#fff'"
          :router="false"
          :unique-opened="true"
          @select="onMenuSelect"
        >
          <template v-for="item in menus" :key="item.title">
            <el-sub-menu v-if="item.children?.length" :index="item.title">
              <template #title>
                <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                <span :class="{ 'menu-locked-text': item.locked }">{{ item.title }}</span>
                <el-icon v-if="item.locked" class="menu-lock"><Lock /></el-icon>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.path"
                :index="child.path!"
                :class="{ 'is-feature-locked': child.locked }"
              >
                <el-tooltip
                  :disabled="!child.locked"
                  content="当前套餐未包含此功能"
                  placement="right"
                >
                  <span class="menu-item-inner">
                    <span>{{ child.title }}</span>
                    <el-icon v-if="child.locked" class="menu-lock"><Lock /></el-icon>
                  </span>
                </el-tooltip>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item
              v-else
              :index="item.path!"
              :class="{ 'is-feature-locked': item.locked }"
            >
              <el-tooltip
                :disabled="!item.locked"
                content="当前套餐未包含此功能"
                placement="right"
              >
                <span class="menu-item-inner">
                  <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
                  <span>{{ item.title }}</span>
                  <el-icon v-if="item.locked" class="menu-lock"><Lock /></el-icon>
                </span>
              </el-tooltip>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header v-if="!isScreen" class="header">
        <div class="left">
          <el-icon class="fold" @click="collapsed = !collapsed"><Fold /></el-icon>
          <template v-if="isMerchant">
            <el-input
              class="shop-search"
              :model-value="shopLabel"
              readonly
              placeholder="当前店铺"
            >
              <template #prefix>
                <el-icon><Shop /></el-icon>
              </template>
            </el-input>
          </template>
          <el-breadcrumb v-else separator="/">
            <el-breadcrumb-item>{{ portalTitle }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right">
          <el-tag v-if="leaseTip" type="warning" size="small">{{ leaseTip }}</el-tag>

          <!-- 社区切换 + 套餐标识（物业端） -->
          <template v-if="isProperty">
            <el-select
              v-if="communities.length > 1"
              :model-value="Number(user.tenantId) || undefined"
              class="community-select"
              size="small"
              placeholder="切换社区"
              :loading="switching || user.featureLoading"
              @change="onSwitchCommunity"
            >
              <el-option
                v-for="c in communities"
                :key="c.id"
                :label="c.name"
                :value="c.id"
              >
                <span>{{ c.name }}</span>
                <span class="opt-pkg">{{ c.packageName || '' }}</span>
              </el-option>
            </el-select>
            <span v-else class="community-label">{{ user.communityName }}</span>
            <span :class="packageTagClass(user.packageCode)">
              {{ user.packageName || '当前套餐' }}
            </span>
          </template>
          <el-tag v-else-if="!isMerchant" size="small">{{ user.communityName }}</el-tag>

          <MerchantMessageBell v-if="isMerchant" />
          <span v-if="isMerchant" class="shop-name">{{ shopLabel }}</span>
          <span class="user">{{ isMerchant ? '管理员' : user.username }}</span>
          <el-button :type="isMerchant ? 'primary' : 'danger'" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main" :class="{ 'main-screen': isScreen, 'main-merchant': isMerchant }">
        <div v-if="user.featureLoading" class="feature-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在切换社区权限…</span>
        </div>
        <router-view v-else />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Shop, Lock, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  adminMenus,
  filterMenusByFeature,
  merchantMenus,
  propertyMenus,
  type MenuItem,
} from '@/config/menus'
import { DEMO_COMMUNITY_PACKAGES, packageTagClass } from '@/config/features'
import { getMyMallShop } from '@/api/mall'
import { getMyLease, listMyCommunitiesApi, switchCommunityApi } from '@/api/auth'
import MerchantMessageBell from '@/components/MerchantMessageBell.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const collapsed = ref(false)
const shopName = ref('')
const switching = ref(false)
const communities = ref<
  Array<{ id: number; name: string; packageCode?: string; packageName?: string }>
>([])

const isMerchant = computed(() => user.portal === 'merchant')
const isProperty = computed(() => user.portal === 'property')
const menus = computed(() => {
  if (user.portal === 'admin') return adminMenus
  if (user.portal === 'merchant') return merchantMenus
  return filterMenusByFeature(propertyMenus, (code) => user.can(code))
})
const portalTitle = computed(() => {
  if (user.portal === 'admin') return 'SaaS运营管理端'
  if (user.portal === 'merchant') return '智慧社区商户平台'
  return '物业工作端'
})
const shopLabel = computed(() => {
  if (shopName.value) return `店铺: ${shopName.value}`
  return user.communityName ? `店铺: ${user.communityName}` : '店铺: —'
})
const isScreen = computed(() => Boolean(route.meta.screen))
const leaseTip = computed(() => {
  if (user.portal !== 'property') return ''
  if (user.tenantStatus === 'degraded') return '套餐已到期降级：仅公告/报修可用'
  if (user.tenantStatus === 'frozen') return '租户已冻结，请续费解锁'
  return ''
})

function findMenuByPath(items: MenuItem[], path: string): MenuItem | null {
  for (const m of items) {
    if (m.path === path) return m
    if (m.children?.length) {
      const hit = findMenuByPath(m.children, path)
      if (hit) return hit
    }
  }
  return null
}

function onMenuSelect(index: string) {
  if (!index || index.startsWith('/') === false && !index.includes('/')) {
    // sub-menu title index
    return
  }
  const path = index.startsWith('/') ? index : `/${index}`
  const item = findMenuByPath(menus.value, path) || findMenuByPath(menus.value, index)
  if (item?.locked) {
    const code = item.feature || findParentFeature(menus.value, index) || ''
    user.openFeatureLimit(code)
    return
  }
  if (index.startsWith('/')) router.push(index)
}

function findParentFeature(items: MenuItem[], childPath: string): string | undefined {
  for (const m of items) {
    if (m.children?.some((c) => c.path === childPath)) return m.feature
  }
  return undefined
}

async function loadCommunities() {
  if (!isProperty.value) return
  try {
    if (user.hasRealToken) {
      communities.value = (await listMyCommunitiesApi()) || []
    } else {
      // 演示登录：用固定四社区
      communities.value = Object.entries(DEMO_COMMUNITY_PACKAGES)
        .filter(([name]) => !name.includes('演示'))
        .map(([name, p], i) => ({
          id: i + 1,
          name,
          packageCode: p.packageCode,
          packageName: p.packageName,
        }))
      // 对齐当前社区 id
      const cur = communities.value.find((c) => c.name === user.communityName)
      if (cur && !user.tenantId) {
        user.applyAuthContext({ tenantId: cur.id })
      }
    }
  } catch {
    communities.value = []
  }
}

async function onSwitchCommunity(tenantId: number) {
  if (!tenantId || String(tenantId) === user.tenantId) return
  const target = communities.value.find((c) => c.id === tenantId)
  switching.value = true
  user.setFeatureLoading(true)
  try {
    if (user.hasRealToken) {
      const data = await switchCommunityApi(tenantId)
      user.applyAuthContext({
        token: data.token,
        features: data.features,
        effectiveFeatureCodes: data.effectiveFeatureCodes,
        tenantStatus: data.tenantStatus,
        tenantId: data.tenantId,
        userId: data.userId,
        shopId: data.shopId,
        communityName: data.tenantName,
        packageCode: data.packageCode,
        packageName: data.packageName,
        role: data.role,
        phone: data.phone,
      })
    } else if (target) {
      // 演示：按社区名强制覆盖套餐，清除上一社区权限
      const demo = DEMO_COMMUNITY_PACKAGES[target.name]
      user.applyAuthContext({
        tenantId,
        communityName: target.name,
        packageCode: demo?.packageCode || target.packageCode,
        packageName: demo?.packageName || target.packageName,
        features: demo?.features || '',
        effectiveFeatureCodes: demo?.features?.split(','),
        tenantStatus: 'active',
      })
      user.applyPackageFromCommunity(target.name, true)
    }
    ElMessage.success(`已切换至 ${user.communityName}`)
    // 当前页在新社区不可用 → 进受限页
    const feature = route.meta.feature as string | undefined
    if (feature && !user.can(feature)) {
      await router.replace({
        path: '/property/feature-unavailable',
        query: { feature, from: route.fullPath },
      })
    } else if (route.path === '/property/feature-unavailable') {
      await router.replace('/property/dashboard')
    } else {
      // 刷新当前页，避免旧社区数据残留
      await router.replace({ path: route.path, query: { ...route.query, _t: String(Date.now()) } })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '切换社区失败')
  } finally {
    user.setFeatureLoading(false)
    switching.value = false
  }
}

function onLogout() {
  user.logout()
  router.push('/login')
}

async function syncLeaseFromServer() {
  if (!isProperty.value || !user.hasRealToken) return
  try {
    const lease = await getMyLease()
    user.applyAuthContext({
      tenantId: lease.tenantId,
      communityName: lease.tenantName,
      packageCode: lease.packageCode,
      packageName: lease.packageName,
      features: lease.features,
      effectiveFeatureCodes: lease.features,
      tenantStatus: lease.status,
    })
  } catch {
    /* 忽略：演示或接口未就绪 */
  }
}

onMounted(async () => {
  if (isProperty.value) {
    await syncLeaseFromServer()
    await loadCommunities()
  }
  if (!isMerchant.value) return
  try {
    const s: any = await getMyMallShop()
    shopName.value = s?.name || ''
  } catch {
    shopName.value = ''
  }
})
</script>

<style scoped>
.admin-layout { height: 100%; }
.aside {
  background: #001529;
  transition: width .2s;
  display: flex;
  flex-direction: column;
}
.is-merchant .aside {
  background: #0f766e;
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
.is-merchant .logo {
  background: #0d9488;
  border-bottom-color: #ffffff22;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
}
.is-merchant .header {
  border-bottom-color: #e7efed;
}
.is-merchant {
  --el-color-primary: #0d9488;
  --el-color-primary-light-3: #2dd4bf;
  --el-color-primary-light-5: #5eead4;
  --el-color-primary-light-7: #99f6e4;
  --el-color-primary-light-8: #ccfbf1;
  --el-color-primary-light-9: #f0fdfa;
  --el-color-primary-dark-2: #0f766e;
}
.left, .right { display: flex; align-items: center; gap: 12px; }
.fold { cursor: pointer; font-size: 18px; }
.user { color: #666; }
.shop-name { color: #0f766e; font-weight: 600; font-size: 13px; }
.shop-search { width: 260px; }
.shop-search :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: #f0fdfa;
  box-shadow: 0 0 0 1px #99f6e4 inset;
}
.main { background: #f0f2f5; position: relative; }
.main-merchant { background: #f3faf8; }
.main-screen {
  padding: 0 !important;
  background: #020b1a;
  overflow: hidden;
}
.feature-loading {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}
:deep(.el-menu) { border-right: none; }
.is-merchant :deep(.el-menu-item.is-active) {
  background: #14b8a6 !important;
}
.is-merchant :deep(.el-menu-item:hover) {
  background: #0d9488 !important;
}

/* 锁定菜单：次要灰 + 锁 */
.menu-item-inner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.menu-lock {
  margin-left: auto;
  font-size: 13px;
  color: #8c8c8c;
  opacity: 0.9;
}
.menu-locked-text {
  color: #8c8c8c !important;
}
:deep(.el-menu-item.is-feature-locked) {
  color: #8c8c8c !important;
  opacity: 0.85;
}
:deep(.el-menu-item.is-feature-locked:hover) {
  background: transparent !important;
  color: #8c8c8c !important;
}

.community-select {
  width: 168px;
}
.community-label {
  font-size: 13px;
  color: #606266;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.opt-pkg {
  float: right;
  color: #a8abb2;
  font-size: 12px;
  margin-left: 12px;
}

/* 低饱和套餐标签 */
.pkg-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 22px;
  border: 1px solid transparent;
}
.pkg-free {
  color: #6b7280;
  background: #f3f4f6;
  border-color: #e5e7eb;
}
.pkg-basic {
  color: #3b82c4;
  background: #eef5fb;
  border-color: #d7e6f5;
}
.pkg-pro {
  color: #2a9d8f;
  background: #eef8f6;
  border-color: #d2ebe7;
}
.pkg-flagship {
  color: #b8860b;
  background: #fbf6e9;
  border-color: #f0e6c8;
}
</style>
