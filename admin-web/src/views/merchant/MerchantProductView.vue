<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商品管理</h2>
        <p class="sub">本店商品新增/编辑/上下架与库存 · 数据按店铺隔离</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="商品名称"
        style="width: 200px"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="filterCategoryId"
        clearable
        placeholder="商品分类"
        style="width: 160px"
        @change="onSearch"
      >
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select
        v-model="filterStatus"
        placeholder="商品状态"
        style="width: 140px"
        @change="onSearch"
      >
        <el-option label="全部" :value="-1" />
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" @click="openEdit()">新增商品</el-button>
    </div>

    <div class="batch-bar">
      <span class="batch-tip">已选 {{ selected.length }} 项</span>
      <el-button :disabled="!selected.length" @click="batchOffShelf">批量下架</el-button>
      <el-button :disabled="!selected.length" type="primary" plain @click="openBatchStock">
        批量修改库存
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="pagedList"
      stripe
      empty-text="暂无商品"
      @selection-change="onSelect"
    >
      <el-table-column type="selection" width="48" />
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
      <el-table-column prop="name" label="商品名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="100" />
      <el-table-column label="售价" width="100" align="right">
        <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="原价" width="100" align="right">
        <template #default="{ row }">
          ¥{{ Number(row.originalPrice ?? row.price ?? 0).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="库存" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'stock-warn': isStockLow(row) }">{{ row.stock ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="限购数量" width="100" align="right">
        <template #default="{ row }">
          {{ Number(row.limitBuy || 0) > 0 ? row.limitBuy : '不限' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.statusLabel || (row.status === 1 ? '上架' : '下架') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :total="list.length"
      />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑商品' : '新增商品'" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" clearable filterable allow-create style="width: 100%" @change="onCat">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品图">
          <div class="cover-row">
            <el-image
              v-if="form.coverUrl"
              :src="fileUrl(form.coverUrl)"
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
                <el-button type="primary" :loading="uploading">
                  {{ form.coverUrl ? '重新上传' : '上传图片' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.coverUrl" link type="danger" @click="form.coverUrl = ''">清除</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="售价">
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存预警">
          <el-input-number v-model="form.stockWarn" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="限购数量">
          <el-input-number v-model="form.limitBuy" :min="0" style="width: 100%" />
          <div class="form-hint">0 表示不限购</div>
        </el-form-item>
        <el-form-item label="上架"><el-switch v-model="form.onSale" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stockVisible" title="批量修改库存" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="统一库存">
          <el-input-number v-model="batchStock" :min="0" style="width: 100%" />
        </el-form-item>
        <p class="form-hint">将对已选 {{ selected.length }} 个商品统一设置为该库存值</p>
      </el-form>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" :loading="batching" @click="confirmBatchStock">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  batchMallProductStatus,
  batchMallProductStock,
  deleteMallProduct,
  listMallCategories,
  listMallProducts,
  saveMallCategory,
  saveMallProduct,
} from '@/api/mall'
import { fileUrl, uploadFile } from '@/api/file'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const batching = ref(false)
const list = ref<any[]>([])
const categories = ref<any[]>([])
const selected = ref<any[]>([])
const keyword = ref('')
const filterCategoryId = ref<number | undefined>()
const filterStatus = ref(-1)
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const stockVisible = ref(false)
const batchStock = ref(0)

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  categoryId: undefined as number | undefined,
  price: 0,
  originalPrice: 0,
  stock: 0,
  stockWarn: 10,
  limitBuy: 0,
  coverUrl: '',
  onSale: true,
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

function isStockLow(row: any) {
  if (row.stockLow === true) return true
  const stock = Number(row.stock ?? 0)
  const warn = Number(row.stockWarn ?? 10)
  return stock < warn
}

function formatTime(v: any) {
  if (!v) return '—'
  const s = String(v)
  return s.length >= 19 ? s.slice(0, 19).replace('T', ' ') : s
}

function onSelect(rows: any[]) {
  selected.value = rows || []
}

function onSearch() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listMallProducts({
        keyword: keyword.value || undefined,
        categoryId: filterCategoryId.value,
        status: filterStatus.value < 0 ? undefined : filterStatus.value,
      })) || []
    selected.value = []
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadCats() {
  try {
    categories.value = (await listMallCategories()) || []
  } catch {
    categories.value = []
  }
}

function openEdit(row?: any) {
  if (row) {
    form.id = row.id
    form.name = row.name
    form.categoryId = row.categoryId
    form.price = Number(row.price || 0)
    form.originalPrice = Number(row.originalPrice ?? row.price ?? 0)
    form.stock = Number(row.stock || 0)
    form.stockWarn = Number(row.stockWarn ?? 10)
    form.limitBuy = Number(row.limitBuy || 0)
    form.coverUrl = row.coverUrl || ''
    form.onSale = row.status === 1
  } else {
    form.id = undefined
    form.name = ''
    form.categoryId = undefined
    form.price = 0
    form.originalPrice = 0
    form.stock = 0
    form.stockWarn = 10
    form.limitBuy = 0
    form.coverUrl = ''
    form.onSale = true
  }
  visible.value = true
}

async function onUploadCover(opt: UploadRequestOptions) {
  const raw = opt.file as File
  if (!raw) return
  uploading.value = true
  try {
    const url = await uploadFile(raw)
    form.coverUrl = url
    ElMessage.success('图片已上传')
    opt.onSuccess?.(url as any)
  } catch (e: any) {
    ElMessage.error(e?.msg || '上传失败')
    opt.onError?.(e)
  } finally {
    uploading.value = false
  }
}

async function onCat(val: any) {
  if (typeof val === 'string' && val.trim()) {
    const created: any = await saveMallCategory({
      name: val.trim(),
      shopId: Number(user.shopId) || undefined,
    })
    await loadCats()
    form.categoryId = created?.id
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写商品名称')
    return
  }
  saving.value = true
  try {
    await saveMallProduct({
      id: form.id,
      shopId: Number(user.shopId) || undefined,
      name: form.name,
      categoryId: form.categoryId,
      price: form.price,
      originalPrice: form.originalPrice,
      stock: form.stock,
      stockWarn: form.stockWarn,
      limitBuy: form.limitBuy,
      coverUrl: form.coverUrl,
      status: form.onSale ? 1 : 0,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: any) {
  await saveMallProduct({
    id: row.id,
    shopId: row.shopId,
    name: row.name,
    categoryId: row.categoryId,
    price: row.price,
    originalPrice: row.originalPrice ?? row.price,
    stock: row.stock,
    stockWarn: row.stockWarn ?? 10,
    limitBuy: row.limitBuy ?? 0,
    coverUrl: row.coverUrl,
    status: row.status === 1 ? 0 : 1,
  })
  ElMessage.success('已更新')
  await load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除商品「${row.name}」？`, '提示', { type: 'warning' })
  await deleteMallProduct(row.id)
  ElMessage.success('已删除')
  await load()
}

async function batchOffShelf() {
  if (!selected.value.length) return
  await ElMessageBox.confirm(`确认将选中的 ${selected.value.length} 个商品批量下架？`, '批量下架', {
    type: 'warning',
  })
  batching.value = true
  try {
    const n = await batchMallProductStatus(
      selected.value.map((r) => Number(r.id)),
      0,
    )
    ElMessage.success(`已下架 ${n} 个商品`)
    await load()
  } finally {
    batching.value = false
  }
}

function openBatchStock() {
  if (!selected.value.length) return
  batchStock.value = 0
  stockVisible.value = true
}

async function confirmBatchStock() {
  batching.value = true
  try {
    const n = await batchMallProductStock(
      selected.value.map((r) => Number(r.id)),
      Number(batchStock.value || 0),
    )
    ElMessage.success(`已更新 ${n} 个商品库存`)
    stockVisible.value = false
    await load()
  } finally {
    batching.value = false
  }
}

onMounted(async () => {
  await loadCats()
  await load()
})
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f0fdf4;
  border: 1px solid #d1fae5;
  border-radius: 8px;
}
.batch-tip { color: #64748b; font-size: 13px; margin-right: 4px; }
.stock-warn { color: #dc2626; font-weight: 600; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.prod-thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: #f1f5f9;
}
.prod-thumb.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
}
.cover-row { display: flex; align-items: center; gap: 12px; }
.cover-preview {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  background: #f1f5f9;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
}
.cover-actions { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.form-hint { margin: 4px 0 0; color: #94a3b8; font-size: 12px; line-height: 1.4; }
</style>
