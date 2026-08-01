<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商户列表</h2>
        <p class="sub">展示本小区可运营商户（含入驻通过同步）· 设置登录账号 · 启用/停用 · 查看商品与订单</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增商户</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="keyword" clearable placeholder="商户名/账号" style="width: 200px" @keyup.enter="load" />
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无商户">
      <el-table-column prop="name" label="商户名称" min-width="140" />
      <el-table-column prop="loginPhone" label="登录账号" width="130">
        <template #default="{ row }">{{ row.loginPhone || '未设置' }}</template>
      </el-table-column>
      <el-table-column prop="contactPhone" label="联系电话" width="120" />
      <el-table-column label="营业时间" width="140">
        <template #default="{ row }">{{ row.openTime || '-' }} — {{ row.closeTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="productCount" label="商品数" width="80" />
      <el-table-column prop="orderCount" label="订单数" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openProducts(row)">商品</el-button>
          <el-button link type="primary" @click="openOrders(row)">订单</el-button>
          <el-button v-if="!row.loginPhone" link type="warning" @click="openAccount(row)">设置账号</el-button>
          <el-button v-else link type="warning" @click="openPwd(row)">重置密码</el-button>
          <el-button
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            @click="toggle(row)"
          >{{ row.status === 1 ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新增商户" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="商户名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="登录账号" required><el-input v-model="form.loginPhone" placeholder="手机号/账号" /></el-form-item>
        <el-form-item label="登录密码" required><el-input v-model="form.password" type="password" placeholder="至少6位" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="form.openTime" style="width: 100px" placeholder="08:00" />
          <span style="margin: 0 8px">—</span>
          <el-input v-model="form.closeTime" style="width: 100px" placeholder="22:00" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="重置商户密码" width="400px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="商户">{{ current?.name }}</el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" placeholder="至少6位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doResetPwd">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="accountVisible" title="设置商户登录账号" width="440px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="商户">{{ current?.name }}</el-form-item>
        <el-form-item label="登录账号" required>
          <el-input v-model="accountForm.loginPhone" placeholder="手机号/账号，如 13800000999" />
        </el-form-item>
        <el-form-item label="登录密码" required>
          <el-input v-model="accountForm.password" type="password" placeholder="至少6位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doBindAccount">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="prodVisible" :title="`商品 · ${current?.name || ''}`" size="720px" destroy-on-close>
      <el-table v-loading="detailLoading" :data="products" stripe>
        <el-table-column prop="name" label="商品" min-width="140" />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="price" label="价格" width="90" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="statusLabel" label="状态" width="80" />
      </el-table>
    </el-drawer>

    <el-drawer v-model="orderVisible" :title="`订单 · ${current?.name || ''}`" size="780px" destroy-on-close>
      <el-table v-loading="detailLoading" :data="orders" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="150" />
        <el-table-column prop="receiverName" label="收货人" width="90" />
        <el-table-column prop="payAmount" label="实付" width="90" />
        <el-table-column prop="statusLabel" label="状态" width="90" />
        <el-table-column prop="createTime" label="下单时间" width="170" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bindMallShopAccount,
  createMallShop,
  listMallOrders,
  listMallProducts,
  listMallShops,
  resetMallShopPassword,
  updateMallShopStatus,
} from '@/api/mall'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const status = ref<number | undefined>()
const createVisible = ref(false)
const pwdVisible = ref(false)
const accountVisible = ref(false)
const prodVisible = ref(false)
const orderVisible = ref(false)
const detailLoading = ref(false)
const products = ref<any[]>([])
const orders = ref<any[]>([])
const current = ref<any>(null)
const newPassword = ref('')
const accountForm = reactive({
  loginPhone: '',
  password: '123456',
})
const form = reactive({
  name: '',
  loginPhone: '',
  password: '',
  contactName: '',
  contactPhone: '',
  address: '',
  openTime: '08:00',
  closeTime: '22:00',
})

async function load() {
  loading.value = true
  try {
    list.value = (await listMallShops({ keyword: keyword.value || undefined, status: status.value })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    name: '',
    loginPhone: '',
    password: '',
    contactName: '',
    contactPhone: '',
    address: '',
    openTime: '08:00',
    closeTime: '22:00',
  })
  createVisible.value = true
}

async function doCreate() {
  if (!form.name.trim() || !form.loginPhone.trim() || !form.password) {
    ElMessage.warning('请填写名称、账号与密码')
    return
  }
  saving.value = true
  try {
    await createMallShop({ ...form })
    ElMessage.success('商户已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: any) {
  const next = row.status === 1 ? 0 : 1
  await updateMallShopStatus(row.shopId, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await load()
}

function openPwd(row: any) {
  current.value = row
  newPassword.value = ''
  pwdVisible.value = true
}

function openAccount(row: any) {
  current.value = row
  accountForm.loginPhone = row.contactPhone && row.contactPhone !== '13800000001' ? row.contactPhone : '13800000999'
  accountForm.password = '123456'
  accountVisible.value = true
}

async function doBindAccount() {
  if (!accountForm.loginPhone.trim() || !accountForm.password || accountForm.password.length < 6) {
    ElMessage.warning('请填写登录账号，密码至少6位')
    return
  }
  saving.value = true
  try {
    await bindMallShopAccount(current.value.shopId, {
      loginPhone: accountForm.loginPhone.trim(),
      password: accountForm.password,
    })
    ElMessage.success('账号已设置，可用该账号登录商户端')
    accountVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function doResetPwd() {
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  saving.value = true
  try {
    await resetMallShopPassword(current.value.shopId, newPassword.value)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function openProducts(row: any) {
  current.value = row
  prodVisible.value = true
  detailLoading.value = true
  try {
    products.value = (await listMallProducts({ shopId: row.shopId })) || []
  } catch {
    products.value = []
  } finally {
    detailLoading.value = false
  }
}

async function openOrders(row: any) {
  current.value = row
  orderVisible.value = true
  detailLoading.value = true
  try {
    orders.value = (await listMallOrders({ shopId: row.shopId })) || []
  } catch {
    orders.value = []
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
