<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商品管理</h2>
        <p class="sub">各商户商品维护，支持设置价格、库存、上下架</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <template v-if="canManage">
          <el-button @click="openCat">新增分类</el-button>
          <el-button type="primary" @click="openProd()">新增商品</el-button>
        </template>
      </div>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="网格员仅可查看商品，不可维护"
    />

    <div class="filter-bar">
      <el-select v-model="categoryId" clearable placeholder="分类" style="width: 140px" @change="load">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="shelf" clearable placeholder="上下架" style="width: 120px" @change="load">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="商品名称"
        style="width: 180px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-if="canManage" :data="categories" size="small" style="margin-bottom: 16px" empty-text="暂无分类">
      <el-table-column prop="name" label="分类" />
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="removeCat(row)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-loading="loading" :data="filteredProducts" stripe empty-text="暂无商品">
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
      <el-table-column prop="price" label="售价" width="90">
        <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openProd(row)">编辑</el-button>
          <el-button link type="warning" @click="toggleShelf(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="removeProd(row)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

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
                <el-button type="primary" :loading="uploading">
                  {{ prodForm.coverUrl ? '重新上传' : '上传图片' }}
                </el-button>
              </el-upload>
              <el-button v-if="prodForm.coverUrl" link type="danger" @click="prodForm.coverUrl = ''">
                清除
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="售价">
          <el-input-number v-model="prodForm.price" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="prodForm.stock" :min="0" />
        </el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileUrl, uploadFile } from '@/api/file'
import {
  deleteStoreCategory,
  deleteStoreProduct,
  listStoreCategories,
  listStoreProducts,
  saveStoreCategory,
  saveStoreProduct,
} from '@/api/store'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const categories = ref<any[]>([])
const products = ref<any[]>([])
const categoryId = ref<number | undefined>()
const keyword = ref('')
const shelf = ref<number | ''>('')
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

const filteredProducts = computed(() => {
  let list = products.value
  if (shelf.value === 0 || shelf.value === 1) {
    list = list.filter((p) => Number(p.status) === shelf.value)
  }
  return list
})

async function load() {
  loading.value = true
  try {
    categories.value = (await listStoreCategories(true)) || []
    products.value =
      (await listStoreProducts({
        categoryId: categoryId.value,
        keyword: keyword.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

async function openCat() {
  const { value } = await ElMessageBox.prompt('分类名称', '新增分类')
  await saveStoreCategory({ name: value })
  ElMessage.success('已添加')
  load()
}

async function removeCat(row: any) {
  await deleteStoreCategory(row.id)
  load()
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
      categoryId: categoryId.value || null,
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
  uploading.value = true
  try {
    const url = await uploadFile(raw)
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
  load()
}

async function toggleShelf(row: any) {
  await saveStoreProduct({
    id: row.id,
    name: row.name,
    categoryId: row.categoryId,
    coverUrl: row.coverUrl || '',
    price: row.price,
    stock: row.stock,
    status: row.status === 1 ? 0 : 1,
  })
  ElMessage.success(row.status === 1 ? '已下架' : '已上架')
  load()
}

async function removeProd(row: any) {
  await ElMessageBox.confirm(`删除商品「${row.name}」？`)
  await deleteStoreProduct(row.id)
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
}
.sub {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}
.ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
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
.cover-row {
  display: flex;
  gap: 16px;
}
.cover-preview {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  background: #f1f5f4;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  border: 1px dashed #cbd5e1;
}
.cover-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
