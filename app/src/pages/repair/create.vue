<template>
  <view class="page">
    <view v-if="store.isStaff" class="empty-card">
      <text class="empty-title">暂无内容</text>
      <text class="muted">物业端不可提交报修，请在工单列表处理社区工单</text>
      <button class="btn-primary" @click="goList">查看工单列表</button>
    </view>

    <template v-else>
      <view class="card">
        <view class="card-title">报修信息</view>
        <view class="field">
          <text class="label">标题</text>
          <input v-model="form.title" class="input" placeholder="简要描述问题" />
        </view>
        <view class="field col">
          <text class="label">详情</text>
          <textarea v-model="form.description" class="area" placeholder="位置、现象等" />
        </view>
        <view class="field">
          <text class="label">故障类型</text>
          <input v-model="form.facilityType" class="input" placeholder="AI识别或手动填写" />
        </view>
        <view class="field">
          <text class="label">损坏程度</text>
          <input v-model="form.damageLevel" class="input" placeholder="轻度/中度/重度" />
        </view>
        <view v-if="form.aiTag" class="ai-tag">AI识别：{{ form.aiTag }}</view>
      </view>

      <view class="card">
        <view class="card-title">拍照上传 · AI 识图</view>
        <view class="imgs">
          <view v-if="previewSrc" class="preview-box" @click="chooseImage">
            <image :src="previewSrc" class="preview" mode="aspectFit" />
            <text class="preview-tip">点击更换</text>
          </view>
          <view v-else class="upload" @click="chooseImage">
            <text class="plus">+</text>
            <text class="up-tip">相机 / 相册</text>
          </view>
        </view>
        <button
          v-if="form.imageUrl"
          class="btn-ghost"
          size="mini"
          :loading="aiLoading"
          @click="runAi"
        >
          重新 AI 识别故障类型
        </button>
        <view v-if="aiTip" class="muted tip">{{ aiTip }}</view>
        <view class="muted tip">识图引擎：配置 AI_PROVIDER=http + 视觉模型（如 qwen3.6-plus）后走真识图，否则为 Mock</view>
      </view>

      <button class="btn-primary submit" :loading="loading" @click="submit">提交报修工单</button>
    </template>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createRepair } from '../../api/biz'
import { imageRepair } from '../../api/ai'
import { uploadFile, getBaseUrl } from '../../utils/request'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const form = reactive({
  title: '',
  description: '',
  facilityType: '',
  damageLevel: '',
  imageUrl: '',
  aiTag: ''
})
/** 本地临时路径，用于立刻预览（不受网关鉴权影响） */
const localPath = ref('')
const loading = ref(false)
const aiLoading = ref(false)
const aiTip = ref('')

const serverDisplayUrl = computed(() => {
  const u = form.imageUrl
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl() // H5 为 /api，App 为网关地址
  return `${base}${u.startsWith('/') ? u : '/' + u}`
})

/** 优先本地预览，其次服务端地址 */
const previewSrc = computed(() => localPath.value || serverDisplayUrl.value)

onShow(() => {
  if (!store.token) uni.reLaunch({ url: '/pages/login/login' })
})

function goList() {
  uni.redirectTo({ url: '/pages/repair/list' })
}

function chooseImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const path = res.tempFilePaths[0]
      localPath.value = path
      uni.showLoading({ title: '上传中' })
      try {
        const url = await uploadFile(path)
        form.imageUrl = typeof url === 'string' ? url : url?.url || ''
        if (!form.imageUrl) {
          uni.showToast({ title: '上传失败：无图片地址', icon: 'none' })
          return
        }
        await runAi()
      } catch (e) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function runAi() {
  if (!form.imageUrl) return
  aiLoading.value = true
  try {
    const data = await imageRepair(form.imageUrl)
    if (data?.facilityType) form.facilityType = data.facilityType
    if (data?.damageLevel) form.damageLevel = data.damageLevel
    if (data?.suggestTitle && !form.title) form.title = data.suggestTitle
    if (data?.suggestDesc && !form.description) form.description = data.suggestDesc
    const tag = [data?.facilityType, data?.damageLevel].filter(Boolean).join(' · ')
    form.aiTag = tag
    const provider = data?.provider || 'mock'
    aiTip.value = tag
      ? `已识别：${tag}（${provider}，置信度 ${data?.confidence != null ? Math.round(data.confidence * 100) + '%' : '-'}）`
      : '未识别到明确故障，请手动填写'
  } catch (e) {
    aiTip.value = '识图失败，请手动填写'
  } finally {
    aiLoading.value = false
  }
}

async function submit() {
  if (store.isStaff) return
  if (!store.guardFeature('basic_repair')) return
  if (store.isFrozen) {
    uni.showToast({ title: '租户已冻结，无法新建工单', icon: 'none' })
    return
  }
  if (!form.title) {
    uni.showToast({ title: '请填写标题', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await createRepair({
      title: form.title,
      description: form.description,
      facilityType: form.facilityType,
      damageLevel: form.damageLevel,
      imageUrl: form.imageUrl,
      aiTag: form.aiTag
    })
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/repair/list' }), 400)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100vh;
  padding: 28rpx;
  background: #f5f7f6;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.card-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
  color: #1f2a28;
}
.field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.field.col {
  flex-direction: column;
  align-items: stretch;
}
.label {
  width: 140rpx;
  color: #8a9693;
  font-size: 26rpx;
  flex-shrink: 0;
}
.input {
  flex: 1;
  height: 72rpx;
  background: #f0f7f5;
  border-radius: 16rpx;
  padding: 0 20rpx;
}
.area {
  width: 100%;
  min-height: 140rpx;
  background: #f0f7f5;
  border-radius: 16rpx;
  padding: 16rpx 20rpx;
  box-sizing: border-box;
}
.ai-tag {
  margin-top: 8rpx;
  display: inline-block;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #ccfbf1;
  color: $mint;
  font-size: 24rpx;
}
.imgs {
  width: 100%;
}
.upload {
  width: 100%;
  height: 360rpx;
  border: 2rpx dashed $mint;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  color: $mint;
  background: #f7fbf9;
  box-sizing: border-box;
}
.plus {
  font-size: 64rpx;
  line-height: 1;
}
.up-tip {
  font-size: 24rpx;
}
.preview-box {
  width: 100%;
  height: 360rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: #eef3f1;
  position: relative;
  box-sizing: border-box;
}
.preview {
  width: 100%;
  height: 100%;
  display: block;
}
.preview-tip {
  position: absolute;
  right: 16rpx;
  bottom: 16rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
}
.btn-ghost {
  margin-top: 16rpx;
  background: #e8f7f0 !important;
  color: $mint !important;
  border: none;
  border-radius: 28rpx;
}
.tip {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #8a9693;
  line-height: 1.5;
}
.btn-primary {
  background: $mint !important;
  color: #fff !important;
  border: none;
  border-radius: 44rpx;
}
.submit {
  margin-top: 8rpx;
}
.empty-card {
  margin-top: 80rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 48rpx 32rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.empty-title {
  font-size: 32rpx;
  font-weight: 700;
}
.muted {
  color: #8a9693;
  font-size: 26rpx;
}
</style>
