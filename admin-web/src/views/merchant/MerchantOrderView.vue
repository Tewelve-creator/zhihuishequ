<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>订单管理</h2>
        <p class="sub">本店用户订单 · 可更新订单状态 · 无法查看其他商家订单</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 140px" @change="load">
        <el-option label="待接单" value="PENDING_ACCEPT" />
        <el-option label="备货中" value="PREPARING" />
        <el-option label="配送中" value="DELIVERING" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="订单号/收货人" style="width: 200px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无订单">
      <el-table-column prop="orderNo" label="订单号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收货人" width="90" />
      <el-table-column prop="receiverPhone" label="电话" width="120" />
      <el-table-column label="实付" width="100" align="right">
        <template #default="{ row }">¥{{ Number(row.payAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="statusLabel" label="状态" width="100" />
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_ACCEPT'" link type="success" @click="act(row, 'ACCEPT')">接单</el-button>
          <el-button v-if="row.status === 'PREPARING'" link type="primary" @click="act(row, 'DELIVER')">配送</el-button>
          <el-button v-if="row.status === 'DELIVERING'" link type="success" @click="act(row, 'COMPLETE')">完成</el-button>
          <el-button
            v-if="['PENDING_ACCEPT', 'PREPARING'].includes(row.status)"
            link
            type="danger"
            @click="act(row, 'CANCEL')"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listMallOrders, updateMallOrderStatus } from '@/api/mall'

const loading = ref(false)
const list = ref<any[]>([])
const status = ref<string | undefined>()
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    list.value =
      (await listMallOrders({
        status: status.value,
        keyword: keyword.value || undefined,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function act(row: any, action: string) {
  await updateMallOrderStatus(row.id, action)
  ElMessage.success('已更新')
  await load()
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
