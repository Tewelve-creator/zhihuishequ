<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">系统配置</h1>
          <p class="page-desc">按业务分类展示 sys_config · 敏感项脱敏 · AI / 平台信息键请到对应页面维护</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
          <el-button type="primary" class="primary-btn" @click="openDialog()">新增配置</el-button>
        </div>
      </header>

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="基础配置" name="basic" />
        <el-tab-pane label="文件与存储" name="file" />
        <el-tab-pane label="消息通知" name="notify" />
        <el-tab-pane label="业务规则" name="biz" />
      </el-tabs>

      <div class="panel">
        <el-table v-loading="loading" :data="filtered" empty-text="该分类下暂无配置">
          <el-table-column label="中文名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ displayName(row) }}</template>
          </el-table-column>
          <el-table-column label="当前值" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ displayValue(row) }}</template>
          </el-table-column>
          <el-table-column label="作用说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="敏感" width="72">
            <template #default="{ row }">
              <span class="pill" :class="isSensitive(row.configKey) ? 'warn' : 'off'">
                {{ isSensitive(row.configKey) ? '是' : '否' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="生效方式" width="110">
            <template #default="{ row }">{{ effectHint(row.configKey) }}</template>
          </el-table-column>
          <el-table-column label="最后修改" width="170">
            <template #default="{ row }">
              <span :title="'修改人字段未返回'">{{ row.updateTime || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="openDialog(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="() => openDetail(row)">
                  <el-button link class="ops-more"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item>查看参数编码</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <p v-if="tab === 'basic'" class="foot-hint">
          platform.* 品牌字段建议在「平台信息」维护；此处仍会列出以便排查。
        </p>
      </div>
    </div>

    <el-dialog v-model="visible" :title="editing ? '编辑配置' : '新增配置'" width="520px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="配置键" required>
          <el-input v-model="form.configKey" :disabled="editing" placeholder="如 saas.expire.warn.days" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input
            v-model="form.configValue"
            type="textarea"
            :rows="3"
            :placeholder="editing && isSensitive(form.configKey) ? '留空保持原值；填写则覆盖' : ''"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" />
        </el-form-item>
        <el-alert
          v-if="isHighImpact(form.configKey)"
          type="warning"
          :closable="false"
          show-icon
          title="该项影响面较大，保存前将二次确认；部分配置可能需要刷新缓存或重启服务。"
        />
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="配置详情" size="400px">
      <template v-if="current">
        <dl class="detail-dl">
          <div><dt>中文名称</dt><dd>{{ displayName(current) }}</dd></div>
          <div><dt>参数编码</dt><dd><code>{{ current.configKey }}</code></dd></div>
          <div><dt>当前值</dt><dd>{{ displayValue(current) }}</dd></div>
          <div><dt>说明</dt><dd>{{ current.remark || '—' }}</dd></div>
          <div><dt>生效方式</dt><dd>{{ effectHint(current.configKey) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ current.updateTime || '—' }}</dd></div>
          <div><dt>修改人</dt><dd class="muted">接口未返回</dd></div>
        </dl>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Refresh } from '@element-plus/icons-vue'
import { listSysConfig, saveSysConfig } from '@/api/saas'

const NAME_MAP: Record<string, string> = {
  'saas.expire.warn.days': '到期预警天数',
  'ai.quota.default': '默认 AI 额度',
  'platform.site.name': '平台名称',
  'platform.site.shortName': '平台简称',
  'platform.site.icp': '备案信息',
  'platform.login.title': '登录页标题',
  'platform.brand.primaryColor': '品牌主色',
  'platform.brand.logo': '平台 Logo',
  'platform.brand.favicon': '浏览器图标',
  'platform.contact.phone': '客服电话',
  'platform.contact.email': '客服邮箱',
  'platform.locale.language': '默认语言',
  'platform.locale.timezone': '默认时区',
}

const tab = ref('basic')
const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const visible = ref(false)
const editing = ref(false)
const hadSensitiveValue = ref(false)
const detailVisible = ref(false)
const current = ref<any>(null)
const form = reactive({ configKey: '', configValue: '', remark: '' })

function isSensitive(key?: string) {
  const k = String(key || '').toLowerCase()
  return k.includes('key') || k.includes('secret') || k.includes('password') || k.includes('token')
}

function isHighImpact(key?: string) {
  const k = String(key || '')
  return (
    k.startsWith('ai.') ||
    k.includes('quota') ||
    k.includes('expire') ||
    k === 'platform.site.name' ||
    k.includes('url')
  )
}

function categorize(key: string): string {
  const k = key.toLowerCase()
  if (k.startsWith('ai.')) return 'biz' // AI 专用页，业务规则里仅保留非 ai. 时不展示
  if (k.includes('file') || k.includes('storage') || k.includes('minio') || k.includes('oss')) return 'file'
  if (k.includes('sms') || k.includes('mail') || k.includes('notify') || k.includes('push') || k.includes('message'))
    return 'notify'
  if (k.startsWith('saas.') || k.includes('quota') || k.includes('expire') || k.includes('fee') || k.includes('order'))
    return 'biz'
  if (k.startsWith('platform.')) return 'basic'
  return 'basic'
}

function displayName(row: any) {
  return NAME_MAP[row.configKey] || row.remark || row.configKey
}

function displayValue(row: any) {
  const val = String(row.configValue ?? '')
  if (!isSensitive(row.configKey)) return val || '—'
  if (!val) return '—'
  if (val.length <= 6) return '******'
  return val.slice(0, 3) + '****' + val.slice(-2)
}

function effectHint(key?: string) {
  const k = String(key || '')
  if (k.startsWith('ai.')) return '可能需重启'
  if (k.includes('quota') || k.includes('expire')) return '即时 / 缓存'
  return '即时读取'
}

const filtered = computed(() => {
  const rows = list.value.filter((r) => {
    const key = String(r.configKey || '')
    if (key.startsWith('ai.')) return false
    return categorize(key) === tab.value
  })
  return rows
})

async function load() {
  loading.value = true
  try {
    list.value = (await listSysConfig()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openDialog(row?: any) {
  if (row) {
    editing.value = true
    hadSensitiveValue.value = !!row.configValue
    Object.assign(form, {
      configKey: row.configKey,
      configValue: isSensitive(row.configKey) ? '' : String(row.configValue ?? ''),
      remark: row.remark || '',
    })
  } else {
    editing.value = false
    hadSensitiveValue.value = false
    Object.assign(form, { configKey: '', configValue: '', remark: '' })
  }
  visible.value = true
}

function openDetail(row: any) {
  current.value = row
  detailVisible.value = true
}

async function save() {
  const key = form.configKey.trim()
  if (!key) {
    ElMessage.error('请填写配置键')
    return
  }
  if (key.startsWith('ai.')) {
    ElMessage.warning('AI 参数请前往「AI 服务配置」维护')
    return
  }
  if (isSensitive(key) && !form.configValue && editing.value && hadSensitiveValue.value) {
    ElMessage.info('未填写新值，已取消覆盖')
    visible.value = false
    return
  }
  if (isHighImpact(key)) {
    try {
      await ElMessageBox.confirm(
        `确认修改「${displayName({ configKey: key, remark: form.remark })}」？\n参数编码：${key}\n生效方式：${effectHint(key)}`,
        '高影响配置确认',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  saving.value = true
  try {
    await saveSysConfig({
      configKey: key,
      configValue: form.configValue,
      remark: form.remark || undefined,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
  gap: 12px;
  margin-bottom: 12px;
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
}
.page-actions {
  display: flex;
  gap: 8px;
}
.icon-btn {
  width: 36px;
  padding: 8px;
}
.primary-btn {
  background: #2563eb;
  border-color: #2563eb;
}
.tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}
.tabs :deep(.el-tabs__active-bar) {
  background: #2563eb;
}
.panel {
  margin-top: 12px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 16px 20px;
}
.ops {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ops-link {
  color: #2563eb !important;
}
.ops-more {
  color: #667085 !important;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.warn {
  background: #fffaeb;
  color: #b54708;
}
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.foot-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: #667085;
}
.detail-dl {
  display: grid;
  gap: 12px;
}
.detail-dl div {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px;
  font-size: 13px;
}
.detail-dl dt {
  color: #667085;
  margin: 0;
}
.detail-dl dd {
  margin: 0;
  color: #182230;
  word-break: break-all;
}
.muted {
  color: #667085;
}
code {
  font-size: 12px;
  background: #f2f4f7;
  padding: 2px 6px;
  border-radius: 4px;
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
}
</style>
