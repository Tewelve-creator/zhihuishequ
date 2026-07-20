<template>
  <div class="page-card">
    <div class="page-header">
      <h2>统计报表</h2>
      <div>
        <el-date-picker v-model="range" type="monthrange" value-format="YYYY-MM" start-placeholder="开始月" end-placeholder="结束月" />
        <el-button style="margin-left: 8px" @click="onExport">导出 Excel</el-button>
      </div>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="收费统计" name="fee" />
      <el-tab-pane label="工单统计" name="wo" />
      <el-tab-pane label="入住统计" name="live" />
    </el-tabs>
    <div class="chart-box"><v-chart :option="option" autoresize /></div>
    <el-table :data="table" stripe style="margin-top: 16px">
      <el-table-column prop="name" label="项目" />
      <el-table-column prop="m1" label="5月" />
      <el-table-column prop="m2" label="6月" />
      <el-table-column prop="m3" label="7月" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { exportExcel } from '@/utils/exportExcel'
import { ElMessage } from 'element-plus'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent])

const tab = ref('fee')
const range = ref<[string, string]>(['2026-05', '2026-07'])
const table = [
  { name: '物业费', m1: 42.1, m2: 44.8, m3: 46.2 },
  { name: '停车费', m1: 11.2, m2: 12.0, m3: 12.6 },
  { name: '其他', m1: 3.4, m2: 3.1, m3: 4.0 },
]

const option = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['物业费', '停车费', '其他'] },
  xAxis: { type: 'category', data: ['5月', '6月', '7月'] },
  yAxis: { type: 'value', name: '万元' },
  series: [
    { name: '物业费', type: 'bar', data: [42.1, 44.8, 46.2] },
    { name: '停车费', type: 'bar', data: [11.2, 12.0, 12.6] },
    { name: '其他', type: 'bar', data: [3.4, 3.1, 4.0] },
  ],
}))

function onExport() {
  exportExcel(table, `统计报表_${tab.value}_${Date.now()}`)
  ElMessage.success('导出成功')
}
</script>
