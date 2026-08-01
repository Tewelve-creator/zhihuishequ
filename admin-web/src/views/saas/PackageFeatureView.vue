<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>套餐功能权限</h2>
        <p class="sub">配置各套餐默认开通的功能权限码，租户新建/换套餐时继承；单独授权请用「租户功能授权」</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon title="勾选功能后点击行内「保存」写入该套餐的 featureCodes" style="margin-bottom: 12px" />

    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无套餐" border>
      <el-table-column prop="name" label="套餐" width="140" fixed />
      <el-table-column v-for="(label, code) in featureDict" :key="code" :label="label" min-width="100" align="center">
        <template #default="{ row }">
          <el-checkbox v-model="row.features[code]" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :loading="row.saving" @click="saveRow(row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPackages, getFeatureDict, updatePackage } from '@/api/saas'

interface MatrixRow {
  id: number
  name: string
  features: Record<string, boolean>
  saving: boolean
}

const loading = ref(false)
const featureDict = ref<Record<string, string>>({})
const rows = ref<MatrixRow[]>([])

function buildFeatures(codes: string[]) {
  const map: Record<string, boolean> = {}
  for (const code of Object.keys(featureDict.value)) {
    map[code] = codes.includes(code)
  }
  return map
}

async function load() {
  loading.value = true
  try {
    const [dict, pkgs] = await Promise.all([getFeatureDict(), listPackages()])
    featureDict.value = dict || {}
    rows.value = (pkgs || []).map((p: any) => ({
      id: p.id,
      name: p.name,
      features: buildFeatures(p.featureCodes || []),
      saving: false,
    }))
  } catch {
    featureDict.value = {}
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function saveRow(row: MatrixRow) {
  row.saving = true
  try {
    const featureCodes = Object.entries(row.features)
      .filter(([, v]) => v)
      .map(([k]) => k)
    await updatePackage({ id: row.id, featureCodes })
    ElMessage.success(`「${row.name}」功能权限已保存`)
  } finally {
    row.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
</style>
