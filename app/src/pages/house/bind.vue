<template>
  <view class="page">
    <!-- 已绑定列表 -->
    <view class="card">
      <view class="card-head">
        <text class="card-title">我的房屋</text>
        <view class="head-links">
          <text class="link" @click="goCars">我的车辆</text>
          <text class="link" @click="openCascader">+ 新增绑定</text>
        </view>
      </view>
      <view v-if="loading" class="empty">加载中...</view>
      <view v-else-if="!houses.length" class="empty">
        <text>尚未绑定房屋</text>
        <button class="btn-primary mt" @click="openCascader">立即绑定</button>
      </view>
      <view v-for="h in houses" :key="h.id" class="house-item" @click="onSelect(h)">
        <view class="hi-main">
          <text class="hi-title">{{ h.building }}{{ h.unit }}{{ h.roomNo }}</text>
          <text class="hi-sub">{{ h.communityName }}</text>
        </view>
        <view class="hi-right">
          <text v-if="isCurrent(h)" class="badge">当前选中</text>
          <text v-else-if="h.isDefault === 1" class="badge soft">默认</text>
          <text class="unbind" @click.stop="onUnbind(h)">解绑</text>
        </view>
      </view>
    </view>

    <view class="tip">绑定/解绑需物业审批通过后生效；一套账号可绑定多套房。</view>

    <!-- 我的申请 -->
    <view class="card mt-card">
      <view class="card-head">
        <text class="card-title">我的申请</text>
        <text class="link" @click="loadApplies">刷新</text>
      </view>
      <view v-if="!applies.length" class="empty">暂无迁入/迁出申请</view>
      <view v-for="a in applies" :key="a.id" class="apply-item">
        <view>
          <text class="hi-title">{{ a.type }} · {{ a.room || a.houseLabel }}</text>
          <text class="hi-sub">{{ a.applyNo }} · {{ a.identityLabel || '' }}</text>
        </view>
        <text class="badge" :class="a.status === 'PENDING' ? '' : a.status === 'APPROVED' ? 'soft' : 'rej'">
          {{ a.statusLabel }}
        </text>
      </view>
    </view>

    <!-- 四级级联弹窗 -->
    <u-popup :show="showCascader" mode="bottom" round="28" @close="showCascader = false">
      <view class="cascader">
        <view class="cas-title">绑定房屋</view>
        <view class="steps">
          <text :class="['st', step === 0 ? 'on' : '']">社区</text>
          <text class="sep">›</text>
          <text :class="['st', step === 1 ? 'on' : '']">楼栋</text>
          <text class="sep">›</text>
          <text :class="['st', step === 2 ? 'on' : '']">单元</text>
          <text class="sep">›</text>
          <text :class="['st', step === 3 ? 'on' : '']">房号</text>
        </view>

        <!-- 社区：仅当前登录租户，禁止跨社区 -->
        <view v-if="step === 0" class="opt-list">
          <view class="opt on locked">
            <text class="opt-t">{{ communityName }}</text>
            <text class="opt-d">当前登录社区（不可跨社区）</text>
          </view>
          <button class="btn-primary" @click="step = 1; loadBuildings()">下一步 · 选楼栋</button>
        </view>

        <view v-else-if="step === 1" class="opt-list">
          <view v-if="!buildings.length" class="empty">暂无楼栋数据</view>
          <view
            v-for="b in buildings"
            :key="b"
            class="opt"
            :class="{ on: pick.building === b }"
            @click="chooseBuilding(b)"
          >
            <text class="opt-t">{{ b }}</text>
          </view>
          <view class="back" @click="step = 0">‹ 返回社区</view>
        </view>

        <view v-else-if="step === 2" class="opt-list">
          <view v-if="!units.length" class="empty">暂无单元</view>
          <view
            v-for="u in units"
            :key="u"
            class="opt"
            :class="{ on: pick.unit === u }"
            @click="chooseUnit(u)"
          >
            <text class="opt-t">{{ u }}</text>
          </view>
          <view class="back" @click="step = 1">‹ 返回楼栋</view>
        </view>

        <view v-else class="opt-list">
          <view v-if="!rooms.length" class="empty">暂无可选房号</view>
          <view
            v-for="r in rooms"
            :key="r.id"
            class="opt"
            :class="{ on: pick.houseId === r.id, disabled: r.bound }"
            @click="chooseRoom(r)"
          >
            <text class="opt-t">{{ r.roomNo }}</text>
            <text class="opt-d">{{ r.bound ? '已绑定' : r.label }}</text>
          </view>
          <view class="back" @click="step = 2">‹ 返回单元</view>
          <view class="id-row">
            <text class="id-lab">身份</text>
            <view class="id-opts">
              <text :class="['id-opt', pick.identityType === 'OWNER' ? 'on' : '']" @click="pick.identityType = 'OWNER'">业主</text>
              <text :class="['id-opt', pick.identityType === 'TENANT' ? 'on' : '']" @click="pick.identityType = 'TENANT'">租客</text>
            </view>
          </view>
          <button
            class="btn-primary"
            :loading="binding"
            :disabled="!pick.houseId"
            @click="confirmBind"
          >
            提交迁入申请
          </button>
        </view>
      </view>
    </u-popup>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  bindHouse,
  listBuildings,
  listMyApplies,
  listMyHouses,
  listRooms,
  listUnits,
  unbindHouse
} from '../../api/house'
import { useHouseStore } from '../../store/house'
import { useUserStore } from '../../store/user'

const houseStore = useHouseStore()
const userStore = useUserStore()
const houses = ref([])
const applies = ref([])
const loading = ref(false)
const showCascader = ref(false)
const step = ref(0)
const buildings = ref([])
const units = ref([])
const rooms = ref([])
const binding = ref(false)
const pick = reactive({
  building: '',
  unit: '',
  houseId: null,
  identityType: 'OWNER'
})

const communityName = computed(
  () =>
    uni.getStorageSync('communityName') ||
    houseStore.currentHouse?.communityName ||
    '当前社区'
)

function isCurrent(h) {
  return String(h.id) === String(houseStore.currentHouseId)
}

async function load() {
  loading.value = true
  try {
    houses.value = (await listMyHouses()) || []
    houseStore.houses = houses.value
    houseStore.persistList()
    if (!houseStore.currentHouseId && houses.value.length) {
      const def = houses.value.find((h) => h.isDefault === 1) || houses.value[0]
      houseStore.setCurrent(def, false)
    }
    await loadApplies()
  } finally {
    loading.value = false
  }
}

async function loadApplies() {
  applies.value = (await listMyApplies().catch(() => [])) || []
}

function openCascader() {
  if (!userStore.tenantId) {
    uni.showToast({ title: '缺少社区信息', icon: 'none' })
    return
  }
  step.value = 0
  pick.building = ''
  pick.unit = ''
  pick.houseId = null
  pick.identityType = 'OWNER'
  buildings.value = []
  units.value = []
  rooms.value = []
  showCascader.value = true
}

function goCars() {
  if (!houseStore.guardBound()) return
  uni.navigateTo({ url: '/pages/house/cars' })
}

async function loadBuildings() {
  buildings.value = (await listBuildings().catch(() => [])) || []
}

async function chooseBuilding(b) {
  pick.building = b
  pick.unit = ''
  pick.houseId = null
  units.value = (await listUnits(b).catch(() => [])) || []
  step.value = 2
}

async function chooseUnit(u) {
  pick.unit = u
  pick.houseId = null
  rooms.value = (await listRooms(pick.building, u).catch(() => [])) || []
  step.value = 3
}

function chooseRoom(r) {
  if (r.bound) {
    uni.showToast({ title: '该房屋已绑定', icon: 'none' })
    return
  }
  pick.houseId = r.id
}

async function confirmBind() {
  if (!pick.houseId) {
    uni.showToast({ title: '请选择房号', icon: 'none' })
    return
  }
  binding.value = true
  try {
    await bindHouse({ houseId: pick.houseId, identityType: pick.identityType })
    uni.showToast({ title: '已提交迁入申请', icon: 'success' })
    showCascader.value = false
    await load()
  } finally {
    binding.value = false
  }
}

function onSelect(h) {
  houseStore.switchTo(h.id).then(() => {
    uni.showToast({ title: '已切换为当前房屋', icon: 'success' })
    load()
  })
}

function onUnbind(h) {
  if (houses.value.length <= 1) {
    uni.showToast({ title: '至少保留一套房屋', icon: 'none' })
    return
  }
  uni.showModal({
    title: '申请解绑（迁出）',
    content: `将提交迁出审批：${h.building}${h.unit}${h.roomNo}，通过后解除绑定。`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await unbindHouse(h.id)
        uni.showToast({ title: '已提交迁出申请', icon: 'success' })
        await load()
      } catch (e) {
        /* toast */
      }
    }
  })
}

onShow(load)
</script>

<style lang="scss" scoped>
$mint: #47b686;
.page {
  min-height: 100dvh;
  background: #f5f7f6;
  padding: 24rpx;
  box-sizing: border-box;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  box-shadow: 0 8rpx 28rpx rgba(31, 42, 40, 0.04);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.head-links {
  display: flex;
  gap: 24rpx;
  align-items: center;
}
.card-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2a28;
}
.link {
  color: $mint;
  font-size: 26rpx;
}
.house-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #e8eeec;
}
.house-item:last-child {
  border-bottom: none;
}
.hi-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2a28;
  display: block;
}
.hi-sub {
  font-size: 24rpx;
  color: #8a9693;
  margin-top: 6rpx;
  display: block;
}
.hi-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
}
.badge {
  font-size: 22rpx;
  color: #fff;
  background: $mint;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
}
.badge.soft {
  background: #e8f7f0;
  color: $mint;
}
.unbind {
  font-size: 24rpx;
  color: #ef4444;
}
.empty {
  text-align: center;
  color: #8a9693;
  padding: 40rpx 0;
  font-size: 26rpx;
}
.mt {
  margin-top: 24rpx;
}
.tip {
  margin-top: 24rpx;
  font-size: 24rpx;
  color: #8a9693;
  padding: 0 8rpx;
  line-height: 1.5;
}
.mt-card {
  margin-top: 24rpx;
}
.apply-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #e8eeec;
}
.apply-item:last-child {
  border-bottom: none;
}
.badge.rej {
  background: #fee2e2;
  color: #ef4444;
}
.id-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16rpx 0 8rpx;
}
.id-lab {
  font-size: 26rpx;
  color: #1f2a28;
}
.id-opts {
  display: flex;
  gap: 16rpx;
}
.id-opt {
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: #f5f7f6;
  font-size: 24rpx;
  color: #8a9693;
}
.id-opt.on {
  background: #e8f7f0;
  color: $mint;
  font-weight: 600;
}
.cascader {
  padding: 32rpx 28rpx 48rpx;
  max-height: 75vh;
}
.cas-title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.steps {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 28rpx;
}
.st {
  font-size: 24rpx;
  color: #8a9693;
}
.st.on {
  color: $mint;
  font-weight: 600;
}
.sep {
  color: #ccd5d2;
  font-size: 22rpx;
}
.opt-list {
  max-height: 52vh;
  overflow-y: auto;
}
.opt {
  background: #f5f7f6;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.opt.on {
  border-color: $mint;
  background: #e8f7f0;
}
.opt.disabled {
  opacity: 0.55;
}
.opt.locked {
  border-color: $mint;
}
.opt-t {
  font-size: 28rpx;
  color: #1f2a28;
  font-weight: 500;
  display: block;
}
.opt-d {
  font-size: 22rpx;
  color: #8a9693;
  margin-top: 6rpx;
  display: block;
}
.back {
  text-align: center;
  color: #8a9693;
  font-size: 26rpx;
  padding: 16rpx;
}
.btn-primary {
  margin-top: 20rpx;
  background: $mint;
  color: #fff;
  border-radius: 999rpx;
  font-size: 28rpx;
  border: none;
}
</style>
