<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">服务状态</h1>
          <p class="page-desc">基于健康探测接口 · 仅展示 name / status / url / latencyMs</p>
        </div>
        <div class="page-actions">
          <el-switch v-model="autoRefresh" active-text="自动刷新" inactive-text="手动" @change="onAutoChange" />
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb"
        title="当前后端仅提供微服务健康探测。接口监控、任务中心、告警记录暂无接口，菜单未创建。"
      />

      <div class="kpi-row">
        <div class="kpi">
          <div class="kpi-label">服务总数</div>
          <div class="kpi-value">{{ services.length }}</div>
        </div>
        <div class="kpi ok">
          <div class="kpi-label">运行中</div>
          <div class="kpi-value">{{ upCount }}</div>
        </div>
        <div class="kpi down">
          <div class="kpi-label">不可用</div>
          <div class="kpi-value">{{ services.length - upCount }}</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">平均延迟</div>
          <div class="kpi-value">{{ avgLatency }}<span class="unit">ms</span></div>
        </div>
      </div>

      <div class="panel">
        <el-table v-loading="loading" :data="services" empty-text="暂无监控数据">
          <el-table-column label="服务名称" min-width="140">
            <template #default="{ row }">{{ displayName(row.name) }}</template>
          </el-table-column>
          <el-table-column label="实例状态" width="100">
            <template #default="{ row }">
              <span class="pill" :class="row.status === 'up' ? 'ok' : 'danger'">
                {{ row.status === 'up' ? '运行中' : '不可用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="探测地址" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.url || '—' }}</template>
          </el-table-column>
          <el-table-column label="最近探测耗时" width="130" align="right">
            <template #default="{ row }">{{ row.latencyMs != null ? `${row.latencyMs} ms` : '—' }}</template>
          </el-table-column>
          <el-table-column label="当前版本" width="100">
            <template #default>
              <span class="muted" title="健康接口未返回版本">—</span>
            </template>
          </el-table-column>
          <el-table-column label="CPU / 内存" width="110">
            <template #default>
              <span class="muted" title="健康接口未返回资源指标">—</span>
            </template>
          </el-table-column>
          <el-table-column label="异常信息" min-width="120">
            <template #default="{ row }">
              <span v-if="row.status !== 'up'" class="danger-text">探测失败或不可达</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
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
  if (!ups.length) return '—'
  const sum = ups.reduce((a, s) => a + Number(s.latencyMs || 0), 0)
  return String(Math.round(sum / ups.length))
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

function onAutoChange(v: string | number | boolean) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (v) timer = setInterval(load, 30000)
}

onMounted(load)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.saas-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.saas-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}
.page-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.icon-btn {
  width: 36px;
  padding: 8px;
}
.mb {
  margin-bottom: 12px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.kpi {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
}
.kpi-label {
  font-size: 12px;
  color: #667085;
}
.kpi-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 650;
  color: #182230;
}
.kpi.ok .kpi-value {
  color: #027a48;
}
.kpi.down .kpi-value {
  color: #b42318;
}
.unit {
  font-size: 12px;
  font-weight: 500;
  margin-left: 2px;
  color: #667085;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 16px 20px;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #027a48;
}
.pill.danger {
  background: #fef3f2;
  color: #b42318;
}
.muted {
  color: #667085;
}
.danger-text {
  color: #b42318;
  font-size: 13px;
}
@media (max-width: 1024px) {
  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
  .kpi-row {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 375px) {
  .kpi-row {
    grid-template-columns: 1fr;
  }
}
</style>
