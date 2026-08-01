<template>
  <view class="page">
    <scroll-view scroll-y class="list">
      <view class="card mode-card">
        <view class="card-title">怎么收货？</view>
        <view class="modes">
          <view class="mode" :class="{ on: deliveryType === 'DELIVERY' }" @click="deliveryType = 'DELIVERY'">
            <text class="mode-t">送货上门</text>
            <text class="mode-s">送到绑定房屋</text>
          </view>
          <view class="mode" :class="{ on: deliveryType === 'PICKUP' }" @click="deliveryType = 'PICKUP'">
            <text class="mode-t">到店自提</text>
            <text class="mode-s">免配送费</text>
          </view>
        </view>

        <view v-if="deliveryType === 'DELIVERY'" class="addr-box">
          <view class="addr-tag">收货地址</view>
          <text class="addr-main">{{ houseStore.label }}</text>
          <text class="addr-tip">满 ¥{{ store.minOrderAmount || 0 }} 起送 · 配送费 ¥{{ store.deliveryFee || 0 }}</text>
          <text v-if="belowMin" class="warn">还差 ¥{{ gap }} 达到起送金额</text>
        </view>
        <view v-else class="addr-box">
          <view class="addr-tag">自提点</view>
          <text class="addr-main">{{ store.address || '社区便利店' }}</text>
          <text class="addr-tip">营业 {{ store.openTime || '08:00' }}-{{ store.closeTime || '22:00' }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">商品清单</view>
        <view v-for="c in cart" :key="c.id" class="line">
          <view class="line-l">
            <image v-if="c.coverUrl" class="thumb" :src="imgUrl(c.coverUrl)" mode="aspectFill" />
            <view v-else class="thumb ph">{{ (c.name || '商').slice(0, 1) }}</view>
            <view>
              <text class="pname">{{ c.name }}</text>
              <text class="pqty">×{{ c.quantity }}</text>
            </view>
          </view>
          <text class="pamt">¥{{ (Number(c.price) * c.quantity).toFixed(2) }}</text>
        </view>
        <view class="bill">
          <view class="bill-row"><text>商品金额</text><text>¥{{ goods.toFixed(2) }}</text></view>
          <view class="bill-row">
            <text>配送费</text>
            <text>{{ deliveryType === 'DELIVERY' ? '¥' + Number(store.deliveryFee || 0).toFixed(2) : '免运费' }}</text>
          </view>
          <view v-if="couponDiscount > 0" class="bill-row">
            <text>优惠券</text>
            <text class="red">-¥{{ couponDiscount.toFixed(2) }}</text>
          </view>
          <view class="bill-row total"><text>应付</text><text class="red">¥{{ payAmount }}</text></view>
        </view>
      </view>

      <view class="card">
        <view class="card-title">优惠券</view>
        <view class="coupon-opt" :class="{ on: selectedClaimId == null }" @click="selectCoupon(null)">
          <view class="coupon-opt-main">
            <text class="coupon-opt-t">不使用优惠券</text>
          </view>
          <view class="radio" :class="{ on: selectedClaimId == null }" />
        </view>
        <view
          v-for="c in myCoupons"
          :key="c.claimId"
          class="coupon-opt"
          :class="{ on: selectedClaimId === c.claimId, disabled: !c.usable }"
          @click="selectCoupon(c)"
        >
          <view class="coupon-opt-main">
            <text class="coupon-opt-t">{{ c.name }}</text>
            <text class="coupon-opt-s">{{ c.ruleText }}{{ c.usable ? ` · 可减¥${Number(c.discountPreview || 0).toFixed(2)}` : ` · ${c.unusableReason || '暂不可用'}` }}</text>
          </view>
          <view class="radio" :class="{ on: selectedClaimId === c.claimId }" />
        </view>
        <text v-if="!myCoupons.length" class="tip">暂无可用优惠券，可先到店铺领取</text>
      </view>

      <view class="card">
        <view class="card-title">支付方式</view>
        <view class="ch" :class="{ on: channel === 'ALIPAY' }" @click="channel = 'ALIPAY'">
          <text class="ch-t">支付宝（沙盒）</text>
          <view class="radio" :class="{ on: channel === 'ALIPAY' }" />
        </view>
        <view class="ch" :class="{ on: channel === 'WECHAT' }" @click="channel = 'WECHAT'">
          <text class="ch-t">微信支付（演示模拟）</text>
          <view class="radio" :class="{ on: channel === 'WECHAT' }" />
        </view>
        <view v-if="alipayReady === false && channel === 'ALIPAY'" class="warn-pay">
          后端尚未配置支付宝密钥，请检查 community-repair 的 application-local.yml
        </view>
        <text v-else-if="channel === 'ALIPAY'" class="tip">将跳转支付宝沙盒收银台，请使用沙箱买家账号登录支付</text>
        <text v-else class="tip">微信渠道为本地模拟支付，可选成功 / 失败 / 处理中。</text>
      </view>
      <view class="pad" />
    </scroll-view>

    <view class="foot-bar">
      <view class="sum-wrap">
        <text class="pay-lab">实付</text>
        <text class="pay-num">¥{{ payAmount }}</text>
      </view>
      <view
        class="btn"
        :class="{ disabled: belowMin || loading }"
        @click="submit"
      >{{ belowMin ? '未满起送' : (loading ? '支付中…' : '提交并支付') }}</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  createStoreAlipayPay,
  createStoreOrder,
  getStoreInfo,
  listCart,
  listMyShopCoupons,
  openAlipayPayForm,
  payStoreOrder,
  pickMockPayAction,
} from '../../api/store'
import { getAlipayStatus } from '../../api/payment'
import { useHouseStore } from '../../store/house'
import { getBaseUrl } from '../../utils/request'

const houseStore = useHouseStore()
const store = ref({})
const cart = ref([])
const deliveryType = ref('DELIVERY')
const channel = ref('ALIPAY')
const loading = ref(false)
const alipayReady = ref(null)
const myCoupons = ref([])
const selectedClaimId = ref(null)

const goods = computed(() =>
  cart.value.reduce((s, c) => s + Number(c.price || 0) * Number(c.quantity || 0), 0),
)
const shopId = computed(() => {
  const ids = [...new Set(cart.value.map((c) => c.shopId || c.shop_id).filter(Boolean))]
  return ids.length === 1 ? Number(ids[0]) : null
})
const selectedCoupon = computed(() =>
  myCoupons.value.find((c) => c.claimId === selectedClaimId.value) || null,
)
const couponDiscount = computed(() => {
  if (!selectedCoupon.value?.usable) return 0
  return Number(selectedCoupon.value.discountPreview || 0)
})
const payAmount = computed(() => {
  const fee = deliveryType.value === 'DELIVERY' ? Number(store.value.deliveryFee || 0) : 0
  const pay = goods.value + fee - couponDiscount.value
  return (pay > 0 ? pay : 0).toFixed(2)
})
const belowMin = computed(() => {
  if (deliveryType.value !== 'DELIVERY') return false
  return goods.value < Number(store.value.minOrderAmount || 0)
})
const gap = computed(() => {
  const g = Number(store.value.minOrderAmount || 0) - goods.value
  return g > 0 ? g.toFixed(2) : '0.00'
})

function selectCoupon(c) {
  if (!c) {
    selectedClaimId.value = null
    return
  }
  if (!c.usable) {
    uni.showToast({ title: c.unusableReason || '暂不可用', icon: 'none' })
    return
  }
  selectedClaimId.value = c.claimId
}

async function loadCoupons() {
  if (!shopId.value) {
    myCoupons.value = []
    selectedClaimId.value = null
    return
  }
  myCoupons.value =
    (await listMyShopCoupons(shopId.value, goods.value).catch(() => [])) || []
  if (
    selectedClaimId.value &&
    !myCoupons.value.some((c) => c.claimId === selectedClaimId.value && c.usable)
  ) {
    selectedClaimId.value = null
  }
}

onShow(async () => {
  if (!houseStore.guardBound()) return
  store.value = (await getStoreInfo().catch(() => ({}))) || {}
  cart.value = (await listCart().catch(() => [])) || []
  if (!cart.value.length) {
    uni.showToast({ title: '购物车为空', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
    return
  }
  await loadCoupons()
  try {
    const st = await getAlipayStatus()
    alipayReady.value = !!st?.ready
  } catch {
    alipayReady.value = false
  }
})

async function submit() {
  if (loading.value || belowMin.value) return
  loading.value = true
  try {
    const order = await createStoreOrder({
      deliveryType: deliveryType.value,
      houseId: houseStore.currentHouseId,
      claimId: selectedClaimId.value || undefined,
    })
    const orderNo = order.orderNo || order.order_no

    if (channel.value === 'ALIPAY') {
      const pay = await createStoreAlipayPay(orderNo, 'page')
      if (!pay?.payForm) throw { msg: '未拿到支付宝支付表单' }
      openAlipayPayForm(pay.payForm)
      return
    }

    const action = await pickMockPayAction()
    if (action === 'cancel') {
      await payStoreOrder(orderNo, 'cancel', channel.value).catch(() => null)
      uni.showToast({ title: '已取消支付', icon: 'none' })
      setTimeout(() => uni.redirectTo({ url: '/pages/mall/orders?tab=UNPAID' }), 400)
      return
    }
    if (action === 'pending') {
      await payStoreOrder(orderNo, 'pending', channel.value)
      uni.showToast({ title: '支付处理中', icon: 'none' })
      setTimeout(() => uni.redirectTo({ url: '/pages/mall/orders?tab=UNPAID' }), 400)
      return
    }
    if (action === 'fail') {
      try {
        await payStoreOrder(orderNo, 'fail', channel.value)
      } catch {
        /* 后端会抛模拟失败 */
      }
      uni.showToast({ title: '支付失败，可在订单中重试', icon: 'none' })
      setTimeout(() => uni.redirectTo({ url: '/pages/mall/orders?tab=UNPAID' }), 500)
      return
    }
    await payStoreOrder(orderNo, 'success', channel.value)
    uni.showToast({ title: '支付成功', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/mall/orders' }), 400)
  } catch (e) {
    uni.showToast({ title: e?.msg || e?.message || '下单失败', icon: 'none' })
  } finally {
    loading.value = false
  }
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
  background: linear-gradient(180deg, #eaf7f1 0%, #f5f7f6 220rpx);
  box-sizing: border-box;
}
.list {
  flex: 1;
  height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 10rpx 28rpx rgba(47, 158, 111, 0.06);
}
.card-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #1f2a28;
  margin-bottom: 20rpx;
}
.modes { display: flex; gap: 16rpx; }
.mode {
  flex: 1;
  padding: 22rpx 18rpx;
  border-radius: 18rpx;
  background: #f3f6f4;
  border: 2rpx solid transparent;
}
.mode.on {
  background: #e8f8f1;
  border-color: $mint;
}
.mode-t { display: block; font-size: 28rpx; font-weight: 800; color: #1f2a28; }
.mode-s { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693; }
.mode.on .mode-t { color: #2f9e6f; }
.addr-box {
  margin-top: 22rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f7faf8;
}
.addr-tag {
  display: inline-block;
  font-size: 20rpx;
  color: $mint;
  background: #e8f5f2;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  margin-bottom: 10rpx;
}
.addr-main { display: block; font-size: 28rpx; font-weight: 700; color: #1f2a28; line-height: 1.4; }
.addr-tip { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a9693; }
.warn { display: block; margin-top: 10rpx; color: #e85d4c; font-size: 22rpx; }
.warn-pay { display: block; margin-top: 8rpx; color: #e85d4c; font-size: 22rpx; line-height: 1.5; }
.line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.line-l { display: flex; align-items: center; gap: 14rpx; flex: 1; min-width: 0; }
.thumb, .ph {
  width: 72rpx;
  height: 72rpx;
  border-radius: 12rpx;
  background: #e8f5f2;
  flex-shrink: 0;
}
.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $mint;
  font-weight: 800;
}
.pname { display: block; font-size: 26rpx; color: #1f2a28; font-weight: 600; }
.pqty { display: block; margin-top: 4rpx; font-size: 22rpx; color: #8a9693; }
.pamt { font-weight: 700; color: #1f2a28; }
.bill { margin-top: 12rpx; }
.bill-row {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
  font-size: 26rpx;
  color: #5a6562;
}
.bill-row.total {
  margin-top: 8rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0f3f2;
  font-weight: 800;
  color: #1f2a28;
}
.red { color: #e85d4c; font-size: 34rpx; }
.ch {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: #f5f7f6;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.ch.on { border-color: $mint; background: #e8f7f0; }
.ch-t { font-size: 28rpx; font-weight: 500; }
.coupon-opt {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
  padding: 22rpx 20rpx;
  border-radius: 16rpx;
  background: #f5f7f6;
  margin-bottom: 12rpx;
  border: 2rpx solid transparent;
}
.coupon-opt.on { border-color: $mint; background: #e8f7f0; }
.coupon-opt.disabled { opacity: 0.55; }
.coupon-opt-main { flex: 1; min-width: 0; }
.coupon-opt-t { display: block; font-size: 28rpx; font-weight: 700; color: #1f2a28; }
.coupon-opt-s { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693; line-height: 1.35; }
.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #c5d0cc;
  flex-shrink: 0;
}
.radio.on {
  background: $mint;
  border-color: $mint;
  box-shadow: inset 0 0 0 6rpx #fff;
}
.tip { display: block; font-size: 22rpx; color: #8a9693; line-height: 1.5; }
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
.pay-lab { color: #8a9693; font-size: 24rpx; margin-right: 8rpx; }
.pay-num { color: #e85d4c; font-size: 36rpx; font-weight: 800; }
.btn {
  flex-shrink: 0;
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  border-radius: 999rpx;
  padding: 0 36rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-weight: 800;
  font-size: 28rpx;
  text-align: center;
}
.btn.disabled { opacity: 0.5; }
</style>
