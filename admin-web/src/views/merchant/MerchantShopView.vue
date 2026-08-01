<template>
  <div class="shop-page" v-loading="loading">
    <div class="page-top">
      <div>
        <h2>店铺信息</h2>
        <p class="sub">维护店铺形象、基础信息与简介 · 仅可修改本店数据</p>
      </div>
      <div class="top-actions">
        <el-button @click="$router.push('/merchant/account')">修改登录密码</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <!-- 头像 + 封面 -->
    <div class="hero-card">
      <div class="avatar-box" @click="pickAvatar">
        <el-image v-if="form.avatarUrl" :src="fileUrl(form.avatarUrl)" fit="cover" class="avatar-img" />
        <div v-else class="avatar-placeholder">点击更换头像</div>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarFile" />
      </div>
      <div class="cover-box">
        <el-image v-if="form.coverUrl" :src="fileUrl(form.coverUrl)" fit="cover" class="cover-img" />
        <div v-else class="cover-placeholder">店铺封面图</div>
        <el-button class="cover-btn" size="small" :loading="uploadingCover" @click="pickCover">更换封面</el-button>
        <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverFile" />
      </div>
    </div>

    <!-- 三栏信息 -->
    <div class="info-grid">
      <div class="panel">
        <h3>店铺形象卡片</h3>
        <el-form label-position="top">
          <el-form-item label="店铺名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
          <el-form-item label="主营品类">
            <el-select v-model="form.categoryName" filterable allow-create default-first-option style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="一句话简介">
            <el-input v-model="form.shortIntro" maxlength="40" placeholder="展示在店铺卡片上的短简介" />
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <h3>店铺基础信息</h3>
        <el-form label-position="top">
          <el-form-item label="店铺名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
          <el-form-item label="主营品类">
            <el-select v-model="form.categoryName" filterable allow-create default-first-option style="width: 100%">
              <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="店铺地址"><el-input v-model="form.address" /></el-form-item>
          <el-form-item label="营业时间">
            <div class="time-row">
              <el-input v-model="form.openTime" placeholder="08:00" />
              <span>—</span>
              <el-input v-model="form.closeTime" placeholder="22:00" />
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <h3>店铺简介</h3>
        <el-input
          v-model="form.intro"
          type="textarea"
          :rows="14"
          maxlength="800"
          show-word-limit
          placeholder="介绍店铺特色、主营商品与配送服务等"
        />
      </div>
    </div>

    <!-- 登录账号 -->
    <div class="account-bar">
      <div class="acc-left">
        <el-icon :size="18"><Lock /></el-icon>
        <span class="acc-lab">登录账号</span>
        <el-input :model-value="form.loginPhone" disabled style="width: 220px" />
      </div>
      <div class="acc-right">
        <el-tag :type="form.status === 1 ? 'success' : 'info'" effect="dark" round>
          {{ form.status === 1 ? '营业中' : '已停用' }}
        </el-tag>
        <el-button @click="$router.push('/merchant/account')">修改登录密码</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </div>

    <!-- 消息推送订阅设置 -->
    <div class="panel notify-panel">
      <div class="notify-head">
        <div>
          <h3>消息推送订阅设置</h3>
          <p class="notify-desc">取消勾选后，本店将不再接收对应类型的站内消息</p>
        </div>
        <el-button type="primary" :loading="notifySaving" @click="saveNotify">保存通知配置</el-button>
      </div>
      <div class="notify-list">
        <el-checkbox v-model="notify.notifyNewOrder">新订单推送通知</el-checkbox>
        <el-checkbox v-model="notify.notifyOrderCancel">订单取消提醒</el-checkbox>
        <el-checkbox v-model="notify.notifyPlatformNotice">平台公告消息通知</el-checkbox>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { getMyMallShop, updateMyMallNotifySettings, updateMyMallShop } from '@/api/mall'
import { fileUrl, uploadFile } from '@/api/file'

const categoryOptions = ['社区便利店', '社区超市', '社区烘焙', '生鲜果蔬', '日用百货', '餐饮小吃']

const loading = ref(false)
const saving = ref(false)
const notifySaving = ref(false)
const uploadingAvatar = ref(false)
const uploadingCover = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const coverInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  name: '',
  contactName: '',
  contactPhone: '',
  address: '',
  openTime: '08:00',
  closeTime: '22:00',
  loginPhone: '',
  status: 1,
  avatarUrl: '',
  coverUrl: '',
  categoryName: '社区便利店',
  intro: '',
  shortIntro: '',
})

const notify = reactive({
  notifyNewOrder: true,
  notifyOrderCancel: true,
  notifyPlatformNotice: true,
})

async function load() {
  loading.value = true
  try {
    const s: any = (await getMyMallShop()) || {}
    const intro = s.intro || ''
    const avatar = s.avatarUrl || s.avatar_url || ''
    const cover = s.coverUrl || s.cover_url || ''
    Object.assign(form, {
      name: s.name || '',
      contactName: s.contactName || '',
      contactPhone: s.contactPhone || '',
      address: s.address || '',
      openTime: s.openTime || '08:00',
      closeTime: s.closeTime || '22:00',
      loginPhone: s.loginPhone || '',
      status: s.status ?? 1,
      avatarUrl: avatar,
      coverUrl: cover,
      categoryName: s.categoryName || s.category_name || '社区超市',
      intro,
      shortIntro: intro ? String(intro).slice(0, 40) : '',
    })
    notify.notifyNewOrder = s.notifyNewOrder !== false
    notify.notifyOrderCancel = s.notifyOrderCancel !== false
    notify.notifyPlatformNotice = s.notifyPlatformNotice !== false
  } finally {
    loading.value = false
  }
}

function pickAvatar() {
  avatarInput.value?.click()
}
function pickCover() {
  coverInput.value?.click()
}

async function onAvatarFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    form.avatarUrl = await uploadFile(file)
    ElMessage.success('头像已更新')
  } catch {
    ElMessage.error('头像上传失败')
  } finally {
    uploadingAvatar.value = false
    if (avatarInput.value) avatarInput.value.value = ''
  }
}

async function onCoverFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploadingCover.value = true
  try {
    form.coverUrl = await uploadFile(file)
    ElMessage.success('封面已更新')
  } catch {
    ElMessage.error('封面上传失败')
  } finally {
    uploadingCover.value = false
    if (coverInput.value) coverInput.value.value = ''
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写店铺名称')
    return
  }
  saving.value = true
  try {
    const intro = form.intro?.trim() || form.shortIntro?.trim() || ''
    await updateMyMallShop({
      name: form.name,
      contactName: form.contactName,
      contactPhone: form.contactPhone,
      address: form.address,
      openTime: form.openTime,
      closeTime: form.closeTime,
      avatarUrl: form.avatarUrl,
      coverUrl: form.coverUrl,
      categoryName: form.categoryName,
      intro,
    })
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
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

onMounted(load)
</script>

<style scoped>
.shop-page { display: flex; flex-direction: column; gap: 14px; }
.page-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.page-top h2 { margin: 0; font-size: 18px; color: #0f172a; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.top-actions { display: flex; gap: 8px; }

.hero-card {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 16px;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  border: 1px solid #e7efed;
}
.avatar-box {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  background: #f0fdfa;
  border: 2px dashed #99f6e4;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-img { width: 100%; height: 100%; }
.avatar-placeholder { font-size: 12px; color: #0d9488; text-align: center; padding: 8px; }
.cover-box {
  position: relative;
  min-height: 120px;
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(135deg, #ccfbf1, #f0fdfa);
  border: 1px solid #99f6e4;
}
.cover-img { width: 100%; height: 120px; }
.cover-placeholder {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0f766e;
  font-size: 14px;
}
.cover-btn { position: absolute; top: 10px; right: 10px; }

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.panel {
  background: #fff;
  border-radius: 16px;
  padding: 16px 18px;
  border: 1px solid #e7efed;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.03);
}
.panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #0f766e;
}
.time-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.account-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 16px;
  padding: 14px 18px;
  border: 1px solid #e7efed;
}
.acc-left, .acc-right { display: flex; align-items: center; gap: 10px; }
.acc-lab { color: #64748b; font-size: 13px; }

.notify-panel {
  padding-bottom: 18px;
}
.notify-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f7f5;
}
.notify-head h3 { margin: 0; }
.notify-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}
.notify-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 24px;
}
.notify-list :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}
.notify-list :deep(.el-checkbox__label) {
  color: #334155;
  font-size: 14px;
}

@media (max-width: 1100px) {
  .info-grid { grid-template-columns: 1fr; }
  .hero-card { grid-template-columns: 1fr; justify-items: center; }
  .cover-box { width: 100%; }
  .notify-list { grid-template-columns: 1fr; }
  .notify-head { flex-direction: column; }
}
</style>
