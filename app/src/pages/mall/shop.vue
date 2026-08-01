<template>
  <view class="shop-page">
    <!-- 封面 + 店铺信息 -->
    <view class="hero">
      <image v-if="coverSrc" class="cover" :src="coverSrc" mode="aspectFill" />
      <view v-else class="cover ph" />
      <view class="hero-mask" />
      <view class="nav" :style="{ paddingTop: statusBar + 'px' }">
        <view class="nav-back" @click="back">‹</view>
        <view class="search" @click="focusSearch">
          <text class="search-ph">{{ keyword || '搜索本店商品' }}</text>
        </view>
        <view class="nav-acts">
          <text class="nav-link" @click="goOrders">订单</text>
        </view>
      </view>
    </view>

    <view class="shop-panel">
      <view class="shop-row">
        <image v-if="avatarSrc" class="avatar" :src="avatarSrc" mode="aspectFill" />
        <view v-else class="avatar ph-a">{{ (shop.name || '店').slice(0, 1) }}</view>
        <view class="shop-main">
          <text class="shop-name">{{ shop.name || '社区店铺' }}</text>
          <text class="shop-cat">{{ shop.categoryName || '社区商超' }}</text>
        </view>
      </view>
      <text class="intro">{{ shop.intro || '本社区优质商户，欢迎选购' }}</text>
      <view class="tips">
        <text>营业 {{ shop.openTime || '08:00' }}-{{ shop.closeTime || '22:00' }}</text>
        <text class="dot">·</text>
        <text>起送 ¥{{ shop.minOrderAmount || 0 }}</text>
        <text class="dot">·</text>
        <text>配送 ¥{{ shop.deliveryFee || 0 }}</text>
      </view>
    </view>

    <!-- 领优惠券（店铺信息与商品区之间） -->
    <view v-if="coupons.length" class="coupon-bar">
      <view class="coupon-hd">
        <text class="coupon-title">优惠券</text>
        <text class="coupon-sub">本店专享 · 下单可用</text>
      </view>
      <scroll-view scroll-x class="coupon-scroll" :show-scrollbar="false">
        <view class="coupon-row">
          <view v-for="c in coupons" :key="c.id" class="coupon-card">
            <view class="coupon-left">
              <view class="coupon-amt">
                <template v-if="c.couponType === 'DISCOUNT'">
                  <text class="amt-num">{{ formatRate(c.discountRate) }}</text>
                  <text class="amt-unit">折</text>
                </template>
                <template v-else>
                  <text class="amt-yen">¥</text>
                  <text class="amt-num">{{ formatPrice(c.discountAmount) }}</text>
                </template>
              </view>
              <text class="coupon-rule">{{ c.ruleText || c.thresholdText || '优惠券' }}</text>
            </view>
            <view class="coupon-right">
              <text class="coupon-name">{{ c.name }}</text>
              <view
                class="claim-btn"
                :class="{ done: c.claimedByMe || c.soldOut }"
                @click="onClaim(c)"
              >{{ claimLabel(c) }}</view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 分类 + 商品（占满剩余空间） -->
    <view class="body">
      <scroll-view scroll-y class="side">
        <view class="side-item" :class="{ on: !catId }" @click="selectCat(null)">
          <text>推荐</text>
        </view>
        <view
          v-for="c in categories"
          :key="c.id"
          class="side-item"
          :class="{ on: catId === c.id }"
          @click="selectCat(c.id)"
        >
          <text>{{ c.name }}</text>
        </view>
      </scroll-view>

      <scroll-view scroll-y class="goods" :scroll-top="goodsScroll">
        <view class="sec-title">{{ currentCatName }}</view>
        <view v-for="p in products" :key="p.id" class="prow">
          <view class="pimg-wrap">
            <image v-if="imgUrl(p.coverUrl)" class="pimg" :src="imgUrl(p.coverUrl)" mode="aspectFill" />
            <view v-else class="pimg ph-g">{{ (p.name || '').slice(0, 1) }}</view>
            <text v-if="isNew(p)" class="pbadge">新品</text>
          </view>
          <view class="pbody">
            <text class="pname">{{ p.name }}</text>
            <text class="psales">库存 {{ p.stock }}{{ p.sales != null ? ' · 已售 ' + p.sales : '' }}</text>
            <view class="pprice-row">
              <view class="prices">
                <text class="yen">¥</text>
                <text class="price">{{ formatPrice(p.price) }}</text>
                <text v-if="showOrigin(p)" class="origin">¥{{ formatPrice(originOf(p)) }}</text>
                <text v-if="discountOf(p)" class="off">{{ discountOf(p) }}</text>
              </view>
            </view>
            <view class="buy" @click="onAdd(p)">抢购</view>
          </view>
        </view>
        <view v-if="!products.length" class="empty">该分类暂无商品</view>
        <view class="safe" />
      </scroll-view>
    </view>

    <view v-if="cartCount" class="dock" @click="goCart">
      <view class="dock-left">
        <view class="dock-icon">购</view>
        <view class="dock-texts">
          <text class="dock-t">已选 {{ cartCount }} 件</text>
          <text class="dock-s">送货上门 / 到店自提</text>
        </view>
      </view>
      <view class="dock-btn">去结算</view>
    </view>

    <!-- 搜索弹层 -->
    <view v-if="showSearch" class="search-mask" @click="showSearch = false">
      <view class="search-box" @click.stop>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索本店商品"
          confirm-type="search"
          focus
          @confirm="doSearch"
        />
        <text class="search-go" @click="doSearch">搜索</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  addCart,
  claimShopCoupon,
  getOpenShop,
  listCart,
  listShopCategories,
  listShopCoupons,
  listShopProducts,
} from '../../api/store'
import { useHouseStore } from '../../store/house'
import { getBaseUrl } from '../../utils/request'

const houseStore = useHouseStore()
const shopId = ref(null)
const shop = ref({})
const categories = ref([])
const products = ref([])
const catId = ref(null)
const cartCount = ref(0)
const goodsScroll = ref(0)
const statusBar = ref(20)
const keyword = ref('')
const showSearch = ref(false)
const coupons = ref([])
const claimingId = ref(null)

const coverSrc = computed(() => imgUrl(shop.value.coverUrl))
const avatarSrc = computed(() => imgUrl(shop.value.avatarUrl))
const currentCatName = computed(() => {
  if (!catId.value) return '推荐'
  return categories.value.find((c) => c.id === catId.value)?.name || '商品'
})

onLoad((q) => {
  shopId.value = Number(q.id)
  try {
    statusBar.value = uni.getSystemInfoSync().statusBarHeight || 20
  } catch {
    statusBar.value = 20
  }
})

onShow(async () => {
  if (!shopId.value) return
  await Promise.all([loadShop(), loadCats(), loadProducts(), loadCartCount(), loadCoupons()])
})

async function loadShop() {
  shop.value = (await getOpenShop(shopId.value).catch(() => ({}))) || {}
}
async function loadCoupons() {
  coupons.value = (await listShopCoupons(shopId.value).catch(() => [])) || []
}
function claimLabel(c) {
  if (c.soldOut) return '已抢光'
  if (c.claimedByMe) return '已领取'
  return '领取'
}
function formatRate(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return v
  return n % 1 === 0 ? String(n) : n.toFixed(1)
}
async function onClaim(c) {
  if (!c?.id || c.claimedByMe || c.soldOut || claimingId.value) return
  if (!houseStore.guardBound('领券前请先绑定房屋')) return
  claimingId.value = c.id
  try {
    await claimShopCoupon(shopId.value, c.id)
    uni.showToast({ title: '领取成功', icon: 'success' })
    await loadCoupons()
  } catch (e) {
    uni.showToast({ title: e?.message || e?.msg || '领取失败', icon: 'none' })
  } finally {
    claimingId.value = null
  }
}
async function loadCats() {
  const list = (await listShopCategories(shopId.value).catch(() => [])) || []
  categories.value = list.filter((c) => !String(c.name || '').includes('生鲜'))
}
async function loadProducts() {
  products.value =
    (await listShopProducts(shopId.value, {
      categoryId: catId.value || undefined,
      keyword: keyword.value.trim() || undefined,
    }).catch(() => [])) || []
}
async function loadCartCount() {
  const cart = (await listCart().catch(() => [])) || []
  cartCount.value = cart.reduce((s, c) => s + (c.quantity || 0), 0)
}

function selectCat(id) {
  catId.value = id
  keyword.value = ''
  goodsScroll.value = goodsScroll.value === 0 ? 0.01 : 0
  loadProducts()
}
function focusSearch() {
  showSearch.value = true
}
function doSearch() {
  showSearch.value = false
  catId.value = null
  loadProducts()
}
async function onAdd(p) {
  if (!houseStore.guardBound('选购商品前请先绑定房屋')) return
  if (Number(p.stock) <= 0) {
    uni.showToast({ title: '暂时缺货', icon: 'none' })
    return
  }
  await addCart(p.id, 1)
  uni.showToast({ title: '已加入购物车', icon: 'success' })
  loadCartCount()
}
function goCart() {
  uni.navigateTo({ url: '/pages/mall/cart' })
}
function goOrders() {
  uni.navigateTo({ url: '/pages/mall/orders' })
}
function back() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/mall/index' }) })
}
function imgUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || String(u).startsWith('data:')) return u
  const base = getBaseUrl().replace(/\/$/, '')
  return base + (u.startsWith('/') ? u : '/' + u)
}
function formatPrice(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return v
  return n % 1 === 0 ? String(n) : n.toFixed(2)
}
function originOf(p) {
  return p.originalPrice ?? p.original_price
}
function showOrigin(p) {
  const o = Number(originOf(p))
  const price = Number(p.price)
  return o > 0 && o > price
}
function discountOf(p) {
  const o = Number(originOf(p))
  const price = Number(p.price)
  if (!(o > 0) || !(price > 0) || o <= price) return ''
  const d = Math.round((price / o) * 100) / 10
  return `${d}折`
}
function isNew(p) {
  return Number(p.sales || 0) < 5
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
$orange: #ff6a00;
$ink: #1f2a28;

.shop-page {
  position: absolute;
  left: 0; right: 0; top: 0;
  bottom: var(--window-bottom, 50px);
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  overflow: hidden;
}
.hero {
  position: relative;
  height: 200rpx;
  flex-shrink: 0;
}
.cover { width: 100%; height: 100%; display: block; background: #d1fae5; }
.cover.ph { background: linear-gradient(165deg, #6ed3a8, $mint); }
.hero-mask {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,.25), rgba(0,0,0,.05) 60%, transparent);
}
.nav {
  position: absolute; left: 0; right: 0; top: 0; z-index: 2;
  display: flex; align-items: center; gap: 12rpx;
  padding: 12rpx 20rpx;
}
.nav-back {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: rgba(0,0,0,.35); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 40rpx; line-height: 1;
}
.search {
  flex: 1; height: 64rpx; border-radius: 999rpx;
  background: rgba(255,255,255,.95); padding: 0 24rpx;
  display: flex; align-items: center;
}
.search-ph { font-size: 24rpx; color: #94a3b8; }
.nav-link {
  color: #fff; font-size: 24rpx; font-weight: 600;
  background: rgba(0,0,0,.3); padding: 12rpx 18rpx; border-radius: 999rpx;
}

.shop-panel {
  margin: -36rpx 20rpx 0; position: relative; z-index: 3;
  background: #fff; border-radius: 20rpx; padding: 18rpx 22rpx;
  box-shadow: 0 8rpx 24rpx rgba(0,0,0,.06);
  flex-shrink: 0;
}
.shop-row { display: flex; align-items: center; gap: 16rpx; }
.avatar {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  border: 4rpx solid #fff; background: #fff;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,.08);
}
.ph-a {
  display: flex; align-items: center; justify-content: center;
  background: $mint; color: #fff; font-weight: 800; font-size: 28rpx;
}
.shop-main { flex: 1; min-width: 0; }
.shop-name {
  display: block; font-size: 32rpx; font-weight: 800; color: $ink;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.shop-cat { display: block; margin-top: 2rpx; font-size: 22rpx; color: #8a9693; }
.intro {
  display: block; margin-top: 10rpx; font-size: 22rpx; color: #64748b; line-height: 1.4;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.tips {
  margin-top: 8rpx; display: flex; flex-wrap: wrap; gap: 8rpx;
  font-size: 20rpx; color: #94a3b8;
}
.dot { opacity: .5; }

.coupon-bar {
  flex-shrink: 0;
  margin: 16rpx 20rpx 0;
  background: #fff;
  border-radius: 20rpx;
  padding: 16rpx 0 18rpx;
  box-shadow: 0 6rpx 18rpx rgba(0, 0, 0, 0.04);
}
.coupon-hd {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  padding: 0 22rpx 12rpx;
}
.coupon-title {
  font-size: 28rpx;
  font-weight: 800;
  color: $ink;
}
.coupon-sub {
  font-size: 20rpx;
  color: #94a3b8;
}
.coupon-scroll {
  width: 100%;
  white-space: nowrap;
}
.coupon-row {
  display: inline-flex;
  gap: 16rpx;
  padding: 0 20rpx;
}
.coupon-card {
  display: inline-flex;
  width: 340rpx;
  min-height: 128rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: linear-gradient(135deg, #fff7ed, #ffedd5);
  border: 1rpx solid #fed7aa;
  flex-shrink: 0;
}
.coupon-left {
  width: 130rpx;
  padding: 16rpx 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #ff8a3d, #ff6a00);
  color: #fff;
}
.coupon-amt {
  display: flex;
  align-items: baseline;
  color: #fff;
}
.amt-yen { font-size: 22rpx; font-weight: 700; }
.amt-num { font-size: 40rpx; font-weight: 800; line-height: 1; }
.amt-unit { font-size: 22rpx; font-weight: 700; margin-left: 2rpx; }
.coupon-rule {
  margin-top: 6rpx;
  font-size: 18rpx;
  opacity: 0.92;
  text-align: center;
  white-space: normal;
  line-height: 1.2;
}
.coupon-right {
  flex: 1;
  min-width: 0;
  padding: 16rpx 14rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.coupon-name {
  font-size: 24rpx;
  font-weight: 700;
  color: $ink;
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.3;
}
.claim-btn {
  align-self: flex-end;
  margin-top: 10rpx;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  background: $orange;
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
}
.claim-btn.done {
  background: #e2e8f0;
  color: #94a3b8;
}

.body {
  flex: 1; min-height: 0; display: flex;
  margin: 16rpx 0 0; background: #fff; border-radius: 20rpx 20rpx 0 0;
  overflow: hidden;
}
.side {
  width: 132rpx; background: #f3f4f6; height: 100%; flex-shrink: 0;
}
.side-item {
  padding: 32rpx 10rpx; font-size: 24rpx; color: #64748b;
  text-align: center; line-height: 1.35; position: relative;
}
.side-item.on {
  background: #fff; color: $orange; font-weight: 700;
}
.side-item.on::before {
  content: '';
  position: absolute; left: 0; top: 28rpx; bottom: 28rpx;
  width: 6rpx; border-radius: 0 6rpx 6rpx 0; background: $orange;
}
.goods {
  flex: 1; height: 100%; padding: 8rpx 20rpx 0; box-sizing: border-box;
  min-width: 0;
}
.sec-title {
  padding: 16rpx 4rpx 12rpx; font-size: 28rpx; font-weight: 700; color: $ink;
}
.prow {
  display: flex; gap: 20rpx; padding: 24rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.pimg-wrap { position: relative; flex-shrink: 0; }
.pimg {
  width: 200rpx; height: 200rpx; border-radius: 16rpx; background: #e8f5f2; display: block;
}
.pbadge {
  position: absolute; left: 0; top: 0;
  background: $orange; color: #fff; font-size: 20rpx;
  padding: 6rpx 12rpx; border-radius: 16rpx 0 16rpx 0;
}
.pbody {
  flex: 1; min-width: 0;
  display: flex; flex-direction: column;
  justify-content: space-between;
  padding: 4rpx 0;
}
.pname {
  font-size: 30rpx; font-weight: 700; color: $ink; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.psales { margin-top: 10rpx; font-size: 22rpx; color: #94a3b8; }
.pprice-row {
  margin-top: 12rpx;
  display: flex; align-items: baseline;
}
.prices { display: flex; align-items: baseline; gap: 8rpx; flex-wrap: wrap; }
.yen { color: #ef4444; font-size: 24rpx; font-weight: 700; }
.price { color: #ef4444; font-size: 40rpx; font-weight: 800; }
.origin { color: #94a3b8; font-size: 22rpx; text-decoration: line-through; }
.off {
  font-size: 20rpx; color: #ef4444; background: #fef2f2;
  padding: 2rpx 10rpx; border-radius: 6rpx;
}
.buy {
  margin-top: 14rpx;
  align-self: flex-end;
  padding: 14rpx 36rpx; border-radius: 999rpx;
  background: $orange; color: #fff; font-size: 26rpx; font-weight: 700;
}
.empty { text-align: center; color: #94a3b8; padding: 80rpx 0; font-size: 28rpx; }
.safe { height: 160rpx; }

.ph-g {
  display: flex; align-items: center; justify-content: center;
  color: $mint; font-size: 48rpx; font-weight: 800; background: #e8f5f2;
}

.dock {
  position: absolute; left: 24rpx; right: 24rpx; bottom: 24rpx;
  height: 96rpx; border-radius: 999rpx;
  background: #1f2a28; color: #fff;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 8rpx 0 20rpx; z-index: 10;
  box-shadow: 0 12rpx 28rpx rgba(0,0,0,.2);
}
.dock-left { display: flex; align-items: center; gap: 14rpx; }
.dock-icon {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: $mint; display: flex; align-items: center; justify-content: center;
  font-weight: 800;
}
.dock-t { display: block; font-size: 26rpx; font-weight: 700; }
.dock-s { display: block; font-size: 20rpx; opacity: .7; margin-top: 2rpx; }
.dock-btn {
  height: 80rpx; padding: 0 36rpx; border-radius: 999rpx;
  background: $orange; color: #fff; font-size: 28rpx; font-weight: 800;
  display: flex; align-items: center;
}

.search-mask {
  position: absolute; inset: 0; z-index: 20;
  background: rgba(0,0,0,.45);
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 160rpx;
}
.search-box {
  width: 86%; background: #fff; border-radius: 16rpx;
  padding: 20rpx; display: flex; gap: 12rpx; align-items: center;
}
.search-input { flex: 1; font-size: 28rpx; }
.search-go { color: $orange; font-weight: 700; font-size: 28rpx; padding: 8rpx 12rpx; }
</style>
