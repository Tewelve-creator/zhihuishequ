<template>
  <view class="page page-safe">
    <view class="card">
      <view class="section-title">修改密码</view>
      <view class="field">
        <text class="label">原密码</text>
        <input v-model="form.oldPassword" password class="input" placeholder="请输入原密码" />
      </view>
      <view class="field">
        <text class="label">新密码</text>
        <input v-model="form.newPassword" password class="input" placeholder="至少 6 位" />
      </view>
      <view class="field">
        <text class="label">确认新密码</text>
        <input v-model="form.confirm" password class="input" placeholder="再次输入新密码" />
      </view>
      <button class="btn-primary" :loading="loading" @click="submit">确认修改</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { changePassword } from '../../api/auth'

const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function submit() {
  if (!form.oldPassword || !form.newPassword) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  if (form.newPassword.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  if (form.newPassword !== form.confirm) {
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    uni.showToast({ title: '已修改，请重新登录', icon: 'success' })
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/login/login' })
    }, 800)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.field { margin-bottom: 24rpx; }
.label { display: block; font-size: 26rpx; color: #5a6562; margin-bottom: 10rpx; }
.input {
  background: #f5f7f6; border-radius: 12rpx; padding: 20rpx; font-size: 28rpx;
}
.btn-primary {
  margin-top: 12rpx; background: #47b686; color: #fff; border: none; border-radius: 999rpx;
}
</style>
