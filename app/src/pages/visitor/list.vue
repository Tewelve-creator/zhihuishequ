<template>
  <view class="page page-safe">
    <view class="toolbar">
      <button class="btn-primary" size="mini" @click="goCreate">+ 预约访客</button>
    </view>
    <view class="card" v-if="list.length">
      <view v-for="v in list" :key="v.id" class="list-item">
        <view class="flex-1">
          <view>{{ v.visitorName }} · {{ v.visitorPhone }}</view>
          <view class="muted">{{ v.visitReason }} · {{ v.visitTime || '' }}</view>
        </view>
        <view class="actions">
          <text class="tag" :class="v.status === 'approved' || v.status === 'checked' ? 'ok' : 'warn'">
            {{ statusText(v.status) }}
          </text>
          <button
            v-if="store.isStaff && v.status === 'pending'"
            size="mini"
            class="btn-primary"
            @click="audit(v.id, 'approved')"
          >
            通过
          </button>
          <button
            v-if="store.isStaff && v.status === 'approved'"
            size="mini"
            class="btn-ghost"
            @click="checkin(v.id)"
          >
            核销
          </button>
        </view>
      </view>
    </view>
    <view v-else class="empty">暂无访客预约</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listVisit, auditVisit, checkinVisit } from '../../api/biz'
import { statusText } from '../../utils/constants'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const list = ref([])

async function load() {
  list.value = (await listVisit().catch(() => [])) || []
}

onShow(load)

function goCreate() {
  uni.navigateTo({ url: '/pages/visitor/create' })
}

async function audit(id, status) {
  await auditVisit(id, { status, remark: 'App审核' })
  uni.showToast({ title: '已审核', icon: 'success' })
  load()
}

async function checkin(id) {
  await checkinVisit(id)
  uni.showToast({ title: '已核销', icon: 'success' })
  load()
}
</script>

<style lang="scss" scoped>
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16rpx;
}
.actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
}
</style>
