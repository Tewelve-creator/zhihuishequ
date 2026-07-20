<template>
  <div class="page-card">
    <div class="page-header">
      <h2>角色权限</h2>
      <el-button type="primary" @click="ElMessage.info('演示：新增角色')">新增角色</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="9">
        <el-table :data="roles" highlight-current-row @current-change="(r: Role) => (current = r)">
          <el-table-column prop="name" label="角色" />
          <el-table-column prop="code" label="编码" width="120" />
        </el-table>
      </el-col>
      <el-col :span="15">
        <h3 style="margin-top: 0">权限树 · {{ current?.name }}</h3>
        <el-tree :data="tree" show-checkbox default-expand-all node-key="id" :default-checked-keys="current?.perms" />
        <el-button type="primary" style="margin-top: 16px" @click="ElMessage.success('权限已保存')">保存权限</el-button>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface Role { name: string; code: string; perms: string[] }

const roles = ref<Role[]>([
  { name: '超级管理员', code: 'admin', perms: ['cockpit', 'basic', 'property', 'payment', 'invest', 'iot', 'report', 'system'] },
  { name: '物业经理', code: 'pm', perms: ['cockpit', 'basic', 'property', 'payment'] },
  { name: '收费员', code: 'cashier', perms: ['payment'] },
  { name: '维修员', code: 'worker', perms: ['property'] },
])
const current = ref(roles.value[0])
const tree = [
  { id: 'cockpit', label: '数据监控驾驶舱' },
  { id: 'basic', label: '基础信息管理', children: [{ id: 'basic-1', label: '园区/房屋/住户' }] },
  { id: 'property', label: '物业管理', children: [{ id: 'p-1', label: '工单/巡检/设备' }] },
  { id: 'payment', label: '缴费管理', children: [{ id: 'pay-1', label: '收银台/账单/仪表' }] },
  { id: 'invest', label: '招商管理' },
  { id: 'iot', label: '智慧物联' },
  { id: 'report', label: '统计报表' },
  { id: 'system', label: '系统管理' },
]
</script>
