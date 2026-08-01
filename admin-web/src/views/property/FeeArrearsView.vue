<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>欠费汇总</h2>
        <p class="sub">按楼栋统计欠费信息，支持筛选逾期住户，可一键发起催缴推送</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="warning" :disabled="!selected.length" @click="goUrge">
          一键催缴（已选 {{ selected.length }}）
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="building" clearable placeholder="楼栋" style="width: 140px" @change="load">
        <el-option v-for="b in buildingOptions" :key="b" :label="b" :value="b" />
      </el-select>
      <el-checkbox v-model="overdueOnly" @change="load">仅逾期住户</el-checkbox>
      <el-input
        v-model="keyword"
        clearable
        placeholder="房屋/业主/手机"
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <div class="summary">
      <span>欠费户数 <b>{{ data.totalHouses || 0 }}</b></span>
      <span>欠费合计 <b>¥{{ Number(data.totalUnpaidAmount || 0).toFixed(2) }}</b></span>
      <span>逾期户数 <b class="danger">{{ data.totalOverdueHouses || 0 }}</b></span>
    </div>

    <el-table
      v-loading="loading"
      :data="data.buildings || []"
      stripe
      empty-text="暂无楼栋欠费"
      style="margin-bottom: 20px"
    >
      <el-table-column prop="building" label="楼栋" width="120" />
      <el-table-column prop="houseCount" label="欠费户数" width="100" />
      <el-table-column prop="unpaidCount" label="欠费笔数" width="100" />
      <el-table-column prop="unpaidAmount" label="欠费金额" width="120">
        <template #default="{ row }">¥{{ Number(row.unpaidAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="overdueCount" label="逾期笔数" width="100" />
      <el-table-column prop="overdueAmount" label="逾期金额" width="120">
        <template #default="{ row }">
          <span :class="{ danger: Number(row.overdueAmount) > 0 }">
            ¥{{ Number(row.overdueAmount || 0).toFixed(2) }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <h3 class="section-title">欠费住户明细</h3>
    <el-table
      v-loading="loading"
      :data="data.houses || []"
      stripe
      empty-text="暂无欠费住户"
      @selection-change="onSelect"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="building" label="楼栋" width="90" />
      <el-table-column prop="room" label="房屋" min-width="180" show-overflow-tooltip />
      <el-table-column prop="owner" label="业主" width="100" />
      <el-table-column prop="phone" label="手机号" width="120" />
      <el-table-column prop="unpaidCount" label="欠费笔数" width="90" />
      <el-table-column prop="unpaidAmount" label="欠费金额" width="110">
        <template #default="{ row }">¥{{ Number(row.unpaidAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="overdueCount" label="逾期笔数" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.overdueCount > 0" type="danger" size="small">{{ row.overdueCount }}</el-tag>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column prop="periods" label="欠费周期" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.periods || []).join('、') || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" @click="urgeOne(row)">催缴</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFeeArrears, type FeeArrearsHouse, type FeeArrearsSummary } from '@/api/fee'

const router = useRouter()
const loading = ref(false)
const building = ref('')
const overdueOnly = ref(false)
const keyword = ref('')
const buildingOptions = ref<string[]>([])
const selected = ref<FeeArrearsHouse[]>([])
const data = reactive<FeeArrearsSummary>({
  buildings: [],
  houses: [],
  buildingOptions: [],
  totalHouses: 0,
  totalUnpaidAmount: 0,
  totalOverdueHouses: 0,
})

function onSelect(rows: FeeArrearsHouse[]) {
  selected.value = rows
}

async function load() {
  loading.value = true
  try {
    const res = await getFeeArrears({
      building: building.value || undefined,
      overdueOnly: overdueOnly.value || undefined,
      keyword: keyword.value || undefined,
    })
    Object.assign(data, res)
    buildingOptions.value = res.buildingOptions || []
    selected.value = []
  } catch {
    Object.assign(data, {
      buildings: [],
      houses: [],
      buildingOptions: [],
      totalHouses: 0,
      totalUnpaidAmount: 0,
      totalOverdueHouses: 0,
    })
  } finally {
    loading.value = false
  }
}

function toTargets(rows: FeeArrearsHouse[]) {
  return rows.map((r) => ({
    houseId: r.houseId,
    userId: r.userId,
    owner: r.owner,
    room: r.room,
    phone: r.phone,
    unpaidAmount: Number(r.unpaidAmount || 0),
  }))
}

function goUrge() {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选要催缴的业主')
    return
  }
  sessionStorage.setItem('fee_urge_targets', JSON.stringify(toTargets(selected.value)))
  router.push({ path: '/property/fee/receipt', query: { create: '1' } })
}

function urgeOne(row: FeeArrearsHouse) {
  sessionStorage.setItem('fee_urge_targets', JSON.stringify(toTargets([row])))
  router.push({ path: '/property/fee/receipt', query: { create: '1' } })
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}
.page-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.summary {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.summary b {
  color: var(--el-color-primary);
  font-size: 15px;
  margin-left: 4px;
}
.danger {
  color: var(--el-color-danger) !important;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}
</style>
