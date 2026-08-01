<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">个人资料</view>

      <view class="avatar-block" @click="pickAvatar">
        <image v-if="previewAvatar" class="avatar-img" :src="previewAvatar" mode="aspectFill" />
        <view v-else class="avatar-fallback">{{ avatarText }}</view>
        <text class="avatar-tip">点击更换头像</text>
      </view>

      <view class="field">
        <text class="label">昵称</text>
        <input v-model="form.nickname" class="input" maxlength="32" placeholder="给自己起个名字" />
      </view>

      <button class="btn-primary" :loading="saving" @click="save">保存</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getProfile, updateProfile } from '../../api/auth'
import { uploadFile, getBaseUrl } from '../../utils/request'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const saving = ref(false)
const form = reactive({ nickname: '', avatar: '' })

const previewAvatar = computed(() => {
  const u = form.avatar
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl()
  return `${base}${u.startsWith('/') ? u : '/' + u}`
})

const avatarText = computed(() => (form.nickname || store.nickname || '居').trim().slice(0, 1))

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  form.nickname = store.nickname || ''
  form.avatar = store.avatar || ''
  try {
    const p = await getProfile()
    if (p?.nickname) form.nickname = p.nickname
    if (p?.avatar !== undefined) form.avatar = p.avatar || ''
  } catch (e) {}
})

function pickAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        const url = await uploadFile(res.tempFilePaths[0])
        form.avatar = typeof url === 'string' ? url : url?.url || ''
      } catch (e) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function save() {
  const nick = (form.nickname || '').trim()
  if (!nick) {
    uni.showToast({ title: '请填写昵称', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const data = await updateProfile({
      nickname: nick,
      avatar: form.avatar || ''
    })
    store.updateProfileLocal({
      nickname: data?.nickname || nick,
      avatar: data?.avatar !== undefined ? data.avatar : form.avatar
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e) {
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0 36rpx;
}
.avatar-img, .avatar-fallback {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
}
.avatar-fallback {
  background: linear-gradient(145deg, #6ed3a8, $mint);
  color: #fff;
  font-size: 56rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-tip {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.field { margin-bottom: 24rpx; }
.label {
  display: block;
  font-size: 24rpx;
  color: #5a6562;
  font-weight: 600;
  margin-bottom: 10rpx;
}
.input {
  background: #f5f7f6;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx;
}
.btn-primary {
  margin-top: 12rpx;
  background: $mint;
  color: #fff;
  border: none;
  border-radius: 999rpx;
}
</style>
