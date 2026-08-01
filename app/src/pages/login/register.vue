<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">居民注册 · 绑定租赁社区</view>
      <view class="input-row">
        <text class="label">手机号</text>
        <input v-model="form.phone" type="number" maxlength="11" placeholder="登录账号（11位手机号）" />
      </view>
      <view class="input-row">
        <text class="label">验证码</text>
        <input v-model="smsCode" type="number" maxlength="6" placeholder="演示固定 123456" />
        <text class="sms-btn" @click="sendSms">获取</text>
      </view>
      <view class="input-row">
        <text class="label">密码</text>
        <input v-model="form.password" password placeholder="设置密码" />
      </view>
      <view class="input-row">
        <text class="label">昵称</text>
        <input v-model="form.nickname" placeholder="怎么称呼您" />
      </view>
      <view class="input-row">
        <text class="label">房号</text>
        <input v-model="form.roomNo" placeholder="如 1栋101" />
      </view>
      <view class="input-row" @click="showTenant = true">
        <text class="label">社区</text>
        <text class="flex-1">{{ tenantName || '请选择所属社区' }}</text>
      </view>
      <button class="btn-primary" :loading="loading" @click="onSubmit">注册并登录</button>
    </view>

    <view v-if="showTenant" class="mask" @click="showTenant = false">
      <view class="sheet" @click.stop>
        <view class="sheet-title">选择所属租赁社区</view>
        <view v-for="t in tenants" :key="t.id" class="sheet-item" @click="pick(t)">
          <text class="name">{{ t.name }}</text>
          <text class="muted">{{ t.address }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { register, getSms, login, listPublicTenants } from '../../api/auth'
import { useUserStore } from '../../store/user'
import { DEMO_TENANTS } from '../../utils/constants'

const store = useUserStore()
const form = reactive({
  phone: '',
  password: '',
  nickname: '',
  roomNo: '',
  tenantId: null
})
const smsCode = ref('')
const tenantName = ref('')
const tenants = ref([...DEMO_TENANTS])
const showTenant = ref(false)
const loading = ref(false)

async function loadTenants() {
  try {
    const list = await listPublicTenants()
    if (Array.isArray(list) && list.length) {
      tenants.value = list
    }
  } catch {
    /* 保留演示兜底 */
  }
}

function pick(t) {
  form.tenantId = t.id
  tenantName.value = t.name
  showTenant.value = false
}

async function sendSms() {
  if (!form.phone) {
    uni.showToast({ title: '请先填手机号', icon: 'none' })
    return
  }
  await getSms(form.phone)
  smsCode.value = '123456'
  uni.showToast({ title: '验证码已发送(演示123456)', icon: 'none' })
}

async function onSubmit() {
  if (!form.phone || !form.password || !form.nickname || !form.tenantId || !form.roomNo) {
    uni.showToast({ title: '请完整填写信息并选择社区', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(String(form.phone).trim())) {
    uni.showToast({ title: '请输入正确的 11 位手机号', icon: 'none' })
    return
  }
  if (smsCode.value !== '123456') {
    uni.showToast({ title: '验证码错误', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await register({ ...form })
    const data = await login({
      phone: form.phone,
      password: form.password,
      tenantId: form.tenantId
    })
    store.setSession(data)
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/home/index' }), 400)
  } catch (e) {
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTenants()
})
</script>

<style lang="scss" scoped>
.sms-btn {
  color: #0f766e;
  font-size: 26rpx;
  padding-left: 16rpx;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 99;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.sheet-item {
  padding: 28rpx 8rpx;
  border-bottom: 1rpx solid #e8f2ef;
}
.sheet-item .name {
  display: block;
  font-size: 30rpx;
}
</style>
