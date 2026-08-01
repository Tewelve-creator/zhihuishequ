<template>
  <view class="page">
    <!-- 物业端保留原账单列表 -->
    <view v-if="store.isGridWorker" class="card">
      <view class="empty">网格员无物业费账单权限</view>
    </view>

    <template v-else-if="store.isStaff">
      <view class="card">
        <view class="card-title">本社区物业费账单</view>
        <view class="muted">仅展示当前登录社区数据</view>
      </view>
      <view class="card" v-if="staffBills.length">
        <view v-for="b in staffBills" :key="b.id" class="staff-bill">
          <view>
            <text class="t">{{ b.title }}</text>
            <text class="d">{{ b.period || '' }} · {{ b.houseNo || '-' }}</text>
          </view>
          <view class="right">
            <text class="amt">¥{{ b.amount }}</text>
            <text class="st">{{ b.status === 'paid' ? '已缴' : '待缴' }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">暂无账单</view>
    </template>

    <!-- 居民端：我的缴费 -->
    <template v-else>
      <view class="house-bar" @click="openHouse">
        <view>
          <text class="hb-lab">当前房屋</text>
          <text class="hb-val">{{ houseText }}</text>
        </view>
        <text class="hb-link">切换 ›</text>
      </view>

      <view class="summary card">
        <view class="sum-item">
          <text class="sum-n">¥{{ formatMoney(summary.unpaidAmount) }}</text>
          <text class="sum-l">待缴总额</text>
        </view>
        <view class="sum-item">
          <text class="sum-n">{{ summary.unpaidCount || 0 }}</text>
          <text class="sum-l">待缴笔数</text>
        </view>
        <view class="sum-item">
          <text class="sum-n due">{{ summary.earliestDueDate || '—' }}</text>
          <text class="sum-l">最早截止</text>
        </view>
      </view>

      <view v-if="summary.overdueCount > 0" class="warn-bar">
        有 {{ summary.overdueCount }} 笔账单已逾期，请尽快缴费
      </view>

      <view class="tabs">
        <text
          v-for="t in tabs"
          :key="t.key"
          :class="['tab', tab === t.key ? 'on' : '']"
          @click="switchTab(t.key)"
        >{{ t.name }}</text>
        <text class="tab-link" @click="goRecords">缴费记录</text>
      </view>

      <view v-if="loading" class="state">加载中...</view>
      <view v-else-if="loadError" class="state">
        <text>加载失败</text>
        <button class="btn-primary retry" @click="load">重试</button>
      </view>
      <view v-else-if="!bills.length" class="state">暂无账单</view>

      <view v-else class="list">
        <view
          v-for="b in bills"
          :key="b.id"
          class="bill-card"
          @click="goDetail(b.id)"
        >
          <view class="bc-top">
            <view
              v-if="canSelect"
              class="chk"
              :class="{ on: selected[b.id] }"
              @click.stop="toggle(b)"
            />
            <view class="bc-main">
              <text class="bc-title">{{ b.title }}</text>
              <text class="bc-sub">{{ b.period }} · {{ b.itemName }}</text>
            </view>
            <text :class="['tag', b.status]">{{ statusLabel(b.status) }}</text>
          </view>
          <view class="bc-row">
            <text class="k">优惠/减免</text>
            <text class="v">{{ b.discount > 0 ? `-¥${formatMoney(b.discount)}` : '无' }}</text>
          </view>
          <view class="bc-row">
            <text class="k">截止日期</text>
            <text class="v" :class="{ danger: b.status === 'OVERDUE' }">{{ b.dueDate }}</text>
          </view>
          <view class="bc-row amt-row">
            <text class="k">应缴金额</text>
            <text class="pay">¥{{ formatMoney(b.payable) }}</text>
          </view>
        </view>
      </view>

      <view v-if="canSelect" class="footer">
        <view class="ft-left" @click="toggleAll">
          <view class="chk" :class="{ on: allSelected }" />
          <text>全选</text>
        </view>
        <view class="ft-mid">
          <text class="ft-lab">已选 {{ selectedCount }} 笔</text>
          <text class="ft-amt">¥{{ formatMoney(selectedAmount) }}</text>
        </view>
        <button
          class="ft-btn"
          :disabled="!selectedCount || paying"
          @click="goPay"
        >立即缴费</button>
      </view>

      <HouseSwitcher ref="switcherRef" :show-trigger="false" @change="onHouseChange" />
    </template>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'
import { useHouseStore } from '../../store/house'
import HouseSwitcher from '../../components/HouseSwitcher.vue'
import { listFeeBills } from '../../api/biz'
import {
  formatMoney,
  getFeeSummary,
  listResidentBills,
  statusLabel
} from '../../api/payment'
import { PERMS } from '../../utils/roleAccess'

const store = useUserStore()
const houseStore = useHouseStore()
const switcherRef = ref(null)

const tabs = [
  { key: 'unpaid', name: '待缴' },
  { key: 'paid', name: '已缴' },
  { key: 'all', name: '全部' }
]
const tab = ref('unpaid')
const loading = ref(false)
const loadError = ref(false)
const bills = ref([])
const staffBills = ref([])
const summary = reactive({
  unpaidAmount: 0,
  unpaidCount: 0,
  earliestDueDate: null,
  overdueCount: 0
})
const selected = reactive({})
const paying = ref(false)

const houseText = computed(() => {
  const h = houseStore.currentHouse
  if (!h) return '请先绑定房屋'
  return `${h.communityName || ''} ${h.building || ''}${h.unit || ''}${h.roomNo || ''}`
})

const canSelect = computed(() => tab.value === 'unpaid')
const selectedIds = computed(() =>
  Object.keys(selected).filter((k) => selected[k]).map(Number)
)
const selectedCount = computed(() => selectedIds.value.length)
const selectedAmount = computed(() =>
  bills.value
    .filter((b) => selected[b.id])
    .reduce((s, b) => s + Number(b.payable || 0), 0)
)
const allSelected = computed(
  () => bills.value.length > 0 && bills.value.every((b) => selected[b.id])
)

function clearSelected() {
  Object.keys(selected).forEach((k) => delete selected[k])
}

function toggle(b) {
  selected[b.id] = !selected[b.id]
}

function toggleAll() {
  const on = !allSelected.value
  bills.value.forEach((b) => {
    selected[b.id] = on
  })
}

function switchTab(k) {
  tab.value = k
  clearSelected()
  loadBills()
}

function openHouse() {
  switcherRef.value?.open?.()
}

function onHouseChange() {
  clearSelected()
  load()
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/payment/detail?id=${id}` })
}

function goRecords() {
  uni.navigateTo({ url: '/pages/payment/records' })
}

function goPay() {
  if (!selectedCount.value) return
  const ids = selectedIds.value.join(',')
  uni.navigateTo({ url: `/pages/payment/confirm?billIds=${ids}` })
}

async function loadBills() {
  loading.value = true
  loadError.value = false
  try {
    bills.value = (await listResidentBills({
      houseId: houseStore.currentHouseId,
      tab: tab.value
    })) || []
  } catch (e) {
    loadError.value = true
    bills.value = []
  } finally {
    loading.value = false
  }
}

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (store.isGridWorker) return
  if (store.isStaff) {
    if (!store.canPerm(PERMS.PAYMENT)) return
    staffBills.value = (await listFeeBills().catch(() => [])) || []
    return
  }
  if (!houseStore.guardBound()) {
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) }), 300)
    return
  }
  try {
    const s = await getFeeSummary(houseStore.currentHouseId)
    Object.assign(summary, s || {})
  } catch (e) {
    /* ignore */
  }
  await loadBills()
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx 24rpx 40rpx;
  box-sizing: border-box;
}
.house-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 20rpx;
}
.hb-lab { display: block; font-size: 22rpx; color: #8a9693; }
.hb-val { display: block; margin-top: 6rpx; font-size: 28rpx; font-weight: 600; color: #1f2a28; }
.hb-link { color: $mint; font-size: 26rpx; }
.summary {
  display: flex;
  justify-content: space-between;
  padding: 28rpx 12rpx !important;
}
.sum-item { flex: 1; text-align: center; }
.sum-n { display: block; font-size: 34rpx; font-weight: 700; color: #1f2a28; }
.sum-n.due { font-size: 26rpx; }
.sum-l { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a9693; }
.warn-bar {
  background: #fff7ed;
  color: #c2410c;
  font-size: 24rpx;
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
}
.tabs {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 8rpx 0 20rpx;
}
.tab {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #8a9693;
  background: #fff;
}
.tab.on { background: #e8f7f0; color: $mint; font-weight: 600; }
.tab-link { margin-left: auto; color: $mint; font-size: 26rpx; }
.state {
  text-align: center;
  color: #8a9693;
  padding: 80rpx 0;
  font-size: 28rpx;
}
.retry { margin-top: 24rpx; width: 240rpx; }
.bill-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}
.bc-top { display: flex; align-items: flex-start; gap: 16rpx; margin-bottom: 12rpx; }
.bc-main { flex: 1; min-width: 0; }
.bc-title { display: block; font-size: 30rpx; font-weight: 600; }
.bc-sub { display: block; margin-top: 6rpx; font-size: 24rpx; color: #8a9693; }
.tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #f1f5f4;
  color: #64748b;
}
.tag.UNPAID { background: #e8f7f0; color: $mint; }
.tag.OVERDUE { background: #fee2e2; color: #ef4444; }
.tag.PAID { background: #e0f2fe; color: #0284c7; }
.tag.PAYING { background: #fff7ed; color: #ea580c; }
.bc-row {
  display: flex;
  justify-content: space-between;
  font-size: 24rpx;
  padding: 6rpx 0;
}
.k { color: #8a9693; }
.v.danger { color: #ef4444; }
.amt-row { margin-top: 8rpx; }
.pay { font-size: 32rpx; font-weight: 700; color: #e85d4c; }
.chk {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #c5d0cc;
  margin-top: 6rpx;
  flex-shrink: 0;
}
.chk.on {
  background: $mint;
  border-color: $mint;
  box-shadow: inset 0 0 0 6rpx #fff;
}
.footer {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 12rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);
}
.ft-left { display: flex; align-items: center; gap: 10rpx; font-size: 24rpx; color: #64748b; }
.ft-left .chk { margin-top: 0; }
.ft-mid { flex: 1; text-align: right; }
.ft-lab { display: block; font-size: 22rpx; color: #8a9693; }
.ft-amt { display: block; font-size: 32rpx; font-weight: 700; color: #e85d4c; }
.ft-btn {
  background: $mint;
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 36rpx;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
}
.ft-btn[disabled] { opacity: 0.45; }
.staff-bill {
  display: flex; justify-content: space-between; padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.t { display: block; font-size: 28rpx; font-weight: 600; }
.d { display: block; margin-top: 6rpx; font-size: 22rpx; color: #8a9693; }
.right { text-align: right; }
.amt { display: block; font-size: 30rpx; font-weight: 700; color: #e85d4c; }
.st { font-size: 22rpx; color: #8a9693; }
.empty { text-align: center; color: #8a9693; padding: 40rpx 0; }
</style>
