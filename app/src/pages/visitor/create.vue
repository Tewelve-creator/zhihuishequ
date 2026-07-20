<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">访客信息</view>
      <view class="input-row">
        <text class="label">姓名</text>
        <input v-model="form.visitorName" placeholder="访客姓名" />
      </view>
      <view class="input-row">
        <text class="label">手机</text>
        <input v-model="form.visitorPhone" type="number" placeholder="访客手机" />
      </view>
      <view class="input-row">
        <text class="label">证件</text>
        <input v-model="form.idCard" placeholder="身份证号（可选）" />
      </view>
      <view class="input-row">
        <text class="label">事由</text>
        <input v-model="form.visitReason" placeholder="探亲/维修等" />
      </view>
      <view class="input-row">
        <text class="label">人数</text>
        <input v-model.number="form.visitorCount" type="number" placeholder="1" />
      </view>
      <view class="input-row">
        <text class="label">时间</text>
        <input v-model="form.visitTime" placeholder="如 2026-07-20 14:00:00" />
      </view>
      <button class="btn-primary" :loading="loading" @click="submit">提交预约</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { createVisit } from '../../api/biz'

const form = reactive({
  visitorName: '',
  visitorPhone: '',
  idCard: '',
  visitReason: '',
  visitorCount: 1,
  visitTime: ''
})
const loading = ref(false)

async function submit() {
  if (!form.visitorName || !form.visitorPhone) {
    uni.showToast({ title: '请填写姓名和手机', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await createVisit({ ...form })
    uni.showToast({ title: '预约成功', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/visitor/list' }), 400)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>
