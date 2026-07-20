<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">维护本社区 AI 知识库</view>
      <view class="input-row">
        <text class="label">问题</text>
        <input v-model="form.question" placeholder="居民常问" />
      </view>
      <view class="input-row" style="height:auto;padding:20rpx 24rpx">
        <text class="label">答案</text>
        <textarea v-model="form.answer" style="flex:1;min-height:120rpx" />
      </view>
      <button class="btn-primary" :loading="loading" @click="save">保存</button>
    </view>

    <view class="card">
      <view v-for="k in list" :key="k.id" class="list-item">
        <view class="flex-1">
          <view>{{ k.question }}</view>
          <view class="muted text-ellipsis">{{ k.answer }}</view>
        </view>
        <button size="mini" class="btn-danger" @click="remove(k.id)">删</button>
      </view>
      <view v-if="!list.length" class="empty">暂无知识</view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listKb, saveKb, deleteKb } from '../../api/ai'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const list = ref([])
const loading = ref(false)
const form = reactive({ question: '', answer: '' })

async function load() {
  list.value = (await listKb().catch(() => [])) || []
}

onShow(() => {
  if (!store.isStaff) {
    uni.showToast({ title: '仅工作人员可维护', icon: 'none' })
  }
  load()
})

async function save() {
  if (!form.question || !form.answer) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await saveKb({ ...form })
    form.question = ''
    form.answer = ''
    uni.showToast({ title: '已保存', icon: 'success' })
    load()
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function remove(id) {
  await deleteKb(id)
  load()
}
</script>
