<template>
  <view class="moments-page">
    <!-- 顶栏 -->
    <view class="top-bar" :style="{ paddingTop: statusBarPx + 'px' }">
      <view class="top-inner">
        <view class="top-left">
          <text class="top-title">邻里圈</text>
        </view>
        <view v-if="!store.isStaff" class="publish-btn" @click="openPublish">发布</view>
      </view>
    </view>

    <scroll-view scroll-y class="feed" :style="{ paddingTop: topPad + 'px' }" @scrolltolower="load">
      <view v-if="muteInfo.muted" class="mute-banner">
        您已被物业禁言{{ muteInfo.permanent ? '（永久）' : '' }}，暂时无法发布动态与评论
      </view>

      <view class="scope-tip">本社区邻居动态 · 全员可见</view>

      <view v-if="!list.length && !loading" class="empty">还没有动态，来发第一条吧～</view>

      <view v-for="item in list" :key="item.id" class="post-card">
        <view class="post-hd">
          <image
            v-if="item.avatar && !item.avatarBroken"
            class="avatar-img"
            :src="imgSrc(item.avatar)"
            mode="aspectFill"
            @error="item.avatarBroken = true"
          />
          <view v-else class="avatar">{{ avatarText(item.nickname) }}</view>
          <view class="meta">
            <text class="name">{{ item.nickname || '住户' }}</text>
            <text class="sub">{{ item.houseLabel || '本社区' }} · {{ item.createTime || '' }}</text>
          </view>
          <text
            v-if="item.mine && !store.isStaff"
            class="del"
            @click="onDeletePost(item)"
          >删除</text>
          <text
            v-else-if="!item.mine && !store.isStaff"
            class="del"
            @click="onReport('POST', item.id)"
          >举报</text>
        </view>

        <text class="content">{{ item.content }}</text>

        <view v-if="item.images?.length" class="img-grid" :class="'n' + Math.min(item.images.length, 9)">
          <image
            v-for="(img, ii) in item.images.slice(0, 9)"
            :key="ii"
            class="img"
            :src="imgSrc(img)"
            mode="aspectFill"
            @click="preview(item.images, ii)"
          />
        </view>

        <view class="actions">
          <view class="act" @click.stop="onLike(item)">
            <view class="icon-heart" :class="{ on: item.liked }">
              <view class="heart-shape" />
            </view>
            <text v-if="item.likeCount" class="num" :class="{ on: item.liked }">{{ item.likeCount }}</text>
          </view>
          <view class="act" @click.stop="onTapComment(item)">
            <view class="icon-comment" :class="{ on: item.showComments }">
              <view class="bubble" />
            </view>
            <text v-if="item.commentCount" class="num">{{ item.commentCount }}</text>
          </view>
        </view>

        <view v-if="item.showComments" class="comments">
          <view v-if="item.loadingComments" class="c-tip">加载中…</view>
          <template v-else>
            <view v-if="!(item.commentList || []).length" class="c-tip">暂无评论，来抢沙发吧</view>
            <view v-for="c in item.commentList || []" :key="c.id" class="c-row">
              <image
                v-if="c.avatar && !c.avatarBroken"
                class="c-avatar-img"
                :src="imgSrc(c.avatar)"
                mode="aspectFill"
                @error="c.avatarBroken = true"
              />
              <view v-else class="c-avatar">{{ avatarText(c.nickname) }}</view>
              <view class="c-main">
                <text class="c-name">{{ c.nickname || '住户' }}</text>
                <text class="c-body">{{ c.content }}</text>
              </view>
              <text
                v-if="c.mine"
                class="c-del"
                @click="onDeleteComment(item, c)"
              >删</text>
              <text
                v-else-if="!store.isStaff"
                class="c-del"
                @click="onReport('COMMENT', c.id)"
              >举报</text>
            </view>
          </template>

          <!-- 内嵌输入：不依赖底部固定栏 / 弹层，避免 H5 点不到 -->
          <view class="c-composer" @click.stop>
            <textarea
              class="c-textarea"
              :value="activeCommentId === item.id ? commentDraft : ''"
              :focus="activeCommentId === item.id && composerFocus"
              maxlength="500"
              auto-height
              placeholder="说点什么…"
              @focus="onComposerFocus(item)"
              @input="onDraftInput"
              @confirm="submitComment"
            />
            <view
              class="c-send-btn"
              :class="{ disabled: commenting || !(activeCommentId === item.id && commentDraft.trim()) }"
              @click.stop="submitComment"
            >发送</view>
          </view>
        </view>
      </view>

      <view class="safe-bottom" />
    </scroll-view>

    <!-- 发布弹层 -->
    <view v-if="showPublish" class="mask" @click="showPublish = false">
      <view class="sheet" @click.stop>
        <view class="sheet-user">
          <image v-if="myAvatarSrc" class="sheet-avatar" :src="myAvatarSrc" mode="aspectFill" />
          <view v-else class="sheet-avatar fallback">{{ avatarText(store.nickname) }}</view>
          <view class="sheet-user-meta">
            <text class="sheet-name">{{ store.nickname || '住户' }}</text>
            <text class="sheet-house">{{ houseStore.label }}</text>
          </view>
        </view>
        <textarea
          v-model="form.content"
          class="sheet-area"
          maxlength="1000"
          placeholder="分享社区里的新鲜事…"
        />
        <view class="sheet-imgs">
          <view v-for="(img, i) in form.images" :key="i" class="sheet-img-wrap">
            <image :src="imgSrc(img)" class="sheet-img" mode="aspectFill" @click="preview(form.images, i)" />
            <text class="sheet-img-x" @click="form.images.splice(i, 1)">×</text>
          </view>
          <view v-if="form.images.length < 9" class="sheet-add" @click="pickImages">+</view>
        </view>
        <button class="btn-primary" :loading="publishing" @click="submitPublish">发布</button>
      </view>
    </view>

    <!-- 举报：页内底部弹层（不用系统 ActionSheet，避免 H5 手机框漂中间） -->
    <view v-if="reportDlg" class="report-mask" @click="closeReport">
      <view class="report-sheet" @click.stop>
        <view class="report-hd">选择举报原因</view>
        <text class="report-sub">提交后由物业审核处理</text>
        <view
          v-for="r in reportReasons"
          :key="r"
          class="report-item"
          @click="pickReportReason(r)"
        >{{ r }}</view>
        <view class="report-cancel" @click="closeReport">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  listAppealFeed, publishAppeal, deleteAppeal,
  listAppealComments, addAppealComment, deleteAppealComment,
  toggleAppealLike, getMyAppealMute, submitAppealReport
} from '../../api/appeal'
import { getProfile } from '../../api/auth'
import { uploadFile, getBaseUrl } from '../../utils/request'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'

const store = useUserStore()
const houseStore = useHouseStore()
const list = ref([])
const loading = ref(false)
const publishing = ref(false)
const showPublish = ref(false)
const muteInfo = reactive({ muted: false, permanent: false })
const form = reactive({ content: '', images: [] })

const statusBarPx = ref(20)
try {
  const sys = uni.getSystemInfoSync()
  statusBarPx.value = sys.statusBarHeight || 20
} catch (e) {}
const topPad = computed(() => statusBarPx.value + 48)

/** 当前正在编辑评论的动态 */
const activeCommentId = ref(null)
const commentDraft = ref('')
const composerFocus = ref(false)
const commenting = ref(false)

const MUTE_TIP_SHORT = '您已被物业禁言，暂时无法发布'

const myAvatarSrc = computed(() => {
  const u = store.avatar
  if (!u) return ''
  return imgSrc(u)
})

function avatarText(name) {
  const s = (name || '住').trim()
  return s.slice(0, 1)
}

function goBack() {
  uni.switchTab({ url: '/pages/home/index' })
}

function imgSrc(u) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  const base = getBaseUrl()
  return `${base}${u.startsWith('/') ? u : '/' + u}`
}

function preview(images, index) {
  const urls = (images || []).map(imgSrc)
  uni.previewImage({ urls, current: urls[index] || urls[0] })
}

onShow(async () => {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isStaff) {
    houseStore.hydrate()
  }
  try {
    const p = await getProfile()
    if (p) {
      store.updateProfileLocal({
        nickname: p.nickname != null ? p.nickname : undefined,
        avatar: p.avatar !== undefined ? p.avatar : undefined
      })
    }
  } catch (e) {}
  await Promise.all([load(), loadMute()])
})

async function loadMute() {
  if (store.isStaff) return
  try {
    const data = (await getMyAppealMute()) || {}
    muteInfo.muted = !!data.muted
    muteInfo.permanent = !!data.permanent
  } catch (e) {
    muteInfo.muted = false
  }
}

async function load() {
  loading.value = true
  try {
    const rows = (await listAppealFeed(50)) || []
    list.value = (Array.isArray(rows) ? rows : []).map((r) => ({
      ...r,
      images: Array.isArray(r.images) ? r.images : [],
      avatarBroken: false,
      showComments: false,
      commentList: null,
      draft: '',
      loadingComments: false
    }))
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openPublish() {
  if (muteInfo.muted) {
    uni.showToast({ title: '您已被物业禁言，暂时无法发布动态', icon: 'none', duration: 2500 })
    return
  }
  if (!houseStore.guardBound()) return
  form.content = ''
  form.images = []
  showPublish.value = true
}

function pickImages() {
  const left = 9 - form.images.length
  if (left <= 0) return
  uni.chooseImage({
    count: left,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        for (const path of res.tempFilePaths || []) {
          const url = await uploadFile(path)
          const rel = typeof url === 'string' ? url : url?.url || ''
          if (rel) form.images.push(rel)
        }
      } catch (e) {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

async function submitPublish() {
  if (!form.content.trim()) {
    uni.showToast({ title: '请填写内容', icon: 'none' })
    return
  }
  publishing.value = true
  try {
    await publishAppeal({
      content: form.content.trim(),
      imageUrls: form.images,
      houseId: houseStore.currentHouseId,
      houseLabel: houseStore.shortLabel || houseStore.label,
      nickname: store.nickname
    })
    showPublish.value = false
    uni.showToast({ title: '已发布', icon: 'success' })
    await load()
  } catch (e) {
    const msg = e?.msg || e?.message || ''
    if (msg.includes('禁言')) {
      uni.showToast({ title: '您已被物业禁言，暂时无法发布动态', icon: 'none', duration: 2500 })
      muteInfo.muted = true
    }
  } finally {
    publishing.value = false
  }
}

async function onLike(item) {
  try {
    const data = await toggleAppealLike(item.id)
    item.liked = !!data?.liked
    item.likeCount = data?.likeCount ?? item.likeCount
  } catch (e) {
    uni.showToast({ title: '点赞失败', icon: 'none' })
  }
}

function onDraftInput(e) {
  commentDraft.value = e?.detail?.value ?? ''
}

function onComposerFocus(item) {
  activeCommentId.value = item.id
}

/** 点击气泡：展开评论区并聚焦输入框 */
async function onTapComment(item) {
  const opening = !item.showComments
  list.value.forEach((x) => {
    if (x.id !== item.id) x.showComments = false
  })
  item.showComments = opening
  if (!opening) {
    if (activeCommentId.value === item.id) {
      activeCommentId.value = null
      commentDraft.value = ''
      composerFocus.value = false
    }
    return
  }
  activeCommentId.value = item.id
  commentDraft.value = ''
  await ensureComments(item)
  composerFocus.value = false
  setTimeout(() => {
    composerFocus.value = true
  }, 100)
}

async function ensureComments(item) {
  if (item.commentList) return
  item.loadingComments = true
  try {
    item.commentList = ((await listAppealComments(item.id)) || []).map((c) => ({
      ...c,
      avatarBroken: false
    }))
  } catch (e) {
    item.commentList = []
  } finally {
    item.loadingComments = false
  }
}

async function submitComment() {
  if (commenting.value) return
  if (muteInfo.muted) {
    uni.showToast({ title: MUTE_TIP_SHORT, icon: 'none', duration: 2500 })
    return
  }
  const text = commentDraft.value.trim()
  if (!text) {
    uni.showToast({ title: '请输入评论内容', icon: 'none' })
    return
  }
  const item = list.value.find((x) => x.id === activeCommentId.value)
  if (!item) {
    uni.showToast({ title: '请先点击评论图标', icon: 'none' })
    return
  }
  commenting.value = true
  try {
    const data = await addAppealComment(item.id, text)
    commentDraft.value = ''
    if (!item.commentList) item.commentList = []
    item.commentList.push({
      ...data,
      avatarBroken: false,
      mine: true
    })
    item.commentCount = data?.commentCount ?? (item.commentCount || 0) + 1
    item.showComments = true
    composerFocus.value = false
    uni.showToast({ title: '已评论', icon: 'success' })
  } catch (e) {
    const msg = e?.msg || e?.message || ''
    if (msg.includes('禁言')) {
      uni.showToast({ title: MUTE_TIP_SHORT, icon: 'none', duration: 2500 })
      muteInfo.muted = true
    } else {
      uni.showToast({ title: msg || '评论失败', icon: 'none' })
    }
  } finally {
    commenting.value = false
  }
}

function onDeletePost(item) {
  uni.showModal({
    title: '删除动态',
    content: '确定删除这条动态？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteAppeal(item.id)
        list.value = list.value.filter((x) => x.id !== item.id)
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (e) {}
    }
  })
}

const reportDlg = ref(false)
const reportSubmitting = ref(false)
const reportTarget = reactive({ type: '', id: null })
const reportReasons = ['垃圾广告', '辱骂攻击', '不实信息', '其他违规']

function onReport(targetType, targetId) {
  reportTarget.type = targetType
  reportTarget.id = targetId
  reportDlg.value = true
}

function closeReport() {
  reportDlg.value = false
  reportTarget.type = ''
  reportTarget.id = null
}

async function pickReportReason(reason) {
  if (reportSubmitting.value || !reportTarget.id) return
  reportSubmitting.value = true
  try {
    await submitAppealReport({
      targetType: reportTarget.type,
      targetId: reportTarget.id,
      reason,
    })
    reportDlg.value = false
    reportTarget.type = ''
    reportTarget.id = null
    uni.showToast({ title: '举报已提交，物业将处理', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: e?.msg || e?.message || '提交失败', icon: 'none' })
  } finally {
    reportSubmitting.value = false
  }
}

function onDeleteComment(item, c) {
  uni.showModal({
    title: '删除评论',
    content: '确定删除这条评论？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteAppealComment(c.id)
        item.commentList = (item.commentList || []).filter((x) => x.id !== c.id)
        item.commentCount = Math.max(0, (item.commentCount || 1) - 1)
      } catch (e) {}
    }
  })
}
</script>

<style lang="scss" scoped>
$mint: #47b686;
$ink: #1f2a28;

.moments-page {
  position: absolute;
  inset: 0;
  background: #ededed;
  overflow: hidden;
}
.top-bar {
  position: absolute;
  left: 0; right: 0; top: 0;
  z-index: 20;
  background: #47b686;
  color: #fff;
}
.top-inner {
  height: 88rpx;
  padding: 0 20rpx 0 8rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.top-left {
  display: flex;
  align-items: center;
  min-width: 0;
  padding-left: 16rpx;
}
.top-title { font-size: 34rpx; font-weight: 700; }
.publish-btn {
  padding: 8rpx 22rpx;
  background: rgba(255,255,255,.2);
  border-radius: 999rpx;
  font-size: 26rpx;
}
.feed {
  height: 100%;
  box-sizing: border-box;
}
.mute-banner {
  margin: 16rpx 24rpx;
  padding: 16rpx 20rpx;
  background: #fff7ed;
  color: #c2410c;
  border-radius: 12rpx;
  font-size: 24rpx;
}
.scope-tip {
  margin: 12rpx 24rpx 4rpx;
  font-size: 22rpx;
  color: #94a3b8;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 80rpx 24rpx;
  font-size: 28rpx;
}
.post-card {
  background: #fff;
  margin-bottom: 16rpx;
  padding: 28rpx 28rpx 20rpx;
}
.post-hd {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}
.avatar {
  width: 80rpx; height: 80rpx; border-radius: 12rpx;
  background: linear-gradient(145deg, #6ed3a8, $mint);
  color: #fff; font-weight: 700; font-size: 32rpx;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.avatar-img {
  width: 80rpx; height: 80rpx; border-radius: 12rpx; flex-shrink: 0; background: #f1f5f9;
}
.meta { flex: 1; min-width: 0; }
.name { display: block; font-size: 30rpx; font-weight: 700; color: #576b95; }
.sub { display: block; margin-top: 6rpx; font-size: 22rpx; color: #9aa3a0; }
.del { font-size: 24rpx; color: #94a3b8; padding: 8rpx; }
.content {
  display: block;
  margin: 16rpx 0 12rpx;
  font-size: 30rpx;
  color: $ink;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.img-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 12rpx;
}
.img-grid .img {
  background: #f1f5f9;
  border-radius: 8rpx;
}
.img-grid.n1 .img { width: 420rpx; height: 420rpx; }
.img-grid.n2 .img, .img-grid.n4 .img { width: 320rpx; height: 320rpx; }
.img-grid.n3 .img, .img-grid.n5 .img, .img-grid.n6 .img,
.img-grid.n7 .img, .img-grid.n8 .img, .img-grid.n9 .img {
  width: 210rpx; height: 210rpx;
}
.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8rpx;
  padding-top: 8rpx;
  border-top: 1rpx solid #f0f3f2;
}
.act {
  display: flex;
  align-items: center;
  gap: 8rpx;
  min-width: 72rpx;
  min-height: 64rpx;
  padding: 8rpx 16rpx;
  justify-content: center;
}
.num {
  font-size: 24rpx;
  color: #94a3b8;
  min-width: 20rpx;
}
.num.on { color: #e11d48; }

/* 心形点赞图标 */
.icon-heart {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.heart-shape {
  width: 22rpx;
  height: 22rpx;
  background: #94a3b8;
  transform: rotate(-45deg);
  position: relative;
  border-radius: 2rpx;
}
.heart-shape::before,
.heart-shape::after {
  content: '';
  position: absolute;
  width: 22rpx;
  height: 22rpx;
  background: #94a3b8;
  border-radius: 50%;
}
.heart-shape::before { top: -11rpx; left: 0; }
.heart-shape::after { top: 0; left: 11rpx; }
.icon-heart.on .heart-shape,
.icon-heart.on .heart-shape::before,
.icon-heart.on .heart-shape::after {
  background: #e11d48;
}

/* 气泡评论图标 */
.icon-comment {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bubble {
  width: 30rpx;
  height: 24rpx;
  border: 3rpx solid #64748b;
  border-radius: 8rpx 8rpx 8rpx 2rpx;
  box-sizing: border-box;
  position: relative;
  background: transparent;
}
.bubble::after {
  content: '';
  position: absolute;
  left: 4rpx;
  bottom: -10rpx;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 8rpx 8rpx 0 0;
  border-color: #64748b transparent transparent transparent;
}
.icon-comment.on .bubble {
  border-color: $mint;
  background: rgba(71, 182, 134, 0.12);
}
.icon-comment.on .bubble::after {
  border-color: $mint transparent transparent transparent;
}

.comments {
  margin-top: 12rpx;
  background: #f7f7f7;
  border-radius: 12rpx;
  padding: 16rpx;
}
.c-tip { font-size: 24rpx; color: #94a3b8; padding: 4rpx 0 8rpx; }
.c-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  font-size: 26rpx;
  line-height: 1.5;
  margin-bottom: 14rpx;
}
.c-avatar, .c-avatar-img {
  width: 48rpx;
  height: 48rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.c-avatar {
  background: linear-gradient(145deg, #6ed3a8, $mint);
  color: #fff;
  font-size: 22rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.c-main { flex: 1; min-width: 0; }
.c-name {
  display: block;
  color: #576b95;
  font-weight: 600;
  margin-bottom: 4rpx;
}
.c-body {
  display: block;
  color: $ink;
  word-break: break-word;
}
.c-del { color: #94a3b8; font-size: 22rpx; flex-shrink: 0; padding: 4rpx; }

.c-composer {
  margin-top: 12rpx;
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  padding: 12rpx;
  background: #fff;
  border-radius: 12rpx;
  border: 1rpx solid #e5e7eb;
}
.c-textarea {
  flex: 1;
  min-width: 0;
  min-height: 72rpx;
  max-height: 200rpx;
  padding: 12rpx 8rpx;
  font-size: 28rpx;
  color: $ink;
  line-height: 1.45;
  width: 100%;
  box-sizing: border-box;
}
.c-send-btn {
  flex-shrink: 0;
  padding: 12rpx 22rpx;
  background: $mint;
  color: #fff;
  border-radius: 10rpx;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.2;
}
.c-send-btn.disabled {
  opacity: 0.4;
}

.mask {
  position: absolute; inset: 0; z-index: 50;
  background: rgba(0,0,0,.45);
  display: flex; align-items: flex-end;
}
.report-mask {
  position: absolute;
  inset: 0;
  z-index: 60;
  background: rgba(15, 31, 26, 0.45);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  box-sizing: border-box;
}
.report-sheet {
  width: 100%;
  max-width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 28rpx calc(16rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.report-hd {
  font-size: 32rpx;
  font-weight: 800;
  color: $ink;
  text-align: center;
}
.report-sub {
  display: block;
  margin: 8rpx 0 20rpx;
  text-align: center;
  font-size: 22rpx;
  color: #94a3b8;
}
.report-item {
  padding: 28rpx 16rpx;
  text-align: center;
  font-size: 30rpx;
  color: $ink;
  border-top: 1rpx solid #f1f5f9;
}
.report-item:active { background: #f8fafc; }
.report-cancel {
  margin-top: 12rpx;
  padding: 28rpx 16rpx;
  text-align: center;
  font-size: 30rpx;
  color: #64748b;
  background: #f5f7f6;
  border-radius: 16rpx;
}
.sheet {
  width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  box-sizing: border-box; max-height: 85%;
}
.sheet-user {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.sheet-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: #f1f5f9;
}
.sheet-avatar.fallback {
  background: linear-gradient(145deg, #6ed3a8, $mint);
  color: #fff;
  font-weight: 700;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sheet-user-meta { flex: 1; min-width: 0; }
.sheet-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: $ink;
}
.sheet-house {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #8a9693;
}
.sheet-area {
  width: 100%; min-height: 180rpx; background: #f5f7f6;
  border-radius: 12rpx; padding: 20rpx; box-sizing: border-box; font-size: 28rpx;
}
.sheet-imgs { display: flex; flex-wrap: wrap; gap: 12rpx; margin: 20rpx 0; }
.sheet-img-wrap { position: relative; width: 140rpx; height: 140rpx; }
.sheet-img { width: 140rpx; height: 140rpx; border-radius: 10rpx; }
.sheet-img-x {
  position: absolute; right: -6rpx; top: -6rpx;
  width: 36rpx; height: 36rpx; border-radius: 50%;
  background: #334155; color: #fff; font-size: 24rpx;
  text-align: center; line-height: 36rpx;
}
.sheet-add {
  width: 140rpx; height: 140rpx; border: 2rpx dashed #94a3b8;
  border-radius: 10rpx; color: #94a3b8; font-size: 48rpx;
  display: flex; align-items: center; justify-content: center;
}
.btn-primary {
  background: $mint; color: #fff; border: none; border-radius: 999rpx;
  height: 84rpx; line-height: 84rpx; font-size: 30rpx;
}
.safe-bottom { height: 40rpx; }
</style>
