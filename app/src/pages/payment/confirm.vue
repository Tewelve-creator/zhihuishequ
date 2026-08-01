<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">确认缴费</view>
      <view class="row"><text class="k">房屋</text><text class="v">{{ houseLabel }}</text></view>
      <view class="row"><text class="k">账单笔数</text><text class="v">{{ bills.length }} 笔</text></view>
    </view>

    <view class="card">
      <view class="card-title">账单明细</view>
      <view v-for="b in bills" :key="b.id" class="line">
        <view>
          <text class="t">{{ b.title }}</text>
          <text class="d">{{ b.billNo }}</text>
        </view>
        <text class="a">¥{{ formatMoney(b.payable) }}</text>
      </view>
      <view class="total">
        <text>应付金额（以后端订单为准）</text>
        <text class="pay">¥{{ formatMoney(displayAmount) }}</text>
      </view>
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
      <view v-if="alipayReady === false && channel === 'ALIPAY'" class="warn">
        后端尚未配置支付宝密钥：请在 community-repair 的 application-local.yml 填写 app-id / private-key / alipay-public-key
      </view>
      <view v-else-if="channel === 'ALIPAY'" class="tip-inline">
        将跳转支付宝沙盒收银台，请使用沙箱买家账号登录支付
      </view>
    </view>

    <button class="btn-primary" :loading="submitting" :disabled="submitting" @click="submit">
      确认支付 ¥{{ formatMoney(displayAmount) }}
    </button>
    <view class="tip">
      支付宝：真实沙盒收银台；微信：本地模拟支付。支付完成后会回到结果页并自动查单。
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useHouseStore } from '../../store/house'
import {
  createAlipayPay,
  createFeeOrder,
  formatMoney,
  getAlipayStatus,
  getBillDetail,
  mockInvokePay,
  openAlipayPayForm
} from '../../api/payment'

const houseStore = useHouseStore()
const bills = ref([])
const channel = ref('ALIPAY')
const submitting = ref(false)
const orderAmount = ref(null)
const alipayReady = ref(null)
let billIds = []

const houseLabel = computed(
  () => houseStore.label || bills.value[0]?.houseLabel || '—'
)
const displayAmount = computed(() => {
  if (orderAmount.value != null) return orderAmount.value
  return bills.value.reduce((s, b) => s + Number(b.payable || 0), 0)
})

onLoad(async (q) => {
  if (!houseStore.guardBound()) return
  billIds = String(q.billIds || '')
    .split(',')
    .filter(Boolean)
  if (!billIds.length) {
    uni.showToast({ title: '未选择账单', icon: 'none' })
    return
  }
  const list = []
  for (const id of billIds) {
    try {
      list.push(await getBillDetail(id))
    } catch (e) {
      /* skip */
    }
  }
  bills.value = list
  try {
    const st = await getAlipayStatus()
    alipayReady.value = !!st?.ready
  } catch {
    alipayReady.value = false
  }
})

async function submit() {
  if (submitting.value) return
  if (!bills.value.length) {
    uni.showToast({ title: '没有可支付账单', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const order = await createFeeOrder({
      billIds: bills.value.map((b) => b.id),
      payChannel: channel.value
    })
    orderAmount.value = order.amount
    const orderId = order.orderId || order.orderNo

    if (channel.value === 'ALIPAY') {
      const pay = await createAlipayPay(orderId, 'page')
      if (!pay?.payForm) {
        throw { msg: '未拿到支付宝支付表单' }
      }
      // 跳转沙盒收银台；支付完成后由 return_url 回跳结果页
      openAlipayPayForm(pay.payForm)
      return
    }

    // 微信：保留原演示模拟
    const action = await pickPayAction()
    if (action === 'cancel') {
      await mockInvokePay(orderId, 'cancel')
      uni.redirectTo({ url: `/pages/payment/result?orderId=${orderId}&from=cancel` })
      return
    }
    await mockInvokePay(orderId, action === 'fail' ? 'fail' : 'success')
    uni.redirectTo({ url: `/pages/payment/result?orderId=${orderId}` })
  } catch (e) {
    uni.showToast({ title: e?.msg || e?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function pickPayAction() {
  return new Promise((resolve) => {
    uni.showActionSheet({
      itemList: ['模拟支付成功', '模拟支付处理中', '模拟支付失败', '取消支付'],
      success: (res) => {
        const map = ['success', 'pending', 'fail', 'cancel']
        resolve(map[res.tapIndex] || 'success')
      },
      fail: () => resolve('cancel')
    })
  })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.row { display: flex; justify-content: space-between; padding: 10rpx 0; font-size: 26rpx; }
.k { color: #8a9693; }
.line {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18rpx 0; border-bottom: 1rpx solid #f0f3f2;
}
.t { display: block; font-size: 28rpx; font-weight: 600; }
.d { display: block; font-size: 22rpx; color: #8a9693; margin-top: 4rpx; }
.a { color: #e85d4c; font-weight: 600; }
.total {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 16rpx; font-size: 26rpx; color: #8a9693;
}
.pay { font-size: 36rpx; font-weight: 700; color: #e85d4c; }
.ch {
  display: flex; justify-content: space-between; align-items: center;
  padding: 28rpx 24rpx; border-radius: 20rpx; background: #f5f7f6; margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.ch.on { border-color: $mint; background: #e8f7f0; }
.ch-t { font-size: 28rpx; font-weight: 500; }
.radio {
  width: 36rpx; height: 36rpx; border-radius: 50%; border: 2rpx solid #c5d0cc;
}
.radio.on { background: $mint; border-color: $mint; box-shadow: inset 0 0 0 6rpx #fff; }
.tip { margin-top: 20rpx; font-size: 22rpx; color: #8a9693; line-height: 1.5; }
.tip-inline { font-size: 22rpx; color: #64748b; line-height: 1.5; margin-top: 4rpx; }
.warn { font-size: 22rpx; color: #b45309; line-height: 1.5; margin-top: 4rpx; }
</style>
