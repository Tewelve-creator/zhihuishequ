<template>
  <view class="page">
    <view v-if="!item" class="empty">
      <text class="empty-title">暂无内容</text>
      <text class="muted">工单不存在或无权查看</text>
    </view>

    <template v-else>
      <view class="card">
        <view class="title">{{ item.title }}</view>
        <view class="row">
          <text class="muted">状态</text>
          <text class="tag">{{ statusText(item.status) }}</text>
        </view>
        <view class="row">
          <text class="muted">故障类型</text>
          <text>{{ item.facilityType || '-' }}</text>
        </view>
        <view class="row">
          <text class="muted">损坏程度</text>
          <text>{{ item.damageLevel || '-' }}</text>
        </view>
        <view v-if="store.isStaff" class="row">
          <text class="muted">处理人</text>
          <text>{{ item.handlerName || '未指派' }}</text>
        </view>
        <view v-if="item.aiTag" class="ai-chip">AI识别标签：{{ item.aiTag }}</view>
        <view class="desc">{{ item.description || '暂无描述' }}</view>
        <view class="pic-block">
          <text class="pic-label">业主报修照片</text>
          <image v-if="ownerImg" :src="ownerImg" class="pic" mode="widthFix" />
          <view v-else class="muted">暂无内容</view>
        </view>
        <view v-if="finishImg" class="pic-block">
          <text class="pic-label">维修完工照片</text>
          <image :src="finishImg" class="pic" mode="widthFix" />
        </view>
        <view v-if="item.handleRemark" class="remark">处理备注：{{ item.handleRemark }}</view>
      </view>

      <!-- 物业：接单 / 完工 / 上传维修照片 -->
      <view v-if="store.isStaff" class="card">
        <view class="card-title">工单处理</view>
        <view class="actions">
          <button
            v-if="item.status === 'pending'"
            class="btn-primary"
            :loading="loading"
            @click="doAccept"
          >
            接单
          </button>
          <button class="btn-ghost" size="mini" :loading="upLoading" @click="uploadFinishPhoto">
            上传维修照片
          </button>
        </view>
        <view class="field col">
          <text class="label">处理备注</text>
          <textarea v-model="form.handleRemark" class="area" placeholder="维修说明" />
        </view>
        <button
          v-if="item.status === 'processing' || item.status === 'pending'"
          class="btn-primary"
          :loading="loading"
          @click="doFinish"
        >
          确认完工
        </button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getRepair, acceptRepair, finishRepair } from '../../api/biz'
import { statusText } from '../../utils/constants'
import { getBaseUrl, uploadFile } from '../../utils/request'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const item = ref(null)
const loading = ref(false)
const upLoading = ref(false)
const form = reactive({ handleRemark: '', finishImageUrl: '' })
let id = null

function fullUrl(u) {
  if (!u) return ''
  if (u.startsWith('http')) return u
  return getBaseUrl() + (u.startsWith('/') ? u : '/' + u)
}

const ownerImg = computed(() => fullUrl(item.value?.imageUrl))
const finishImg = computed(() => fullUrl(item.value?.finishImageUrl || form.finishImageUrl))

async function reload() {
  try {
    item.value = await getRepair(id)
    form.handleRemark = item.value?.handleRemark || ''
    form.finishImageUrl = item.value?.finishImageUrl || ''
  } catch (e) {
    item.value = null
  }
}

onLoad(async (q) => {
  id = q.id
  await reload()
})

async function doAccept() {
  if (!store.isStaff) return
  loading.value = true
  try {
    item.value = await acceptRepair(id)
    uni.showToast({ title: '已接单', icon: 'success' })
  } catch (e) {
  } finally {
    loading.value = false
  }
}

function uploadFinishPhoto() {
  uni.chooseImage({
    count: 1,
    sourceType: ['camera', 'album'],
    success: async (res) => {
      upLoading.value = true
      try {
        const url = await uploadFile(res.tempFilePaths[0])
        form.finishImageUrl = url
        uni.showToast({ title: '照片已上传', icon: 'success' })
      } catch (e) {
      } finally {
        upLoading.value = false
      }
    }
  })
}

async function doFinish() {
  if (!store.isStaff) return
  loading.value = true
  try {
    item.value = await finishRepair(id, {
      handleRemark: form.handleRemark,
      finishImageUrl: form.finishImageUrl
    })
    uni.showToast({ title: '已完工', icon: 'success' })
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
.title {
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}
.row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}
.muted {
  color: #8a9693;
}
.desc {
  margin-top: 20rpx;
  line-height: 1.6;
  font-size: 28rpx;
}
.ai-chip {
  margin: 16rpx 0;
  display: inline-block;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #ccfbf1;
  color: $mint;
  font-size: 24rpx;
}
.pic-block {
  margin-top: 24rpx;
}
.pic-label {
  display: block;
  margin-bottom: 12rpx;
  color: #8a9693;
  font-size: 24rpx;
}
.pic {
  width: 100%;
  border-radius: 20rpx;
}
.remark {
  margin-top: 20rpx;
  padding: 16rpx;
  background: #f0f7f5;
  border-radius: 16rpx;
  font-size: 26rpx;
}
.tag {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef2f1;
  font-size: 22rpx;
}
.actions {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
  flex-wrap: wrap;
}
.field.col {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 20rpx;
}
.label {
  color: #8a9693;
  font-size: 26rpx;
}
.area {
  width: 100%;
  min-height: 120rpx;
  background: #f0f7f5;
  border-radius: 16rpx;
  padding: 16rpx;
  box-sizing: border-box;
}
.btn-primary {
  background: $mint !important;
  color: #fff !important;
  border: none;
  border-radius: 40rpx;
}
.btn-ghost {
  background: #e8f7f0 !important;
  color: $mint !important;
  border: none;
  border-radius: 28rpx;
}
.empty {
  margin-top: 120rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.empty-title {
  font-size: 32rpx;
  font-weight: 700;
}
</style>
