<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>商超总订单</h2>
        <p class="sub">汇总本小区全部商户订单 · 支持按商户/状态筛选</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="shopId" clearable filterable placeholder="商户" style="width: 180px" @change="load">
        <el-option v-for="s in shops" :key="s.shopId" :label="s.name" :value="s.shopId" />
      </el-select>
      <el-select v-model="status" clearable placeholder="订单状态" style="width: 140px" @change="load">
        <el-option label="待接单" value="PENDING_ACCEPT" />
        <el-option label="备货中" value="PREPARING" />
        <el-option label="配送中" value="DELIVERING" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="订单号/收货人/商户" style="width: 200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-tag type="info" effect="plain">合计 {{ list.length }}</el-tag>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无订单">
      <el-table-column prop="shopName" label="商户" min-width="120" />
      <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收货人" width="90" />
      <el-table-column prop="receiverPhone" label="电话" width="120" />
      <el-table-column label="实付" width="100" align="right">
        <template #default="{ row }">¥{{ Number(row.payAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column prop="createTime" label="下单时间" width="170" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMallOrders, listMallShops } from '@/api/mall'

const loading = ref(false)
const list = ref<any[]>([])
const shops = ref<any[]>([])
const shopId = ref<number | undefined>()
const status = ref<string | undefined>()
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    list.value =
      (await listMallOrders({
        shopId: shopId.value,
        status: status.value,
        keyword: keyword.value || undefined,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    shops.value = (await listMallShops()) || []
  } catch {
    shops.value = []
  }
  await load()
})
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
