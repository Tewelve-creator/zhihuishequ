import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { listPages } from '@/config/listPages'

const Layout = () => import('@/layouts/AdminLayout.vue')
const GenericList = () => import('@/views/shared/GenericListView.vue')

const listChildren: RouteRecordRaw[] = Object.keys(listPages)
  .filter((path) => path !== '/property/repair' && path !== '/property/notice')
  .map((path) => ({
    path: path.replace(/^\//, ''),
    component: GenericList,
    meta: {
      title: listPages[path].title,
      portal: path.startsWith('/admin') ? 'admin' : 'property',
    },
  }))

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/admin/cockpit',
    children: [
      {
        path: 'admin/cockpit',
        component: () => import('@/views/admin/CockpitView.vue'),
        meta: { title: '数据监控驾驶舱', portal: 'admin', screen: true },
      },
      {
        path: 'admin/payment/cashier',
        component: () => import('@/views/admin/CashierView.vue'),
        meta: { title: '收银台', portal: 'admin' },
      },
      {
        path: 'admin/reports',
        component: () => import('@/views/admin/ReportsView.vue'),
        meta: { title: '统计报表', portal: 'admin' },
      },
      {
        path: 'admin/system/roles',
        component: () => import('@/views/admin/RolesView.vue'),
        meta: { title: '角色权限', portal: 'admin' },
      },
      {
        path: 'property/dashboard',
        component: () => import('@/views/property/DashboardView.vue'),
        meta: { title: '工作台', portal: 'property' },
      },
      {
        path: 'property/repair',
        component: () => import('@/views/property/RepairView.vue'),
        meta: { title: '报事报修', portal: 'property' },
      },
      {
        path: 'property/staff',
        component: () => import('@/views/property/StaffAccountsView.vue'),
        meta: { title: '账号管理', portal: 'property' },
      },
      {
        path: 'property/notice',
        component: () => import('@/views/property/NoticeView.vue'),
        meta: { title: '公告通知', portal: 'property' },
      },
      ...listChildren,
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.public) return true
  if (!user.isLogin) return { path: '/login', query: { redirect: to.fullPath } }

  const portal = to.meta.portal as string | undefined
  if (portal && portal !== user.portal) {
    return user.portal === 'admin' ? '/admin/cockpit' : '/property/dashboard'
  }
  return true
})

export default router
