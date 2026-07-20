<template>
  <div>
    <div class="page-header-plain">
      <h2>物业工作台</h2>
      <el-tag type="success">{{ user.communityName }}</el-tag>
    </div>
    <el-row :gutter="12">
      <el-col :span="6" v-for="k in kpis" :key="k.label">
        <div class="kpi-card" @click="$router.push(k.to)">
          <div class="label">{{ k.label }}</div>
          <div class="value">{{ k.value }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="14">
        <div class="page-card">
          <h3>今日待办</h3>
          <el-table :data="todos" size="small">
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="title" label="内容" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="操作" width="90">
              <template #default><el-button link type="primary">处理</el-button></template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="page-card">
          <h3>快捷入口</h3>
          <div class="shortcuts">
            <el-button v-for="s in shortcuts" :key="s.path" @click="$router.push(s.path)">{{ s.title }}</el-button>
          </div>
          <div class="chart-box" style="height: 240px; margin-top: 12px">
            <v-chart :option="opt" autoresize />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useUserStore } from '@/stores/user'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent])
const user = useUserStore()

const kpis = [
  { label: '待派报修', value: 12, to: '/property/repair' },
  { label: '待审迁入迁出', value: 5, to: '/property/move' },
  { label: '今日巡检未完成', value: 6, to: '/property/inspection' },
  { label: '待审访客', value: 3, to: '/property/visitor' },
]

const todos = [
  { type: '报修', title: '1栋502 卫生间漏水', priority: '高' },
  { type: '访客', title: '2栋301 访客待审核', priority: '中' },
  { type: '巡检', title: '消防巡检剩余 6 个点位', priority: '中' },
  { type: '维保', title: '1号电梯季度维保到期', priority: '高' },
]

const shortcuts = [
  { title: '报事报修', path: '/property/repair' },
  { title: '巡检任务', path: '/property/inspection' },
  { title: '临时访客', path: '/property/visitor' },
  { title: '设备维保', path: '/property/maintenance' },
  { title: '收款通知', path: '/property/receipt' },
  { title: '缴费查询', path: '/property/fee' },
]

const opt = computed(() => ({
  title: { text: '本周工单分布', left: 'center', textStyle: { fontSize: 13 } },
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie',
    radius: '60%',
    data: [
      { name: '报修', value: 28 },
      { name: '巡检异常', value: 9 },
      { name: '维保', value: 6 },
      { name: '其他', value: 4 },
    ],
  }],
}))
</script>

<style scoped>
.page-header-plain { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.page-header-plain h2 { margin: 0; font-size: 18px; }
.kpi-card {
  background: #fff; border-radius: 8px; padding: 18px; cursor: pointer;
  transition: box-shadow .2s;
}
.kpi-card:hover { box-shadow: 0 6px 18px rgba(22,119,255,.12); }
.kpi-card .label { color: #666; font-size: 13px; }
.kpi-card .value { margin-top: 8px; font-size: 30px; font-weight: 700; color: #1677ff; }
.shortcuts { display: flex; flex-wrap: wrap; gap: 8px; }
h3 { margin: 0 0 12px; font-size: 15px; }
</style>
