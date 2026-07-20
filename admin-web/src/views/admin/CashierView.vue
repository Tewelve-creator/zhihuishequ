<template>
  <div class="page-card">
    <div class="page-header">
      <h2>收银台</h2>
    </div>
    <el-row :gutter="16">
      <el-col :span="10">
        <el-form label-width="90px">
          <el-form-item label="房屋/车牌">
            <el-input v-model="keyword" placeholder="输入房号或车牌查询账单" clearable>
              <template #append><el-button @click="search">查询</el-button></template>
            </el-input>
          </el-form-item>
          <el-form-item label="缴费人"><el-input v-model="payer" /></el-form-item>
          <el-form-item label="支付方式">
            <el-radio-group v-model="method">
              <el-radio-button value="现金">现金</el-radio-button>
              <el-radio-button value="微信">微信</el-radio-button>
              <el-radio-button value="支付宝">支付宝</el-radio-button>
              <el-radio-button value="银行卡">银行卡</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="应收合计">
            <span class="amount">¥ {{ total.toFixed(2) }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" @click="pay">确认收款</el-button>
            <el-button size="large" @click="print">打印小票</el-button>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="14">
        <el-table :data="bills" border @selection-change="onSelect">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="item" label="收费项" />
          <el-table-column prop="period" label="账期" width="100" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

const keyword = ref('1栋502')
const payer = ref('王芳')
const method = ref('微信')
const selected = ref<{ amount: number }[]>([])
const bills = ref([
  { item: '物业费', period: '2026-07', amount: 274.4, status: '未缴' },
  { item: '垃圾处理费', period: '2026-07', amount: 10, status: '未缴' },
  { item: '停车费', period: '2026-07', amount: 200, status: '未缴' },
])

const total = computed(() => selected.value.reduce((s, i) => s + i.amount, 0))

function search() {
  ElMessage.success(`已查询：${keyword.value}`)
}
function onSelect(rows: { amount: number }[]) {
  selected.value = rows
}
function pay() {
  if (!selected.value.length) return ElMessage.warning('请先勾选账单')
  ElMessage.success(`已通过${method.value}收款 ¥${total.value.toFixed(2)}`)
}
function print() {
  ElMessage.info('演示：调用小票打印')
}
</script>

<style scoped>
.amount { font-size: 28px; font-weight: 700; color: #f5222d; }
</style>
