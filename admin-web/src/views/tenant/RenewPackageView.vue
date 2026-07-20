<template>
  <div class="page-card">
    <div class="page-header">
      <h2>续费套餐</h2>
    </div>
    <el-row :gutter="16">
      <el-col :span="8" v-for="pkg in packages" :key="pkg.id">
        <el-card shadow="hover" class="pkg-card">
          <h3>{{ pkg.name }}</h3>
          <p class="price">¥{{ pkg.price }}<span>/{{ pkg.unit }}</span></p>
          <ul>
            <li v-for="f in pkg.features" :key="f">{{ f }}</li>
          </ul>
          <el-button type="primary" style="width: 100%" @click="renew(pkg)">立即续费</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const packages = ref([
  {
    id: 1,
    name: '基础版',
    price: 2999,
    unit: '年',
    features: ['用户管理', '工单管理', '公告活动', '基础数据看板'],
  },
  {
    id: 2,
    name: '专业版',
    price: 5999,
    unit: '年',
    features: ['含基础版全部能力', '社区数据大屏', 'Excel 导出', '优先支持'],
  },
  {
    id: 3,
    name: '旗舰版',
    price: 9999,
    unit: '年',
    features: ['含专业版全部能力', '多小区管理', '定制报表', '专属顾问'],
  },
])

async function renew(pkg: (typeof packages.value)[0]) {
  await ElMessageBox.confirm(`确认续费「${pkg.name}」？金额 ¥${pkg.price}`, '续费确认')
  // POST /api/tenant/packages/renew
  ElMessage.success('续费订单已提交')
}
</script>

<style scoped>
.pkg-card {
  margin-bottom: 16px;
}
.pkg-card h3 {
  margin: 0 0 8px;
}
.price {
  font-size: 28px;
  font-weight: 700;
  color: #1677ff;
  margin: 0 0 12px;
}
.price span {
  font-size: 14px;
  color: #999;
  font-weight: 400;
}
ul {
  padding-left: 18px;
  color: #666;
  min-height: 110px;
}
</style>
