<template>
  <div class="settings-page">
    <div class="page-title">
      <h2>系统设置</h2>
      <p class="sub">账号安全与消息推送 · 两项配置独立保存</p>
    </div>

    <!-- 账号安全 -->
    <section class="setting-card">
      <div class="card-head">
        <h3>账号安全</h3>
        <p>修改本店商户登录密码，仅影响当前登录账号</p>
      </div>
      <el-form class="pwd-form" label-width="110px" @submit.prevent>
        <el-form-item label="登录账号">
          <el-input :model-value="loginPhone || user.username" disabled />
        </el-form-item>
        <el-form-item label="原密码" required>
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            placeholder="至少6位"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="pwdSaving" @click="savePassword">保存修改</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 消息推送设置 -->
    <section class="setting-card">
      <div class="card-head">
        <h3>消息推送设置</h3>
        <p>开启后，相关事件将向本店账号推送站内消息</p>
      </div>
      <div class="notify-list">
        <el-checkbox v-model="notify.notifyNewOrder">新订单推送通知</el-checkbox>
        <el-checkbox v-model="notify.notifyOrderCancel">订单取消提醒</el-checkbox>
        <el-checkbox v-model="notify.notifyPlatformNotice">平台公告消息通知</el-checkbox>
        <el-checkbox v-model="notify.notifyStockLow">库存不足预警通知</el-checkbox>
      </div>
      <div class="card-actions">
        <el-button type="primary" :loading="notifySaving" @click="saveNotify">保存通知配置</el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePasswordApi } from '@/api/auth'
import { getMyMallShop, updateMyMallNotifySettings } from '@/api/mall'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const pwdSaving = ref(false)
const notifySaving = ref(false)
const loginPhone = ref('')

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const notify = reactive({
  notifyNewOrder: true,
  notifyOrderCancel: true,
  notifyPlatformNotice: true,
  notifyStockLow: true,
})

async function loadShop() {
  try {
    const s: any = (await getMyMallShop()) || {}
    loginPhone.value = s.loginPhone || ''
    notify.notifyNewOrder = s.notifyNewOrder !== false
    notify.notifyOrderCancel = s.notifyOrderCancel !== false
    notify.notifyPlatformNotice = s.notifyPlatformNotice !== false
    notify.notifyStockLow = s.notifyStockLow !== false
  } catch {
    loginPhone.value = ''
  }
}

async function savePassword() {
  if (!form.oldPassword || !form.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (form.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePasswordApi(form.oldPassword, form.newPassword)
    ElMessage.success('密码已修改，请牢记新密码')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
  } finally {
    pwdSaving.value = false
  }
}

async function saveNotify() {
  notifySaving.value = true
  try {
    await updateMyMallNotifySettings({ ...notify })
    ElMessage.success('通知配置已保存')
  } finally {
    notifySaving.value = false
  }
}

onMounted(loadShop)
</script>

<style scoped>
.settings-page {
  max-width: 720px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.page-title .sub {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.setting-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px 16px;
  box-shadow: 0 1px 3px rgba(15, 118, 110, 0.06), 0 4px 16px rgba(15, 23, 42, 0.04);
  border: 1px solid #e7efed;
}

.card-head {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f7f5;
}

.card-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: #0f766e;
}

.card-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.pwd-form {
  max-width: 480px;
}

.pwd-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.pwd-form :deep(.el-form-item:last-child) {
  margin-bottom: 8px;
}

.notify-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 8px;
}

.notify-list :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}

.notify-list :deep(.el-checkbox__label) {
  color: #334155;
  font-size: 14px;
}

.card-actions {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px solid #f0f7f5;
}
</style>
