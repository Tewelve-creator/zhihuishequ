<template>
  <view class="mine">
    <scroll-view scroll-y class="scroll">
      <view class="hero" :style="{ paddingTop: statusBarPx + 'px' }">
        <view class="hero-top">
          <view class="spacer" />
          <view class="gear" @click="nav('/pages/mine/settings')">
            <u-icon name="setting" color="#ffffff" size="22" />
          </view>
        </view>
        <view class="profile" @click="nav('/pages/mine/profile')">
          <image v-if="avatarSrc" class="avatar-img" :src="avatarSrc" mode="aspectFill" />
          <view v-else class="avatar">{{ avatarText }}</view>
          <view class="pinfo">
            <view class="name-row">
              <text class="name">{{ displayName }}</text>
              <text class="role-tag">{{ roleTag }}</text>
            </view>
            <text class="phone">{{ displayPhone }}</text>
            <text class="comm">{{ communityName }}</text>
            <text class="edit-tip">点击编辑昵称与头像 ›</text>
          </view>
        </view>
      </view>

      <view class="body">
        <!-- 物业端 -->
        <template v-if="store.isStaff">
          <view class="card">
            <view class="info-row"><text class="k">账号</text><text class="v">{{ displayPhone }}</text></view>
            <view class="info-row"><text class="k">岗位</text><text class="v">{{ roleTag }}</text></view>
            <view class="info-row"><text class="k">所属社区</text><text class="v">{{ communityName }}</text></view>
            <view class="info-row"><text class="k">租户ID</text><text class="v">{{ store.tenantId }}</text></view>
          </view>

          <view v-if="store.isPropertyAdmin" class="card stats">
            <view class="sec-title">管理数据</view>
            <view class="stat-grid">
              <view class="st" @click="nav('/pages/repair/list')">
                <text class="n">{{ perf.repairDone }}</text>
                <text class="l">完工绩效</text>
              </view>
              <view class="st" @click="nav('/pages/staff/dashboard')">
                <text class="n">{{ perf.pending }}</text>
                <text class="l">待办汇总</text>
              </view>
              <view class="st">
                <text class="n">{{ packageDays }}</text>
                <text class="l">套餐剩余天</text>
              </view>
            </view>
          </view>

          <view v-if="store.isGridWorker" class="tip">网格员不展示工单绩效、套餐时长与数据统计</view>

          <view class="card menu">
            <view class="mi" @click="nav('/pages/mine/profile')"><text>个人资料</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/staff/workbench')"><text>工作台</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/mine/password')"><text>修改密码</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/mine/settings')"><text>系统设置</text><text class="ar">›</text></view>
          </view>
        </template>

        <!-- 居民端 -->
        <template v-else>
          <!-- 租客信息卡：身份 + 租房日期 -->
          <view v-if="isTenant" class="card tenant-card">
            <view class="sec-title">租客信息</view>
            <view class="info-row"><text class="k">姓名</text><text class="v">{{ tenantInfo.name }}</text></view>
            <view class="info-row"><text class="k">登录手机号</text><text class="v">{{ displayPhone }}</text></view>
            <view class="info-row"><text class="k">租赁房屋</text><text class="v house-v">{{ tenantInfo.house }}</text></view>
            <view class="info-row">
              <text class="k">租房日期</text>
              <text class="v">{{ tenantInfo.leaseRange }}</text>
            </view>
            <view class="info-row">
              <text class="k">租约状态</text>
              <text class="v" :class="'lease-' + (tenantInfo.leaseStatus || '').toLowerCase()">{{ tenantInfo.leaseStatusLabel }}</text>
            </view>
          </view>

          <view v-else class="card house-card">
            <view class="info-row"><text class="k">身份</text><text class="v">{{ roleTag }}</text></view>
            <view class="info-row"><text class="k">当前房屋</text><text class="v house-v">{{ currentHouseLabel }}</text></view>
          </view>

          <view class="card menu">
            <view class="mi" @click="nav('/pages/mine/profile')"><text>个人资料</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/house/bind')"><text>房屋绑定</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/house/cars')"><text>我的车辆</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/repair/list')"><text>我的报事</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/payment/index')"><text>我的缴费</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/mall/orders')"><text>超市订单</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/payment/records')"><text>缴费记录</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/mine/password')"><text>修改密码</text><text class="ar">›</text></view>
            <view class="mi" @click="nav('/pages/mine/settings')"><text>系统设置</text><text class="ar">›</text></view>
          </view>

          <view class="card butler-card">
            <view class="butler-row">
              <view class="butler-avatar">管</view>
              <view class="butler-mid">
                <text class="butler-title">智能管家</text>
                <text class="butler-desc">有问题随时问我</text>
              </view>
              <view class="btn-solid" @click="onCallButler">联系管家</view>
            </view>
          </view>
        </template>

        <!-- 退出账号：两端都醒目展示 -->
        <view class="logout-wrap">
          <button class="logout-btn" @click="onLogout">退出账号</button>
        </view>

        <view class="safe-bottom" />
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'
import { ROLE_LABELS } from '../../utils/constants'
import { listRepair } from '../../api/biz'
import { getProfile } from '../../api/auth'
import { getBaseUrl } from '../../utils/request'

const store = useUserStore()
const houseStore = useHouseStore()
const statusBarPx = ref(20)
const communityName = ref(uni.getStorageSync('communityName') || '阳光花园社区')
const packageDays = ref('-')
const perf = reactive({ repairDone: 0, pending: 0 })

const displayName = computed(() => store.nickname || (store.isStaff ? '物业工作人员' : '社区居民'))
const avatarText = computed(() => (displayName.value || '居')[0])
const avatarSrc = computed(() => {
  const u = store.avatar
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl()
  return `${base}${u.startsWith('/') ? u : '/' + u}`
})
const currentHouse = computed(() => houseStore.currentHouse || {})
const isTenant = computed(() => String(currentHouse.value.identityType || '').toUpperCase() === 'TENANT')
const roleTag = computed(() => {
  if (store.isGridWorker) return '网格员'
  if (store.isPropertyAdmin || store.isStaff) return ROLE_LABELS[store.role] || '物业管理员'
  if (isTenant.value) return '租客'
  return store.role === 'resident' ? '业主' : '游客'
})
const displayPhone = computed(() => {
  const p = uni.getStorageSync('phone') || currentHouse.value.residentPhone || ''
  if (!p) return '未绑定手机号'
  const s = String(p)
  return s.length >= 11 ? s.slice(0, 3) + '****' + s.slice(-4) : s
})
const currentHouseLabel = computed(() => houseStore.label || '未绑定房屋')
const tenantInfo = computed(() => {
  const h = currentHouse.value || {}
  const start = formatDate(h.leaseStart)
  const end = formatDate(h.leaseEnd)
  let leaseRange = '未登记'
  if (start || end) leaseRange = `${start || '—'} ~ ${end || '—'}`
  return {
    name: h.residentName || store.nickname || '租客',
    house: h.label || currentHouseLabel.value,
    leaseRange,
    leaseStatus: h.leaseStatus || 'UNSET',
    leaseStatusLabel: h.leaseStatusLabel || (start || end ? '在租中' : '未登记')
  }
})

function formatDate(v) {
  if (!v) return ''
  return String(v).substring(0, 10)
}

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  try {
    statusBarPx.value = uni.getSystemInfoSync().statusBarHeight || 20
  } catch (e) {}
  // 刷新房屋与租约身份
  if (!store.isStaff) {
    await houseStore.refresh().catch(() => {})
  }
  communityName.value =
    (houseStore.currentHouse?.communityName &&
    !/[?？]/.test(String(houseStore.currentHouse.communityName))
      ? houseStore.currentHouse.communityName
      : null) ||
    uni.getStorageSync('communityName') ||
    communityName.value
  // 若本地曾被乱码污染，纠正回租户名
  if (/[?？]/.test(String(communityName.value || ''))) {
    communityName.value = store.tenantId == 2 ? '星河湾社区' : '阳光花园社区'
    uni.setStorageSync('communityName', communityName.value)
  }

  // 同步最新昵称/头像，并回填租约字段（若本地房屋缓存较旧）
  getProfile()
    .then((p) => {
      if (!p) return
      store.updateProfileLocal({
        nickname: p.nickname != null ? p.nickname : undefined,
        avatar: p.avatar !== undefined ? p.avatar : undefined
      })
      if (p.currentHouse && p.currentHouse.id) {
        const merged = { ...(houseStore.currentHouse || {}), ...p.currentHouse }
        houseStore.setCurrent(merged, false)
        if (Array.isArray(houseStore.houses) && houseStore.houses.length) {
          houseStore.houses = houseStore.houses.map((h) =>
            String(h.id) === String(merged.id) ? { ...h, ...merged } : h
          )
          houseStore.persistList()
        }
      }
    })
    .catch(() => {})

  if (store.isPropertyAdmin) {
    const [done, pending, profile] = await Promise.all([
      listRepair('done').catch(() => []),
      listRepair('pending').catch(() => []),
      getProfile().catch(() => null)
    ])
    perf.repairDone = (done || []).length
    perf.pending = (pending || []).length
    if (profile?.expireTime) {
      const end = new Date(profile.expireTime).getTime()
      const days = Math.ceil((end - Date.now()) / 86400000)
      packageDays.value = Number.isFinite(days) ? String(Math.max(days, 0)) : '-'
    } else {
      packageDays.value = '未启用'
    }
  }
})

function nav(url) {
  if (
    !store.isStaff &&
    (url.includes('/repair/') ||
      url.includes('/payment/') ||
      url.includes('/visitor/') ||
      url.includes('/house/cars'))
  ) {
    if (!houseStore.guardBound()) return
  }
  uni.navigateTo({ url })
}

function onCallButler() {
  uni.switchTab({ url: '/pages/ai/chat' })
}

function onLogout() {
  uni.showModal({
    title: '退出账号',
    content: '确定退出当前账号吗？',
    confirmText: '退出',
    confirmColor: '#e85d4c',
    success: (res) => {
      if (res.confirm) store.logout()
    }
  })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.mine {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: var(--window-bottom, 50px);
  background: #f3f6f5;
  overflow: hidden;
  box-sizing: border-box;
}
.scroll { width: 100%; height: 100%; }
.hero {
  padding: 12rpx 32rpx 72rpx;
  background: linear-gradient(165deg, #6ed3a8, $mint 55%, #3fad7d);
}
.hero-top { display: flex; justify-content: flex-end; height: 64rpx; }
.gear { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; }
.profile { display: flex; align-items: center; gap: 24rpx; }
.avatar {
  width: 112rpx; height: 112rpx; border-radius: 50%; background: #fff; color: $mint;
  font-size: 44rpx; font-weight: 700; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.avatar-img {
  width: 112rpx; height: 112rpx; border-radius: 50%; flex-shrink: 0; background: #fff;
}
.edit-tip {
  display: block; margin-top: 8rpx; font-size: 22rpx; color: rgba(255,255,255,.75);
}
.name { font-size: 36rpx; font-weight: 700; color: #fff; }
.role-tag {
  margin-left: 12rpx; font-size: 20rpx; color: #8a5a00; background: #ffe8a3;
  padding: 4rpx 12rpx; border-radius: 8rpx;
}
.phone, .comm { display: block; margin-top: 8rpx; font-size: 24rpx; color: rgba(255,255,255,.88); }
.body { margin-top: -48rpx; padding: 0 28rpx; position: relative; z-index: 2; }
.card {
  background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(31,42,40,.05);
}
.info-row {
  display: flex; justify-content: space-between; padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f3f2; font-size: 28rpx;
}
.info-row:last-child { border-bottom: none; }
.k { color: #8a9693; }
.v { color: #1f2a28; font-weight: 600; }
.sec-title { font-size: 30rpx; font-weight: 700; margin-bottom: 20rpx; }
.stat-grid { display: flex; }
.st { flex: 1; text-align: center; }
.n { display: block; font-size: 40rpx; font-weight: 700; color: $mint; }
.l { font-size: 22rpx; color: #8a9693; }
.tip {
  background: #fff8e8; color: #8a5a00; border-radius: 20rpx;
  padding: 20rpx 24rpx; margin-bottom: 24rpx; font-size: 24rpx;
}
.menu .mi {
  display: flex; justify-content: space-between; padding: 28rpx 0;
  border-bottom: 1rpx solid #f0f3f2; font-size: 30rpx;
}
.menu .mi:last-child { border-bottom: none; }
.ar { color: #c5cecb; }
.danger { color: #e85d4c; }
.name-row { display: flex; align-items: center; flex-wrap: wrap; }
.house-v { max-width: 62%; text-align: right; word-break: break-all; }
.tenant-card .sec-title { margin-bottom: 8rpx; }
.lease-active { color: #47b686 !important; }
.lease-expiring { color: #d97706 !important; }
.lease-expired { color: #e85d4c !important; }
.lease-unset { color: #8a9693 !important; }
.butler-row { display: flex; align-items: center; gap: 16rpx; }
.butler-avatar {
  width: 80rpx; height: 80rpx; border-radius: 50%;
  background: linear-gradient(145deg,#6ed3a8,$mint); color: #fff;
  font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.butler-mid { flex: 1; }
.butler-title { display: block; font-weight: 700; font-size: 30rpx; }
.butler-desc { font-size: 22rpx; color: #8a9693; }
.btn-solid {
  padding: 12rpx 22rpx; border-radius: 999rpx; background: $mint; color: #fff; font-size: 24rpx;
}
.logout-wrap {
  margin: 8rpx 0 24rpx;
}
.logout-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #fff;
  color: #e85d4c;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 28rpx;
  border: 2rpx solid #ffd4ce;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.logout-btn::after {
  border: none;
}
.safe-bottom { height: calc(40rpx + constant(safe-area-inset-bottom)); height: calc(40rpx + env(safe-area-inset-bottom)); }
</style>
