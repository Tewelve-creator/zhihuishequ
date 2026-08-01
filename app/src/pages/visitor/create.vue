<template>
  <view class="page page-safe">
    <view class="card">
      <view class="card-title">访客预约</view>
      <view class="tip">
        提交后生成通行链接与链接二维码，转发给访客即可。门岗通行码由访客打开链接后实时签发。
      </view>

      <!-- 姓名：整块纵向布局，避免右对齐挤在一起 -->
      <view class="section">
        <view class="section-hd">
          <text class="section-title">访客姓名 <text class="req">*</text></text>
          <text class="section-sub">{{ countLabel }}</text>
        </view>
        <view v-for="(item, idx) in nameList" :key="item.id" class="person-item">
          <view class="person-top">
            <text class="person-label">{{ idx === 0 ? '主访客' : `同行人 ${idx}` }}</text>
            <text v-if="nameList.length > 1" class="del" @click="removeName(idx)">删除</text>
          </view>
          <input
            class="person-input"
            :value="item.name"
            :placeholder="idx === 0 ? '请输入姓名' : '请输入同行人姓名'"
            @input="onNameInput(idx, $event)"
          />
        </view>
        <view class="add-row" @click="addName">
          <text class="add-text">+ 添加同行人</text>
        </view>
      </view>

      <view class="input-row">
        <text class="label">手机</text>
        <input
          :value="form.visitorPhone"
          type="number"
          maxlength="11"
          placeholder="选填，11 位手机号"
          @input="onPhoneInput"
        />
      </view>
      <view class="input-row switch-row">
        <text class="label">驾车来访</text>
        <switch :checked="form.driveFlag" color="#3aaedc" @change="onDriveChange" />
      </view>
      <view v-if="form.driveFlag" class="input-row">
        <text class="label">车牌 <text class="req">*</text></text>
        <input v-model="form.plateNo" placeholder="如 京A12345" />
      </view>
      <view class="input-row">
        <text class="label">事由 <text class="req">*</text></text>
        <picker :range="reasonOptions" :value="reasonIndex" @change="onReasonChange">
          <view class="picker-val" :class="{ placeholder: !form.visitReason }">
            {{ form.visitReason || '请选择来访事由' }}
          </view>
        </picker>
      </view>
      <view class="input-row">
        <text class="label">人数</text>
        <text class="picker-val">{{ displayCount }}</text>
      </view>
      <view class="input-row">
        <text class="label">到访时间 <text class="req">*</text></text>
        <picker
          mode="multiSelector"
          :range="pickerRange"
          :value="pickerIndex"
          @change="onPickerChange"
          @columnchange="onColumnChange"
        >
          <view class="picker-val" :class="{ placeholder: !form.visitTimeLabel }">
            {{ form.visitTimeLabel || '点击选择日期时间' }}
          </view>
        </picker>
      </view>
      <button class="btn-primary" :loading="loading" @click="submit">提交预约</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createVisit } from '../../api/biz'
import { useHouseStore } from '../../store/house'
import { useUserStore } from '../../store/user'

let nameSeq = 1
const store = useUserStore()
const houseStore = useHouseStore()

const reasonOptions = ['亲友探访', '家政服务', '装修施工']
const reasonIndex = ref(0)
const nameList = ref([{ id: nameSeq++, name: '' }])

const form = reactive({
  visitorPhone: '',
  driveFlag: false,
  plateNo: '',
  visitReason: '亲友探访',
  visitTime: '',
  visitTimeLabel: ''
})
const loading = ref(false)

const filledNames = computed(() =>
  nameList.value.map((x) => String(x.name || '').trim()).filter(Boolean)
)
/** 人数：已填姓名数；尚未填写时按当前行数显示 */
const displayCount = computed(() => {
  const n = filledNames.value.length
  return n > 0 ? n : nameList.value.length
})
const countLabel = computed(() => {
  const filled = filledNames.value.length
  const rows = nameList.value.length
  if (filled === 0) return `共 ${rows} 行，请填写姓名`
  if (filled < rows) return `已填 ${filled} 人（还有空行）`
  return `已填 ${filled} 人`
})

const years = []
const months = []
const days = []
const hours = []
const minutes = []
const now = new Date()
for (let y = now.getFullYear(); y <= now.getFullYear() + 1; y++) years.push(String(y))
for (let m = 1; m <= 12; m++) months.push(String(m).padStart(2, '0'))
for (let d = 1; d <= 31; d++) days.push(String(d).padStart(2, '0'))
for (let h = 0; h <= 23; h++) hours.push(String(h).padStart(2, '0'))
for (let m = 0; m <= 59; m += 5) minutes.push(String(m).padStart(2, '0'))

const pickerRange = computed(() => [years, months, days, hours, minutes])

function defaultPickerIndex() {
  const d = new Date(Date.now() + 60 * 60 * 1000)
  return [
    Math.max(0, years.indexOf(String(d.getFullYear()))),
    d.getMonth(),
    d.getDate() - 1,
    d.getHours(),
    Math.min(minutes.length - 1, Math.floor(d.getMinutes() / 5))
  ]
}

const pickerIndex = ref(defaultPickerIndex())

function syncPickerLabel() {
  const [yi, mi, di, hi, mni] = pickerIndex.value
  form.visitTime = `${years[yi]}-${months[mi]}-${days[di]} ${hours[hi]}:${minutes[mni]}:00`
  form.visitTimeLabel = `${years[yi]}-${months[mi]}-${days[di]} ${hours[hi]}:${minutes[mni]}`
}

function onColumnChange(e) {
  const { column, value } = e.detail
  const next = [...pickerIndex.value]
  next[column] = value
  pickerIndex.value = next
}

function onPickerChange(e) {
  pickerIndex.value = e.detail.value
  syncPickerLabel()
}

function onReasonChange(e) {
  reasonIndex.value = Number(e.detail.value)
  form.visitReason = reasonOptions[reasonIndex.value]
}

function onNameInput(idx, e) {
  const val = e?.detail?.value ?? ''
  nameList.value[idx].name = val
}

function addName() {
  if (nameList.value.length >= 10) {
    uni.showToast({ title: '最多 10 位访客', icon: 'none' })
    return
  }
  nameList.value.push({ id: nameSeq++, name: '' })
}

function removeName(idx) {
  if (nameList.value.length <= 1) return
  nameList.value.splice(idx, 1)
}

onShow(() => {
  if (!store.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  if (!store.isStaff && !houseStore.guardBound()) {
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/home/index' }) }), 300)
  }
  if (!form.visitTime) {
    pickerIndex.value = defaultPickerIndex()
    syncPickerLabel()
  }
})

function onDriveChange(e) {
  form.driveFlag = !!e.detail.value
  if (!form.driveFlag) form.plateNo = ''
}

/** 仅保留数字，最多 11 位 */
function onPhoneInput(e) {
  const raw = String(e?.detail?.value ?? '')
  form.visitorPhone = raw.replace(/\D/g, '').slice(0, 11)
}

function isValidCnMobile(phone) {
  return /^1[3-9]\d{9}$/.test(phone)
}

async function submit() {
  if (!filledNames.value.length) {
    uni.showToast({ title: '请至少填写一位访客姓名', icon: 'none' })
    return
  }
  const phone = form.visitorPhone.trim()
  if (phone && !isValidCnMobile(phone)) {
    uni.showToast({ title: '请输入正确的11位手机号', icon: 'none' })
    return
  }
  if (!form.visitReason) {
    uni.showToast({ title: '请选择来访事由', icon: 'none' })
    return
  }
  if (form.driveFlag && !form.plateNo.trim()) {
    uni.showToast({ title: '驾车来访请填写车牌', icon: 'none' })
    return
  }
  if (!form.visitTime) {
    uni.showToast({ title: '请选择到访时间', icon: 'none' })
    return
  }
  if (!houseStore.guardBound()) return
  loading.value = true
  try {
    const created = await createVisit({
      visitorName: filledNames.value.join('、'),
      visitorPhone: form.visitorPhone.trim() || undefined,
      driveFlag: form.driveFlag,
      plateNo: form.driveFlag ? form.plateNo.trim() : undefined,
      visitReason: form.visitReason,
      visitorCount: filledNames.value.length,
      visitTime: form.visitTime,
      houseId: houseStore.currentHouseId
    })
    uni.showToast({ title: '预约已生效', icon: 'success' })
    const token = created?.passToken
    setTimeout(() => {
      if (token) {
        uni.redirectTo({ url: `/pages/visitor/share?token=${encodeURIComponent(token)}` })
      } else {
        uni.redirectTo({ url: '/pages/visitor/list' })
      }
    }, 400)
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.tip {
  font-size: 24rpx;
  color: #64748b;
  margin-bottom: 24rpx;
  line-height: 1.55;
}
.req {
  color: #e11d48;
}
.switch-row {
  justify-content: space-between;
}
.picker-val {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
  color: #0f172a;
}
.picker-val.placeholder {
  color: #94a3b8;
}

.section {
  margin-bottom: 8rpx;
  padding-bottom: 20rpx;
  border-bottom: 1px solid #f1f5f9;
}
.section-hd {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16rpx;
}
.section-title {
  font-size: 28rpx;
  color: #334155;
  font-weight: 600;
}
.section-sub {
  font-size: 24rpx;
  color: #94a3b8;
}
.person-item {
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
  background: #f8fafc;
  border-radius: 12rpx;
  border: 1px solid #eef2f7;
}
.person-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}
.person-label {
  font-size: 24rpx;
  color: #64748b;
}
.del {
  font-size: 24rpx;
  color: #e11d48;
  padding: 4rpx 8rpx;
}
.person-input {
  width: 100%;
  box-sizing: border-box;
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #0f172a;
  text-align: left;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10rpx;
}
.add-row {
  margin-top: 4rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #99f6e4;
  border-radius: 12rpx;
  background: #f0fdfa;
}
.add-text {
  font-size: 28rpx;
  color: #0f766e;
  font-weight: 500;
}
</style>
