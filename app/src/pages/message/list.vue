<template>
  <view class="page page-safe">
    <view class="card" v-if="list.length">
      <view v-for="m in list" :key="m.id" class="list-item" @click="mark(m)">
        <view class="flex-1">
          <view class="text-ellipsis">{{ m.title }}</view>
          <view class="muted">{{ m.content }}</view>
          <view class="muted">{{ m.createTime || m.create_time || '' }}</view>
        </view>
        <text v-if="!(m.readFlag || m.read_flag)" class="tag warn">未读</text>
        <text v-else class="tag ok">已读</text>
      </view>
    </view>
    <view v-else class="empty">暂无消息</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMessages, readMessage } from '../../api/auth'

const list = ref([])

async function load() {
  list.value = (await getMessages().catch(() => [])) || []
}

onShow(load)

async function mark(m) {
  if (!(m.readFlag || m.read_flag)) {
    await readMessage(m.id).catch(() => {})
    m.readFlag = 1
    m.read_flag = 1
  }
}
</script>
