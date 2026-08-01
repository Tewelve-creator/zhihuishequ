<template>
  <view class="page">
    <view v-if="task" class="card hero">
      <view class="row-between">
        <text class="title">{{ task.title }}</text>
        <text class="tag" :class="tagClass(task.status)">{{ task.statusLabel }}</text>
      </view>
      <view class="stats">
        <view class="stat">
          <text class="num">{{ task.pointTotal || 0 }}</text>
          <text class="lab">总点位</text>
        </view>
        <view class="stat">
          <text class="num mint">{{ task.pointDone || 0 }}</text>
          <text class="lab">已巡</text>
        </view>
        <view class="stat">
          <text class="num warn">{{ task.hazardCount || 0 }}</text>
          <text class="lab">隐患</text>
        </view>
      </view>
      <view class="meta">执行人：{{ task.assigneeName || '—' }}</view>
      <view class="meta">时间：{{ formatTime(task.startTime) }} ~ {{ formatTime(task.endTime) }}</view>
      <view class="meta" v-if="task.requirement">要求：{{ task.requirement }}</view>
      <view class="meta" v-if="task.remark">备注：{{ task.remark }}</view>
    </view>

    <view class="card">
      <view class="sec-t">巡检点位清单</view>
      <view v-if="!(task?.taskPoints || []).length" class="empty">暂无点位</view>
      <view
        v-for="(p, idx) in task?.taskPoints || []"
        :key="p.id"
        class="point"
        @click="goCheckin(p)"
      >
        <view class="idx" :class="{ done: p.checked }">{{ idx + 1 }}</view>
        <view class="flex-1">
          <text class="pname">{{ p.pointName }}</text>
          <text class="muted">{{ p.locationDesc || '—' }}</text>
          <text v-if="p.checked && p.checkTime" class="muted">打卡 {{ formatTime(p.checkTime) }}</text>
          <text v-if="p.checkinRemark" class="muted">备注 {{ p.checkinRemark }}</text>
          <text v-if="p.hazardDesc" class="muted hazard">隐患 {{ p.hazardDesc }}</text>
          <view v-if="(p.photos || []).length" class="photos" @click.stop>
            <image
              v-for="(u, i) in p.photos"
              :key="i"
              :src="absUrl(u)"
              class="ph"
              mode="aspectFill"
              @click.stop="preview(p.photos, i)"
            />
          </view>
        </view>
        <text class="tag" :class="p.checked ? 'ok' : 'warn'">
          {{ p.checked ? '已巡' : '去打卡' }}
        </text>
      </view>
    </view>

    <view class="safe" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getMyInspectionTask } from '../../api/inspection'
import { useUserStore } from '../../store/user'
import { getBaseUrl } from '../../utils/request'

const store = useUserStore()
const taskId = ref(null)
const task = ref(null)

function tagClass(s) {
  if (s === 'DONE') return 'ok'
  if (s === 'DOING') return 'warn'
  return ''
}

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').substring(0, 16)
}

function absUrl(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || u.startsWith('blob:')) return u
  const base = getBaseUrl().replace(/\/$/, '')
  return u.startsWith('/') ? `${base}${u}` : `${base}/${u}`
}

function preview(list, i) {
  const urls = (list || []).map(absUrl)
  uni.previewImage({ urls, current: urls[i] })
}

async function load() {
  if (!validTaskId(taskId.value)) return
  try {
    task.value = await getMyInspectionTask(taskId.value)
  } catch (e) {
    task.value = null
  }
}

function validTaskId(id) {
  return id != null && id !== '' && String(id) !== 'undefined' && String(id) !== 'null'
}

function goCheckin(p) {
  if (p.checked) {
    uni.showToast({ title: '该点位已打卡', icon: 'none' })
    return
  }
  if (task.value?.status === 'DONE') {
    uni.showToast({ title: '任务已完成', icon: 'none' })
    return
  }
  if (!validTaskId(taskId.value) || !validTaskId(p?.id)) {
    uni.showToast({ title: '任务参数错误', icon: 'none' })
    return
  }
  const uid = store.userId
  const isAssignee = String(task.value?.assigneeId) === String(uid)
  if (!store.isPropertyAdmin && !isAssignee) {
    uni.showToast({ title: '仅执行人可打卡', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/pages/staff/inspection-checkin?taskId=${taskId.value}&taskPointId=${p.id}`
  })
}

onLoad((q) => {
  const id = q?.id || q?.taskId
  if (!validTaskId(id)) {
    uni.showToast({ title: '任务参数错误', icon: 'none' })
    setTimeout(() => uni.navigateBack({ fail: () => {} }), 400)
    return
  }
  taskId.value = id
  load()
})

onShow(() => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (validTaskId(taskId.value)) load()
})
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 28rpx;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.05);
}
.hero .title {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
  flex: 1;
}
.row-between {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
}
.stats {
  display: flex;
  margin: 28rpx 0 12rpx;
  background: #f5fbf8;
  border-radius: 20rpx;
  padding: 20rpx 0;
}
.stat {
  flex: 1;
  text-align: center;
}
.num {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1f2a28;
}
.num.mint { color: $mint; }
.num.warn { color: #e67e22; }
.lab {
  font-size: 22rpx;
  color: #8a9693;
}
.meta {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #64748b;
  line-height: 1.5;
}
.sec-t {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}
.point {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.point:last-child { border-bottom: none; }
.idx {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #eef2f1;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.idx.done {
  background: #e8f8f0;
  color: $mint;
}
.pname {
  display: block;
  font-weight: 700;
  font-size: 28rpx;
  margin-bottom: 6rpx;
}
.muted {
  display: block;
  font-size: 22rpx;
  color: #8a9693;
}
.muted.hazard {
  color: #e67e22;
}
.photos {
  display: flex;
  gap: 10rpx;
  margin-top: 12rpx;
  flex-wrap: wrap;
}
.ph {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: #f5f7f6;
}
.tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  background: #eef1f0;
  color: #666;
  white-space: nowrap;
}
.tag.warn {
  background: #fff4e5;
  color: #d48806;
}
.tag.ok {
  background: #e8f8f0;
  color: #2f9e6a;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 40rpx 0;
}
.safe { height: 40rpx; }
</style>
