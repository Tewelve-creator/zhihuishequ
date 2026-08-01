<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>全小区风险工单</h2>
        <p class="sub">汇总各租户待处理/高紧急/超时报修工单 · 平台只读监管，处理仍由各小区物业执行</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-tag type="danger" effect="plain">在办 {{ summary.openOrders ?? 0 }}</el-tag>
      <el-tag type="warning" effect="plain">超时 {{ summary.overdueOrders ?? 0 }}</el-tag>
      <el-tag type="info" effect="plain">30日到期租户 {{ summary.expiringTenants ?? 0 }}</el-tag>
      <el-select v-model="tenantId" clearable filterable placeholder="归属小区" style="width: 160px" @change="load">
        <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="标题/工单号" style="width: 180px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无风险工单">
      <el-table-column prop="tenantName" label="归属小区" min-width="120" />
      <el-table-column prop="orderNo" label="工单号" width="90" />
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column label="风险" width="90">
        <template #default="{ row }">
          <el-tag :type="row.riskLevel === '紧急' || row.riskLevel === '超时' ? 'danger' : 'warning'" size="small">
            {{ row.riskLevel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageTenants } from '@/api/saas'
import { getRiskSummary, listRiskRepairs } from '@/api/platformHub'

const loading = ref(false)
const list = ref<any[]>([])
const summary = ref<any>({})
const tenants = ref<any[]>([])
const tenantId = ref<number | undefined>()
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const [s, rows] = await Promise.all([
      getRiskSummary(),
      listRiskRepairs({ tenantId: tenantId.value, keyword: keyword.value || undefined }),
    ])
    summary.value = s || {}
    list.value = rows || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await pageTenants({ page: 1, size: 200 })
    tenants.value = res?.records || []
  } catch {
    tenants.value = []
  }
  await load()
})
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
