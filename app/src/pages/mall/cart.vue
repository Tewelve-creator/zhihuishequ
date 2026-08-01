<template>
  <view class="page">
    <scroll-view scroll-y class="list">
      <view v-for="c in cart" :key="c.id" class="row">
        <image v-if="c.coverUrl" class="cover" :src="imgUrl(c.coverUrl)" mode="aspectFill" />
        <view v-else class="cover ph">{{ (c.name || '商').slice(0, 1) }}</view>
        <view class="mid">
          <text class="name">{{ c.name }}</text>
          <text class="price">¥{{ c.price }}</text>
        </view>
        <view class="ops">
          <view class="op" @click="chg(c, c.quantity - 1)">−</view>
          <text class="q">{{ c.quantity }}</text>
          <view class="op plus" @click="chg(c, c.quantity + 1)">+</view>
        </view>
      </view>
      <view v-if="!cart.length" class="empty">
        <text class="empty-t">购物车还是空的</text>
        <text class="empty-s">去社区超市逛逛吧</text>
        <button class="ghost" @click="goMall">去选购</button>
      </view>
      <view class="pad" />
    </scroll-view>

    <view class="foot-bar" v-if="cart.length">
      <view class="sum-wrap">
        <text class="lab">合计</text>
        <text class="sum">¥{{ total }}</text>
      </view>
      <view class="btn" @click="goCheckout">去结算</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listCart, updateCart } from '../../api/store'
import { getBaseUrl } from '../../utils/request'

const cart = ref([])
const total = computed(() =>
  cart.value.reduce((s, c) => s + Number(c.price || 0) * Number(c.quantity || 0), 0).toFixed(2),
)

onShow(load)
async function load() {
  cart.value = (await listCart().catch(() => [])) || []
}
async function chg(c, q) {
  await updateCart(c.id, q)
  load()
}
function goCheckout() {
  uni.navigateTo({ url: '/pages/mall/checkout' })
}
function goMall() {
  uni.switchTab({ url: '/pages/mall/index' })
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
.page {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(180deg, #eaf7f1 0%, #f5f7f6 180rpx);
  box-sizing: border-box;
}
.list {
  flex: 1;
  height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}
.row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 16rpx;
  padding: 20rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(47, 158, 111, 0.06);
}
.cover, .ph {
  width: 120rpx;
  height: 120rpx;
  border-radius: 16rpx;
  background: #e8f5f2;
  flex-shrink: 0;
}
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $mint;
  font-weight: 800;
  font-size: 40rpx;
}
.mid { flex: 1; min-width: 0; }
.name {
  display: block;
  font-weight: 700;
  color: #1f2a28;
  font-size: 28rpx;
}
.price {
  display: block;
  margin-top: 12rpx;
  color: #e85d4c;
  font-weight: 800;
  font-size: 30rpx;
}
.ops { display: flex; align-items: center; gap: 12rpx; }
.op {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #eef3f1;
  color: #1f2a28;
  text-align: center;
  line-height: 46rpx;
  font-size: 32rpx;
}
.op.plus { background: $mint; color: #fff; }
.q { min-width: 40rpx; text-align: center; font-weight: 700; }
.pad { height: 24rpx; }

.foot-bar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e8eeeb;
  box-sizing: border-box;
}
.sum-wrap { display: flex; align-items: baseline; min-width: 0; }
.lab { color: #8a9693; font-size: 24rpx; margin-right: 8rpx; }
.sum { color: #e85d4c; font-size: 36rpx; font-weight: 800; }
.btn {
  flex-shrink: 0;
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  border-radius: 999rpx;
  padding: 0 36rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 28rpx;
  font-weight: 700;
  text-align: center;
}
.empty {
  text-align: center;
  padding: 120rpx 40rpx;
}
.empty-t { display: block; font-size: 32rpx; font-weight: 700; color: #1f2a28; }
.empty-s { display: block; margin: 12rpx 0 32rpx; color: #8a9693; font-size: 24rpx; }
.ghost {
  display: inline-block;
  background: #e8f5f2;
  color: $mint;
  border: none;
  border-radius: 999rpx;
  padding: 0 40rpx;
  font-weight: 700;
}
</style>
