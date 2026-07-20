<template>
  <view class="page page-safe">
    <view v-if="!store.can('ai_lost_match')" class="card">
      <view class="card-title">失物 AI 匹配暂不可用</view>
      <text class="muted">当前社区未开通此功能，请联系物业。</text>
    </view>

    <template v-else>
      <view class="card">
        <view class="card-title">发布失物 / 招领</view>
        <view class="input-row">
          <text class="label">标题</text>
          <input v-model="form.title" placeholder="如 丢失钥匙" />
        </view>
        <view class="input-row" style="height:auto;padding:20rpx 24rpx">
          <text class="label">描述</text>
          <textarea v-model="form.description" style="flex:1;min-height:100rpx" />
        </view>
        <view class="input-row">
          <text class="label">联系</text>
          <input v-model="form.contact" placeholder="手机号" />
        </view>
        <view class="imgs">
          <image v-if="form.imageUrl" :src="form.imageUrl" class="preview" mode="aspectFill" @click="pick" />
          <view v-else class="upload" @click="pick">+</view>
        </view>
        <button class="btn-primary" :loading="loading" @click="publish">发布</button>
      </view>

      <view class="card">
        <view class="card-title">AI 匹配查找</view>
        <view class="input-row" style="height:auto;padding:20rpx 24rpx">
          <textarea v-model="matchDesc" placeholder="描述你捡到/丢失的物品特征" style="flex:1;min-height:100rpx;width:100%" />
        </view>
        <button class="btn-ghost" :loading="matching" @click="doMatch">开始匹配</button>
        <view v-if="matchResult" class="result">{{ matchResult }}</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { createLost, lostMatch } from '../../api/ai'
import { uploadFile, getBaseUrl } from '../../utils/request'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const form = reactive({ title: '', description: '', contact: '', imageUrl: '' })
const loading = ref(false)
const matching = ref(false)
const matchDesc = ref('')
const matchResult = ref('')

function pick() {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        const url = await uploadFile(res.tempFilePaths[0])
        form.imageUrl = url.startsWith('http') ? url : getBaseUrl() + url
      } catch (e) {
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function publish() {
  if (!store.guardFeature('ai_lost_match')) return
  if (!form.title) {
    uni.showToast({ title: '请填标题', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await createLost({ ...form })
    uni.showToast({ title: '已发布', icon: 'success' })
    form.title = ''
    form.description = ''
    form.contact = ''
    form.imageUrl = ''
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function doMatch() {
  if (!store.guardFeature('ai_lost_match')) return
  matching.value = true
  try {
    const data = await lostMatch({ description: matchDesc.value, imageUrl: form.imageUrl })
    matchResult.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
  } catch (e) {
  } finally {
    matching.value = false
  }
}
</script>

<style lang="scss" scoped>
.upload {
  width: 160rpx;
  height: 160rpx;
  border: 2rpx dashed #0f766e;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  color: #0f766e;
  margin-bottom: 20rpx;
}
.preview {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  margin-bottom: 20rpx;
}
.result {
  margin-top: 16rpx;
  padding: 16rpx;
  background: #f0f7f5;
  border-radius: 12rpx;
  white-space: pre-wrap;
  font-size: 24rpx;
}
</style>
