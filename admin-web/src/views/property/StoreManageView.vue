<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>社区商户</h2>
        <p class="sub">便利店信息 · 商品 · 订单与退款（网格员仅可查看订单并配送）</p>
      </div>
      <el-button :loading="loading" @click="reload">刷新</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="商户信息" name="store" :disabled="!canManage" />
      <el-tab-pane label="分类商品" name="product" :disabled="!canManage" />
      <el-tab-pane label="订单管理" name="order" />
    </el-tabs>

    <!-- 商户 -->
    <div v-if="tab === 'store' && canManage" v-loading="loading">
      <el-form label-width="110px" style="max-width: 560px">
        <el-form-item label="店铺名称"><el-input v-model="storeForm.name" /></el-form-item>
        <el-form-item label="自提地址"><el-input v-model="storeForm.address" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="storeForm.phone" /></el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="storeForm.openTime" style="width: 120px" /> —
          <el-input v-model="storeForm.closeTime" style="width: 120px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="最低起送"><el-input-number v-model="storeForm.minOrderAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="配送费"><el-input-number v-model="storeForm.deliveryFee" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="营业状态">
          <el-switch v-model="storeForm.statusOn" active-text="营业" inactive-text="停业" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="saveStore">保存</el-button></el-form-item>
      </el-form>
    </div>

    <!-- 商品 -->
    <div v-if="tab === 'product' && canManage" v-loading="loading">
      <div class="filter-bar">
        <el-button type="primary" @click="openCat">新增分类</el-button>
        <el-button type="primary" @click="openProd()">新增商品</el-button>
      </div>
      <el-table :data="categories" size="small" style="margin-bottom: 16px" empty-text="暂无分类">
        <el-table-column prop="name" label="分类" />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeCat(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-table :data="products" stripe empty-text="暂无商品">
        <el-table-column label="图片" width="72">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="fileUrl(row.coverUrl)"
              :preview-src-list="[fileUrl(row.coverUrl)]"
              fit="cover"
              class="prod-thumb"
            />
            <div v-else class="prod-thumb empty">无图</div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品" min-width="140" />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="price" label="售价" width="90" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ row.status === 1 ? '上架' : '下架' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProd(row)">编辑</el-button>
            <el-button link type="danger" @click="removeProd(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 订单 -->
    <div v-if="tab === 'order'" v-loading="loading">
      <div class="filter-bar">
        <el-select v-model="orderStatus" clearable placeholder="状态" style="width: 140px" @change="loadOrders">
          <el-option v-for="s in statusOpts" :key="s.v" :label="s.l" :value="s.v" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="单号/姓名/手机" style="width: 200px" @keyup.enter="loadOrders" />
        <el-button type="primary" @click="loadOrders">查询</el-button>
        <el-button @click="exportOrders">导出</el-button>
      </div>
      <el-alert v-if="!canManage" type="info" show-icon :closable="false" style="margin-bottom: 12px"
        title="网格员可查看订单并执行配送操作；商品维护与退款审核仅管理员" />
      <el-table :data="orders" stripe empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="deliveryTypeLabel" label="配送" width="90" />
        <el-table-column prop="payAmount" label="实付" width="90" />
        <el-table-column prop="receiverName" label="收货人" width="90" />
        <el-table-column prop="receiverPhone" label="电话" width="120" />
        <el-table-column prop="receiverAddr" label="地址" min-width="140" show-overflow-tooltip />
        <el-table-column prop="statusLabel" label="状态" width="90" />
        <el-table-column label="退款" width="90">
          <template #default="{ row }">{{ refundLabel(row.refundStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING_ACCEPT'" link type="primary" @click="act(row, 'accept')">接单</el-button>
            <el-button v-if="row.status === 'PREPARING'" link type="primary" @click="act(row, 'prepare')">备货完成</el-button>
            <el-button v-if="row.status === 'PREPARING' || row.status === 'DELIVERING'" link type="primary" @click="act(row, 'deliver')">配送</el-button>
            <el-button v-if="row.status === 'DELIVERING'" link type="success" @click="act(row, 'complete')">完成</el-button>
            <el-button
              v-if="canManage && row.refundStatus === 'APPLYING'"
              link type="warning"
              @click="auditRefund(row, true)"
            >退款通过</el-button>
            <el-button
              v-if="canManage && row.refundStatus === 'APPLYING'"
              link type="danger"
              @click="auditRefund(row, false)"
            >拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="prodVisible" :title="prodForm.id ? '编辑商品' : '新增商品'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="prodForm.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="prodForm.categoryId" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品图">
          <div class="cover-row">
            <el-image
              v-if="prodForm.coverUrl"
              :src="fileUrl(prodForm.coverUrl)"
              fit="cover"
              class="cover-preview"
            />
            <div v-else class="cover-preview empty">暂无图片</div>
            <div class="cover-actions">
              <el-upload
                :show-file-list="false"
                accept="image/jpeg,image/png,image/webp,image/gif"
                :http-request="onUploadCover"
                :disabled="uploading"
              >
                <el-button type="primary" :loading="uploading">{{ prodForm.coverUrl ? '重新上传' : '上传图片' }}</el-button>
              </el-upload>
              <el-button v-if="prodForm.coverUrl" link type="danger" @click="prodForm.coverUrl = ''">清除</el-button>
              <p class="cover-tip">支持 jpg / png / webp，建议正方形清晰图</p>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="售价"><el-input-number v-model="prodForm.price" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="prodForm.stock" :min="0" /></el-form-item>
        <el-form-item label="上架"><el-switch v-model="prodForm.onSale" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="prodVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import { fileUrl, uploadFile } from '@/api/file'
import type { UploadRequestOptions } from 'element-plus'
import {
  auditStoreRefund,
  deleteStoreCategory,
  deleteStoreProduct,
  getStoreInfo,
  listStoreCategories,
  listStoreOrders,
  listStoreProducts,
  saveStoreCategory,
  saveStoreInfo,
  saveStoreProduct,
  staffStoreAction,
} from '@/api/store'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const tab = ref(canManage.value ? 'store' : 'order')
const loading = ref(false)
const storeForm = reactive({
  name: '',
  address: '',
  phone: '',
  openTime: '08:00',
  closeTime: '22:00',
  minOrderAmount: 0,
  deliveryFee: 0,
  statusOn: true,
})
const categories = ref<any[]>([])
const products = ref<any[]>([])
const orders = ref<any[]>([])
const orderStatus = ref('')
const keyword = ref('')
const prodVisible = ref(false)
const uploading = ref(false)
const prodForm = reactive<any>({
  id: null,
  name: '',
  categoryId: null,
  coverUrl: '',
  price: 1,
  stock: 0,
  onSale: true,
})

const statusOpts = [
  { v: 'UNPAID', l: '待支付' },
  { v: 'PENDING_ACCEPT', l: '待接单' },
  { v: 'PREPARING', l: '备货中' },
  { v: 'DELIVERING', l: '配送中' },
  { v: 'COMPLETED', l: '已完成' },
  { v: 'CANCELLED', l: '已取消' },
  { v: 'REFUNDED', l: '已退款' },
]

watch(tab, () => reload())

async function reload() {
  if (tab.value === 'store') await loadStore()
  else if (tab.value === 'product') await loadProducts()
  else await loadOrders()
}

async function loadStore() {
  loading.value = true
  try {
    const s: any = (await getStoreInfo()) || {}
    storeForm.name = s.name || ''
    storeForm.address = s.address || ''
    storeForm.phone = s.phone || ''
    storeForm.openTime = s.openTime || '08:00'
    storeForm.closeTime = s.closeTime || '22:00'
    storeForm.minOrderAmount = Number(s.minOrderAmount || 0)
    storeForm.deliveryFee = Number(s.deliveryFee || 0)
    storeForm.statusOn = s.status !== 0
  } finally {
    loading.value = false
  }
}

async function saveStore() {
  await saveStoreInfo({
    ...storeForm,
    status: storeForm.statusOn ? 1 : 0,
  })
  ElMessage.success('已保存')
}

async function loadProducts() {
  loading.value = true
  try {
    categories.value = (await listStoreCategories(true)) || []
    products.value = (await listStoreProducts({})) || []
  } finally {
    loading.value = false
  }
}

async function openCat() {
  const { value } = await ElMessageBox.prompt('分类名称', '新增分类')
  await saveStoreCategory({ name: value })
  ElMessage.success('已添加')
  loadProducts()
}

async function removeCat(row: any) {
  await deleteStoreCategory(row.id)
  loadProducts()
}

function openProd(row?: any) {
  if (row) {
    Object.assign(prodForm, {
      id: row.id,
      name: row.name,
      categoryId: row.categoryId,
      coverUrl: row.coverUrl || '',
      price: Number(row.price),
      stock: Number(row.stock),
      onSale: row.status === 1,
    })
  } else {
    Object.assign(prodForm, {
      id: null,
      name: '',
      categoryId: null,
      coverUrl: '',
      price: 1,
      stock: 10,
      onSale: true,
    })
  }
  prodVisible.value = true
}

async function onUploadCover(opt: UploadRequestOptions) {
  const raw = opt.file as File
  if (!raw?.type?.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片请小于 5MB')
    return
  }
  uploading.value = true
  try {
    const url = await uploadFile(raw)
    if (!url) throw new Error('无图片地址')
    prodForm.coverUrl = url
    ElMessage.success('上传成功')
    opt.onSuccess?.(url as any)
  } catch (e) {
    opt.onError?.(e as any)
  } finally {
    uploading.value = false
  }
}

async function saveProd() {
  await saveStoreProduct({
    id: prodForm.id,
    name: prodForm.name,
    categoryId: prodForm.categoryId,
    coverUrl: prodForm.coverUrl || '',
    price: prodForm.price,
    stock: prodForm.stock,
    status: prodForm.onSale ? 1 : 0,
  })
  prodVisible.value = false
  ElMessage.success('已保存')
  loadProducts()
}

async function removeProd(row: any) {
  await ElMessageBox.confirm(`删除商品「${row.name}」？`)
  await deleteStoreProduct(row.id)
  loadProducts()
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value =
      (await listStoreOrders({
        status: orderStatus.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

async function act(row: any, action: string) {
  await staffStoreAction(row.id, action)
  ElMessage.success('已更新')
  loadOrders()
}

async function auditRefund(row: any, approve: boolean) {
  await auditStoreRefund(row.id, approve)
  ElMessage.success(approve ? '已退款' : '已拒绝')
  loadOrders()
}

function refundLabel(status?: string) {
  const map: Record<string, string> = {
    NONE: '无',
    APPLYING: '退款中',
    APPROVED: '已退款',
    REJECTED: '已拒绝',
  }
  if (!status) return '无'
  return map[String(status).toUpperCase()] || status
}

function exportOrders() {
  const rows = orders.value.map((o) => ({
    订单号: o.orderNo,
    配送: o.deliveryTypeLabel,
    实付: o.payAmount,
    收货人: o.receiverName,
    电话: o.receiverPhone,
    地址: o.receiverAddr,
    状态: o.statusLabel,
    退款: refundLabel(o.refundStatus),
  }))
  exportExcel(rows, `超市订单_${Date.now()}`)
  ElMessage.success('已导出')
}

onMounted(() => {
  if (!canManage.value) tab.value = 'order'
  reload()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.prod-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #e8f5f2;
}
.prod-thumb.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #94a3b8;
}
.cover-row { display: flex; gap: 16px; align-items: flex-start; }
.cover-preview {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  background: #f1f5f4;
  flex-shrink: 0;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 12px;
  border: 1px dashed #cbd5e1;
}
.cover-actions { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.cover-tip { margin: 0; font-size: 12px; color: #94a3b8; }
</style>
