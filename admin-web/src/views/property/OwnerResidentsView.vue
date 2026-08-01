<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>业主住户</h2>
        <p class="sub">登记房主信息并绑定房屋，支持按状态与关键词筛选</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增业主</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 130px" @change="load">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="迁出中" value="MOVING_OUT" />
        <el-option label="已迁出" value="MOVED_OUT" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="姓名/手机/房屋"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无业主">
      <el-table-column prop="name" label="业主姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="绑定房屋" min-width="200" show-overflow-tooltip />
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="登记时间" width="170" />
      <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== 'MOVED_OUT'"
            link
            type="danger"
            @click="onRemove(row)"
          >
            迁出
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑业主' : '新增业主'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" placeholder="App 登录账号（11位手机号）" />
        </el-form-item>
        <el-form-item label="房屋" required>
          <el-select v-model="form.houseId" filterable style="width: 100%" placeholder="选择本社区房屋">
            <el-option
              v-for="h in catalog"
              :key="h.id"
              :label="h.label || `${h.building}${h.unit}${h.roomNo}`"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="迁出中" value="MOVING_OUT" />
            <el-option label="已迁出" value="MOVED_OUT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listHouseCatalog,
  listHouseholds,
  removeHousehold,
  saveHousehold,
  type HouseCatalogItem,
  type Household,
} from '@/api/household'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

const loading = ref(false)
const saving = ref(false)
const list = ref<Household[]>([])
const catalog = ref<HouseCatalogItem[]>([])
const status = ref('ACTIVE')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const visible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  phone: '',
  houseId: undefined as number | undefined,
  status: 'ACTIVE',
})

function tagType(s?: string) {
  if (s === 'ACTIVE') return 'success'
  if (s === 'MOVED_OUT') return 'info'
  return 'warning'
}

async function load() {
  loading.value = true
  try {
    const res = await listHouseholds({
      status: status.value || undefined,
      identityType: 'OWNER',
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    list.value = res?.records || []
    total.value = res?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadCatalog() {
  catalog.value = (await listHouseCatalog().catch(() => [])) || []
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', phone: '', houseId: undefined, status: 'ACTIVE' })
  visible.value = true
}

function openEdit(row: Household) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    phone: row.phone,
    houseId: row.houseId,
    status: row.status || 'ACTIVE',
  })
  visible.value = true
}

async function save() {
  if (!form.name || !form.phone || !form.houseId) {
    ElMessage.warning('请完善姓名、手机号和房屋')
    return
  }
  saving.value = true
  try {
    await saveHousehold({
      id: form.id,
      name: form.name,
      phone: form.phone,
      houseId: form.houseId,
      identityType: 'OWNER',
      status: form.status,
    })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onRemove(row: Household) {
  await ElMessageBox.confirm(`确认将业主「${row.name}」标记为已迁出？`, '迁出', { type: 'warning' })
  await removeHousehold(row.id)
  ElMessage.success('已迁出')
  load()
}

onMounted(() => {
  load()
  loadCatalog()
})
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
.actions {
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
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
