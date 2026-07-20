<template>
  <div class="screen-page">
    <div class="page-header" style="color: inherit">
      <h2>社区数据可视化大屏</h2>
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始"
        end-placeholder="结束"
        dark
      />
    </div>
    <div class="kpi-row">
      <div class="kpi-item" v-for="k in kpis" :key="k.label">
        <div class="label">{{ k.label }}</div>
        <div class="value">{{ k.value }}</div>
      </div>
    </div>
    <div class="chart-grid">
      <div class="chart-box"><v-chart :option="lineOpt" autoresize /></div>
      <div class="chart-box"><v-chart :option="pieOpt" autoresize /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const range = ref<[string, string]>([
  dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])

const kpis = [
  { label: '入住户数', value: '1,286' },
  { label: '本月工单', value: '186' },
  { label: '活动参与', value: '426' },
  { label: '满意度', value: '96%' },
]

const textStyle = { color: '#cfe6ff' }

const lineOpt = computed(() => ({
  backgroundColor: 'transparent',
  textStyle,
  tooltip: { trigger: 'axis' },
  legend: { textStyle, data: ['报修', '咨询'] },
  xAxis: { type: 'category', data: ['W1', 'W2', 'W3', 'W4'], axisLabel: textStyle },
  yAxis: { type: 'value', axisLabel: textStyle, splitLine: { lineStyle: { color: '#ffffff22' } } },
  series: [
    { name: '报修', type: 'line', smooth: true, data: [32, 40, 28, 45] },
    { name: '咨询', type: 'line', smooth: true, data: [18, 22, 30, 26] },
  ],
}))

const pieOpt = computed(() => ({
  backgroundColor: 'transparent',
  textStyle,
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle },
  series: [
    {
      type: 'pie',
      radius: ['40%', '65%'],
      data: [
        { name: '水电', value: 38 },
        { name: '门禁', value: 22 },
        { name: '电梯', value: 18 },
        { name: '其他', value: 12 },
      ],
    },
  ],
}))
</script>
