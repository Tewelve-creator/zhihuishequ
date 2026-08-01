<template>
  <view class="page page-safe">
    <!-- ========== 列表：先看失物/招领 ========== -->
    <view v-if="mode === 'list'" class="list-mode">
      <view class="tabs">
        <view class="tab" :class="{ on: tab === 'lost' }" @click="switchTab('lost')">失物</view>
        <view class="tab" :class="{ on: tab === 'found' }" @click="switchTab('found')">招领</view>
      </view>

      <scroll-view scroll-y class="list-scroll" :show-scrollbar="false">
        <view class="card list-card">
          <view class="list-hd">
            <text class="card-title">{{ tab === 'lost' ? '失物信息' : '招领信息' }}</text>
            <text class="muted link" @click="loadList">刷新</text>
          </view>
          <view v-if="listLoading" class="empty">加载中…</view>
          <view v-else-if="!list.length" class="empty">
            暂无{{ tab === 'lost' ? '失物' : '招领' }}信息，可点击下方发布
          </view>
          <view v-for="item in list" :key="item.id" class="item">
            <view class="item-top">
              <text class="item-title">{{ item.title }}</text>
              <text class="item-tag" :class="item.itemType">{{ item.itemTypeLabel }}</text>
            </view>
            <text class="item-desc">{{ item.description || '暂无描述' }}</text>
            <view class="item-foot">
              <text class="item-meta">{{ item.contactName || item.publisherName || '住户' }} · {{ item.houseLabel || '本社区' }}</text>
              <text class="item-meta">{{ item.createTime || '' }}</text>
            </view>
            <image
              v-if="item.imageUrl"
              class="item-img"
              :src="imgSrc(item.imageUrl)"
              mode="aspectFill"
              @click="preview(item.imageUrl)"
            />
            <view class="item-actions">
              <text v-if="item.category" class="item-cat">{{ item.category }}</text>
              <text v-if="item.contact" class="item-contact" @click="callPhone(item.contact)">
                联系 {{ item.contact }}
              </text>
              <text v-if="isMine(item)" class="item-mine">我发布的</text>
              <text
                v-else-if="!store.isStaff && item.status === 'open'"
                class="item-claim"
                @click="claim(item)"
              >申请认领</text>
            </view>
          </view>
        </view>
        <view class="list-pad" />
      </scroll-view>

      <view class="foot-bar">
        <view class="btn-publish" @click="openPublish">
          {{ tab === 'lost' ? '发布失物' : '发布招领' }}
        </view>
      </view>
    </view>

    <!-- ========== 发布表单 ========== -->
    <view v-if="mode === 'publish'" class="publish-mode">
      <view class="pub-nav">
        <text class="back" @click="backToList">‹ 返回列表</text>
        <text class="pub-title">{{ form.itemType === 'lost' ? '发布失物' : '发布招领' }}</text>
        <text class="back ghost" />
      </view>

      <scroll-view scroll-y class="pub-scroll">
        <view class="card">
          <view class="muted house-tip">发布房屋：{{ houseStore.label || '请先绑定房屋' }}</view>

          <view class="type-row">
            <view class="type-opt" :class="{ on: form.itemType === 'lost' }" @click="form.itemType = 'lost'">
              <text class="type-t">失物</text>
              <text class="type-s">我丢了东西</text>
            </view>
            <view class="type-opt" :class="{ on: form.itemType === 'found' }" @click="form.itemType = 'found'">
              <text class="type-t">招领</text>
              <text class="type-s">我捡到东西</text>
            </view>
          </view>

          <view class="input-row">
            <text class="label">标题</text>
            <input v-model="form.title" :placeholder="form.itemType === 'lost' ? '如 丢失钥匙' : '如 捡到雨伞'" />
          </view>
          <view class="input-row">
            <text class="label">分类</text>
            <picker :range="categoryNames" @change="onCategoryPick">
              <view class="picker-val">{{ form.category || '请选择物品分类' }}</view>
            </picker>
          </view>
          <view class="input-row desc-row">
            <text class="label">描述</text>
            <textarea v-model="form.description" class="desc" placeholder="时间、地点、物品特征" />
          </view>
          <view class="input-row">
            <text class="label">联系</text>
            <input v-model="form.contact" placeholder="手机号" />
          </view>
          <view class="imgs">
            <image v-if="form.imageUrl" :src="previewSrc" class="preview" mode="aspectFill" @click="pick" />
            <view v-else class="upload" @click="pick">+</view>
          </view>
          <button class="btn-primary" :loading="loading" @click="publish">发布</button>
        </view>
        <view class="list-pad" />
      </scroll-view>
    </view>

    <!-- 认领申请（独立浮层，不与发布页抢 v-else） -->
    <view v-if="claimDlg" class="claim-mask" @click="closeClaim">
      <view class="claim-sheet" @click.stop>
        <view class="claim-hd">申请认领</view>
        <text class="claim-sub">物品：{{ claimItem?.title || '—' }}</text>
        <view class="claim-field">
          <text class="claim-label">我的联系电话</text>
          <input
            v-model="claimForm.phone"
            type="text"
            maxlength="11"
            placeholder="请填写11位手机号"
          />
        </view>
        <view class="claim-field">
          <text class="claim-label">认领说明（可选）</text>
          <textarea
            v-model="claimForm.remark"
            class="claim-ta"
            placeholder="特征、丢失时间地点等"
          />
        </view>
        <view class="claim-actions">
          <button class="claim-btn ghost" @click="closeClaim">取消</button>
          <button
            class="claim-btn primary"
            :loading="claimLoading"
            :disabled="claimLoading"
            @click="submitClaim"
          >提交申请</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createLost, listLost, listLostCategories, submitLostClaim } from '../../api/ai'
import { uploadFile, getBaseUrl } from '../../utils/request'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'

const store = useUserStore()
const houseStore = useHouseStore()
const mode = ref('list') // list | publish
const tab = ref('lost')
const form = reactive({
  itemType: 'lost',
  title: '',
  category: '',
  description: '',
  contact: '',
  imageUrl: ''
})
const categories = ref([])
const categoryNames = computed(() => categories.value.map((c) => c.name).filter(Boolean))
const loading = ref(false)
const listLoading = ref(false)
const list = ref([])
const claimDlg = ref(false)
const claimLoading = ref(false)
const claimItem = ref(null)
const claimForm = reactive({ phone: '', remark: '' })

const previewSrc = computed(() => imgSrc(form.imageUrl))

watch(tab, () => {
  loadList()
})

watch(
  () => form.itemType,
  (v) => {
    if (mode.value === 'publish') {
      uni.setNavigationBarTitle({ title: v === 'lost' ? '发布失物' : '发布招领' })
    }
  },
)

onShow(() => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.guardFeature('ai_lost_match')) {
    uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) })
    return
  }
  if (!store.isStaff) houseStore.hydrate()
  if (mode.value === 'list') {
    uni.setNavigationBarTitle({ title: '失物招领' })
  }
  loadCategories()
  loadList()
})

function switchTab(v) {
  tab.value = v
}

function openPublish() {
  if (!store.isStaff && !houseStore.guardBound()) return
  form.itemType = tab.value
  mode.value = 'publish'
  uni.setNavigationBarTitle({ title: tab.value === 'lost' ? '发布失物' : '发布招领' })
}

function backToList() {
  mode.value = 'list'
  uni.setNavigationBarTitle({ title: '失物招领' })
  loadList()
}

function onCategoryPick(e) {
  const i = Number(e.detail.value)
  form.category = categoryNames.value[i] || ''
}

async function loadCategories() {
  try {
    categories.value = (await listLostCategories(true)) || []
  } catch {
    categories.value = []
  }
}

function imgSrc(u) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl()
  return `${base}${u.startsWith('/') ? u : '/' + u}`
}

function preview(u) {
  uni.previewImage({ urls: [imgSrc(u)] })
}

function callPhone(phone) {
  if (!phone) return
  uni.makePhoneCall({ phoneNumber: String(phone) })
}

function pick() {
  uni.chooseImage({
    count: 1,
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        const url = await uploadFile(res.tempFilePaths[0])
        form.imageUrl = typeof url === 'string' ? url : url?.url || ''
      } catch (e) {
        /* ignore */
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function loadList() {
  listLoading.value = true
  try {
    list.value = (await listLost({ itemType: tab.value, status: 'open' })) || []
  } catch {
    list.value = []
  } finally {
    listLoading.value = false
  }
}

async function publish() {
  if (!store.isStaff && !houseStore.guardBound()) return
  if (!form.title.trim()) {
    uni.showToast({ title: '请填标题', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await createLost({
      title: form.title.trim(),
      category: form.category || '',
      description: form.description,
      contact: form.contact,
      imageUrl: form.imageUrl,
      itemType: form.itemType,
      type: form.itemType,
      houseId: houseStore.currentHouseId != null ? String(houseStore.currentHouseId) : '',
      houseLabel: houseStore.label || '',
      contactName: store.nickname || ''
    })
    uni.showToast({ title: '已发布（已过AI审核）', icon: 'success' })
    tab.value = form.itemType
    form.title = ''
    form.category = ''
    form.description = ''
    form.contact = ''
    form.imageUrl = ''
    backToList()
  } catch (e) {
    /* toast by request */
  } finally {
    loading.value = false
  }
}

function isMine(item) {
  const uid = store.userId ?? uni.getStorageSync('userId')
  if (uid == null || uid === '' || !item) return false
  const pub = item.userId ?? item.user_id
  return pub != null && String(pub) === String(uid)
}

function claim(item) {
  if (!item?.id) return
  if (isMine(item)) {
    uni.showToast({ title: '不能认领自己发布的信息', icon: 'none' })
    return
  }
  claimItem.value = item
  // 只用登录账号手机号预填，不拿别人发布信息里的联系电话
  claimForm.phone = String(uni.getStorageSync('phone') || '').replace(/\D/g, '').slice(0, 11)
  claimForm.remark = ''
  claimDlg.value = true
}

function closeClaim() {
  claimDlg.value = false
  claimItem.value = null
}

async function submitClaim() {
  if (!claimItem.value?.id || claimLoading.value) return
  const phone = String(claimForm.phone || '').replace(/\D/g, '')
  claimForm.phone = phone
  if (!/^1\d{10}$/.test(phone)) {
    uni.showToast({ title: '请填写正确的11位手机号', icon: 'none' })
    return
  }
  claimLoading.value = true
  try {
    await submitLostClaim(claimItem.value.id, {
      remark: (claimForm.remark || '').trim(),
      phone,
      userName: store.nickname || uni.getStorageSync('nickname') || '住户'
    })
    uni.setStorageSync('phone', phone)
    uni.showToast({ title: '已提交，等待核实', icon: 'success' })
    closeClaim()
    await loadList()
  } catch {
    /* 错误提示由 request 统一弹出，如「不能认领自己发布的信息」 */
  } finally {
    claimLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
/* 勿用 absolute top:0，H5 预览框下会顶进导航栏，导致顶部 Tab 被挡住 */
.page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
  padding: 0 !important;
  padding-bottom: 0 !important;
}
.list-mode,
.publish-mode {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
}
.tabs {
  flex-shrink: 0;
  display: flex;
  gap: 16rpx;
  margin: 20rpx 24rpx 16rpx;
  position: relative;
  z-index: 2;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 999rpx;
  background: #fff;
  color: #5a6562;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 6rpx 18rpx rgba(31, 42, 40, 0.04);
}
.tab.on {
  background: $mint;
  color: #fff;
}
.list-scroll,
.pub-scroll {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 0 24rpx;
  box-sizing: border-box;
  position: relative;
  z-index: 1;
}
.list-card {
  margin-bottom: 0;
}
.list-pad {
  height: calc(140rpx + env(safe-area-inset-bottom));
}
.foot-bar {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e8eeeb;
  box-sizing: border-box;
}
.btn-publish {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
}
.pub-nav {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 24rpx 8rpx;
}
.back {
  width: 160rpx;
  font-size: 28rpx;
  color: $mint;
  font-weight: 600;
}
.back.ghost {
  opacity: 0;
  pointer-events: none;
}
.pub-title {
  font-size: 32rpx;
  font-weight: 800;
  color: #1f2a28;
}
.house-tip {
  display: block;
  margin-bottom: 16rpx;
  font-size: 24rpx;
}
.type-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.type-opt {
  flex: 1;
  padding: 20rpx 18rpx;
  border-radius: 18rpx;
  background: #f3f6f4;
  border: 2rpx solid transparent;
}
.type-opt.on {
  background: #e8f8f1;
  border-color: $mint;
}
.type-t {
  display: block;
  font-size: 28rpx;
  font-weight: 800;
  color: #1f2a28;
}
.type-opt.on .type-t { color: #2f9e6f; }
.type-s {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.desc-row {
  height: auto !important;
  padding: 20rpx 24rpx !important;
  align-items: flex-start !important;
}
.desc {
  flex: 1;
  min-height: 100rpx;
  font-size: 28rpx;
}
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
.list-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}
.link { color: $mint; }
.empty {
  text-align: center;
  color: #8a9693;
  padding: 60rpx 0;
  font-size: 26rpx;
}
.item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.item:last-child { border-bottom: none; }
.item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
}
.item-title {
  flex: 1;
  font-size: 30rpx;
  font-weight: 700;
  color: #1f2a28;
}
.item-tag {
  flex-shrink: 0;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #e8f5f2;
  color: $mint;
}
.item-tag.lost {
  background: #fff7ed;
  color: #c2410c;
}
.item-tag.found {
  background: #e8f5f2;
  color: $mint;
}
.item-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #5a6562;
  line-height: 1.45;
}
.item-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 10rpx;
  gap: 12rpx;
}
.item-meta {
  font-size: 22rpx;
  color: #8a9693;
}
.item-img {
  margin-top: 12rpx;
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #f3f6f4;
}
.item-actions {
  margin-top: 8rpx;
}
.item-contact {
  display: inline-block;
  margin-top: 12rpx;
  margin-right: 24rpx;
  font-size: 24rpx;
  color: $mint;
  font-weight: 600;
}
.item-cat {
  display: inline-block;
  margin-top: 12rpx;
  margin-right: 16rpx;
  font-size: 22rpx;
  color: #64748b;
  background: #f1f5f9;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.item-claim {
  display: inline-block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #c2410c;
  font-weight: 700;
}
.item-mine {
  display: inline-block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #94a3b8;
}
.picker-val {
  flex: 1;
  font-size: 28rpx;
  color: #1f2a28;
}
view.picker-val {
  color: #8a9693;
}

/* 必须用 absolute 贴在 .page 内：H5 手机框下 fixed 会逃出 375 预览屏 */
.claim-mask {
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
.claim-sheet {
  width: 100%;
  max-width: 100%;
  max-height: 78%;
  overflow-y: auto;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 28rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.claim-hd {
  font-size: 34rpx;
  font-weight: 800;
  color: #1f2a28;
}
.claim-sub {
  display: block;
  margin-top: 8rpx;
  margin-bottom: 24rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.claim-field {
  margin-bottom: 20rpx;
  background: #f5f7f6;
  border-radius: 16rpx;
  padding: 18rpx 22rpx;
}
.claim-label {
  display: block;
  font-size: 22rpx;
  color: #8a9693;
  margin-bottom: 8rpx;
}
.claim-field input {
  width: 100%;
  font-size: 30rpx;
  color: #1f2a28;
}
.claim-ta {
  width: 100%;
  min-height: 120rpx;
  font-size: 28rpx;
  color: #1f2a28;
}
.claim-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
}
.claim-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
  padding: 0;
  margin: 0;
}
.claim-btn::after {
  border: none;
}
.claim-btn.ghost {
  background: #f3f6f4;
  color: #5a6562;
}
.claim-btn.primary {
  background: linear-gradient(135deg, #6ed3a8, $mint);
  color: #fff;
}
</style>
