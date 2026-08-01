<template>
  <div class="page-card house-page">
    <div class="page-header">
      <div>
        <h2>房屋档案</h2>
        <p class="sub">楼栋信息对应的房间明细 · 户型 / 面积 / 空置自住出租状态</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openHouse()">新增房号</el-button>
        <el-button v-if="canManage" @click="openBatch">导入</el-button>
        <el-button @click="onExport">导出</el-button>
      </div>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 4px"
      title="网格员仅可查看房屋档案，不可新增编辑删除"
    />

    <!-- 与楼栋汇总对应的房间统计 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">总户数</div>
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-hint">套</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-label">空置户数</div>
        <div class="stat-value">{{ stats.vacant }}</div>
        <div class="stat-hint">套</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">自住户数</div>
        <div class="stat-value">{{ stats.self }}</div>
        <div class="stat-hint">套</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">出租户数</div>
        <div class="stat-value">{{ stats.rent }}</div>
        <div class="stat-hint">套 · 入住率 {{ stats.rate }}%</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="building" clearable filterable placeholder="按楼栋" style="width: 140px" @change="onBuilding">
        <el-option v-for="b in buildings" :key="b" :label="formatBuilding(b)" :value="b" />
      </el-select>
      <el-select v-model="unit" clearable filterable placeholder="按单元" style="width: 140px" @change="load">
        <el-option v-for="u in units" :key="u" :label="formatUnit(u)" :value="u" />
      </el-select>
      <el-select v-model="occupyStatus" clearable placeholder="按状态" style="width: 130px" @change="load">
        <el-option label="空置" value="VACANT" />
        <el-option label="自住" value="SELF" />
        <el-option label="出租" value="RENT" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="房号/户型" style="width: 160px" @keyup.enter="load" />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </div>

    <div class="table-panel">
      <div class="table-head">
        <h3>房间明细</h3>
        <span class="muted">共 {{ list.length }} 套{{ building ? ` · ${formatBuilding(building)}` : '' }}</span>
      </div>
      <el-table v-loading="loading" :data="paged" stripe empty-text="暂无房屋">
        <el-table-column label="楼栋" width="100" fixed>
          <template #default="{ row }">{{ formatBuilding(row.buildingName || row.building) }}</template>
        </el-table-column>
        <el-table-column label="单元" width="100">
          <template #default="{ row }">{{ formatUnit(row.unitName || row.unit) }}</template>
        </el-table-column>
        <el-table-column prop="houseNo" label="房号" width="90" />
        <el-table-column label="户型" width="120">
          <template #default="{ row }">{{ row.layoutType || '—' }}</template>
        </el-table-column>
        <el-table-column label="面积㎡" width="100">
          <template #default="{ row }">
            {{ row.areaSqm != null ? Number(row.areaSqm).toFixed(2) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="房屋状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.occupyStatus)" size="small" round>
              {{ row.occupyStatusLabel || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="启用" width="80" />
        <el-table-column label="绑定" width="90">
          <template #default="{ row }">
            <el-tag :type="row.bound ? 'success' : 'info'" size="small" effect="plain" round>
              {{ row.bound ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openHouse(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.bound" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="list.length"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </div>

    <el-dialog v-model="dlgHouse" :title="form.id ? '编辑房屋' : '新增房号'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="楼栋"><el-input v-model="form.buildingName" placeholder="如 1栋" /></el-form-item>
        <el-form-item label="单元"><el-input v-model="form.unitName" placeholder="如 1单元" /></el-form-item>
        <el-form-item label="房号"><el-input v-model="form.houseNo" placeholder="如 101" /></el-form-item>
        <el-form-item label="户型">
          <el-input v-model="form.layoutType" placeholder="如 两室一厅" maxlength="32" />
        </el-form-item>
        <el-form-item label="面积㎡">
          <el-input-number v-model="form.areaSqm" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="房屋状态">
          <el-select v-model="form.occupyStatus" style="width: 100%">
            <el-option label="空置" value="VACANT" />
            <el-option label="自住" value="SELF" />
            <el-option label="出租" value="RENT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.statusOn" active-text="可用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgHouse = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlgBatch" title="导入房号" width="480px">
      <el-form label-width="80px">
        <el-form-item label="楼栋"><el-input v-model="batch.buildingName" /></el-form-item>
        <el-form-item label="单元"><el-input v-model="batch.unitName" /></el-form-item>
        <el-form-item label="房号">
          <el-input
            v-model="batch.houseNos"
            type="textarea"
            :rows="4"
            placeholder="如：101,102,103 或 101-110"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgBatch = false">取消</el-button>
        <el-button type="primary" @click="doBatch">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  batchHouses,
  deleteHouse,
  listAdminBuildings,
  listAdminHouses,
  listAdminUnits,
  saveHouse,
} from '@/api/houseAdmin'

const user = useUserStore()
const route = useRoute()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<any[]>([])
const buildings = ref<string[]>([])
const units = ref<string[]>([])
const building = ref('')
const unit = ref('')
const keyword = ref('')
const occupyStatus = ref('')
const page = ref(1)
const pageSize = ref(50)
const dlgHouse = ref(false)
const dlgBatch = ref(false)

const form = reactive<any>({
  id: null,
  buildingName: '',
  unitName: '',
  houseNo: '',
  layoutType: '',
  areaSqm: null as number | null,
  occupyStatus: 'VACANT',
  statusOn: true,
})
const batch = reactive({ buildingName: '', unitName: '', houseNos: '' })

const paged = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const rows = list.value
  const total = rows.length
  const vacant = rows.filter((r) => r.occupyStatus === 'VACANT').length
  const self = rows.filter((r) => r.occupyStatus === 'SELF').length
  const rent = rows.filter((r) => r.occupyStatus === 'RENT').length
  const rate = total <= 0 ? 0 : Math.round(((total - vacant) * 1000) / total) / 10
  return { total, vacant, self, rent, rate }
})

watch([building, unit, occupyStatus, keyword], () => {
  page.value = 1
})

function formatBuilding(name: string) {
  if (!name) return '—'
  return String(name).trim().replace(/^(\d+)(栋)$/, '$1 $2')
}
function formatUnit(name: string) {
  if (!name) return '—'
  return String(name).trim().replace(/^(\d+)(单元)$/, '$1 $2')
}
function statusTag(s: string) {
  if (s === 'VACANT') return 'warning'
  if (s === 'SELF') return 'success'
  if (s === 'RENT') return ''
  return 'info'
}

async function loadMeta() {
  buildings.value = (await listAdminBuildings()) || []
}
async function onBuilding() {
  unit.value = ''
  units.value = building.value ? (await listAdminUnits(building.value)) || [] : []
  load()
}
function resetFilter() {
  building.value = ''
  unit.value = ''
  occupyStatus.value = ''
  keyword.value = ''
  units.value = []
  load()
}
async function load() {
  loading.value = true
  try {
    list.value =
      (await listAdminHouses({
        building: building.value || undefined,
        unit: unit.value || undefined,
        keyword: keyword.value || undefined,
        occupyStatus: occupyStatus.value || undefined,
      })) || []
  } catch (e: any) {
    list.value = []
    ElMessage.error(e?.msg || e?.message || '加载房屋失败，请确认已登录物业账号且 auth 服务已启动')
  } finally {
    loading.value = false
  }
}
function openHouse(row?: any) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      buildingName: row.buildingName,
      unitName: row.unitName,
      houseNo: row.houseNo,
      layoutType: row.layoutType || '',
      areaSqm: row.areaSqm != null ? Number(row.areaSqm) : null,
      occupyStatus: row.occupyStatus || 'VACANT',
      statusOn: row.status === 1,
    })
  } else {
    Object.assign(form, {
      id: null,
      buildingName: building.value || '',
      unitName: unit.value || '',
      houseNo: '',
      layoutType: '两室一厅',
      areaSqm: 89,
      occupyStatus: 'VACANT',
      statusOn: true,
    })
  }
  dlgHouse.value = true
}
function openBatch() {
  batch.buildingName = building.value || ''
  batch.unitName = unit.value || ''
  batch.houseNos = ''
  dlgBatch.value = true
}
async function save() {
  await saveHouse({
    id: form.id,
    buildingName: form.buildingName,
    unitName: form.unitName,
    houseNo: form.houseNo,
    layoutType: form.layoutType || undefined,
    areaSqm: form.areaSqm ?? undefined,
    occupyStatus: form.occupyStatus,
    status: form.statusOn ? 1 : 0,
  })
  dlgHouse.value = false
  ElMessage.success('已保存')
  await loadMeta()
  load()
}
async function doBatch() {
  const r: any = await batchHouses({ ...batch })
  ElMessage.success(`新增 ${r.created}，跳过 ${r.skipped}`)
  dlgBatch.value = false
  await loadMeta()
  load()
}
async function remove(row: any) {
  await ElMessageBox.confirm(`删除 ${row.label}？已绑定住户不可删`)
  await deleteHouse(row.id)
  ElMessage.success('已删除')
  load()
}
function onExport() {
  const rows = list.value.map((r) => ({
    楼栋: formatBuilding(r.buildingName || r.building),
    单元: formatUnit(r.unitName || r.unit),
    房号: r.houseNo,
    户型: r.layoutType || '',
    '面积(m2)': r.areaSqm != null ? Number(r.areaSqm).toFixed(2) : '',
    房屋状态: r.occupyStatusLabel || '',
    启用: r.statusLabel || '',
    绑定: r.bound ? '已绑定' : '未绑定',
    创建时间: r.createdAt || '',
  }))
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  exportExcel(rows, `房屋档案_${Date.now()}`)
}

onMounted(async () => {
  await loadMeta()
  if (route.query.building) {
    building.value = String(route.query.building)
    units.value = (await listAdminUnits(building.value)) || []
  }
  load()
})
</script>

<style scoped>
.house-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 22px;
  color: #0f1f1a;
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
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.stat-card {
  background: linear-gradient(180deg, #f7fbf9 0%, #ffffff 100%);
  border: 1px solid #e5eee9;
  border-radius: 14px;
  padding: 16px 18px;
}
.stat-card.accent {
  background: linear-gradient(180deg, #fff7f2 0%, #ffffff 100%);
  border-color: #f3ddd2;
}
.stat-label {
  font-size: 13px;
  color: #64748b;
}
.stat-value {
  margin-top: 8px;
  font-size: 30px;
  font-weight: 800;
  color: #1f2a28;
}
.stat-card.accent .stat-value {
  color: #d97706;
}
.stat-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  padding: 12px 14px;
  background: #f8faf9;
  border: 1px solid #e8eeeb;
  border-radius: 12px;
}
.table-panel {
  background: #fff;
  border: 1px solid #e8eeeb;
  border-radius: 14px;
  padding: 14px 16px 16px;
}
.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.table-head h3 {
  margin: 0;
  font-size: 15px;
  color: #1f2a28;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
