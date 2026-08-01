<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">租户授权</h1>
          <p class="page-desc">
            选择租户后对比套餐默认权限与租户实际授权；保存将整体覆盖该租户 features
          </p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="panel select-panel">
        <el-select
          v-model="selectedId"
          filterable
          clearable
          placeholder="搜索或选择租户"
          style="width: min(360px, 100%)"
          @change="onSelectTenant"
        >
          <el-option
            v-for="t in tenants"
            :key="t.id"
            :label="`${t.name}（${statusLabel(t.status)}）`"
            :value="t.id"
          />
        </el-select>
      </div>

      <el-empty v-if="!selectedId" description="请先选择租户" :image-size="72" />

      <template v-else-if="current">
        <div class="stats-bar">
          <div class="stat-item">
            <span class="stat-label">当前套餐</span>
            <span class="stat-value sm">{{ current.packageName }}</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-label">套餐默认权限</span>
            <span class="stat-value">{{ summary.pkgDefault }}</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-label">额外开通</span>
            <span class="stat-value ok">{{ summary.extra }}</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-label">单独禁用</span>
            <span class="stat-value warn">{{ summary.disabled }}</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-label">最终有效</span>
            <span class="stat-value">{{ summary.final }}</span>
          </div>
        </div>

        <el-alert
          class="limit-alert"
          type="info"
          :closable="false"
          show-icon
          title="后端按完整 featureCodes 列表覆盖保存，不支持差异授权、授权有效期与变更日志接口。最终权限 = 当前勾选结果。"
        />

        <div class="panel">
          <div class="toolbar">
            <el-button size="small" @click="restorePackageDefault">恢复套餐默认值</el-button>
            <div class="spacer" />
            <el-button type="primary" :loading="saving" @click="save">保存授权</el-button>
          </div>

          <div v-for="group in featureGroups" :key="group.key" class="feat-group">
            <div class="feat-group-hd">{{ group.label }}</div>
            <el-table :data="rowsOf(group.codes)" size="small" class="feat-table" empty-text="无">
              <el-table-column label="功能" min-width="140">
                <template #default="{ row }">{{ row.label }}</template>
              </el-table-column>
              <el-table-column label="套餐默认" width="100" align="center">
                <template #default="{ row }">
                  <span class="flag" :class="row.inPackage ? 'on' : 'off'">
                    {{ row.inPackage ? '开启' : '关闭' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="租户级调整" width="110" align="center">
                <template #default="{ row }">
                  <span v-if="row.adjust === 'extra'" class="flag extra">额外开通</span>
                  <span v-else-if="row.adjust === 'disabled'" class="flag disabled">单独禁用</span>
                  <span v-else class="flag off">无调整</span>
                </template>
              </el-table-column>
              <el-table-column label="最终有效" width="120" align="center">
                <template #default="{ row }">
                  <el-switch v-model="draft[row.code]" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </template>
    </div>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getFeatureDict,
  listPackages,
  pageTenants,
  updateTenantFeatures,
} from '@/api/saas'
import { FEATURE_GROUPS, parseFeatures } from '@/config/features'

const STATUS: Record<string, string> = {
  active: '正常',
  degraded: '降级',
  frozen: '冻结',
  disabled: '停用',
}

const loading = ref(false)
const saving = ref(false)
const featureDict = ref<Record<string, string>>({})
const tenants = ref<any[]>([])
const packages = ref<any[]>([])
const selectedId = ref<number | undefined>()
const current = ref<any>(null)
const packageCodes = ref<string[]>([])
const draft = reactive<Record<string, boolean>>({})

const featureGroups = FEATURE_GROUPS

const pkgMap = computed(() => {
  const m: Record<number, any> = {}
  for (const p of packages.value) m[p.id] = p
  return m
})

const summary = computed(() => {
  const pkgSet = new Set(packageCodes.value)
  const finalCodes = Object.keys(draft).filter((c) => draft[c])
  const finalSet = new Set(finalCodes)
  let extra = 0
  let disabled = 0
  for (const c of finalCodes) if (!pkgSet.has(c)) extra++
  for (const c of packageCodes.value) if (!finalSet.has(c)) disabled++
  return {
    pkgDefault: packageCodes.value.length,
    extra,
    disabled,
    final: finalCodes.length,
  }
})

function statusLabel(s: string) {
  return STATUS[s] || s || '--'
}

function rowsOf(codes: string[]) {
  return codes
    .filter((c) => featureDict.value[c])
    .map((code) => {
      const inPackage = packageCodes.value.includes(code)
      const on = !!draft[code]
      let adjust: 'extra' | 'disabled' | 'none' = 'none'
      if (on && !inPackage) adjust = 'extra'
      if (!on && inPackage) adjust = 'disabled'
      return {
        code,
        label: featureDict.value[code] || code,
        inPackage,
        adjust,
      }
    })
}

function resetDraft(codes: string[]) {
  for (const key of Object.keys(draft)) delete draft[key]
  for (const code of Object.keys(featureDict.value)) {
    draft[code] = codes.includes(code)
  }
}

function onSelectTenant(id?: number) {
  if (!id) {
    current.value = null
    return
  }
  const t = tenants.value.find((x) => x.id === id)
  if (!t) return
  const pkg = pkgMap.value[t.packageId]
  packageCodes.value = parseFeatures(pkg?.featureCodes)
  current.value = {
    ...t,
    packageName: pkg?.name || (t.packageId ? `套餐#${t.packageId}` : '未绑定'),
  }
  resetDraft(parseFeatures(t.features || t.featureCodes))
}

async function restorePackageDefault() {
  try {
    await ElMessageBox.confirm(
      '将草稿恢复为当前套餐的默认权限，需再点击「保存授权」才会生效。',
      '恢复套餐默认值',
      { type: 'warning' },
    )
  } catch {
    return
  }
  resetDraft([...packageCodes.value])
  ElMessage.success('已恢复为套餐默认（未保存）')
}

async function save() {
  if (!selectedId.value) return
  try {
    await ElMessageBox.confirm('确认覆盖保存该租户的功能授权？', '保存授权', {
      type: 'warning',
      confirmButtonText: '确认保存',
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const featureCodes = Object.keys(draft).filter((c) => draft[c])
    await updateTenantFeatures(selectedId.value, featureCodes)
    ElMessage.success('授权已保存')
    await load(true)
  } finally {
    saving.value = false
  }
}

async function load(keepSelection = false) {
  loading.value = true
  const keepId = keepSelection ? selectedId.value : undefined
  try {
    const [dict, pkgs, tenantPage] = await Promise.all([
      getFeatureDict(),
      listPackages(),
      pageTenants({ page: 1, size: 500 }),
    ])
    featureDict.value = dict || {}
    packages.value = pkgs || []
    tenants.value = tenantPage?.records || []
    if (keepId) {
      selectedId.value = keepId
      onSelectTenant(keepId)
    }
  } catch {
    tenants.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
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
  gap: 16px;
  margin-bottom: 16px;
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
  max-width: 640px;
}
.icon-btn {
  width: 36px;
  padding: 8px;
  border-color: #e4e7ec;
  background: #fff;
  color: #667085;
}
.select-panel {
  margin-bottom: 12px;
  padding: 14px 16px;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 16px;
}
.stats-bar {
  display: flex;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: auto;
}
.stat-item {
  flex: 1;
  min-width: 110px;
  padding: 12px 14px;
}
.stat-label {
  display: block;
  font-size: 12px;
  color: #667085;
}
.stat-value {
  display: block;
  margin-top: 4px;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.stat-value.sm {
  font-size: 15px;
  font-weight: 600;
}
.stat-value.ok {
  color: #067647;
}
.stat-value.warn {
  color: #b54708;
}
.stat-divider {
  width: 1px;
  background: #e4e7ec;
}
.limit-alert {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
}
.spacer {
  flex: 1;
}
.feat-group {
  margin-bottom: 14px;
}
.feat-group-hd {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #182230;
}
.feat-table :deep(.el-table__header th) {
  background: #f9fafb;
  color: #667085;
}
.flag {
  font-size: 12px;
}
.flag.on {
  color: #067647;
}
.flag.off {
  color: #98a2b3;
}
.flag.extra {
  color: #175cd3;
}
.flag.disabled {
  color: #b54708;
}
@media (max-width: 768px) {
  .saas-page {
    padding: 16px 12px;
  }
}
</style>
