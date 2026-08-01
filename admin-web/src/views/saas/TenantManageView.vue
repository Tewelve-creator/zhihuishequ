<template>
  <div class="tenant-page">
    <div class="tenant-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">SaaS 租户管理</h1>
          <p class="page-desc">管理租户资料、套餐状态及管理员账号</p>
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
          <el-button class="ghost-btn" @click="$router.push('/admin/saas/onboard')">
            开户向导
          </el-button>
          <el-button type="primary" class="primary-btn" @click="openDialog()">
            快速开通
          </el-button>
        </div>
      </header>

      <div class="panel">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="search-input"
            clearable
            placeholder="搜索租户名称或联系人"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix>
              <el-icon class="search-ico"><Search /></el-icon>
            </template>
            <template #append>
              <el-button :loading="loading" @click="onSearch">查询</el-button>
            </template>
          </el-input>
        </div>

        <!-- 桌面 / 平板表格 -->
        <div class="table-wrap">
          <el-table
            v-loading="loading"
            :data="list"
            class="tenant-table"
            empty-text="暂无租户数据"
            row-class-name="tenant-row"
          >
            <el-table-column label="租户名称" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="name-cell">{{ row.name || '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="contactName" label="联系人" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.contactName || '--' }}</template>
            </el-table-column>
            <el-table-column label="联系电话" width="128">
              <template #default="{ row }">{{ row.contactPhone || '--' }}</template>
            </el-table-column>
            <el-table-column label="套餐" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ pkgName(row.packageId) }}</template>
            </el-table-column>
            <el-table-column label="到期时间" width="150">
              <template #default="{ row }">{{ formatDateTime(row.expireTime) }}</template>
            </el-table-column>
            <el-table-column label="AI额度" width="100" align="right">
              <template #default="{ row }">{{ formatQuota(row.aiQuota) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <span class="status-pill" :class="statusTone(row.status)">
                  {{ statusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150" class-name="col-create-time">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="168" fixed="right" class-name="col-ops">
              <template #default="{ row }">
                <div class="ops">
                  <el-button
                    link
                    class="ops-link"
                    aria-label="编辑租户"
                    @click="openDialog(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    link
                    class="ops-link"
                    aria-label="管理账号"
                    @click="openAdminDialog(row)"
                  >
                    管理账号
                  </el-button>
                  <el-tooltip content="更多操作" placement="top">
                    <span class="ops-more-wrap">
                      <el-dropdown trigger="click" @command="(cmd: string) => onMoreCommand(row, cmd)">
                        <el-button
                          link
                          class="ops-more"
                          aria-label="更多操作"
                        >
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item
                              v-for="s in STATUS_OPTS"
                              :key="s.value"
                              :command="`status:${s.value}`"
                              :disabled="row.status === s.value"
                            >
                              设为{{ s.label }}
                            </el-dropdown-item>
                            <el-dropdown-item divided command="delete" class="danger-item">
                              删除租户
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

        <!-- 手机端卡片列表 -->
        <div v-loading="loading" class="mobile-list">
          <div v-if="!loading && !list.length" class="mobile-empty">暂无租户数据</div>
          <article v-for="row in list" :key="row.id" class="mobile-card">
            <div class="mobile-top">
              <div class="mobile-name" :title="row.name">{{ row.name || '--' }}</div>
              <span class="status-pill" :class="statusTone(row.status)">
                {{ statusLabel(row.status) }}
              </span>
            </div>
            <div class="mobile-meta">
              <div><span>套餐</span>{{ pkgName(row.packageId) }}</div>
              <div><span>到期</span>{{ formatDateTime(row.expireTime) }}</div>
              <div><span>联系人</span>{{ row.contactName || '--' }} · {{ row.contactPhone || '--' }}</div>
            </div>
            <div class="mobile-ops">
              <el-button size="small" @click="openDialog(row)">编辑</el-button>
              <el-button size="small" @click="openAdminDialog(row)">管理账号</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => onMoreCommand(row, cmd)">
                <el-button size="small" aria-label="更多操作">
                  更多
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="s in STATUS_OPTS"
                      :key="s.value"
                      :command="`status:${s.value}`"
                      :disabled="row.status === s.value"
                    >
                      设为{{ s.label }}
                    </el-dropdown-item>
                    <el-dropdown-item divided command="delete" class="danger-item">
                      删除租户
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </article>
        </div>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            background
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            :total="total"
            @current-change="load"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>

    <el-dialog v-model="visible" :title="editingId ? '编辑租户' : '开通租户'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="套餐" required>
          <el-select v-model="form.packageId" style="width: 100%" placeholder="选择套餐">
            <el-option v-for="p in packages" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="editingId" label="AI额度">
          <el-input-number v-model="form.aiQuota" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>

        <template v-if="!editingId">
          <el-divider content-position="left">物业管理端账号</el-divider>
          <el-form-item label="登录账号" required>
            <el-input v-model="form.adminPhone" placeholder="手机号，用于管理后台登录" maxlength="20" />
          </el-form-item>
          <el-form-item label="登录密码" required>
            <el-input v-model="form.adminPassword" type="password" show-password placeholder="至少 6 位" maxlength="32" />
          </el-form-item>
          <el-form-item label="管理员姓名">
            <el-input v-model="form.adminNickname" placeholder="默认用联系人姓名" />
          </el-form-item>
          <p class="tip">开通后可用该账号登录管理后台，并选择本社区租户</p>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adminVisible" title="设置管理端账号" width="480px" destroy-on-close>
      <el-form :model="adminForm" label-width="110px" v-loading="adminLoading">
        <el-form-item label="所属租户">
          <el-input :model-value="adminTenantName" disabled />
        </el-form-item>
        <el-form-item label="登录账号" required>
          <el-input v-model="adminForm.adminPhone" placeholder="手机号" maxlength="20" />
        </el-form-item>
        <el-form-item label="登录密码" :required="!adminExists">
          <el-input
            v-model="adminForm.adminPassword"
            type="password"
            show-password
            :placeholder="adminExists ? '留空则不修改密码' : '至少 6 位'"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="管理员姓名">
          <el-input v-model="adminForm.adminNickname" placeholder="显示名称" />
        </el-form-item>
        <p class="tip">账号为手机号，密码用于物业端管理后台登录</p>
      </el-form>
      <template #footer>
        <el-button @click="adminVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminSaving" @click="saveAdmin">保存账号</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, MoreFilled, Refresh, Search } from '@element-plus/icons-vue'
import {
  pageTenants,
  createTenant,
  updateTenant,
  updateTenantStatus,
  deleteTenant,
  listPackages,
  getTenantAdminAccount,
  upsertTenantAdminAccount,
} from '@/api/saas'

const STATUS_OPTS = [
  { value: 'active', label: '正常' },
  { value: 'degraded', label: '降级' },
  { value: 'frozen', label: '冻结' },
  { value: 'disabled', label: '停用' },
]
const STATUS_LABEL: Record<string, string> = Object.fromEntries(STATUS_OPTS.map((o) => [o.value, o.label]))

const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const list = ref<any[]>([])
const packages = ref<any[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  address: '',
  contactName: '',
  contactPhone: '',
  packageId: undefined as number | undefined,
  expireTime: '',
  remark: '',
  aiQuota: 0,
  adminPhone: '',
  adminPassword: '',
  adminNickname: '',
})

const adminVisible = ref(false)
const adminLoading = ref(false)
const adminSaving = ref(false)
const adminExists = ref(false)
const adminTenantId = ref<number | null>(null)
const adminTenantName = ref('')
const adminForm = reactive({
  adminPhone: '',
  adminPassword: '',
  adminNickname: '',
})

function statusLabel(s?: string) {
  if (!s) return '--'
  return STATUS_LABEL[s] || s
}

/** 低饱和状态色：正常绿 / 降级橙 / 停用·冻结·过期红 */
function statusTone(s?: string) {
  if (s === 'active') return 'tone-ok'
  if (s === 'degraded') return 'tone-warn'
  if (s === 'frozen' || s === 'disabled' || s === 'expired') return 'tone-danger'
  return 'tone-muted'
}

function pkgName(id?: number) {
  if (id == null) return '--'
  return packages.value.find((p) => p.id === id)?.name || String(id)
}

function formatDateTime(v?: string | null) {
  if (v == null || String(v).trim() === '') return '--'
  const d = dayjs(v)
  if (!d.isValid()) {
    const s = String(v).replace('T', ' ').trim()
    return s.slice(0, 16) || '--'
  }
  return d.format('YYYY-MM-DD HH:mm')
}

function formatQuota(v?: number | string | null) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (!Number.isFinite(n)) return String(v)
  return n.toLocaleString('zh-CN')
}

async function loadPackages() {
  try {
    packages.value = (await listPackages()) || []
  } catch {
    packages.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageTenants({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
    })
    list.value = data?.records || []
    total.value = Number(data?.total || 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

function openDialog(row?: any) {
  editingId.value = row?.id ?? null
  form.name = row?.name || ''
  form.address = row?.address || ''
  form.contactName = row?.contactName || ''
  form.contactPhone = row?.contactPhone || ''
  form.packageId = row?.packageId
  form.expireTime = toPickerTime(row?.expireTime)
  form.remark = row?.remark || ''
  form.aiQuota = row?.aiQuota ?? 0
  form.adminPhone = row?.contactPhone || ''
  form.adminPassword = '123456'
  form.adminNickname = row?.contactName || ''
  visible.value = true
}

/** 统一成后端 Jackson 默认可解析的 ISO 本地时间 */
function toPickerTime(v?: string) {
  if (!v) return ''
  const s = String(v).trim()
  if (!s) return ''
  if (s.includes('T')) return s.slice(0, 19)
  if (s.length >= 19 && s[10] === ' ') return `${s.slice(0, 10)}T${s.slice(11, 19)}`
  return s
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('请填写租户名称')
    return
  }
  if (!form.packageId) {
    ElMessage.error('请选择套餐')
    return
  }
  if (!editingId.value) {
    if (!form.adminPhone.trim()) {
      ElMessage.error('请填写管理端登录账号（手机号）')
      return
    }
    if (!form.adminPassword || form.adminPassword.length < 6) {
      ElMessage.error('管理端密码至少 6 位')
      return
    }
  }
  saving.value = true
  try {
    const payload: Record<string, any> = {
      name: form.name.trim(),
      address: form.address,
      contactName: form.contactName,
      contactPhone: form.contactPhone,
      packageId: form.packageId,
      expireTime: toPickerTime(form.expireTime) || undefined,
      remark: form.remark,
    }
    if (editingId.value) {
      payload.aiQuota = form.aiQuota
      await updateTenant({ id: editingId.value, ...payload })
      ElMessage.success('已保存')
    } else {
      payload.adminPhone = form.adminPhone.trim()
      payload.adminPassword = form.adminPassword
      payload.adminNickname = form.adminNickname.trim() || form.contactName || undefined
      await createTenant(payload)
      ElMessage.success(`已开通，管理端账号 ${payload.adminPhone} / 已设密码`)
    }
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openAdminDialog(row: any) {
  adminTenantId.value = row.id
  adminTenantName.value = row.name
  adminForm.adminPhone = ''
  adminForm.adminPassword = ''
  adminForm.adminNickname = ''
  adminExists.value = false
  adminVisible.value = true
  adminLoading.value = true
  try {
    const data = await getTenantAdminAccount(row.id)
    adminExists.value = !!data?.exists
    if (data?.exists) {
      adminForm.adminPhone = data.adminPhone || ''
      adminForm.adminNickname = data.adminNickname || ''
    } else {
      adminForm.adminPhone = ''
      adminForm.adminNickname = row.contactName || ''
      adminForm.adminPassword = '123456'
    }
  } catch {
    /* interceptor */
  } finally {
    adminLoading.value = false
  }
}

async function saveAdmin() {
  if (!adminTenantId.value) return
  if (!adminForm.adminPhone.trim()) {
    ElMessage.error('请填写管理端账号（手机号）')
    return
  }
  if (!adminExists.value && (!adminForm.adminPassword || adminForm.adminPassword.length < 6)) {
    ElMessage.error('新建账号时密码至少 6 位')
    return
  }
  if (adminForm.adminPassword && adminForm.adminPassword.length < 6) {
    ElMessage.error('密码至少 6 位')
    return
  }
  adminSaving.value = true
  try {
    await upsertTenantAdminAccount(adminTenantId.value, {
      adminPhone: adminForm.adminPhone.trim(),
      adminPassword: adminForm.adminPassword || undefined,
      adminNickname: adminForm.adminNickname.trim() || undefined,
    })
    ElMessage.success('管理端账号已保存')
    adminVisible.value = false
  } finally {
    adminSaving.value = false
  }
}

function onMoreCommand(row: any, cmd: string) {
  if (cmd === 'delete') {
    onDelete(row)
    return
  }
  if (cmd.startsWith('status:')) {
    onStatus(row, cmd.slice('status:'.length))
  }
}

async function onStatus(row: any, status: string) {
  if (row.status === status) return
  try {
    await ElMessageBox.confirm(`确认将「${row.name}」设为「${statusLabel(status)}」？`, '修改状态', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await updateTenantStatus(row.id, status)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    /* handled by interceptor */
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除租户「${row.name}」？删除后不可恢复，相关套餐与订单数据可能受影响。`,
      '删除租户',
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
    await deleteTenant(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* handled */
  }
}

onMounted(async () => {
  await loadPackages()
  await load()
})
</script>

<style scoped>
.tenant-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}

.tenant-shell {
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
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.icon-btn,
.ghost-btn,
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

.ghost-btn {
  border-color: #dfe3e8;
  color: #182230;
  background: #fff;
}

.primary-btn {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
}

.panel {
  background: #fff;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  padding: 24px;
  box-sizing: border-box;
}

.toolbar {
  margin-bottom: 16px;
}

.search-input {
  width: 300px;
  max-width: 100%;
}

.search-ico {
  color: #98a2b3;
}

.table-wrap {
  width: 100%;
  overflow-x: auto;
}

.tenant-table {
  width: 100%;
  --el-table-border-color: #eef1f4;
  --el-table-header-bg-color: #fafbfc;
  --el-table-row-hover-bg-color: #f5f8fc;
  --el-table-text-color: #344054;
  --el-table-header-text-color: #667085;
}

.tenant-table :deep(.el-table__header th) {
  font-weight: 500;
  font-size: 13px;
  height: 44px;
}

.tenant-table :deep(.el-table__row) {
  height: 54px;
}

.tenant-table :deep(.el-table__cell) {
  padding: 10px 0;
  font-size: 14px;
}

.tenant-table :deep(.el-table__empty-text) {
  color: #98a2b3;
}

.name-cell {
  font-weight: 600;
  color: #182230;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}
.status-pill.tone-ok {
  background: #ecfdf3;
  color: #067647;
}
.status-pill.tone-warn {
  background: #fffaeb;
  color: #b54708;
}
.status-pill.tone-danger {
  background: #fef3f2;
  color: #b42318;
}
.status-pill.tone-muted {
  background: #f2f4f7;
  color: #667085;
}

.ops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.ops-link {
  color: #2563eb !important;
  font-weight: 500;
  padding: 0 4px !important;
}

.ops-more-wrap {
  display: inline-flex;
  vertical-align: middle;
}
.ops-more {
  color: #667085 !important;
  padding: 0 4px !important;
  min-height: 28px;
}

:deep(.danger-item) {
  color: #b42318 !important;
}

.pager {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #eef1f4;
  display: flex;
  justify-content: flex-end;
}

.tip {
  margin: -4px 0 8px 110px;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.4;
}

.mobile-list {
  display: none;
}

/* —— 响应式 —— */
@media (max-width: 1024px) {
  .tenant-table :deep(.col-create-time) {
    display: none;
  }
}

@media (max-width: 768px) {
  .tenant-page {
    padding: 16px 12px 24px;
  }

  .page-hd {
    flex-direction: column;
    align-items: stretch;
  }

  .page-actions {
    justify-content: flex-start;
  }

  .panel {
    padding: 16px;
  }

  .search-input {
    width: 100%;
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
    font-size: 14px;
  }

  .mobile-card {
    border: 1px solid #eef1f4;
    border-radius: 8px;
    padding: 14px;
    margin-bottom: 10px;
  }

  .mobile-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .mobile-name {
    font-size: 15px;
    font-weight: 600;
    color: #182230;
    line-height: 22px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
  }

  .mobile-meta {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 13px;
    color: #344054;
    margin-bottom: 12px;
  }

  .mobile-meta span {
    display: inline-block;
    width: 48px;
    color: #667085;
  }

  .mobile-ops {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .pager {
    justify-content: center;
  }

  .pager :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 8px;
  }
}

@media (max-width: 375px) {
  .page-title {
    font-size: 22px;
  }

  .page-actions :deep(.el-button) {
    flex: 1;
  }

  .icon-btn {
    flex: 0 0 36px;
  }
}
</style>
