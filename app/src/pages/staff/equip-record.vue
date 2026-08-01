<template>
  <view class="page">
    <view class="card">
      <text class="title">登记维保记录</text>
      <text class="muted">完成后可一键按周期顺延下次维保日期</text>
    </view>

    <view class="card">
      <view class="field col">
        <text class="label">维保内容（必填）</text>
        <textarea v-model="content" class="area" placeholder="填写本次保养项目、更换配件、检测结果等" />
      </view>
      <view class="field col">
        <text class="label">维保人</text>
        <input v-model="workerName" class="input" placeholder="姓名" />
      </view>
      <view class="field col">
        <text class="label">现场照片（最多3张）</text>
        <view class="photos">
          <view v-for="(u, i) in photos" :key="i" class="ph">
            <image :src="u" mode="aspectFill" class="img" />
            <text class="del" @click="photos.splice(i, 1)">×</text>
          </view>
          <view v-if="photos.length < 3" class="ph add" @click="pickPhoto">+</view>
        </view>
      </view>
      <view class="field row">
        <text class="label">按周期顺延下次维保</text>
        <switch :checked="advanceNext" color="#47b686" @change="onAdvance" />
      </view>
      <view class="field col">
        <text class="label">备注</text>
        <input v-model="remark" class="input" placeholder="选填" />
      </view>
    </view>

    <button class="btn-primary" :loading="submitting" @click="submit">提交维保记录</button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { addEquipRecord } from '../../api/equip'
import { useUserStore } from '../../store/user'
import { uploadFile } from '../../utils/request'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const deviceId = ref(null)
const content = ref('')
const workerName = ref('')
const remark = ref('')
const photos = ref([])
const advanceNext = ref(true)
const submitting = ref(false)

function onAdvance(e) {
  advanceNext.value = !!(e.detail && e.detail.value)
}

function pickPhoto() {
  uni.chooseImage({
    count: 3 - photos.value.length,
    sizeType: ['compressed'],
    success: (res) => {
      photos.value = [...photos.value, ...(res.tempFilePaths || [])].slice(0, 3)
    }
  })
}

function todayYmd() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function submit() {
  if (!store.canPerm(PERMS.EQUIP_MAINT)) {
    uni.showToast({ title: '无权限', icon: 'none' })
    return
  }
  if (!content.value.trim()) {
    uni.showToast({ title: '请填写维保内容', icon: 'none' })
    return
  }
  if (!deviceId.value) {
    uni.showToast({ title: '设备参数错误', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const remoteUrls = []
    for (const path of photos.value) {
      if (!path) continue
      if (/^https?:\/\//i.test(path) && !path.startsWith('blob:')) {
        remoteUrls.push(path)
        continue
      }
      uni.showLoading({ title: '上传照片中' })
      const url = await uploadFile(path)
      const real = typeof url === 'string' ? url : url?.url || ''
      if (real && !String(real).startsWith('blob:')) {
        remoteUrls.push(real)
      }
    }
    uni.hideLoading()
    await addEquipRecord({
      deviceId: Number(deviceId.value),
      maintDate: todayYmd(),
      content: content.value.trim(),
      photoUrls: remoteUrls.join(',') || undefined,
      workerName: workerName.value.trim() || store.nickname || undefined,
      remark: remark.value.trim() || undefined,
      advanceNext: advanceNext.value
    })
    uni.showToast({
      title: advanceNext.value ? '已登记并顺延' : '已登记',
      icon: 'success'
    })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e) {
    uni.hideLoading()
  } finally {
    submitting.value = false
  }
}

onLoad((q) => {
  deviceId.value = q.deviceId
  workerName.value = store.nickname || ''
})
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx 80rpx;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}
.title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2a28;
}
.muted {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.field {
  margin-bottom: 24rpx;
}
.field.col {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.field.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.label {
  font-size: 26rpx;
  color: #334155;
  font-weight: 600;
}
.area {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx;
  background: #f5f7f6;
  border-radius: 16rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}
.input {
  padding: 20rpx;
  background: #f5f7f6;
  border-radius: 16rpx;
  font-size: 28rpx;
}
.photos {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}
.ph {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  position: relative;
  background: #eef2f1;
  overflow: hidden;
}
.ph .img {
  width: 100%;
  height: 100%;
}
.ph.add {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 56rpx;
  color: #94a3b8;
  border: 2rpx dashed #c5cecb;
}
.del {
  position: absolute;
  top: 4rpx;
  right: 8rpx;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  font-size: 24rpx;
}
.btn-primary {
  margin-top: 12rpx;
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
