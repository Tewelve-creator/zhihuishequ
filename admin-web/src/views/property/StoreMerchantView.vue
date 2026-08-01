<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商户档案</h2>
        <p class="sub">维护入驻小区商户信息，控制店铺营业状态与平台佣金比例</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="网格员仅可查看商户档案，不可编辑"
    />

    <el-form v-loading="loading" label-width="110px" style="max-width: 560px" :disabled="!canManage">
      <el-form-item label="店铺名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
      <el-form-item label="自提地址"><el-input v-model="form.address" maxlength="128" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="form.phone" maxlength="20" /></el-form-item>
      <el-form-item label="营业时间">
        <el-input v-model="form.openTime" style="width: 120px" placeholder="08:00" />
        <span style="margin: 0 8px">—</span>
        <el-input v-model="form.closeTime" style="width: 120px" placeholder="22:00" />
      </el-form-item>
      <el-form-item label="最低起送">
        <el-input-number v-model="form.minOrderAmount" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item label="配送费">
        <el-input-number v-model="form.deliveryFee" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item label="平台佣金%">
        <el-input-number v-model="form.commissionRate" :min="0" :max="100" :precision="2" />
        <span class="hint">结算时按营业额计提</span>
      </el-form-item>
      <el-form-item label="营业状态">
        <el-switch v-model="form.statusOn" active-text="营业" inactive-text="停业" />
      </el-form-item>
      <el-form-item v-if="canManage">
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getStoreInfo, saveStoreInfo } from '@/api/store'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  address: '',
  phone: '',
  openTime: '08:00',
  closeTime: '22:00',
  minOrderAmount: 0,
  deliveryFee: 0,
  commissionRate: 5,
  statusOn: true,
})

async function load() {
  loading.value = true
  try {
    const s: any = (await getStoreInfo()) || {}
    form.name = s.name || ''
    form.address = s.address || ''
    form.phone = s.phone || ''
    form.openTime = s.openTime || '08:00'
    form.closeTime = s.closeTime || '22:00'
    form.minOrderAmount = Number(s.minOrderAmount || 0)
    form.deliveryFee = Number(s.deliveryFee || 0)
    form.commissionRate = Number(s.commissionRate ?? 5)
    form.statusOn = s.status !== 0
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveStoreInfo({
      ...form,
      status: form.statusOn ? 1 : 0,
    })
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 4px;
}
.sub {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}
.hint {
  margin-left: 12px;
  color: #94a3b8;
  font-size: 12px;
}
</style>
