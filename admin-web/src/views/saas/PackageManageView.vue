<template>
  <div class="pkg-page">
    <div class="pkg-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">套餐管理</h1>
          <p class="page-desc">配置套餐信息、价格额度与默认功能权限</p>
        </div>
        <div class="page-actions">
          <el-tooltip content="刷新列表" placement="top">
            <el-button
              class="icon-btn"
              :loading="loading"
              aria-label="刷新列表"
              @click="load"
            >
              <el-icon v-if="!loading"><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button type="primary" class="primary-btn" @click="openDialog()">
            <el-icon class="btn-ico"><Plus /></el-icon>
            新增套餐
          </el-button>
        </div>
      </header>

      <div class="stats-bar" aria-label="套餐概览">
        <div class="stat-item">
          <span class="stat-label">全部套餐</span>
          <span class="stat-value">{{ stats.total }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-label">启用</span>
          <span class="stat-value ok">{{ stats.enabled }}</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <span class="stat-label">停用</span>
          <span class="stat-value muted">{{ stats.disabled }}</span>
        </div>
      </div>

      <div class="panel">
        <div class="table-wrap">
          <el-table
            v-loading="loading"
            :data="list"
            class="pkg-table"
            empty-text="暂无套餐数据"
          >
            <el-table-column label="套餐" min-width="180">
              <template #default="{ row }">
                <div class="pkg-cell">
                  <div class="pkg-name-row">
                    <span class="pkg-name">{{ row.name || '--' }}</span>
                    <span v-if="tierOf(row)" class="tier-tag" :class="tierOf(row)!.tone">
                      {{ tierOf(row)!.label }}
                    </span>
                  </div>
                  <div class="pkg-code">{{ row.code || '--' }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="月付" width="100" align="right">
              <template #default="{ row }">
                <span class="price">{{ formatMoney(row.priceMonth) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="季付" width="100" align="right">
              <template #default="{ row }">
                <span class="price">{{ formatMoney(row.priceQuarter) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="年付" width="120" align="right">
              <template #default="{ row }">
                <div class="year-cell">
                  <span class="price">{{ formatMoney(row.priceYear) }}</span>
                  <span v-if="yearMonthlyHint(row.priceYear)" class="year-hint">
                    {{ yearMonthlyHint(row.priceYear) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="AI 额度" width="120" align="right">
              <template #default="{ row }">
                <span class="quota">{{ formatQuota(row.aiQuota) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="套餐说明"
              min-width="140"
              show-overflow-tooltip
              class-name="col-remark"
            >
              <template #default="{ row }">
                <span class="remark">{{ row.remark || '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <span class="status-pill" :class="row.status === 1 ? 'tone-ok' : 'tone-off'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="112" fixed="right">
              <template #default="{ row }">
                <div class="ops">
                  <el-button
                    link
                    class="ops-link"
                    aria-label="编辑套餐"
                    @click="openDialog(row)"
                  >
                    编辑
                  </el-button>
                  <el-tooltip content="更多操作" placement="top">
                    <span class="ops-more-wrap">
                      <el-dropdown trigger="click" @command="(cmd: string) => onMore(row, cmd)">
                        <el-button link class="ops-more" aria-label="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item
                              :command="row.status === 1 ? 'disable' : 'enable'"
                            >
                              {{ row.status === 1 ? '停用套餐' : '启用套餐' }}
                            </el-dropdown-item>
                            <el-dropdown-item divided command="delete" class="danger-item">
                              删除套餐
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </span>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 手机端列表 -->
        <div v-loading="loading" class="mobile-list">
          <div v-if="!loading && !list.length" class="mobile-empty">暂无套餐数据</div>
          <article v-for="row in list" :key="row.id" class="mobile-card">
            <div class="mobile-top">
              <div class="mobile-title">
                <div class="pkg-name-row">
                  <span class="pkg-name">{{ row.name || '--' }}</span>
                  <span v-if="tierOf(row)" class="tier-tag" :class="tierOf(row)!.tone">
                    {{ tierOf(row)!.label }}
                  </span>
                </div>
                <div class="pkg-code">{{ row.code || '--' }}</div>
              </div>
              <span class="status-pill" :class="row.status === 1 ? 'tone-ok' : 'tone-off'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </div>
            <div class="mobile-meta">
              <div><span>月付</span>{{ formatMoney(row.priceMonth) }}</div>
              <div><span>年付</span>{{ formatMoney(row.priceYear) }}</div>
              <div><span>AI</span>{{ formatQuota(row.aiQuota) }}</div>
            </div>
            <div class="mobile-ops">
              <el-button size="small" @click="openDialog(row)">编辑</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => onMore(row, cmd)">
                <el-button size="small" aria-label="更多操作">
                  更多
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="row.status === 1 ? 'disable' : 'enable'">
                      {{ row.status === 1 ? '停用套餐' : '启用套餐' }}
                    </el-dropdown-item>
                    <el-dropdown-item divided command="delete" class="danger-item">
                      删除套餐
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </article>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="visible"
      :title="editingId ? '编辑套餐' : '新增套餐'"
      width="720px"
      destroy-on-close
      class="pkg-dialog"
      @closed="dialogTab = 'info'"
    >
      <el-tabs v-model="dialogTab" class="pkg-tabs">
        <el-tab-pane label="套餐信息" name="info" />
        <el-tab-pane label="价格与额度" name="price" />
        <el-tab-pane label="默认功能权限" name="features" />
      </el-tabs>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        require-asterisk-position="right"
        class="pkg-form"
      >
        <div v-show="dialogTab === 'info'" class="form-section">
          <el-form-item label="套餐名称" prop="name">
            <el-input v-model="form.name" maxlength="64" placeholder="如 专业版" />
          </el-form-item>
          <el-form-item label="套餐编码" prop="code">
            <el-input v-model="form.code" maxlength="32" placeholder="如 PRO" />
          </el-form-item>
          <el-form-item label="套餐说明">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="200"
              placeholder="面向运营的套餐说明（选填）"
            />
          </el-form-item>
          <div class="field-row">
            <el-form-item label="启用状态" class="field-half">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="展示顺序" class="field-half">
              <el-input-number v-model="form.sortNo" :min="0" style="width: 100%" />
            </el-form-item>
          </div>
        </div>

        <div v-show="dialogTab === 'price'" class="form-section">
          <div class="field-row triple">
            <el-form-item label="月付价格" prop="priceMonth">
              <div class="money-input">
                <span class="money-prefix">¥</span>
                <el-input-number
                  v-model="form.priceMonth"
                  :min="0"
                  :precision="2"
                  controls-position="right"
                  style="width: 100%"
                />
              </div>
            </el-form-item>
            <el-form-item label="季付价格" prop="priceQuarter">
              <div class="money-input">
                <span class="money-prefix">¥</span>
                <el-input-number
                  v-model="form.priceQuarter"
                  :min="0"
                  :precision="2"
                  controls-position="right"
                  style="width: 100%"
                />
              </div>
            </el-form-item>
            <el-form-item label="年付价格" prop="priceYear">
              <div class="money-input">
                <span class="money-prefix">¥</span>
                <el-input-number
                  v-model="form.priceYear"
                  :min="0"
                  :precision="2"
                  controls-position="right"
                  style="width: 100%"
                />
              </div>
            </el-form-item>
          </div>
          <p v-if="yearMonthlyHint(form.priceYear)" class="form-hint">
            按年付折算：{{ yearMonthlyHint(form.priceYear) }}
          </p>
          <el-form-item label="AI 调用额度" prop="aiQuota">
            <el-input-number
              v-model="form.aiQuota"
              :min="0"
              controls-position="right"
              style="width: 240px"
            />
            <span class="suffix-text">次/月</span>
          </el-form-item>
        </div>

        <div v-show="dialogTab === 'features'" class="form-section">
          <div class="feat-toolbar">
            <div class="feat-actions">
              <el-button size="small" @click="selectAllFeatures">全选</el-button>
              <el-button size="small" @click="clearFeatures">清空</el-button>
            </div>
            <el-select
              v-model="copyFromId"
              clearable
              filterable
              placeholder="复制其他套餐权限"
              style="width: 220px"
              @change="onCopyFromPackage"
            >
              <el-option
                v-for="p in copyPackageOptions"
                :key="p.id"
                :label="`${p.name}（${featureCount(p)}）`"
                :value="p.id"
              />
            </el-select>
          </div>
          <p class="feat-summary">已选 {{ form.featureCodes.length }} 项功能权限</p>
          <div
            v-for="group in featureGroups"
            :key="group.key"
            class="feat-group"
          >
            <div class="feat-group-hd">
              <span>{{ group.label }}</span>
              <el-button link type="primary" @click="toggleGroup(group.codes)">
                {{ isGroupAllSelected(group.codes) ? '取消本组' : '全选本组' }}
              </el-button>
            </div>
            <el-checkbox-group v-model="form.featureCodes" class="feature-group">
              <el-checkbox
                v-for="code in group.codes"
                :key="code"
                :value="code"
                :disabled="!featureDict[code]"
              >
                {{ featureDict[code] || code }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div v-if="ungroupedCodes.length" class="feat-group">
            <div class="feat-group-hd"><span>其他</span></div>
            <el-checkbox-group v-model="form.featureCodes" class="feature-group">
              <el-checkbox v-for="code in ungroupedCodes" :key="code" :value="code">
                {{ featureDict[code] || code }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存套餐</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, MoreFilled, Plus, Refresh } from '@element-plus/icons-vue'
import { listPackages, createPackage, updatePackage, deletePackage, getFeatureDict } from '@/api/saas'
import { FEATURE_GROUPS, parseFeatures } from '@/config/features'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const featureDict = ref<Record<string, string>>({})
const visible = ref(false)
const dialogTab = ref('info')
const editingId = ref<number | null>(null)
const copyFromId = ref<number | undefined>()
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  code: '',
  priceMonth: 0,
  priceQuarter: 0,
  priceYear: 0,
  aiQuota: 0,
  status: 1,
  sortNo: 0,
  remark: '',
  featureCodes: [] as string[],
})

const featureGroups = FEATURE_GROUPS

const groupedCodeSet = computed(() => new Set(FEATURE_GROUPS.flatMap((g) => g.codes)))

const ungroupedCodes = computed(() =>
  Object.keys(featureDict.value).filter((c) => !groupedCodeSet.value.has(c as any)),
)

const copyPackageOptions = computed(() =>
  list.value.filter((p) => p.id !== editingId.value),
)

const rules: FormRules = {
  name: [{ required: true, message: '请填写套餐名称', trigger: 'blur' }],
  code: [{ required: true, message: '请填写套餐编码', trigger: 'blur' }],
}

const stats = computed(() => {
  const total = list.value.length
  const enabled = list.value.filter((x) => Number(x.status) === 1).length
  return {
    total,
    enabled,
    disabled: Math.max(0, total - enabled),
  }
})

function formatMoney(v: unknown) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  const abs = Math.abs(n)
  const formatted =
    abs % 1 === 0
      ? abs.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
      : abs.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
  return `¥${formatted}`
}

function yearMonthlyHint(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n) || n <= 0) return ''
  const monthly = Math.round(n / 12)
  return `约 ¥${monthly.toLocaleString('zh-CN')}/月`
}

function formatQuota(v: unknown) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return `${n.toLocaleString('zh-CN')} 次/月`
}

type Tier = { label: string; tone: string }

function tierOf(row: any): Tier | null {
  const raw = `${row?.name || ''} ${row?.code || ''}`.toLowerCase()
  if (/免费|free/.test(raw)) return { label: '免费版', tone: 'tier-free' }
  if (/旗舰|旗舰版|flagship|ultra|enterprise/.test(raw)) return { label: '旗舰版', tone: 'tier-flagship' }
  if (/专业|专业版|pro|premium/.test(raw)) return { label: '专业版', tone: 'tier-pro' }
  if (/基础|基础版|basic|standard|标准/.test(raw)) return { label: '基础版', tone: 'tier-basic' }
  return null
}

async function loadFeatures() {
  try {
    featureDict.value = (await getFeatureDict()) || {}
  } catch {
    featureDict.value = {}
  }
}

async function load() {
  loading.value = true
  try {
    list.value = (await listPackages()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function featureCount(row: any) {
  return parseFeatures(row?.featureCodes).length
}

function selectAllFeatures() {
  form.featureCodes = Object.keys(featureDict.value)
}

function clearFeatures() {
  form.featureCodes = []
}

function isGroupAllSelected(codes: string[]) {
  const available = codes.filter((c) => featureDict.value[c])
  return available.length > 0 && available.every((c) => form.featureCodes.includes(c))
}

function toggleGroup(codes: string[]) {
  const available = codes.filter((c) => featureDict.value[c])
  if (isGroupAllSelected(available)) {
    form.featureCodes = form.featureCodes.filter((c) => !available.includes(c))
  } else {
    const set = new Set([...form.featureCodes, ...available])
    form.featureCodes = [...set]
  }
}

function onCopyFromPackage(id?: number) {
  if (!id) return
  const pkg = list.value.find((p) => p.id === id)
  if (!pkg) return
  form.featureCodes = [...parseFeatures(pkg.featureCodes)]
  ElMessage.success(`已复制「${pkg.name}」的权限配置`)
  copyFromId.value = undefined
}

function openDialog(row?: any) {
  editingId.value = row?.id ?? null
  dialogTab.value = 'info'
  copyFromId.value = undefined
  form.name = row?.name || ''
  form.code = row?.code || ''
  form.priceMonth = Number(row?.priceMonth ?? 0)
  form.priceQuarter = Number(row?.priceQuarter ?? 0)
  form.priceYear = Number(row?.priceYear ?? 0)
  form.aiQuota = Number(row?.aiQuota ?? 0)
  form.status = row?.status ?? 1
  form.sortNo = Number(row?.sortNo ?? 0)
  form.remark = row?.remark || ''
  form.featureCodes = [...parseFeatures(row?.featureCodes)]
  visible.value = true
}

async function save() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) {
    dialogTab.value = 'info'
    if (!form.name.trim() || !form.code.trim()) {
      ElMessage.error('请填写名称与编码')
    }
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      code: form.code.trim(),
    }
    if (editingId.value) {
      await updatePackage({ id: editingId.value, ...payload })
    } else {
      await createPackage(payload)
    }
    ElMessage.success('套餐已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function onMore(row: any, cmd: string) {
  if (cmd === 'delete') {
    onDelete(row)
    return
  }
  if (cmd === 'enable' || cmd === 'disable') {
    onToggleStatus(row, cmd === 'enable' ? 1 : 0)
  }
}

async function onToggleStatus(row: any, status: number) {
  const label = status === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认将套餐「${row.name}」设为${label}？`, '修改状态', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await updatePackage({
      id: row.id,
      name: row.name,
      code: row.code,
      priceMonth: row.priceMonth,
      priceQuarter: row.priceQuarter,
      priceYear: row.priceYear,
      aiQuota: row.aiQuota,
      status,
      sortNo: row.sortNo,
      remark: row.remark,
      featureCodes: row.featureCodes || [],
    })
    ElMessage.success(`已${label}`)
    await load()
  } catch {
    /* interceptor */
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除套餐「${row.name}」？删除后不可恢复。若仍有租户使用该套餐，将按后端规则拦截。`,
      '删除套餐',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await deletePackage(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* handled by interceptor — 展示后端错误 */
  }
}

onMounted(async () => {
  await loadFeatures()
  await load()
})
</script>

<style scoped>
.pkg-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}

.pkg-shell {
  max-width: 1440px;
  margin: 0 auto;
}

.page-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 32px;
  letter-spacing: 0;
  color: #182230;
}

.page-desc {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 22px;
  color: #667085;
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.icon-btn,
.primary-btn {
  height: 36px;
  border-radius: 6px;
}

.icon-btn {
  width: 36px;
  padding: 0;
  border-color: #dfe3e8;
  color: #344054;
}

.primary-btn {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
}

.btn-ico {
  margin-right: 4px;
}

.stats-bar {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 0 20px;
}

.stat-item:first-child {
  padding-left: 4px;
}

.stat-label {
  font-size: 13px;
  color: #667085;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #182230;
  font-variant-numeric: tabular-nums;
}

.stat-value.ok {
  color: #067647;
}

.stat-value.muted {
  color: #667085;
}

.stat-divider {
  width: 1px;
  height: 28px;
  background: #e4e7ec;
}

.panel {
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  padding: 24px;
  box-sizing: border-box;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.pkg-table {
  width: 100%;
  --el-table-border-color: #e4e7ec;
  --el-table-header-bg-color: #f8fafc;
  --el-table-row-hover-bg-color: #f5f6f8;
  --el-table-text-color: #344054;
  --el-table-header-text-color: #667085;
}

.pkg-table :deep(.el-table__header th) {
  font-weight: 500;
  font-size: 13px;
  height: 44px;
}

.pkg-table :deep(.el-table__row) {
  height: 62px;
}

.pkg-table :deep(.el-table__cell) {
  padding: 12px 0;
  font-size: 14px;
}

.pkg-table :deep(td.el-table__cell) {
  border-bottom-color: #eef1f4;
}

.pkg-cell {
  min-width: 0;
}

.pkg-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pkg-name {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: #182230;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pkg-code {
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: #98a2b3;
}

.tier-tag {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.tier-free {
  background: #f2f4f7;
  color: #667085;
}
.tier-basic {
  background: #eff6ff;
  color: #175cd3;
}
.tier-pro {
  background: #f0fdfa;
  color: #0f766e;
}
.tier-flagship {
  background: #fffaeb;
  color: #b54708;
}

.price {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  color: #182230;
}

.year-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.year-hint {
  font-size: 11px;
  line-height: 16px;
  color: #98a2b3;
  font-variant-numeric: tabular-nums;
}

.quota {
  font-variant-numeric: tabular-nums;
  color: #344054;
}

.remark {
  color: #667085;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.status-pill.tone-ok {
  background: #ecfdf3;
  color: #067647;
}
.status-pill.tone-off {
  background: #f2f4f7;
  color: #667085;
}

.ops {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.ops-link {
  color: #2563eb !important;
  font-weight: 500;
  padding: 0 4px !important;
}

.ops-more-wrap {
  display: inline-flex;
}

.ops-more {
  color: #667085 !important;
  padding: 0 4px !important;
}

:deep(.danger-item) {
  color: #b42318 !important;
}

.mobile-list {
  display: none;
}

/* 弹窗表单 */
.pkg-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.pkg-form :deep(.el-form-item__label) {
  margin-bottom: 6px !important;
  color: #182230;
  font-weight: 500;
}
.pkg-form :deep(.el-input__wrapper),
.pkg-form :deep(.el-textarea__inner) {
  border-radius: 6px;
}

.form-section + .form-section {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #eef1f4;
}

.form-section-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #182230;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.field-row.triple {
  grid-template-columns: 1fr 1fr 1fr;
}

.form-hint {
  margin: -4px 0 8px;
  font-size: 12px;
  color: #667085;
}

.money-input {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.money-prefix {
  flex-shrink: 0;
  font-size: 14px;
  color: #667085;
  font-weight: 500;
}

.suffix-text {
  margin-left: 8px;
  font-size: 13px;
  color: #667085;
}

.feature-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.pkg-tabs {
  margin: -4px 0 12px;
}
.pkg-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.pkg-tabs :deep(.el-tabs__item) {
  color: #667085;
  font-weight: 500;
}
.pkg-tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.pkg-tabs :deep(.el-tabs__active-bar),
.pkg-tabs :deep(.el-tabs__ink-bar) {
  background-color: #2563eb;
}

.feat-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.feat-actions {
  display: flex;
  gap: 8px;
}
.feat-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: #667085;
}
.feat-group {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  background: #fafbfc;
}
.feat-group-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #182230;
}

@media (max-width: 1024px) {
  .pkg-table :deep(.col-remark) {
    display: none;
  }
  .year-hint {
    display: none;
  }
}

@media (max-width: 768px) {
  .pkg-page {
    padding: 16px 12px 24px;
  }
  .page-hd {
    flex-direction: column;
    align-items: stretch;
  }
  .stats-bar {
    flex-wrap: wrap;
    gap: 8px 0;
  }
  .stat-divider {
    display: none;
  }
  .stat-item {
    flex: 1 1 30%;
    padding: 4px 8px;
  }
  .panel {
    padding: 16px;
  }
  .table-wrap {
    display: none;
  }
  .mobile-list {
    display: block;
  }
  .mobile-empty {
    padding: 40px 16px;
    text-align: center;
    color: #98a2b3;
  }
  .mobile-card {
    border: 1px solid #eef1f4;
    border-radius: 8px;
    padding: 14px;
    margin-bottom: 10px;
  }
  .mobile-top {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }
  .mobile-title {
    min-width: 0;
  }
  .mobile-meta {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px 12px;
    font-size: 13px;
    color: #344054;
    margin-bottom: 12px;
  }
  .mobile-meta span {
    display: inline-block;
    width: 36px;
    color: #667085;
  }
  .mobile-ops {
    display: flex;
    gap: 8px;
  }
  .field-row,
  .field-row.triple {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 375px) {
  .page-title {
    font-size: 22px;
  }
}
</style>
