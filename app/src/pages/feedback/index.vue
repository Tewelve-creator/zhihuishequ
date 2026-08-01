<template>
  <view class="page page-safe">
    <view class="tabs">
      <view class="tab on">我的反馈</view>
      <view class="tab act" @click="showForm = !showForm">{{ showForm ? '收起' : '+ 写意见' }}</view>
    </view>

    <view v-if="showForm" class="card">
      <view class="card-title">提交意见反馈</view>
      <view class="muted tip">通过后物业将转入报事工单处理</view>
      <view class="input-row">
        <text class="label">类型</text>
        <picker :range="types" @change="onType">
          <view class="picker">{{ form.feedbackType }}</view>
        </picker>
      </view>
      <view class="input-row">
        <text class="label">标题</text>
        <input v-model="form.title" maxlength="64" placeholder="简要说明问题" />
      </view>
      <view class="input-row desc-row">
        <text class="label">内容</text>
        <textarea v-model="form.content" class="desc" maxlength="1000" placeholder="请详细描述您的意见或建议" />
      </view>
      <view class="input-row">
        <text class="label">电话</text>
        <input v-model="form.phone" type="number" placeholder="方便联系的手机号" />
      </view>
      <view class="imgs">
        <image v-if="form.imageUrl" :src="previewSrc" class="preview" mode="aspectFill" @click="pick" />
        <view v-else class="upload" @click="pick">+</view>
      </view>
      <button class="btn-primary" :loading="saving" @click="submit">提交反馈</button>
    </view>

    <view class="card">
      <view class="list-hd">
        <text class="card-title">反馈记录</text>
        <text class="muted" @click="load">刷新</text>
      </view>
      <view v-if="!list.length && !loading" class="empty">暂无记录，点上方写意见</view>
      <view v-for="item in list" :key="item.id" class="item">
        <view class="item-top">
          <text class="item-title">{{ item.title }}</text>
          <text class="item-tag" :class="item.status">{{ item.statusLabel || statusLab(item.status) }}</text>
        </view>
        <text class="item-desc">{{ item.content || '—' }}</text>
        <view class="item-foot">
          <text class="meta">{{ item.feedbackType || '其他' }} · {{ item.createTime || '' }}</text>
          <text v-if="item.workOrderId" class="wo">工单 #{{ item.workOrderId }}</text>
        </view>
        <view v-if="item.replyContent" class="reply">
          <text class="reply-lab">物业回复</text>
          <text class="reply-txt">{{ item.replyContent }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'
import { createFeedback, listMyFeedback } from '../../api/feedback'
import { uploadFile, getBaseUrl } from '../../utils/request'

const store = useUserStore()
const houseStore = useHouseStore()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const types = ['环境卫生', '设施设备', '噪音扰民', '停车管理', '物业服务', '其他']
const form = reactive({
  feedbackType: '其他',
  title: '',
  content: '',
  phone: '',
  imageUrl: '',
})

function imgSrc(u) {
  if (!u) return ''
  if (/^https?:\/\//i.test(u) || u.startsWith('data:') || u.startsWith('blob:')) return u
  const base = getBaseUrl() || ''
  return u.startsWith('/') ? base + u : base + '/' + u
}

const previewSrc = computed(() => imgSrc(form.imageUrl))

function statusLab(s) {
  return ({ PENDING: '待处理', PROCESSING: '处理中', APPROVED: '已通过', DONE: '已办结' })[s] || s || '—'
}

function onType(e) {
  form.feedbackType = types[Number(e.detail.value)] || '其他'
}

function pick() {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      try {
        form.imageUrl = await uploadFile(path)
      } catch {
        uni.showToast({ title: '上传失败', icon: 'none' })
      }
    },
  })
}

async function submit() {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写标题', icon: 'none' })
    return
  }
  if (!houseStore.hasHouse && !houseStore.guardBound()) return
  saving.value = true
  try {
    await createFeedback({
      feedbackType: form.feedbackType,
      title: form.title.trim(),
      content: form.content.trim(),
      phone: form.phone.trim() || store.phone || undefined,
      ownerName: store.nickname || undefined,
      houseId: houseStore.currentHouseId || houseStore.currentHouse?.id,
      houseLabel: houseStore.label || undefined,
      images: form.imageUrl ? [form.imageUrl] : undefined,
    })
    uni.showToast({ title: '已提交', icon: 'success' })
    form.title = ''
    form.content = ''
    form.imageUrl = ''
    showForm.value = false
    await load()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    list.value = (await listMyFeedback()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f7f6; padding: 24rpx; box-sizing: border-box; }
.tabs { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.tab { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.tab.act { color: #47b686; font-weight: 600; font-size: 26rpx; }
.card {
  background: #fff; border-radius: 24rpx; padding: 28rpx; margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(31,42,40,.04);
}
.card-title { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.tip { display: block; margin: 8rpx 0 16rpx; font-size: 22rpx; color: #94a3b8; }
.muted { color: #94a3b8; font-size: 24rpx; }
.input-row {
  display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.desc-row { align-items: flex-start; }
.label { width: 80rpx; flex-shrink: 0; color: #64748b; font-size: 26rpx; }
input, .picker { flex: 1; font-size: 28rpx; color: #1f2a28; }
.desc { flex: 1; min-height: 140rpx; font-size: 28rpx; width: 100%; }
.imgs { margin-top: 20rpx; }
.upload, .preview {
  width: 160rpx; height: 160rpx; border-radius: 16rpx;
  background: #f1f5f9; display: flex; align-items: center; justify-content: center;
  color: #94a3b8; font-size: 48rpx;
}
.btn-primary {
  margin-top: 28rpx; background: #47b686; color: #fff; border: none;
  border-radius: 44rpx; font-size: 30rpx; font-weight: 600;
}
.list-hd { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.empty { text-align: center; color: #94a3b8; padding: 40rpx 0; font-size: 26rpx; }
.item { padding: 20rpx 0; border-bottom: 1rpx solid #f1f5f9; }
.item:last-child { border-bottom: none; }
.item-top { display: flex; justify-content: space-between; gap: 12rpx; margin-bottom: 8rpx; }
.item-title { font-size: 28rpx; font-weight: 600; color: #1f2a28; flex: 1; }
.item-tag {
  font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 8rpx;
  background: #ecfdf5; color: #047857;
}
.item-tag.PENDING { background: #fff7ed; color: #c2410c; }
.item-tag.PROCESSING { background: #eff6ff; color: #1d4ed8; }
.item-tag.DONE { background: #f1f5f9; color: #64748b; }
.item-desc { font-size: 24rpx; color: #64748b; line-height: 1.5; display: block; }
.item-foot { display: flex; justify-content: space-between; margin-top: 10rpx; }
.meta { font-size: 22rpx; color: #94a3b8; }
.wo { font-size: 22rpx; color: #47b686; }
.reply {
  margin-top: 12rpx; padding: 16rpx; background: #f8fafc; border-radius: 12rpx;
}
.reply-lab { display: block; font-size: 22rpx; color: #47b686; margin-bottom: 6rpx; }
.reply-txt { font-size: 24rpx; color: #475569; line-height: 1.5; }
</style>
