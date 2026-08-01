<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>活动统计</h2>
        <p class="sub">活动数、报名/签到、分类占比与热门活动（当前小区）</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="kpi">
      <el-col :span="4"><el-statistic title="活动总数" :value="data.activityTotal || 0" /></el-col>
      <el-col :span="4"><el-statistic title="上架中" :value="data.onlineTotal || 0" /></el-col>
      <el-col :span="4"><el-statistic title="有效报名" :value="data.signupTotal || 0" /></el-col>
      <el-col :span="4"><el-statistic title="已签到" :value="data.checkedTotal || 0" /></el-col>
      <el-col :span="4"><el-statistic title="已取消" :value="data.cancelledTotal || 0" /></el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 16px">
      <el-col :span="8">
        <div class="panel">
          <h3>分类占比</h3>
          <el-table :data="data.byCategory || []" size="small" stripe>
            <el-table-column prop="name" label="分类" />
            <el-table-column prop="value" label="数量" width="80" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <h3>报名热门 TOP10</h3>
          <el-table :data="data.topSignup || []" size="small" stripe max-height="320">
            <el-table-column prop="title" label="活动" min-width="120" show-overflow-tooltip />
            <el-table-column label="报名" width="90">
              <template #default="{ row }">{{ row.signedCount }}/{{ row.maxPeople || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel">
          <h3>近 14 日报名趋势</h3>
          <el-table :data="data.recentSignups || []" size="small" stripe max-height="320">
            <el-table-column prop="day" label="日期" />
            <el-table-column prop="value" label="报名数" width="80" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { exportExcel } from '@/utils/exportExcel'
import { getActivityStats } from '@/api/activity'

const loading = ref(false)
const data = ref<any>({})

async function load() {
  loading.value = true
  try {
    data.value = (await getActivityStats()) || {}
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}

function onExport() {
  const rows = [
    { 指标: '活动总数', 数值: data.value.activityTotal },
    { 指标: '上架中', 数值: data.value.onlineTotal },
    { 指标: '有效报名', 数值: data.value.signupTotal },
    { 指标: '已签到', 数值: data.value.checkedTotal },
    { 指标: '已取消', 数值: data.value.cancelledTotal },
    ...(data.value.byCategory || []).map((r: any) => ({ 指标: `分类-${r.name}`, 数值: r.value })),
    ...(data.value.topSignup || []).map((r: any) => ({
      指标: `热门-${r.title}`,
      数值: r.signedCount,
    })),
  ]
  exportExcel(rows, '活动统计.xlsx')
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.kpi { margin-bottom: 8px; }
.panel {
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  min-height: 280px;
}
.panel h3 { margin: 0 0 10px; font-size: 14px; }
</style>
