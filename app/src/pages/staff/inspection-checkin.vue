<template>
  <view class="page">
    <view class="card">
      <text class="title">{{ point?.pointName || '点位打卡' }}</text>
      <text class="muted">{{ point?.locationDesc || '' }}</text>
      <view class="loc-box">
        <text class="loc-lab">GPS 定位校验</text>
        <text class="loc-txt">{{ locText }}</text>
        <view class="relocate" @click="locate">重新定位</view>
      </view>
    </view>

    <view class="card">
      <view class="field row">
        <text class="label">现场情况正常</text>
        <switch :checked="!hasHazard" color="#47b686" @change="onNormalSwitch" />
      </view>
      <view class="field row">
        <text class="label">发现隐患</text>
        <switch :checked="hasHazard" color="#e67e22" @change="onHazardSwitch" />
      </view>

      <view v-if="hasHazard" class="field col">
        <text class="label">隐患描述（必填）</text>
        <textarea v-model="hazardDesc" class="area" placeholder="请描述隐患位置、现象与紧急程度" />
      </view>

      <view class="field col">
        <text class="label">现场照片（可选，最多3张）</text>
        <view class="photos">
          <view v-for="(u, i) in photos" :key="i" class="ph">
            <image :src="u" mode="aspectFill" class="img" />
            <text class="del" @click="photos.splice(i, 1)">×</text>
          </view>
          <view v-if="photos.length < 3" class="ph add" @click="pickPhoto">+</view>
        </view>
      </view>

      <view class="field col">
        <text class="label">备注</text>
        <input v-model="remark" class="input" placeholder="选填" />
      </view>
    </view>

    <button class="btn-primary" :loading="submitting" @click="submit">
      {{ hasHazard ? '上报隐患并完成打卡' : '确认打卡完成' }}
    </button>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { checkinInspection, getMyInspectionTask } from '../../api/inspection'
import { useUserStore } from '../../store/user'
import { uploadFile } from '../../utils/request'

const store = useUserStore()
const taskId = ref(null)
const taskPointId = ref(null)
const task = ref(null)
const hasHazard = ref(false)
const hazardDesc = ref('')
const remark = ref('')
const photos = ref([])
const submitting = ref(false)
const locText = ref('正在获取定位...')
const myLat = ref(null)
const myLng = ref(null)

const point = computed(() =>
  (task.value?.taskPoints || []).find((p) => String(p.id) === String(taskPointId.value))
)

function onNormalSwitch(e) {
  const on = !!(e.detail && e.detail.value)
  hasHazard.value = !on
}

function onHazardSwitch(e) {
  hasHazard.value = !!(e.detail && e.detail.value)
}

function locate() {
  locText.value = '正在获取定位...'
  // #ifdef H5
  if (typeof navigator !== 'undefined' && navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        myLat.value = pos.coords.latitude
        myLng.value = pos.coords.longitude
        locText.value = `当前位置 ${myLat.value.toFixed(5)}, ${myLng.value.toFixed(5)}（偏差≤200米）`
      },
      (err) => {
        myLat.value = null
        myLng.value = null
        const code = err && err.code
        if (code === 1) {
          locText.value =
            '浏览器未授权定位：请点击地址栏左侧「位置」图标 → 允许，然后点「重新定位」'
        } else if (code === 2) {
          locText.value = '无法获取位置信息，请检查系统定位服务是否开启'
        } else if (code === 3) {
          locText.value = '定位超时，请到空旷处重试或点击「重新定位」'
        } else {
          locText.value = '定位失败，请允许本站点访问位置后重试'
        }
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    )
    return
  }
  // #endif
  uni.getLocation({
    type: 'gcj02',
    isHighAccuracy: true,
    highAccuracyExpireTime: 15000,
    success: (res) => {
      myLat.value = res.latitude
      myLng.value = res.longitude
      locText.value = `当前位置 ${res.latitude.toFixed(5)}, ${res.longitude.toFixed(5)}（偏差≤200米）`
    },
    fail: (err) => {
      myLat.value = null
      myLng.value = null
      const msg = (err && (err.errMsg || err.message)) || ''
      if (/auth|deny|permission|权限/i.test(msg)) {
        locText.value = '未授权定位权限，请在系统/浏览器中允许后点「重新定位」'
      } else {
        locText.value = '定位失败，请开启定位权限后重试'
      }
    }
  })
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

async function submit() {
  if (hasHazard.value && !hazardDesc.value.trim()) {
    uni.showToast({ title: '请填写隐患描述', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    // 先上传到服务器，避免 blob: 本地地址无法在 PC 明细查看
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
    const result = await checkinInspection(taskId.value, {
      taskPointId: Number(taskPointId.value),
      latitude: myLat.value,
      longitude: myLng.value,
      photoUrls: remoteUrls.join(','),
      hasHazard: hasHazard.value,
      hazardDesc: hazardDesc.value.trim() || undefined,
      remark: remark.value.trim() || undefined
    })
    uni.showToast({
      title: result?.status === 'DONE' ? '全部点位已完成' : '打卡成功',
      icon: 'success'
    })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch (e) {
    uni.hideLoading()
    // toast by request
  } finally {
    submitting.value = false
  }
}

onLoad(async (q) => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  const tid = q?.taskId
  const tpid = q?.taskPointId
  if (
    tid == null ||
    tpid == null ||
    String(tid) === 'undefined' ||
    String(tpid) === 'undefined'
  ) {
    uni.showToast({ title: '打卡参数错误', icon: 'none' })
    setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
    return
  }
  taskId.value = tid
  taskPointId.value = tpid
  try {
    task.value = await getMyInspectionTask(tid)
  } catch (e) {
    setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
    return
  }
  if (point.value?.checked) {
    uni.showToast({ title: '该点位已打卡', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 500)
    return
  }
  locate()
})
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
}
.muted {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.loc-box {
  margin-top: 24rpx;
  padding: 20rpx;
  background: #f5fbf8;
  border-radius: 16rpx;
}
.loc-lab {
  display: block;
  font-size: 24rpx;
  color: $mint;
  font-weight: 600;
}
.loc-txt {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #3a4543;
  line-height: 1.5;
}
.relocate {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $mint;
  font-weight: 600;
}
.field {
  margin-bottom: 24rpx;
}
.field.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.field.col {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.label {
  font-size: 28rpx;
  color: #3a4543;
  font-weight: 600;
}
.area {
  min-height: 160rpx;
  background: #f5f7f6;
  border-radius: 16rpx;
  padding: 20rpx;
  width: 100%;
  box-sizing: border-box;
}
.input {
  background: #f5f7f6;
  border-radius: 16rpx;
  padding: 20rpx;
}
.photos {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}
.ph {
  width: 150rpx;
  height: 150rpx;
  border-radius: 16rpx;
  position: relative;
  overflow: hidden;
  background: #f5f7f6;
}
.ph.add {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  color: #999;
  border: 1rpx dashed #ccc;
}
.img {
  width: 100%;
  height: 100%;
}
.del {
  position: absolute;
  right: 4rpx;
  top: 0;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  padding: 0 12rpx;
}
.btn-primary {
  margin-top: 12rpx;
  background: $mint;
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 600;
}
</style>
