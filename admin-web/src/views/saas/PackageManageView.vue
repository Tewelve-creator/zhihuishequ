<template>
  <div class="page-card">
    <div class="page-header">
      <h2>租赁套餐配置</h2>
      <el-button type="primary" @click="openDialog()">新增套餐</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="name" label="套餐名称" />
      <el-table-column prop="price" label="价格(元/年)" width="140" />
      <el-table-column prop="maxCommunity" label="最大小区数" width="120" />
      <el-table-column prop="enabled" label="启用" width="100">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑套餐' : '新增套餐'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" /></el-form-item>
        <el-form-item label="最大小区数"><el-input-number v-model="form.maxCommunity" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface Pkg {
  id: number
  name: string
  price: number
  maxCommunity: number
  enabled: boolean
}

const list = ref<Pkg[]>([
  { id: 1, name: '基础版', price: 2999, maxCommunity: 1, enabled: true },
  { id: 2, name: '专业版', price: 5999, maxCommunity: 5, enabled: true },
  { id: 3, name: '旗舰版', price: 9999, maxCommunity: 50, enabled: true },
])

const visible = ref(false)
const editing = ref<Pkg | null>(null)
const form = reactive({ name: '', price: 2999, maxCommunity: 1 })

function openDialog(row?: Pkg) {
  editing.value = row || null
  form.name = row?.name || ''
  form.price = row?.price ?? 2999
  form.maxCommunity = row?.maxCommunity ?? 1
  visible.value = true
}

function save() {
  if (editing.value) {
    Object.assign(editing.value, { ...form })
  } else {
    list.value.push({ id: Date.now(), ...form, enabled: true })
  }
  visible.value = false
  ElMessage.success('套餐已保存')
}
</script>
