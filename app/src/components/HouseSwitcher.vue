<template>
  <!-- 首页顶部当前房屋切换 -->
  <view class="hs-wrap">
    <view v-if="showTrigger" class="hs-trigger" @click="open">
      <view class="hs-texts">
        <text class="hs-label">当前房屋</text>
        <text class="hs-name">{{ displayLabel }}</text>
      </view>
      <u-icon name="arrow-down" :color="iconColor" size="12" />
    </view>

    <u-popup :show="visible" mode="bottom" round="28" @close="visible = false">
      <view class="sheet">
        <text class="sheet-title">切换房屋</text>
        <view v-if="!houseStore.houses.length" class="empty">
          <text>暂无绑定房屋</text>
          <button class="btn" @click="goBind">去绑定</button>
        </view>
        <view
          v-for="h in houseStore.houses"
          :key="h.id"
          class="card-item"
          :class="{ active: isCurrent(h) }"
          @click="pick(h)"
        >
          <view class="ci-left">
            <text class="ci-t">{{ h.building }}{{ h.unit }}{{ h.roomNo }}</text>
            <text class="ci-s">{{ h.communityName }}</text>
          </view>
          <text v-if="isCurrent(h)" class="tag">当前选中</text>
        </view>
        <view class="add" @click="goBind">+ 新增绑定房屋</view>
      </view>
    </u-popup>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useHouseStore } from '../store/house'

const props = defineProps({
  iconColor: { type: String, default: '#ffffff' },
  showTrigger: { type: Boolean, default: true }
})

const emit = defineEmits(['change'])

const houseStore = useHouseStore()
const visible = ref(false)

const displayLabel = computed(() => {
  if (!houseStore.hasHouse) return '点击绑定房屋'
  return houseStore.shortLabel
})

function open() {
  visible.value = true
}

function isCurrent(h) {
  return String(h.id) === String(houseStore.currentHouseId)
}

async function pick(h) {
  if (isCurrent(h)) {
    visible.value = false
    return
  }
  await houseStore.switchTo(h.id)
  visible.value = false
  uni.showToast({ title: '已切换房屋', icon: 'success' })
  emit('change', h)
}

function goBind() {
  visible.value = false
  uni.navigateTo({ url: '/pages/house/bind' })
}

defineExpose({ open })
</script>

<style lang="scss" scoped>
$mint: #47b686;
.hs-wrap {
  flex: 1;
  min-width: 0;
}
.hs-trigger {
  display: flex;
  align-items: center;
  gap: 8rpx;
  max-width: 100%;
}
.hs-texts {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.hs-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.75);
  line-height: 1.2;
}
.hs-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360rpx;
}
.sheet {
  padding: 32rpx 28rpx 48rpx;
}
.sheet-title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 24rpx;
  color: #1f2a28;
}
.card-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f5f7f6;
  border-radius: 24rpx;
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 2rpx solid transparent;
}
.card-item.active {
  border-color: $mint;
  background: #e8f7f0;
}
.ci-t {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2a28;
  display: block;
}
.ci-s {
  font-size: 24rpx;
  color: #8a9693;
  margin-top: 6rpx;
  display: block;
}
.tag {
  font-size: 22rpx;
  color: #fff;
  background: $mint;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}
.add {
  text-align: center;
  color: $mint;
  font-size: 28rpx;
  padding: 24rpx;
  font-weight: 500;
}
.empty {
  text-align: center;
  color: #8a9693;
  padding: 40rpx 0;
}
.btn {
  margin-top: 20rpx;
  background: $mint;
  color: #fff;
  border-radius: 999rpx;
  font-size: 26rpx;
  border: none;
}
</style>
