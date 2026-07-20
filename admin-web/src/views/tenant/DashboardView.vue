<template>
  <div class="page-card">
    <div class="page-header">
      <h2>物业租户工作台</h2>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="refresh"
      />
    </div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="item in kpis" :key="item.label">
        <el-statistic :title="item.label" :value="item.value" />
      </el-col>
    </el-row>
    <div class="chart-box" style="margin-top: 24px">
      <v-chart :option="chartOption" autoresize />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const range = ref<[string, string]>([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])

const kpis = ref([
  { label: '住户数', value: 1286 },
  { label: '待处理工单', value: 23 },
  { label: '本周公告', value: 5 },
  { label: '活动报名', value: 168 },
])

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['工单量', '活跃用户'] },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
  },
  yAxis: { type: 'value' },
  series: [
    { name: '工单量', type: 'bar', data: [12, 18, 9, 15, 22, 8, 11] },
    { name: '活跃用户', type: 'line', data: [220, 260, 240, 280, 310, 290, 330] },
  ],
}))

function refresh() {
  // 对接网关统计接口：GET /api/tenant/dashboard/stats?start=&end=
}
</script>
