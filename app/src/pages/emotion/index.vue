<template>
  <view class="page">
    <view class="intro">
      <text class="intro-title">情绪轻调解</text>
      <text class="intro-desc">
        AI 识别情绪并给出疏导建议；尖锐矛盾可通知物业工作人员协助调解（隐私隔离）
      </text>
    </view>

    <view class="card">
      <text class="label">描述您的诉求或邻里矛盾</text>
      <textarea
        v-model="text"
        class="area"
        maxlength="800"
        placeholder="请客观描述事实与感受，勿包含他人隐私证件号"
      />
      <view class="actions">
        <button class="btn-primary" :loading="loading" @click="analyze">AI疏导建议</button>
        <button class="btn-ghost" @click="goButler">请求调解</button>
      </view>
    </view>

    <view v-if="result" class="result-wrap">
      <view class="card result-block">
        <text class="block-label">情绪识别</text>
        <view class="tag-row">
          <view class="meta-item">
            <text class="meta-k">情绪倾向</text>
            <text class="pill" :class="result.sentiment">{{ sentimentLabel }}</text>
          </view>
          <view class="meta-item">
            <text class="meta-k">细分类</text>
            <text class="meta-v">{{ result.emotionTags || '—' }}</text>
          </view>
        </view>
      </view>

      <view class="card result-block advice-block">
        <text class="block-label">AI 疏导建议</text>
        <text class="advice-text">{{ displayAdvice }}</text>
      </view>

      <view v-if="result.needGrid" class="card result-block warn-block">
        <text class="block-label">调解工单</text>
        <text class="warn-text">
          已判定情绪较尖锐{{ result.caseId ? `，调解工单 ${result.caseId}` : '' }}，物业工作人员将介入（原文仅员工可见）。
        </text>
      </view>
    </view>

    <view class="card">
      <view class="card-hd">
        <text class="card-title">我的调解记录</text>
        <text class="link" @click="loadMine">刷新</text>
      </view>
      <view v-if="!mine.length" class="empty">暂无记录</view>
      <view v-for="c in mine" :key="c.id" class="case">
        <text class="case-t">工单 {{ c.id }} · {{ sentimentText(c.sentiment) }} · {{ statusText(c.status) }}</text>
        <text class="case-q">我的问题：{{ c.content || '—' }}</text>
        <text class="case-a">疏导建议：{{ sanitizeStaffText(c.advice) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { emotion, myEmotionCases } from '../../api/ai'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const text = ref('')
const loading = ref(false)
const result = ref(null)
const mine = ref([])

function leaveIfLocked() {
  if (!store.guardFeature('ai_emotion')) {
    uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) })
    return true
  }
  return false
}

const sentimentLabel = computed(() => sentimentText(result.value?.sentiment))

function sentimentText(s) {
  if (s === 'negative') return '负面'
  if (s === 'positive') return '正面'
  if (s === 'neutral') return '中性'
  return s || '中性'
}

function statusText(s) {
  const map = {
    pending: '待处理',
    processing: '处理中',
    handling: '处理中',
    closed: '已办结',
    done: '已办结',
    rejected: '已驳回',
  }
  return map[s] || (s ? String(s) : '未知')
}

function sanitizeStaffText(s) {
  if (!s) return ''
  return String(s).replace(/网格员/g, '物业工作人员')
}

const displayAdvice = computed(() => sanitizeStaffText(result.value?.advice || ''))

function goButler() {
  if (!store.guardFeature('ai_chat')) return
  uni.switchTab({ url: '/pages/ai/chat' })
}

async function analyze() {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  if (!store.guardFeature('ai_emotion')) return
  if (!text.value.trim()) {
    uni.showToast({ title: '请先填写描述', icon: 'none' })
    return
  }
  loading.value = true
  try {
    result.value = await emotion(text.value.trim())
    await loadMine()
  } finally {
    loading.value = false
  }
}

async function loadMine() {
  try {
    mine.value = (await myEmotionCases()) || []
  } catch {
    mine.value = []
  }
}

onMounted(() => {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  if (leaveIfLocked()) return
  loadMine()
})
</script>

<style lang="scss" scoped>
$mint: #47b686;
$mint-soft: #e8f6ef;
$text: #1f2a28;
$muted: #8a9693;
$line: #e8eeec;
$bg: #f5f7f6;

.page {
  min-height: 100vh;
  padding: 36rpx 32rpx 48rpx;
  background: $bg;
  box-sizing: border-box;
}

.intro {
  padding: 8rpx 8rpx 28rpx;
}
.intro-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: $text;
  letter-spacing: 1rpx;
}
.intro-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $muted;
  line-height: 1.65;
}

.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.06);
}

.label {
  font-size: 26rpx;
  color: $muted;
  font-weight: 500;
}
.area {
  width: 100%;
  margin-top: 16rpx;
  min-height: 220rpx;
  background: #f8faf9;
  border: 2rpx solid $line;
  border-radius: 20rpx;
  padding: 20rpx;
  box-sizing: border-box;
  font-size: 28rpx;
  color: $text;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}
.btn-primary {
  flex: 1.2;
  background: $mint;
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 28rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-weight: 600;
}
.btn-ghost {
  flex: 1;
  background: #fff;
  color: $mint;
  border: 2rpx solid rgba(71, 182, 134, 0.45);
  border-radius: 999rpx;
  font-size: 28rpx;
  height: 84rpx;
  line-height: 80rpx;
  font-weight: 500;
}

.result-wrap {
  margin-bottom: 8rpx;
}
.result-block {
  margin-bottom: 20rpx;
}
.block-label {
  display: block;
  font-size: 24rpx;
  color: $muted;
  margin-bottom: 16rpx;
  letter-spacing: 1rpx;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx 40rpx;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.meta-k {
  font-size: 26rpx;
  color: $muted;
}
.meta-v {
  font-size: 28rpx;
  color: $text;
  font-weight: 500;
}
.pill {
  font-size: 24rpx;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  font-weight: 600;
}
.pill.negative {
  color: #b42318;
  background: #fef3f2;
}
.pill.positive {
  color: #027a48;
  background: #ecfdf3;
}
.pill.neutral {
  color: #475467;
  background: #f2f4f7;
}

.advice-block {
  background: linear-gradient(180deg, #f7fcf9 0%, #fff 48%);
  border: 2rpx solid rgba(71, 182, 134, 0.12);
}
.advice-text {
  display: block;
  font-size: 28rpx;
  color: $text;
  line-height: 1.7;
}

.warn-block {
  background: #fffaf5;
  border: 2rpx solid rgba(180, 83, 9, 0.12);
}
.warn-text {
  display: block;
  font-size: 26rpx;
  color: #9a3412;
  line-height: 1.65;
}

.card-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text;
}
.link {
  color: $mint;
  font-size: 24rpx;
}
.empty {
  color: #94a3b8;
  font-size: 24rpx;
  padding: 12rpx 0;
}
.case {
  padding: 20rpx 0;
  border-bottom: 1px solid #f1f5f9;
}
.case:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.case-t {
  display: block;
  font-size: 26rpx;
  color: $text;
  font-weight: 500;
}
.case-q {
  display: block;
  font-size: 26rpx;
  color: $text;
  margin-top: 10rpx;
  line-height: 1.55;
}
.case-a {
  display: block;
  font-size: 24rpx;
  color: $muted;
  margin-top: 8rpx;
  line-height: 1.55;
}
</style>
