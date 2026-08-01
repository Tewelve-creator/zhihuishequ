<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>AI接口调用监控</h2>
        <p class="sub">统计 DeepSeek/兼容接口各租户调用量、成功率与耗时（数据来自 ai_call_log）</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <span>统计窗口</span>
      <el-select v-model="days" style="width: 120px" @change="loadStats">
        <el-option label="近 7 天" :value="7" />
        <el-option label="近 15 天" :value="15" />
        <el-option label="近 30 天" :value="30" />
      </el-select>
      <el-tag type="danger" effect="plain">今日 {{ stats.todayCalls || 0 }}</el-tag>
      <el-tag type="warning" effect="plain">窗口合计 {{ stats.totalCalls || 0 }}</el-tag>
      <el-tag type="success" effect="plain">成功 {{ stats.successCalls || 0 }}</el-tag>
      <el-tag type="info" effect="plain">失败 {{ stats.failCalls || 0 }}</el-tag>
    </div>

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="12">
        <div class="panel">
          <h3>按租户调用量</h3>
          <el-table :data="stats.byTenant || []" size="small" stripe max-height="280" empty-text="暂无调用">
            <el-table-column prop="tenantName" label="租户" min-width="120" />
            <el-table-column prop="total" label="调用" width="80" />
            <el-table-column prop="successCnt" label="成功" width="80" />
            <el-table-column prop="avgCostMs" label="均耗时ms" width="100" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel">
          <h3>按场景分布</h3>
          <el-table :data="stats.byScene || []" size="small" stripe max-height="280" empty-text="暂无数据">
            <el-table-column prop="scene" label="场景" />
            <el-table-column prop="total" label="调用" width="80" />
            <el-table-column prop="successCnt" label="成功" width="80" />
          </el-table>
        </div>
      </el-col>
    </el-row>

    <div class="filter-bar">
      <el-select v-model="tenantId" clearable placeholder="租户" style="width: 180px" @change="loadLogs">
        <el-option
          v-for="t in tenantOptions"
          :key="t.tenantId"
          :label="t.tenantName"
          :value="t.tenantId"
        />
      </el-select>
      <el-select v-model="scene" clearable placeholder="场景" style="width: 160px" @change="loadLogs">
        <el-option v-for="s in sceneOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" :loading="logLoading" @click="loadLogs">查询明细</el-button>
    </div>

    <el-table v-loading="logLoading" :data="logs" stripe empty-text="暂无调用明细">
      <el-table-column prop="tenantName" label="租户" width="140" />
      <el-table-column prop="scene" label="场景" width="120" />
      <el-table-column prop="providerLabel" label="接口" width="140" />
      <el-table-column prop="successLabel" label="结果" width="80" />
      <el-table-column prop="costMs" label="耗时ms" width="90" />
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        background
        layout="total, prev, pager, next"
        :total="total"
        @current-change="loadLogs"
        @size-change="loadLogs"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAiCallStats, pageAiCalls } from '@/api/saas'

const loading = ref(false)
const logLoading = ref(false)
const days = ref(7)
const stats = ref<any>({})
const logs = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const tenantId = ref<number | undefined>()
const scene = ref('')

const tenantOptions = computed(() => stats.value.byTenant || [])
const sceneOptions = computed(() =>
  (stats.value.byScene || []).map((s: any) => s.scene).filter(Boolean),
)

async function loadStats() {
  loading.value = true
  try {
    stats.value = (await getAiCallStats(days.value)) || {}
  } catch {
    stats.value = {}
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await pageAiCalls({
      tenantId: tenantId.value,
      scene: scene.value || undefined,
      page: page.value,
      size: size.value,
    })
    logs.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch {
    logs.value = []
    total.value = 0
  } finally {
    logLoading.value = false
  }
}

async function reload() {
  await loadStats()
  await loadLogs()
}

onMounted(reload)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.panel {
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  min-height: 300px;
}
.panel h3 { margin: 0 0 10px; font-size: 14px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
