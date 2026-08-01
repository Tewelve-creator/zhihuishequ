<template>
  <view class="page">
    <view v-if="store.isStaff" class="empty-card">
      <text class="empty-title">暂无内容</text>
      <text class="muted">物业端不可提交报修，请在工单列表处理社区工单</text>
      <button class="btn-primary" @click="goList">查看工单列表</button>
    </view>

    <template v-else>
      <view class="card">
        <view class="card-title">① 拍照上传 · AI 自动识别损坏程度</view>
        <view class="imgs">
          <view v-if="previewSrc" class="preview-box" @click="chooseImage">
            <image :src="previewSrc" class="preview" mode="aspectFit" />
            <text class="preview-tip">点击更换</text>
          </view>
          <view v-else class="upload" @click="chooseImage">
            <text class="plus">+</text>
            <text class="up-tip">拍摄或选择损坏照片</text>
            <text class="up-sub">上传后自动识别损坏程度并上报物业</text>
          </view>
        </view>
        <button
          v-if="form.imageUrl"
          class="btn-ghost"
          size="mini"
          :loading="aiLoading"
          @click="runAi"
        >
          重新 AI 识别
        </button>
        <view v-if="form.aiTag" class="ai-tag">AI识别：{{ form.aiTag }}</view>
        <view v-if="confidence != null" class="conf">
          置信度 {{ Math.round(confidence * 100) }}%
          <text v-if="needManual" class="warn">（偏低，请核对下方结果）</text>
        </view>
        <view v-if="aiTip" class="muted tip">{{ aiTip }}</view>
      </view>

      <view class="card">
        <view class="card-title">② 确认报修信息</view>
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
          <picker v-if="needManual" :range="facilityOptions" @change="onFacilityPick">
            <view class="input picker">{{ form.facilityType || '请手动选择设施类型' }}</view>
          </picker>
          <input v-else v-model="form.facilityType" class="input" placeholder="AI识别或手动填写" />
        </view>
        <view class="field">
          <text class="label">房屋位置</text>
          <view class="input readonly">{{ houseStore.shortLabel || '请先绑定房屋' }}</view>
        </view>
        <view class="field">
          <text class="label">具体位置</text>
          <picker v-if="needManual" :range="locationOptions" @change="onLocationPick">
            <view class="input picker">{{ form.locationHint || '楼道/电梯厅等（可选）' }}</view>
          </picker>
          <input v-else v-model="form.locationHint" class="input" placeholder="如：楼道、电梯厅（可选）" />
        </view>
        <view class="field">
          <text class="label">损坏程度</text>
          <picker :range="damageOptions" @change="onDamagePick">
            <view class="input picker damage">{{ form.damageLevel || 'AI自动识别 / 请选择' }}</view>
          </picker>
        </view>
        <view class="muted tip">提交后工单将进入物业「工单列表」，损坏程度会映射为紧急等级</view>
      </view>

      <button class="btn-primary submit" :loading="loading" @click="submit">提交并上报物业</button>
      <button class="btn-ghost submit" @click="goButler">不确定？问智能管家</button>
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
import { useHouseStore } from '../../store/house'

const store = useUserStore()
const houseStore = useHouseStore()
const form = reactive({
  title: '',
  description: '',
  facilityType: '',
  damageLevel: '',
  locationHint: '',
  imageUrl: '',
  aiTag: ''
})
const localPath = ref('')
const loading = ref(false)
const aiLoading = ref(false)
const aiTip = ref('')
const confidence = ref(null)
const needManual = ref(false)
const facilityOptions = ['路灯', '电梯', '健身器材', '楼道门窗', '水管', '门禁/单元门', '消防设施', '公共设施']
const locationOptions = ['楼道', '单元门口', '电梯厅', '健身区', '地下室', '室外公共区域', '其他']
const damageOptions = ['轻微', '中度', '严重']

function onFacilityPick(e) {
  form.facilityType = facilityOptions[Number(e.detail.value)]
}
function onLocationPick(e) {
  form.locationHint = locationOptions[Number(e.detail.value)]
}
function onDamagePick(e) {
  form.damageLevel = damageOptions[Number(e.detail.value)]
}
function goButler() {
  uni.switchTab({ url: '/pages/ai/chat' })
}

const serverDisplayUrl = computed(() => {
  const u = form.imageUrl
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl()
  return `${base}${u.startsWith('/') ? u : '/' + u}`
})

const previewSrc = computed(() => localPath.value || serverDisplayUrl.value)

onShow(() => {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  if (!store.isStaff && !houseStore.guardBound()) {
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) }), 300)
  }
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
      uni.showLoading({ title: '上传并识别中' })
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
  if (!store.can('ai_image_repair')) {
    needManual.value = true
    aiTip.value = '当前套餐暂未开放识图，请手动填写'
    uni.showToast({ title: '暂未开放', icon: 'none' })
    return
  }
  aiLoading.value = true
  try {
    const data = await imageRepair(form.imageUrl)
    if (data?.facilityType) form.facilityType = data.facilityType
    if (data?.damageLevel) form.damageLevel = data.damageLevel
    if (data?.locationHint) form.locationHint = data.locationHint
    if (data?.suggestTitle && !form.title) form.title = data.suggestTitle
    if (data?.suggestDesc && !form.description) form.description = data.suggestDesc
    confidence.value = data?.confidence != null ? Number(data.confidence) : null
    needManual.value = !!data?.needManual || (confidence.value != null && confidence.value < 0.85)
    const tag = [data?.facilityType, data?.locationHint, data?.damageLevel].filter(Boolean).join(' · ')
    form.aiTag = tag
    const provider = data?.provider || 'mock'
    aiTip.value = tag
      ? `已识别损坏程度「${data.damageLevel || '-'}」· ${tag}（${provider}）`
      : '未识别到明确故障，请手动选择损坏程度'
    if (!form.damageLevel) {
      needManual.value = true
      uni.showToast({ title: '请手动选择损坏程度', icon: 'none' })
    } else if (needManual.value) {
      uni.showToast({ title: '请核对识别结果', icon: 'none' })
    } else {
      uni.showToast({ title: `损坏程度：${form.damageLevel}`, icon: 'none' })
    }
  } catch (e) {
    needManual.value = true
    aiTip.value = '识图失败，请手动选择故障类型与损坏程度'
    uni.showToast({ title: '识图失败，请手选', icon: 'none' })
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
  if (!form.imageUrl) {
    uni.showToast({ title: '请先拍照上传损坏图片', icon: 'none' })
    return
  }
  if (!form.damageLevel) {
    uni.showToast({ title: '请确认 AI 识别的损坏程度', icon: 'none' })
    return
  }
  if (!form.facilityType) {
    uni.showToast({ title: '请填写或选择故障类型', icon: 'none' })
    return
  }
  if (!store.isStaff && !houseStore.currentHouseId) {
    uni.showToast({ title: '请先绑定房屋', icon: 'none' })
    return
  }
  if (!form.title) {
    form.title = `${form.facilityType || '设施'}报修（${form.damageLevel}）`
  }
  loading.value = true
  try {
    const desc = [
      form.description,
      form.locationHint ? `位置：${form.locationHint}` : '',
      `损坏程度：${form.damageLevel}`
    ]
      .filter(Boolean)
      .join('\n')
    const house = houseStore.currentHouse || {}
    const building = house.building || ''
    const unit = house.unit || ''
    const roomNo = house.roomNo || ''
    const houseAddr = [building, unit, roomNo].filter(Boolean).join('') || houseStore.shortLabel || ''
    await createRepair({
      title: form.title,
      description: desc,
      facilityType: form.facilityType,
      damageLevel: form.damageLevel,
      imageUrl: form.imageUrl,
      aiTag: form.aiTag || [form.facilityType, form.damageLevel].filter(Boolean).join(' · '),
      houseId: houseStore.currentHouseId,
      building,
      unit,
      roomNo,
      locationHint: form.locationHint || '',
      locationLabel: houseAddr
    })
    uni.showToast({ title: '已上报物业', icon: 'success' })
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
  min-height: 100dvh;
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
  display: flex;
  align-items: center;
  box-sizing: border-box;
  font-size: 28rpx;
  color: #0f172a;
}
.input.readonly {
  color: #334155;
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
  margin-top: 12rpx;
  display: inline-block;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #ccfbf1;
  color: $mint;
  font-size: 24rpx;
}
.conf {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #64748b;
}
.conf .warn {
  color: #c2410c;
}
.input.picker {
  color: #0f172a;
}
.input.picker.damage {
  font-weight: 600;
  color: #0f766e;
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
  font-size: 28rpx;
  font-weight: 600;
}
.up-sub {
  font-size: 22rpx;
  color: #8a9693;
  margin-top: 4rpx;
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
