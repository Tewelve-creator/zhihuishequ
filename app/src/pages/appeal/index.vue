<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">邻里倾诉 · AI 情绪陪伴</view>
      <view v-if="!store.can('ai_emotion')" class="muted">
        邻里倾诉功能暂不可用，请联系物业。
      </view>
      <template v-else>
        <view class="input-row" style="height:auto;padding:20rpx 24rpx">
          <textarea v-model="text" placeholder="说说心里话，AI 会识别情绪；负面诉求将推送网格员" style="flex:1;min-height:160rpx;width:100%" />
        </view>
        <button class="btn-primary" :loading="loading" @click="submit">发送倾诉</button>
        <view v-if="result" class="result">{{ result }}</view>
      </template>
    </view>

    <view v-if="store.isStaff" class="card">
      <view class="card-title">诉求工单处理</view>
      <view v-for="a in appeals" :key="a.id" class="list-item">
        <view class="flex-1">
          <view class="text-ellipsis">{{ a.content || a.title }}</view>
          <view class="muted">情绪：{{ a.sentiment || '-' }} · {{ a.status || 'pending' }}</view>
        </view>
        <button size="mini" class="btn-primary" @click="handle(a)">处理</button>
      </view>
      <view v-if="!appeals.length" class="empty">暂无诉求</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { emotion } from '../../api/ai'
import { listAppeal, handleAppeal } from '../../api/biz'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const text = ref('')
const loading = ref(false)
const result = ref('')
const appeals = ref([])

onShow(async () => {
  if (store.isStaff) {
    appeals.value = (await listAppeal().catch(() => [])) || []
  }
})

async function submit() {
  if (!store.guardFeature('ai_emotion')) return
  if (!text.value.trim()) return
  loading.value = true
  try {
    const data = await emotion(text.value)
    result.value = `情绪：${data.sentiment || '-'}（${data.score ?? ''}）；${data.advice || ''}${
      data.appealCreated ? '；已自动创建诉求工单推送网格员' : ''
    }`
    text.value = ''
    if (store.isStaff) appeals.value = (await listAppeal().catch(() => [])) || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function handle(a) {
  await handleAppeal(a.id, { status: 'done', handleRemark: '已调解安抚' })
  uni.showToast({ title: '已处理', icon: 'success' })
  appeals.value = (await listAppeal().catch(() => [])) || []
}
</script>

<style lang="scss" scoped>
.result {
  margin-top: 20rpx;
  padding: 20rpx;
  background: #f0f7f5;
  border-radius: 12rpx;
  line-height: 1.6;
}
</style>
