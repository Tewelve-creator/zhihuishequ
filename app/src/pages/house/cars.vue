<template>
  <view class="page">
    <view class="card head">
      <text class="title">我的车辆</text>
      <text class="sub">绑定当前房屋的常驻车辆，一套房屋可登记多辆</text>
      <view class="house" v-if="houseLabel">当前房屋：{{ houseLabel }}</view>
      <button class="btn-primary" size="mini" @click="openForm()">+ 添加车辆</button>
    </view>

    <view v-if="loading" class="empty">加载中...</view>
    <view v-else-if="!list.length" class="empty">暂无车辆，请添加</view>
    <view v-for="c in list" :key="c.id" class="card item">
      <view class="row">
        <text class="plate">{{ c.plateNo }}</text>
        <text class="tag">{{ c.status === 'ACTIVE' ? '正常' : '停用' }}</text>
      </view>
      <text class="meta">{{ c.brandModel || '未填车型' }} · {{ c.color || '—' }}</text>
      <view class="ops">
        <text class="link" @click="openForm(c)">编辑</text>
        <text class="link danger" @click="onRemove(c)">删除</text>
      </view>
    </view>

    <u-popup :show="showForm" mode="bottom" round="28" @close="showForm = false">
      <view class="form">
        <text class="ft">{{ form.id ? '编辑车辆' : '添加车辆' }}</text>
        <view class="input-row">
          <text class="label">车牌</text>
          <input v-model="form.plateNo" placeholder="如 京A12345" />
        </view>
        <view class="input-row">
          <text class="label">车型</text>
          <input v-model="form.brandModel" placeholder="品牌型号" />
        </view>
        <view class="input-row">
          <text class="label">颜色</text>
          <input v-model="form.color" placeholder="可选" />
        </view>
        <button class="btn-primary" :loading="saving" @click="submit">保存</button>
      </view>
    </u-popup>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listResidentCars, removeResidentCar, saveResidentCar } from '../../api/residentCar'
import { useHouseStore } from '../../store/house'
import { useUserStore } from '../../store/user'

const store = useUserStore()
const houseStore = useHouseStore()
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const showForm = ref(false)
const form = reactive({
  id: undefined,
  plateNo: '',
  brandModel: '',
  color: ''
})

const houseLabel = computed(() => houseStore.label || '')

async function load() {
  if (!store.token) return uni.reLaunch({ url: '/pages/login/login' })
  if (!store.isResident) {
    uni.showToast({ title: '请使用居民账号', icon: 'none' })
    return
  }
  if (!houseStore.guardBound()) return
  loading.value = true
  try {
    list.value =
      (await listResidentCars({ houseId: houseStore.currentHouseId })) || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openForm(c) {
  form.id = c?.id
  form.plateNo = c?.plateNo || ''
  form.brandModel = c?.brandModel || ''
  form.color = c?.color || ''
  showForm.value = true
}

async function submit() {
  if (!form.plateNo.trim()) {
    uni.showToast({ title: '请填写车牌', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await saveResidentCar({
      id: form.id,
      houseId: houseStore.currentHouseId,
      plateNo: form.plateNo.trim(),
      brandModel: form.brandModel,
      color: form.color
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    showForm.value = false
    load()
  } finally {
    saving.value = false
  }
}

function onRemove(c) {
  uni.showModal({
    title: '删除车辆',
    content: `确认删除 ${c.plateNo}？`,
    success: async (res) => {
      if (!res.confirm) return
      await removeResidentCar(c.id)
      uni.showToast({ title: '已删除', icon: 'success' })
      load()
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
  padding: 24rpx 28rpx 48rpx;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}
.title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
}
.sub,
.house,
.meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a9693;
}
.btn-primary {
  margin-top: 20rpx;
  background: $mint;
  color: #fff;
  border: none;
  border-radius: 999rpx;
}
.plate {
  font-size: 34rpx;
  font-weight: 700;
  color: #1f2a28;
}
.row {
  display: flex;
  justify-content: space-between;
}
.tag {
  font-size: 22rpx;
  color: $mint;
}
.ops {
  margin-top: 16rpx;
  display: flex;
  gap: 28rpx;
}
.link {
  color: $mint;
  font-size: 26rpx;
}
.link.danger {
  color: #e74c3c;
}
.empty {
  text-align: center;
  color: #94a3b8;
  padding: 60rpx 0;
}
.form {
  padding: 32rpx;
}
.ft {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}
.input-row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f0f3f2;
}
.label {
  width: 100rpx;
  font-size: 28rpx;
}
.input-row input {
  flex: 1;
  font-size: 28rpx;
}
</style>
