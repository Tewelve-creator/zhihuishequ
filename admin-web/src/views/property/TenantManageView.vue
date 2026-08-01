<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>租客管理</h2>
        <p class="sub">录入租房人员信息、登记租赁周期；与业主身份区分，支持按状态与租约筛选</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增租客</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 130px" @change="load">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="迁出中" value="MOVING_OUT" />
        <el-option label="已迁出" value="MOVED_OUT" />
      </el-select>
      <el-select v-model="leaseFilter" clearable placeholder="租约" style="width: 130px" @change="applyClientFilter">
        <el-option label="在租中" value="active" />
        <el-option label="即将到期(30天)" value="expiring" />
        <el-option label="已到期" value="expired" />
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

    <el-table v-loading="loading" :data="displayList" stripe empty-text="暂无租客">
      <el-table-column prop="name" label="租客姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="room" label="租赁房屋" min-width="180" show-overflow-tooltip />
      <el-table-column label="租赁周期" min-width="200">
        <template #default="{ row }">
          <span v-if="row.leaseStart || row.leaseEnd">
            {{ formatDate(row.leaseStart) || '—' }} ~ {{ formatDate(row.leaseEnd) || '—' }}
          </span>
          <span v-else class="muted">未登记</span>
        </template>
      </el-table-column>
      <el-table-column label="租约状态" width="110">
        <template #default="{ row }">
          <el-tag :type="leaseTag(row)" size="small">{{ leaseLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="住户状态" width="90" />
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

    <el-dialog v-model="visible" :title="form.id ? '编辑租客' : '新增租客'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" maxlength="11" placeholder="App 登录账号（11位手机号）" />
        </el-form-item>
        <el-form-item label="租赁房屋" required>
          <el-select v-model="form.houseId" filterable style="width: 100%" placeholder="选择本社区房屋">
            <el-option
              v-for="h in catalog"
              :key="h.id"
              :label="h.label || `${h.building}${h.unit}${h.roomNo}`"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="租赁开始">
          <el-date-picker
            v-model="form.leaseStart"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="开始日期"
          />
        </el-form-item>
        <el-form-item label="租赁结束">
          <el-date-picker
            v-model="form.leaseEnd"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="结束日期"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="App 端统一用手机号登录：此处手机号即为登录账号，默认密码 123456；租房日期会在 App「我的」页展示"
          style="margin-bottom: 8px"
        />
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
const leaseFilter = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const visible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  phone: '',
  houseId: undefined as number | undefined,
  leaseStart: '',
  leaseEnd: '',
  status: 'ACTIVE',
})

const displayList = computed(() => {
  if (!leaseFilter.value) return list.value
  return list.value.filter((r) => matchLease(r, leaseFilter.value))
})

function formatDate(v?: string) {
  if (!v) return ''
  return String(v).substring(0, 10)
}

function daysToEnd(row: Household) {
  const end = formatDate(row.leaseEnd)
  if (!end) return null
  const d = (new Date(end).getTime() - Date.now()) / 86400000
  return d
}

function leaseLabel(row: Household) {
  const d = daysToEnd(row)
  if (d == null) return '未登记'
  if (d < 0) return '已到期'
  if (d <= 30) return '即将到期'
  return '在租中'
}

function leaseTag(row: Household) {
  const lab = leaseLabel(row)
  if (lab === '已到期') return 'danger'
  if (lab === '即将到期') return 'warning'
  if (lab === '在租中') return 'success'
  return 'info'
}

function matchLease(row: Household, f: string) {
  const d = daysToEnd(row)
  if (f === 'active') return d != null && d >= 0
  if (f === 'expiring') return d != null && d >= 0 && d <= 30
  if (f === 'expired') return d != null && d < 0
  return true
}

function applyClientFilter() {
  /* displayList computed */
}

async function load() {
  loading.value = true
  try {
    const res = await listHouseholds({
      status: status.value || undefined,
      identityType: 'TENANT',
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
  Object.assign(form, {
    id: undefined,
    name: '',
    phone: '',
    houseId: undefined,
    leaseStart: '',
    leaseEnd: '',
    status: 'ACTIVE',
  })
  visible.value = true
}

function openEdit(row: Household) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    phone: row.phone,
    houseId: row.houseId,
    leaseStart: formatDate(row.leaseStart),
    leaseEnd: formatDate(row.leaseEnd),
    status: row.status || 'ACTIVE',
  })
  visible.value = true
}

async function save() {
  if (!form.name || !form.phone || !form.houseId) {
    ElMessage.warning('请完善姓名、手机号和房屋')
    return
  }
  if (!/^1\d{10}$/.test(String(form.phone).trim())) {
    ElMessage.warning('请填写正确的 11 位手机号（即 App 登录账号）')
    return
  }
  saving.value = true
  try {
    await saveHousehold({
      id: form.id,
      name: form.name,
      phone: form.phone,
      houseId: form.houseId,
      identityType: 'TENANT',
      status: form.status,
      leaseStart: form.leaseStart || undefined,
      leaseEnd: form.leaseEnd || undefined,
    })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onRemove(row: Household) {
  await ElMessageBox.confirm(`确认将租客「${row.name}」标记为已迁出？`, '迁出', { type: 'warning' })
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
.muted {
  color: #94a3b8;
}
</style>
