<template>
  <div class="screen-page">
    <div class="page-header" style="color: inherit">
      <h2>全局营收可视化大屏</h2>
      <el-date-picker
        v-model="range"
        type="monthrange"
        value-format="YYYY-MM"
        start-placeholder="开始月"
        end-placeholder="结束月"
      />
    </div>
    <div class="kpi-row">
      <div class="kpi-item" v-for="k in kpis" :key="k.label">
        <div class="label">{{ k.label }}</div>
        <div class="value">{{ k.value }}</div>
      </div>
    </div>
    <div class="chart-grid">
      <div class="chart-box"><v-chart :option="barOpt" autoresize /></div>
      <div class="chart-box"><v-chart :option="areaOpt" autoresize /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const range = ref<[string, string]>(['2026-01', '2026-07'])

const kpis = [
  { label: '本年营收(万)', value: '328.6' },
  { label: '有效租户', value: '86' },
  { label: '续费率', value: '78%' },
  { label: '本月新增', value: '12' },
]

const textStyle = { color: '#cfe6ff' }

const barOpt = computed(() => ({
  backgroundColor: 'transparent',
  textStyle,
  tooltip: { trigger: 'axis' },
  xAxis: {
    type: 'category',
    data: ['1月', '2月', '3月', '4月', '5月', '6月', '7月'],
    axisLabel: textStyle,
  },
  yAxis: { type: 'value', axisLabel: textStyle, splitLine: { lineStyle: { color: '#ffffff22' } } },
  series: [{ name: '营收(万)', type: 'bar', data: [36, 42, 48, 51, 55, 49, 47], itemStyle: { color: '#5ec8ff' } }],
}))

const areaOpt = computed(() => ({
  backgroundColor: 'transparent',
  textStyle,
  tooltip: { trigger: 'axis' },
  legend: { textStyle, data: ['新开通', '续费'] },
  xAxis: {
    type: 'category',
    data: ['1月', '2月', '3月', '4月', '5月', '6月', '7月'],
    axisLabel: textStyle,
  },
  yAxis: { type: 'value', axisLabel: textStyle, splitLine: { lineStyle: { color: '#ffffff22' } } },
  series: [
    { name: '新开通', type: 'line', areaStyle: {}, data: [8, 10, 12, 9, 14, 11, 12] },
    { name: '续费', type: 'line', areaStyle: {}, data: [15, 18, 16, 20, 22, 19, 21] },
  ],
}))
</script>
