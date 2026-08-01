<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>微服务运行监控</h2>
        <p class="sub">各微服务健康探测 · {{ autoRefresh ? '每 30 秒自动刷新' : '手动刷新' }}</p>
      </div>
      <div class="actions">
        <el-switch v-model="autoRefresh" active-text="自动刷新" inactive-text="手动" @change="onAutoChange" />
        <el-button :loading="loading" @click="load">立即刷新</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="summary">
      <el-col :xs="12" :sm="8" :md="6">
        <div class="kpi"><div class="kpi-label">服务总数</div><div class="kpi-value">{{ services.length }}</div></div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <div class="kpi ok"><div class="kpi-label">运行中</div><div class="kpi-value">{{ upCount }}</div></div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <div class="kpi down"><div class="kpi-label">不可用</div><div class="kpi-value">{{ services.length - upCount }}</div></div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <div class="kpi"><div class="kpi-label">平均延迟</div><div class="kpi-value">{{ avgLatency }} ms</div></div>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col v-for="s in services" :key="s.name" :xs="24" :sm="12" :md="8" :lg="6">
        <div class="health-card" :class="s.status === 'up' ? 'up' : 'down'">
          <div class="health-name">{{ displayName(s.name) }}</div>
          <div class="health-status">{{ s.status === 'up' ? '运行中' : '不可用' }}</div>
          <div class="health-meta">{{ s.url }}</div>
          <div class="health-latency">延迟 {{ s.latencyMs ?? '-' }} ms</div>
        </div>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && !services.length" description="暂无监控数据" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getServiceHealth } from '@/api/saas'

const NAME_MAP: Record<string, string> = {
  auth: '认证服务',
  saas: 'SaaS 服务',
  repair: '业务服务',
  ai: 'AI 服务',
  order: '订单服务',
  stats: '统计服务',
  gateway: '网关',
}

const loading = ref(false)
const autoRefresh = ref(false)
const services = ref<any[]>([])
let timer: ReturnType<typeof setInterval> | null = null

const upCount = computed(() => services.value.filter((s) => s.status === 'up').length)
const avgLatency = computed(() => {
  const ups = services.value.filter((s) => s.status === 'up' && s.latencyMs != null)
  if (!ups.length) return '-'
  const sum = ups.reduce((a, s) => a + Number(s.latencyMs || 0), 0)
  return Math.round(sum / ups.length)
})

function displayName(name: string) {
  return NAME_MAP[name] || name
}

async function load() {
  loading.value = true
  try {
    services.value = (await getServiceHealth()) || []
  } catch {
    services.value = []
  } finally {
    loading.value = false
  }
}

function onAutoChange(on: boolean) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (on) {
    timer = setInterval(load, 30000)
  }
}

onMounted(load)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; align-items: center; gap: 12px; }
.summary { margin-bottom: 8px; }
.kpi {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.kpi.ok { background: #f0fdf4; border-color: #86efac; }
.kpi.down { background: #fef2f2; border-color: #fca5a5; }
.kpi-label { font-size: 13px; color: #64748b; }
.kpi-value { margin-top: 4px; font-size: 20px; font-weight: 700; color: #1677ff; }
.health-card {
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
}
.health-card.up { background: #f0fdf4; border-color: #86efac; }
.health-card.down { background: #fef2f2; border-color: #fca5a5; }
.health-name { font-size: 16px; font-weight: 600; }
.health-status { margin-top: 6px; font-size: 14px; }
.health-card.up .health-status { color: #16a34a; }
.health-card.down .health-status { color: #dc2626; }
.health-meta { margin-top: 8px; font-size: 12px; color: #64748b; word-break: break-all; }
.health-latency { margin-top: 4px; font-size: 13px; color: #475569; }
</style>
