<template>
  <!-- 物业：接单配送 -->
  <view v-if="storeUser.isStaff" class="page page-safe staff-page">
    <view class="hero staff">
      <text class="name">超市订单</text>
      <text class="meta">待接单 {{ todo.pendingAccept || 0 }} · 进行中 {{ todo.doing || 0 }}</text>
    </view>
    <view class="tabs">
      <view
        v-for="t in staffTabs"
        :key="t.v"
        class="tab"
        :class="{ on: status === t.v }"
        @click="status = t.v; loadStaff()"
      >{{ t.l }}</view>
    </view>
    <view v-for="o in list" :key="o.id" class="card item">
      <view class="row">
        <text class="no">{{ o.orderNo }}</text>
        <text class="st">{{ o.statusLabel }}</text>
      </view>
      <text class="ometa">{{ o.deliveryTypeLabel }} · ¥{{ o.payAmount }}</text>
      <text class="ometa">{{ o.receiverName }} {{ o.receiverPhone }}</text>
      <text class="ometa">{{ o.receiverAddr }}</text>
      <view class="ops">
        <button v-if="o.status === 'PENDING_ACCEPT'" size="mini" class="btn" @click="act(o, 'accept')">接单</button>
        <button v-if="o.status === 'PREPARING'" size="mini" class="btn" @click="act(o, 'prepare')">备货完成</button>
        <button v-if="o.status === 'PREPARING' || o.status === 'DELIVERING'" size="mini" class="btn" @click="act(o, 'deliver')">开始配送</button>
        <button v-if="o.status === 'DELIVERING'" size="mini" class="btn ok" @click="act(o, 'complete')">确认收货</button>
      </view>
    </view>
    <view v-if="!list.length" class="empty">暂无订单</view>
  </view>

  <!-- 居民：商户列表（封面入口） -->
  <view v-else class="mall-home">
    <view class="head">
      <view class="head-row">
        <text class="head-title">社区商城</text>
        <view class="head-acts">
          <text class="link" @click="goOrders">订单</text>
          <text class="link" @click="goCart">购物车{{ cartCount ? `(${cartCount})` : '' }}</text>
        </view>
      </view>
      <text class="head-sub">精选本社区商户 · 点击进入店铺选购</text>
    </view>

    <scroll-view scroll-y class="shop-scroll">
      <view
        v-for="s in shops"
        :key="s.shopId"
        class="shop-card"
        @click="goShop(s)"
      >
        <image
          v-if="imgUrl(s.coverUrl)"
          class="shop-cover"
          :src="imgUrl(s.coverUrl)"
          mode="aspectFill"
        />
        <view v-else class="shop-cover ph">{{ (s.name || '店').slice(0, 1) }}</view>
        <view class="shop-body">
          <view class="shop-top">
            <image
              v-if="imgUrl(s.avatarUrl)"
              class="avatar"
              :src="imgUrl(s.avatarUrl)"
              mode="aspectFill"
            />
            <view v-else class="avatar ph-a">{{ (s.name || '店').slice(0, 1) }}</view>
            <view class="shop-info">
              <text class="shop-name">{{ s.name }}</text>
              <text class="shop-cat">{{ s.categoryName || '社区商超' }}</text>
            </view>
            <view class="enter">进店</view>
          </view>
          <text class="shop-intro">{{ s.intro || '本社区优质商户，欢迎选购' }}</text>
          <view class="shop-meta">
            <text>营业 {{ s.openTime || '08:00' }}-{{ s.closeTime || '22:00' }}</text>
            <text class="dot">·</text>
            <text>{{ s.productCount || 0 }} 件在售</text>
          </view>
        </view>
      </view>
      <view v-if="!shops.length" class="empty">暂无营业商户</view>
      <view class="safe" />
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getStoreTodo,
  listCart,
  listOpenShops,
  listStoreOrders,
  staffStoreAction,
} from '../../api/store'
import { useUserStore } from '../../store/user'
import { getBaseUrl } from '../../utils/request'

const storeUser = useUserStore()
const shops = ref([])
const cartCount = ref(0)
const status = ref('PENDING_ACCEPT')
const list = ref([])
const todo = ref({})
const staffTabs = [
  { v: 'PENDING_ACCEPT', l: '待接单' },
  { v: 'PREPARING', l: '备货中' },
  { v: 'DELIVERING', l: '配送中' },
  { v: '', l: '全部' },
]

onShow(async () => {
  if (!storeUser.token) return uni.reLaunch({ url: '/pages/login/login' })
  // 居民：社区商城仅旗舰套餐；物业端接单不受此限制
  if (!storeUser.isStaff && !storeUser.guardFeature('vendor_manage')) {
    uni.switchTab({ url: '/pages/home/index' })
    return
  }
  if (storeUser.isStaff) {
    todo.value = (await getStoreTodo().catch(() => ({}))) || {}
    await loadStaff()
    return
  }
  shops.value = (await listOpenShops().catch(() => [])) || []
  const cart = (await listCart().catch(() => [])) || []
  cartCount.value = cart.reduce((s, c) => s + (c.quantity || 0), 0)
})

async function loadStaff() {
  list.value = (await listStoreOrders(status.value || undefined).catch(() => [])) || []
}
async function act(o, action) {
  await staffStoreAction(o.id, action)
  uni.showToast({ title: '已更新', icon: 'success' })
  todo.value = (await getStoreTodo().catch(() => ({}))) || {}
  loadStaff()
}

function goShop(s) {
  uni.navigateTo({ url: `/pages/mall/shop?id=${s.shopId}` })
}
function goCart() {
  uni.navigateTo({ url: '/pages/mall/cart' })
}
function goOrders() {
  uni.navigateTo({ url: '/pages/mall/orders' })
}
function imgUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || String(u).startsWith('data:')) return u
  const base = getBaseUrl().replace(/\/$/, '')
  return base + (u.startsWith('/') ? u : '/' + u)
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
$orange: #ff6a00;

.mall-home {
  position: absolute;
  left: 0; right: 0; top: 0;
  bottom: var(--window-bottom, 50px);
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
  overflow: hidden;
}
.head {
  padding: 28rpx 28rpx 16rpx;
  background: linear-gradient(165deg, #6ed3a8, $mint);
  color: #fff;
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-title { font-size: 40rpx; font-weight: 800; }
.head-acts { display: flex; gap: 24rpx; }
.link { font-size: 26rpx; opacity: .95; }
.head-sub { display: block; margin-top: 10rpx; font-size: 22rpx; opacity: .9; }

.shop-scroll { flex: 1; min-height: 0; padding: 20rpx 24rpx; box-sizing: border-box; }
.shop-card {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,.04);
}
.shop-cover {
  width: 100%;
  height: 240rpx;
  display: block;
  background: #e8f5f2;
}
.shop-cover.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $mint;
  font-size: 64rpx;
  font-weight: 800;
}
.shop-body { padding: 20rpx 22rpx 24rpx; }
.shop-top { display: flex; align-items: center; gap: 16rpx; }
.avatar {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  border: 4rpx solid #fff; margin-top: -48rpx;
  background: #fff; box-shadow: 0 4rpx 12rpx rgba(0,0,0,.08);
}
.ph-a {
  display: flex; align-items: center; justify-content: center;
  background: $mint; color: #fff; font-weight: 700; font-size: 28rpx;
}
.shop-info { flex: 1; min-width: 0; }
.shop-name {
  display: block; font-size: 32rpx; font-weight: 800; color: #1f2a28;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.shop-cat { display: block; margin-top: 4rpx; font-size: 22rpx; color: #8a9693; }
.enter {
  padding: 10rpx 22rpx; border-radius: 999rpx;
  background: $orange; color: #fff; font-size: 24rpx; font-weight: 700;
}
.shop-intro {
  display: block; margin-top: 14rpx; font-size: 24rpx; color: #64748b;
  line-height: 1.45;
  overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.shop-meta {
  margin-top: 12rpx; display: flex; gap: 8rpx;
  font-size: 22rpx; color: #94a3b8;
}
.dot { opacity: .5; }
.safe { height: 40rpx; }
.empty { text-align: center; color: #94a3b8; padding: 80rpx 0; font-size: 28rpx; }

.staff-page { padding-bottom: 40rpx; }
.hero.staff {
  background: linear-gradient(165deg, #6ed3a8, $mint);
  color: #fff; padding: 32rpx 28rpx; border-radius: 0 0 24rpx 24rpx;
}
.hero .name { display: block; font-size: 36rpx; font-weight: 800; }
.hero .meta { display: block; margin-top: 8rpx; font-size: 24rpx; opacity: .9; }
.tabs { display: flex; gap: 12rpx; padding: 20rpx 24rpx; flex-wrap: wrap; }
.tab {
  padding: 10rpx 22rpx; border-radius: 999rpx; background: #eef2f1;
  font-size: 24rpx; color: #5a6562;
}
.tab.on { background: $mint; color: #fff; font-weight: 700; }
.card.item {
  margin: 0 24rpx 16rpx; background: #fff; border-radius: 16rpx; padding: 20rpx;
}
.row { display: flex; justify-content: space-between; }
.no { font-weight: 700; color: #1f2a28; }
.st { color: $mint; font-size: 24rpx; }
.ometa { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693; }
.ops { display: flex; gap: 12rpx; margin-top: 16rpx; flex-wrap: wrap; }
.btn {
  background: #e8f5f2 !important; color: $mint !important; border: none;
}
.btn.ok { background: $mint !important; color: #fff !important; }
</style>
