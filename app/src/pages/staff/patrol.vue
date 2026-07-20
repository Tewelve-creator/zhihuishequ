<template>
  <view class="page page-safe">
    <view class="card">
      <view class="section-title">巡查上报</view>
      <view class="muted">网格员岗位 · 仅本社区巡查记录</view>
      <view v-if="!store.canPerm(PERMS.PATROL_REPORT)" class="empty">当前岗位无巡查权限</view>
    </view>

    <view v-if="store.canPerm(PERMS.PATROL_REPORT)" class="card">
      <view class="field">
        <text class="label">巡查地点</text>
        <input v-model="form.place" class="input" placeholder="如：1 号楼电梯厅" />
      </view>
      <view class="field col">
        <text class="label">情况说明</text>
        <textarea v-model="form.content" class="area" placeholder="巡查发现的问题或正常情况" />
      </view>
      <button class="btn-primary" :loading="loading" @click="submit">提交上报</button>
    </view>

    <view v-if="store.canPerm(PERMS.PATROL_REPORT)" class="card">
      <view class="card-title">我的上报</view>
      <view v-if="!list.length" class="empty">暂无记录</view>
      <view v-for="(item, i) in list" :key="i" class="row">
        <view>
          <text class="t">{{ item.place }}</text>
          <text class="muted">{{ item.content }}</text>
        </view>
        <text class="muted">{{ item.time }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const loading = ref(false)
const form = reactive({ place: '', content: '' })
const list = ref([])

onShow(() => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.guardPerm(PERMS.PATROL_REPORT, '仅网格员可巡查上报')) {
    return
  }
  list.value = uni.getStorageSync('patrolReports') || []
})

function submit() {
  if (!form.place.trim() || !form.content.trim()) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  loading.value = true
  const row = {
    place: form.place.trim(),
    content: form.content.trim(),
    time: new Date().toLocaleString(),
    tenantId: store.tenantId,
    userId: store.userId
  }
  const next = [row, ...list.value].slice(0, 50)
  list.value = next
  uni.setStorageSync('patrolReports', next)
  form.place = ''
  form.content = ''
  loading.value = false
  uni.showToast({ title: '已上报', icon: 'success' })
}
</script>

<style lang="scss" scoped>
.field {
  margin-bottom: 20rpx;
}
.field.col {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.label {
  font-size: 26rpx;
  color: #3a4543;
  font-weight: 600;
}
.input {
  margin-top: 10rpx;
  background: #f5f7f6;
  border-radius: 12rpx;
  padding: 18rpx 20rpx;
}
.area {
  min-height: 160rpx;
  background: #f5f7f6;
  border-radius: 12rpx;
  padding: 18rpx 20rpx;
  width: 100%;
  box-sizing: border-box;
}
.row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.t {
  display: block;
  font-weight: 600;
  margin-bottom: 6rpx;
}
</style>
