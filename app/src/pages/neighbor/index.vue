<template>
  <view class="page page-safe">
    <view class="tabs">
      <view class="tab on">邻里互助</view>
      <view class="tab act" @click="showForm = !showForm">{{ showForm ? '收起' : '+ 发布求助' }}</view>
    </view>

    <view v-if="showForm" class="card">
      <view class="card-title">发布互助</view>
      <view class="input-row">
        <text class="label">类型</text>
        <picker :range="types" @change="onType">
          <view class="picker">{{ form.helpType }}</view>
        </picker>
      </view>
      <view class="input-row">
        <text class="label">标题</text>
        <input v-model="form.title" maxlength="64" placeholder="如：求借电钻、拼车上班" />
      </view>
      <view class="input-row">
        <text class="label">电话</text>
        <input v-model="form.contactPhone" type="number" maxlength="11" placeholder="方便邻居联系您" />
      </view>
      <view class="input-row desc-row">
        <text class="label">说明</text>
        <textarea v-model="form.content" class="desc" maxlength="1000" placeholder="时间、地点、需要什么帮助等" />
      </view>
      <button class="btn-primary" :loading="saving" @click="submit">发布</button>
    </view>

    <view class="card">
      <view class="list-hd">
        <text class="card-title">互助广场</text>
        <text class="muted" @click="load">刷新</text>
      </view>
      <view v-if="!list.length && !loading" class="empty">暂无互助信息</view>
      <view v-for="item in list" :key="item.id" class="item">
        <view class="item-top">
          <text class="item-title">{{ item.title }}</text>
          <text class="item-tag">{{ item.helpType || '其他' }}</text>
        </view>
        <text class="item-desc">{{ item.content || '—' }}</text>
        <view class="item-foot">
          <text class="meta">{{ item.ownerName || '住户' }} · {{ item.houseLabel || '本社区' }}</text>
          <text class="meta">{{ item.createTime || '' }}</text>
        </view>
        <view class="item-actions">
          <template v-if="item.mine">
            <text class="mine-tag">我发布的</text>
            <text v-if="item.helperCount" class="helper-hint">已有 {{ item.helperCount }} 人愿意帮忙</text>
          </template>
          <template v-else>
            <button
              v-if="item.contactPhone"
              class="act-btn ghost"
              size="mini"
              @click="callOwner(item)"
            >联系 TA</button>
            <button
              v-if="item.offered"
              class="act-btn done"
              size="mini"
              disabled
            >已申请</button>
            <button
              v-else
              class="act-btn primary"
              size="mini"
              @click="openOffer(item)"
            >我来帮忙</button>
          </template>
        </view>
      </view>
    </view>

    <!-- 帮忙申请：absolute 贴在 .page 内，避免 H5 手机框溢出 -->
    <view v-if="offerDlg" class="offer-mask" @click="closeOffer">
      <view class="offer-sheet" @click.stop>
        <view class="offer-hd">我来帮忙</view>
        <text class="offer-sub">求助：{{ offerItem?.title || '—' }}</text>
        <view class="offer-field">
          <text class="offer-label">我的联系电话</text>
          <input
            v-model="offerForm.phone"
            type="number"
            maxlength="11"
            placeholder="请填写11位手机号"
          />
        </view>
        <view class="offer-field">
          <text class="offer-label">留言（可选）</text>
          <textarea
            v-model="offerForm.remark"
            class="offer-ta"
            maxlength="200"
            placeholder="方便对方了解您能提供的帮助"
          />
        </view>
        <view class="offer-actions">
          <button class="offer-btn ghost" @click="closeOffer">取消</button>
          <button
            class="offer-btn primary"
            :loading="offerLoading"
            :disabled="offerLoading"
            @click="submitOffer"
          >提交申请</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'
import { createNeighborHelp, listNeighborHelp, offerNeighborHelp } from '../../api/feedback'

const store = useUserStore()
const houseStore = useHouseStore()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const showForm = ref(false)
const types = ['借物', '拼车', '照看', '技能互助', '其他']
const form = reactive({
  helpType: '其他',
  title: '',
  content: '',
  contactPhone: '',
})

const offerDlg = ref(false)
const offerLoading = ref(false)
const offerItem = ref(null)
const offerForm = reactive({ phone: '', remark: '' })

function onType(e) {
  form.helpType = types[Number(e.detail.value)] || '其他'
}

function callOwner(item) {
  const phone = String(item.contactPhone || '').trim()
  if (!phone) {
    uni.showToast({ title: '对方未留联系电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => uni.showToast({ title: phone, icon: 'none', duration: 2500 }),
  })
}

function openOffer(item) {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  offerItem.value = item
  offerForm.phone = ''
  offerForm.remark = ''
  offerDlg.value = true
}

function closeOffer() {
  offerDlg.value = false
  offerItem.value = null
}

async function submitOffer() {
  const phone = String(offerForm.phone || '').trim()
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ title: '请填写正确手机号', icon: 'none' })
    return
  }
  const id = offerItem.value?.id
  if (!id) return
  offerLoading.value = true
  try {
    await offerNeighborHelp(id, {
      phone,
      remark: String(offerForm.remark || '').trim(),
      userName: store.nickname || undefined,
    })
    uni.showToast({ title: '已提交，等待对方联系', icon: 'success' })
    closeOffer()
    await load()
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    offerLoading.value = false
  }
}

async function submit() {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写标题', icon: 'none' })
    return
  }
  if (!houseStore.hasHouse && !houseStore.guardBound()) return
  saving.value = true
  try {
    await createNeighborHelp({
      helpType: form.helpType,
      title: form.title.trim(),
      content: form.content.trim(),
      contactPhone: String(form.contactPhone || '').trim() || undefined,
      ownerName: store.nickname || undefined,
      houseId: houseStore.currentHouseId || houseStore.currentHouse?.id,
      houseLabel: houseStore.label || undefined,
    })
    uni.showToast({ title: '已发布', icon: 'success' })
    form.title = ''
    form.content = ''
    form.contactPhone = ''
    showForm.value = false
    await load()
  } catch (e) {
    uni.showToast({ title: e?.message || '发布失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    list.value = (await listNeighborHelp()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: #f5f7f6;
  padding: 24rpx;
  box-sizing: border-box;
  overflow: hidden;
}
.tabs { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.tab { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.tab.act { color: #47b686; font-weight: 600; font-size: 26rpx; }
.card {
  background: #fff; border-radius: 24rpx; padding: 28rpx; margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(31,42,40,.04);
}
.card-title { font-size: 30rpx; font-weight: 700; color: #1f2a28; }
.muted { color: #94a3b8; font-size: 24rpx; }
.input-row {
  display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.desc-row { align-items: flex-start; }
.label { width: 80rpx; flex-shrink: 0; color: #64748b; font-size: 26rpx; }
input, .picker { flex: 1; font-size: 28rpx; color: #1f2a28; }
.desc { flex: 1; min-height: 140rpx; font-size: 28rpx; width: 100%; }
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
  background: #ecfdf5; color: #047857; flex-shrink: 0;
}
.item-desc { font-size: 24rpx; color: #64748b; line-height: 1.5; display: block; }
.item-foot { display: flex; justify-content: space-between; margin-top: 10rpx; gap: 12rpx; }
.meta { font-size: 22rpx; color: #94a3b8; }
.item-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}
.mine-tag {
  font-size: 22rpx;
  color: #047857;
  background: #ecfdf5;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
}
.helper-hint { font-size: 22rpx; color: #94a3b8; margin-right: auto; }
.act-btn {
  margin: 0;
  padding: 0 22rpx;
  height: 56rpx;
  line-height: 56rpx;
  font-size: 24rpx;
  border-radius: 28rpx;
  border: none;
}
.act-btn::after { border: none; }
.act-btn.primary { background: #47b686; color: #fff; }
.act-btn.ghost {
  background: #fff;
  color: #047857;
  border: 1rpx solid #a7f3d0;
}
.act-btn.done {
  background: #f1f5f9;
  color: #94a3b8;
}

.offer-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  width: 100%;
  max-width: 100%;
  background: rgba(15, 31, 26, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  box-sizing: border-box;
  overflow: hidden;
}
.offer-sheet {
  width: 100%;
  max-width: 100%;
  max-height: 78%;
  overflow-y: auto;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 28rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.offer-hd { font-size: 34rpx; font-weight: 800; color: #1f2a28; }
.offer-sub {
  display: block;
  margin-top: 8rpx;
  margin-bottom: 24rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.offer-field {
  margin-bottom: 20rpx;
  background: #f5f7f6;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
}
.offer-label {
  display: block;
  font-size: 22rpx;
  color: #8a9693;
  margin-bottom: 8rpx;
}
.offer-field input {
  width: 100%;
  font-size: 30rpx;
  color: #1f2a28;
}
.offer-ta {
  width: 100%;
  min-height: 120rpx;
  font-size: 28rpx;
  color: #1f2a28;
}
.offer-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
}
.offer-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
  font-weight: 600;
  border: none;
  margin: 0;
}
.offer-btn::after { border: none; }
.offer-btn.ghost { background: #f1f5f9; color: #64748b; }
.offer-btn.primary { background: #47b686; color: #fff; }
</style>
