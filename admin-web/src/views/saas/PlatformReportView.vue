<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>{{ title }}</h2>
        <p class="sub">{{ subtitle }}</p>
      </div>
      <div class="actions">
        <el-select v-model="days" style="width: 120px" @change="load">
          <el-option label="近7天" :value="7" />
          <el-option label="近30天" :value="30" />
          <el-option label="近90天" :value="90" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat">
        <div class="lab">{{ s.label }}</div>
        <div class="val">{{ s.value }}</div>
      </div>
    </div>

    <h3 class="section">明细</h3>
    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无数据">
      <el-table-column v-for="c in columns" :key="c.prop" :prop="c.prop" :label="c.label" :min-width="c.width || 120" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { getAiReport, getOpsReport, getStoreReport } from '@/api/platformHub'

const props = defineProps<{ type: 'ops' | 'store' | 'ai' }>()

const loading = ref(false)
const days = ref(30)
const data = ref<any>({})

const title = computed(() =>
  props.type === 'ops' ? '全域运营报表' : props.type === 'store' ? '商业超市营收报表' : 'AI资源消耗报表',
)
const subtitle = computed(() =>
  props.type === 'ops'
    ? '租户、报修、公告、活动等全域运营指标 · 支持筛选导出'
    : props.type === 'store'
      ? '社区超市成交额与佣金应收汇总 · 与 SaaS 套餐订购账务独立'
      : 'DeepSeek/AI 接口调用量与失败率 · 按小区/场景汇总',
)

const stats = computed(() => {
  const d = data.value || {}
  if (props.type === 'ops') {
    return [
      { label: '租户总数', value: d.tenantCount ?? 0 },
      { label: '活跃租户', value: d.activeTenantCount ?? 0 },
      { label: '报修工单', value: d.repairOrderCount ?? 0 },
      { label: '公告数', value: d.noticeCount ?? 0 },
      { label: '活动数', value: d.activityCount ?? 0 },
    ]
  }
  if (props.type === 'store') {
    return [
      { label: '成交额(GMV)', value: `¥${Number(d.gmv || 0).toFixed(2)}` },
      { label: '完成订单', value: d.orderCount ?? 0 },
      { label: '应收佣金', value: `¥${Number(d.commissionAmount || 0).toFixed(2)}` },
      { label: '待结算佣金', value: `¥${Number(d.pendingCommission || 0).toFixed(2)}` },
    ]
  }
  return [
    { label: '调用总量', value: d.totalCalls ?? 0 },
    { label: '失败次数', value: d.failCalls ?? 0 },
    {
      label: '失败率',
      value:
        d.totalCalls > 0
          ? `${(((d.failCalls || 0) / d.totalCalls) * 100).toFixed(1)}%`
          : '0%',
    },
  ]
})

const columns = computed(() => {
  if (props.type === 'ai') {
    return [
      { prop: 'tenantName', label: '小区', width: 140 },
      { prop: 'cnt', label: '调用次数', width: 100 },
    ]
  }
  if (props.type === 'store') {
    return [
      { prop: 'tenantName', label: '小区', width: 140 },
      { prop: 'orderCount', label: '订单数', width: 90 },
      { prop: 'gmv', label: '成交额', width: 120 },
    ]
  }
  return [
    { prop: 'tenantName', label: '小区', width: 140 },
    { prop: 'cnt', label: '报修数', width: 100 },
  ]
})

const rows = computed(() => {
  const d = data.value || {}
  if (props.type === 'ai') return d.byTenant || []
  if (props.type === 'store') return d.byTenant || []
  return d.byTenantRepair || []
})

async function load() {
  loading.value = true
  try {
    if (props.type === 'ops') data.value = (await getOpsReport(days.value)) || {}
    else if (props.type === 'store') data.value = (await getStoreReport(days.value)) || {}
    else data.value = (await getAiReport(days.value)) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

function onExport() {
  if (!rows.value.length) {
    ElMessage.warning('暂无数据')
    return
  }
  exportExcel(rows.value, title.value, '报表')
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; align-items: center; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; margin-bottom: 16px; }
.stat { background: #f8fafc; border-radius: 8px; padding: 12px 14px; }
.lab { color: #64748b; font-size: 12px; }
.val { margin-top: 6px; font-size: 20px; font-weight: 600; color: #0f172a; }
.section { margin: 8px 0 12px; font-size: 14px; }
</style>
