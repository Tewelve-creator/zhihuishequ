<template>
  <view class="page page-safe">
    <view class="card" v-if="list.length">
      <view v-for="m in list" :key="m.id" class="list-item" @click="mark(m)">
        <view class="flex-1">
          <view class="row-title">
            <text class="text-ellipsis">{{ m.title }}</text>
            <text v-if="isFeeRemind(m)" class="tag fee">催缴</text>
          </view>
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

function isFeeRemind(m) {
  const typ = m.msgType || m.msg_type || ''
  const title = String(m.title || '')
  return typ === 'fee_remind' || title.includes('催缴') || title.includes('物业费') || title.includes('缴纳提醒')
}

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
  const typ = m.msgType || m.msg_type || ''
  if (isFeeRemind(m)) {
    uni.navigateTo({ url: '/pages/payment/index' })
    return
  }
  if (typ === 'equip_maint' || String(m.title || '').includes('设备维保')) {
    uni.navigateTo({ url: '/pages/staff/todo?tab=equip' })
    return
  }
  if (typ === 'inspection' || String(m.title || '').includes('巡检')) {
    uni.navigateTo({ url: '/pages/staff/todo?tab=insp' })
  }
}
</script>

<style scoped>
.row-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 4rpx;
}
.row-title .text-ellipsis {
  flex: 1;
  min-width: 0;
}
.tag.fee {
  flex-shrink: 0;
  background: #fff1f2;
  color: #e11d48;
  border: 1rpx solid #fecdd3;
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}
</style>
