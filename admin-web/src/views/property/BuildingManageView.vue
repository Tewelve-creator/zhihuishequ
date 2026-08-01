<template>
  <div class="page-card building-page">
    <!-- 顶部：标题 + 操作 -->
    <div class="page-header">
      <div>
        <h2>楼栋信息</h2>
        <p class="sub">楼栋沙盘总览 · 户数 / 电梯 / 空置一站查看，支撑房屋档案与住户绑定</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openAddBuilding">新增楼栋</el-button>
        <el-button v-if="canManage" @click="openImport">导入</el-button>
        <el-button @click="onExport">导出</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">楼栋总数</div>
        <div class="stat-value">{{ stats.buildingCount }}</div>
        <div class="stat-hint">栋</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总户数</div>
        <div class="stat-value">{{ stats.houseCount }}</div>
        <div class="stat-hint">户</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">电梯总数</div>
        <div class="stat-value">{{ stats.elevatorCount }}</div>
        <div class="stat-hint">部</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-label">空置户数</div>
        <div class="stat-value">{{ stats.vacantCount }}</div>
        <div class="stat-hint">户 · 空置率 {{ stats.vacantRate }}%</div>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="filter-bar">
      <el-select v-model="filterBuilding" clearable filterable placeholder="按楼栋" style="width: 140px" @change="onBuildingFilter">
        <el-option v-for="b in buildingOptions" :key="b" :label="b" :value="b" />
      </el-select>
      <el-select v-model="filterUnit" clearable filterable placeholder="按单元" style="width: 140px">
        <el-option v-for="u in unitOptions" :key="u" :label="u" :value="u" />
      </el-select>
      <el-select v-model="filterStatus" clearable placeholder="按状态" style="width: 130px">
        <el-option label="有空置" value="HAS_VACANT" />
        <el-option label="已住满" value="FULL" />
        <el-option label="含出租" value="HAS_RENT" />
        <el-option label="含自住" value="HAS_SELF" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索楼栋名称"
        style="width: 180px"
        @keyup.enter="noop"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </div>

    <!-- 楼栋沙盘预览 -->
    <div class="sand-panel">
      <div class="sand-head">
        <h3>楼栋沙盘预览</h3>
        <div class="legend">
          <span><i class="dot occ" />已入住</span>
          <span><i class="dot vac" />空置</span>
          <span><i class="dot rent" />出租占比高</span>
        </div>
      </div>
      <div v-loading="loading" class="sand-grid">
        <div
          v-for="row in filtered"
          :key="row.building"
          class="sand-card"
          :class="sandTone(row)"
          @click="selectBuilding(row)"
        >
          <div class="sand-title">
            <span>{{ row.building }}</span>
            <el-tag size="small" effect="plain" round>{{ Number(row.occupancyRate || 0) }}%</el-tag>
          </div>
          <div class="sand-bars">
            <div
              v-for="u in row.unitStats || []"
              :key="u.unit"
              class="unit-col"
              :title="`${u.unit} · ${u.houseCount}户 · 空置${u.vacantCount}`"
            >
              <div class="unit-stack">
                <div
                  class="seg occ"
                  :style="{ flex: Math.max(0, Number(u.houseCount) - Number(u.vacantCount)) || 0.01 }"
                />
                <div class="seg vac" :style="{ flex: Number(u.vacantCount) || 0.01 }" />
              </div>
              <div class="unit-name">{{ shortenUnit(u.unit) }}</div>
            </div>
            <div v-if="!(row.unitStats || []).length" class="unit-empty">暂无单元</div>
          </div>
          <div class="sand-meta">
            <span>{{ row.houseCount }}户</span>
            <span>{{ row.elevatorCount || 0 }}梯</span>
            <span class="vac-text">空置 {{ row.vacantCount }}</span>
          </div>
        </div>
        <el-empty v-if="!filtered.length && !loading" description="暂无楼栋，请先新增或导入" :image-size="72" />
      </div>
    </div>

    <!-- 楼栋信息表格 -->
    <div class="table-panel">
      <div class="table-head">
        <h3>楼栋信息表格</h3>
        <span class="muted">共 {{ filtered.length }} 栋</span>
      </div>
      <el-table
        v-loading="loading"
        :data="filtered"
        stripe
        empty-text="暂无楼栋"
        highlight-current-row
        :current-row-key="selectedBuilding"
        row-key="building"
        @row-click="(row) => selectBuilding(row)"
      >
        <el-table-column label="楼栋" width="100" fixed>
          <template #default="{ row }">{{ formatBuilding(row.building) }}</template>
        </el-table-column>
        <el-table-column label="层数" width="90">
          <template #default="{ row }">{{ Number(row.floors || 0) }} 层</template>
        </el-table-column>
        <el-table-column prop="unitCount" label="单元数" width="90" align="center" />
        <el-table-column prop="houseCount" label="总户数" width="90" align="center" />
        <el-table-column label="电梯数量" width="100" align="center">
          <template #default="{ row }">{{ Number(row.elevatorCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="空置户数" width="100" align="center">
          <template #default="{ row }">{{ Number(row.vacantCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="自住户数" width="100" align="center">
          <template #default="{ row }">{{ Number(row.selfCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="出租户数" width="100" align="center">
          <template #default="{ row }">{{ Number(row.rentCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="入住率" width="100" align="center">
          <template #default="{ row }">{{ formatRate(row.occupancyRate) }}</template>
        </el-table-column>
        <el-table-column label="单元分户详情" min-width="260">
          <template #default="{ row }">{{ formatUnitDetail(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goHouses(row.building)">房屋档案</el-button>
            <el-button v-if="canManage" link type="primary" @click.stop="addUnitFor(row.building)">加单元</el-button>
            <el-button v-if="canManage" link type="primary" @click.stop="editMeta(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增 / 编辑楼栋 -->
    <el-dialog v-model="dlgBuilding" :title="buildingForm.exists ? '编辑楼栋' : '新增楼栋'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="楼栋名称" required>
          <el-input v-model="buildingForm.buildingName" :disabled="buildingForm.exists" placeholder="如 1栋 / A栋" />
        </el-form-item>
        <el-form-item label="层数">
          <el-input-number v-model="buildingForm.floors" :min="1" :max="99" style="width: 100%" />
        </el-form-item>
        <el-form-item label="电梯数">
          <el-input-number v-model="buildingForm.elevators" :min="0" :max="20" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="buildingForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgBuilding = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBuilding">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入房号 -->
    <el-dialog v-model="dlgImport" title="导入房号" width="480px">
      <el-form label-width="80px">
        <el-form-item label="楼栋" required>
          <el-input v-model="batch.buildingName" placeholder="如 1栋" />
        </el-form-item>
        <el-form-item label="单元" required>
          <el-input v-model="batch.unitName" placeholder="如 1单元" />
        </el-form-item>
        <el-form-item label="房号" required>
          <el-input
            v-model="batch.houseNos"
            type="textarea"
            :rows="4"
            placeholder="如：101,102,103 或 101-110"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgImport = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import {
  batchHouses,
  ensureBuilding,
  ensureUnit,
  listAdminUnits,
  listBuildingSummary,
} from '@/api/houseAdmin'

const user = useUserStore()
const router = useRouter()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const keyword = ref('')
const filterBuilding = ref('')
const filterUnit = ref('')
const filterStatus = ref('')
const unitOptions = ref<string[]>([])
const selectedBuilding = ref('')
const dlgBuilding = ref(false)
const dlgImport = ref(false)

const buildingForm = reactive({
  exists: false,
  buildingName: '',
  floors: 18,
  elevators: 2,
  remark: '',
})
const batch = reactive({ buildingName: '', unitName: '', houseNos: '' })

const buildingOptions = computed(() => list.value.map((r) => String(r.building)))

const filtered = computed(() => {
  let rows = [...list.value]
  const k = keyword.value.trim()
  if (filterBuilding.value) {
    rows = rows.filter((r) => r.building === filterBuilding.value)
  }
  if (k) {
    rows = rows.filter((r) => String(r.building || '').includes(k))
  }
  if (filterUnit.value) {
    rows = rows.filter((r) => (r.units || []).includes(filterUnit.value))
  }
  if (filterStatus.value === 'HAS_VACANT') {
    rows = rows.filter((r) => Number(r.vacantCount) > 0)
  } else if (filterStatus.value === 'FULL') {
    rows = rows.filter((r) => Number(r.vacantCount) === 0 && Number(r.houseCount) > 0)
  } else if (filterStatus.value === 'HAS_RENT') {
    rows = rows.filter((r) => Number(r.rentCount) > 0)
  } else if (filterStatus.value === 'HAS_SELF') {
    rows = rows.filter((r) => Number(r.selfCount) > 0)
  }
  return rows
})

const stats = computed(() => {
  const rows = filtered.value
  const buildingCount = rows.length
  const houseCount = rows.reduce((s, r) => s + Number(r.houseCount || 0), 0)
  const elevatorCount = rows.reduce((s, r) => s + Number(r.elevatorCount || 0), 0)
  const vacantCount = rows.reduce((s, r) => s + Number(r.vacantCount || 0), 0)
  const vacantRate = houseCount <= 0 ? 0 : Math.round((vacantCount * 1000) / houseCount) / 10
  return { buildingCount, houseCount, elevatorCount, vacantCount, vacantRate }
})

function noop() {}

async function load() {
  loading.value = true
  try {
    list.value = (await listBuildingSummary()) || []
  } catch (e: any) {
    list.value = []
    ElMessage.error(e?.msg || e?.message || '加载楼栋失败，请确认已登录物业账号且 auth 服务已启动')
  } finally {
    loading.value = false
  }
}

async function onBuildingFilter() {
  filterUnit.value = ''
  unitOptions.value = filterBuilding.value
    ? (await listAdminUnits(filterBuilding.value).catch(() => [])) || []
    : []
}

function resetFilter() {
  filterBuilding.value = ''
  filterUnit.value = ''
  filterStatus.value = ''
  keyword.value = ''
  unitOptions.value = []
}

function selectBuilding(row: any) {
  selectedBuilding.value = row.building
}

function formatBuilding(name: string) {
  if (!name) return '—'
  const s = String(name).trim()
  // 「1栋」→「1 栋」
  return s.replace(/^(\d+)(栋)$/, '$1 $2').replace(/^([A-Za-z]+)(栋)$/, '$1 $2')
}

function formatRate(rate: unknown) {
  const n = Number(rate || 0)
  return `${n.toFixed(1)}%`
}

function formatUnitDetail(row: any) {
  const stats = row.unitStats || []
  if (!stats.length) {
    const units = row.units || []
    if (!units.length) return '—'
    return units.map((u: string) => `${formatUnitLabel(u)}：—`).join('，')
  }
  return stats
    .map((u: any) => `${formatUnitLabel(u.unit)}：${Number(u.houseCount || 0)} 户`)
    .join('，')
}

function formatUnitLabel(u: string) {
  if (!u) return ''
  const s = String(u).trim()
  return s.replace(/^(\d+)(单元)$/, '$1 $2')
}

function shortenUnit(u: string) {
  if (!u) return ''
  return String(u).replace(/单元$/, '')
}

function sandTone(row: any) {
  const vac = Number(row.vacantCount || 0)
  const houses = Number(row.houseCount || 0)
  const rent = Number(row.rentCount || 0)
  if (houses > 0 && rent / houses >= 0.4) return 'tone-rent'
  if (houses > 0 && vac / houses >= 0.35) return 'tone-vac'
  return 'tone-occ'
}

function openAddBuilding() {
  Object.assign(buildingForm, {
    exists: false,
    buildingName: '',
    floors: 18,
    elevators: 2,
    remark: '',
  })
  dlgBuilding.value = true
}

function editMeta(row: any) {
  Object.assign(buildingForm, {
    exists: true,
    buildingName: row.building,
    floors: Number(row.floors || 1),
    elevators: Number(row.elevatorCount || 1),
    remark: row.remark || '',
  })
  dlgBuilding.value = true
}

async function saveBuilding() {
  if (!buildingForm.buildingName.trim()) {
    ElMessage.warning('请填写楼栋名称')
    return
  }
  saving.value = true
  try {
    await ensureBuilding({
      buildingName: buildingForm.buildingName.trim(),
      floors: buildingForm.floors,
      elevators: buildingForm.elevators,
      remark: buildingForm.remark,
    })
    ElMessage.success(buildingForm.exists ? '楼栋已更新' : '楼栋已登记')
    dlgBuilding.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openImport() {
  Object.assign(batch, {
    buildingName: filterBuilding.value || selectedBuilding.value || '',
    unitName: filterUnit.value || '',
    houseNos: '',
  })
  dlgImport.value = true
}

async function doImport() {
  if (!batch.buildingName || !batch.unitName || !batch.houseNos) {
    ElMessage.warning('请填写楼栋、单元与房号')
    return
  }
  saving.value = true
  try {
    const res: any = await batchHouses({ ...batch })
    ElMessage.success(`导入完成：新增 ${res?.created ?? 0}，跳过 ${res?.skipped ?? 0}`)
    dlgImport.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '导入失败')
  } finally {
    saving.value = false
  }
}

function onExport() {
  const rows = filtered.value.map((r) => ({
    楼栋: formatBuilding(r.building),
    层数: `${Number(r.floors || 0)} 层`,
    单元数: r.unitCount,
    总户数: r.houseCount,
    电梯数量: r.elevatorCount,
    空置户数: r.vacantCount,
    自住户数: r.selfCount,
    出租户数: r.rentCount,
    入住率: formatRate(r.occupancyRate),
    单元分户详情: formatUnitDetail(r),
  }))
  if (!rows.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  exportExcel(rows, `楼栋信息_${Date.now()}`)
}

async function addUnitFor(building: string) {
  const { value: unit } = await ElMessageBox.prompt(`为「${building}」新增单元`, '新增单元')
  await ensureUnit({ buildingName: building, unitName: unit })
  ElMessage.success('单元已登记')
  load()
}

function goHouses(building: string) {
  router.push({ path: '/property/residents/houses', query: { building } })
}

onMounted(load)
</script>

<style scoped>
.building-page {
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
  position: relative;
  overflow: hidden;
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
  letter-spacing: -0.5px;
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

.sand-panel,
.table-panel {
  background: #fff;
  border: 1px solid #e8eeeb;
  border-radius: 14px;
  padding: 14px 16px 16px;
}
.sand-head,
.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.sand-head h3,
.table-head h3 {
  margin: 0;
  font-size: 15px;
  color: #1f2a28;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
.legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #64748b;
}
.legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.dot.occ { background: #2f9e6f; }
.dot.vac { background: #e5e7eb; }
.dot.rent { background: #f59e0b; }

.sand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.sand-card {
  border: 1px solid #e5eee9;
  border-radius: 14px;
  padding: 12px;
  cursor: pointer;
  background: linear-gradient(165deg, #f4faf7, #fff);
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}
.sand-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(47, 158, 111, 0.1);
  border-color: #b7e0cc;
}
.sand-card.tone-vac {
  background: linear-gradient(165deg, #f8fafc, #fff);
}
.sand-card.tone-rent {
  background: linear-gradient(165deg, #fff8eb, #fff);
}
.sand-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 800;
  color: #1f2a28;
  margin-bottom: 10px;
}
.sand-bars {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  min-height: 72px;
  padding: 4px 2px 0;
}
.unit-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.unit-stack {
  width: 100%;
  height: 64px;
  display: flex;
  flex-direction: column-reverse;
  border-radius: 6px 6px 2px 2px;
  overflow: hidden;
  background: #eef2f0;
  border: 1px solid #e2e8e5;
}
.seg.occ { background: linear-gradient(180deg, #6ed3a8, #2f9e6f); }
.seg.vac { background: #e8ecea; }
.unit-name {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.unit-empty {
  color: #94a3b8;
  font-size: 12px;
  padding: 20px 0;
}
.sand-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #64748b;
}
.vac-text { color: #d97706; font-weight: 600; }

.rate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rate-cell .el-progress {
  flex: 1;
}
.rate-cell > span {
  width: 42px;
  font-size: 12px;
  color: #475569;
}

@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
  .page-header {
    flex-direction: column;
  }
}
</style>
