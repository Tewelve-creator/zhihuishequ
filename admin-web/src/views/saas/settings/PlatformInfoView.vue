<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">平台信息</h1>
          <p class="page-desc">通过系统配置接口保存 · 登录页尚未全部读取品牌字段</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="saveHint"
        :title="saveHint"
        :type="dirty ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="status-alert"
      />

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="基本信息" name="basic" />
        <el-tab-pane label="品牌外观" name="brand" />
        <el-tab-pane label="联系信息" name="contact" />
      </el-tabs>

      <div class="panel form-panel" v-loading="loading">
        <el-form v-show="tab === 'basic'" label-position="top" class="form">
          <el-form-item label="平台名称">
            <el-input v-model="form.siteName" maxlength="64" placeholder="如：智慧社区 SaaS" @input="markDirty" />
            <p class="field-hint">参数编码 platform.site.name</p>
          </el-form-item>
          <el-form-item label="平台简称">
            <el-input v-model="form.shortName" maxlength="32" placeholder="侧栏或窄屏展示" @input="markDirty" />
            <p class="field-hint">参数编码 platform.site.shortName</p>
          </el-form-item>
          <el-form-item label="默认语言">
            <el-select v-model="form.language" style="width: 100%" @change="markDirty">
              <el-option label="简体中文 (zh-CN)" value="zh-CN" />
              <el-option label="English (en-US)" value="en-US" />
            </el-select>
            <p class="field-hint">写入 platform.locale.language · 当前前端未消费</p>
          </el-form-item>
          <el-form-item label="默认时区">
            <el-select v-model="form.timezone" style="width: 100%" filterable @change="markDirty">
              <el-option label="Asia/Shanghai (UTC+8)" value="Asia/Shanghai" />
              <el-option label="UTC" value="UTC" />
            </el-select>
            <p class="field-hint">写入 platform.locale.timezone · 当前前端未消费</p>
          </el-form-item>
          <el-form-item label="备案信息">
            <el-input v-model="form.icp" maxlength="128" placeholder="ICP 备案号" @input="markDirty" />
            <p class="field-hint">参数编码 platform.site.icp</p>
          </el-form-item>
        </el-form>

        <el-form v-show="tab === 'brand'" label-position="top" class="form">
          <el-form-item label="登录页标题">
            <el-input v-model="form.loginTitle" maxlength="64" @input="markDirty" />
            <p class="field-hint">参数编码 platform.login.title</p>
          </el-form-item>
          <el-form-item label="品牌色">
            <div class="color-row">
              <el-color-picker v-model="form.primaryColor" @change="markDirty" />
              <el-input v-model="form.primaryColor" maxlength="16" style="width: 140px" @input="markDirty" />
            </div>
            <p class="field-hint">参数编码 platform.brand.primaryColor · 预览见下方</p>
          </el-form-item>
          <el-form-item label="Logo">
            <div class="upload-row">
              <el-upload
                :show-file-list="false"
                :before-upload="beforeLogo"
                accept="image/png,image/jpeg,image/svg+xml,image/webp"
                :http-request="(o) => doUpload(o, 'logo')"
              >
                <el-button :loading="uploadingLogo">上传 Logo</el-button>
              </el-upload>
              <el-button v-if="form.logo" link type="danger" @click="clearLogo">清除</el-button>
            </div>
            <p class="field-hint">PNG/JPG/SVG/WebP · ≤ 2MB · 建议 200×60 · 存 platform.brand.logo</p>
            <div v-if="form.logo" class="preview-box">
              <img :src="fileUrl(form.logo)" alt="Logo 预览" class="logo-preview" />
            </div>
          </el-form-item>
          <el-form-item label="浏览器图标">
            <div class="upload-row">
              <el-upload
                :show-file-list="false"
                :before-upload="beforeFavicon"
                accept="image/png,image/x-icon,image/svg+xml,image/webp"
                :http-request="(o) => doUpload(o, 'favicon')"
              >
                <el-button :loading="uploadingFavicon">上传图标</el-button>
              </el-upload>
              <el-button v-if="form.favicon" link type="danger" @click="clearFavicon">清除</el-button>
            </div>
            <p class="field-hint">ICO/PNG/SVG · ≤ 512KB · 建议 32×32 · 存 platform.brand.favicon</p>
            <div v-if="form.favicon" class="preview-box">
              <img :src="fileUrl(form.favicon)" alt="Favicon 预览" class="favicon-preview" />
            </div>
          </el-form-item>
          <div class="brand-preview" :style="{ borderColor: form.primaryColor || '#e4e7ec' }">
            <div class="preview-bar" :style="{ background: form.primaryColor || '#2563eb' }" />
            <div class="preview-body">
              <strong>{{ form.loginTitle || form.siteName || '登录页标题预览' }}</strong>
              <span>{{ form.shortName || '简称' }}</span>
            </div>
          </div>
        </el-form>

        <el-form v-show="tab === 'contact'" label-position="top" class="form">
          <el-form-item label="客服电话">
            <el-input v-model="form.phone" maxlength="32" placeholder="如 400-xxx-xxxx" @input="markDirty" />
            <p class="field-hint">参数编码 platform.contact.phone</p>
          </el-form-item>
          <el-form-item label="客服邮箱">
            <el-input v-model="form.email" maxlength="64" placeholder="support@example.com" @input="markDirty" />
            <p class="field-hint">参数编码 platform.contact.email</p>
          </el-form-item>
        </el-form>
      </div>

      <footer class="footer-bar">
        <div class="footer-left">
          <span v-if="dirty" class="dirty">有未保存更改</span>
          <span v-else class="clean">已与服务器同步</span>
          <span v-if="lastSaved" class="muted">最近保存 {{ lastSaved }}</span>
        </div>
        <div class="footer-right">
          <el-button @click="restoreDefaults">恢复默认值</el-button>
          <el-button :disabled="!dirty" @click="resetForm">撤销更改</el-button>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="saveAll">保存</el-button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listSysConfig, saveSysConfig } from '@/api/saas'
import { fileUrl, uploadFile } from '@/api/file'

const KEYS = {
  siteName: 'platform.site.name',
  shortName: 'platform.site.shortName',
  language: 'platform.locale.language',
  timezone: 'platform.locale.timezone',
  icp: 'platform.site.icp',
  loginTitle: 'platform.login.title',
  primaryColor: 'platform.brand.primaryColor',
  logo: 'platform.brand.logo',
  favicon: 'platform.brand.favicon',
  phone: 'platform.contact.phone',
  email: 'platform.contact.email',
} as const

const DEFAULTS = {
  siteName: '智慧社区 SaaS',
  shortName: '智慧社区',
  language: 'zh-CN',
  timezone: 'Asia/Shanghai',
  icp: '',
  loginTitle: '智慧社区管理平台',
  primaryColor: '#2563eb',
  logo: '',
  favicon: '',
  phone: '',
  email: '',
}

const REMARKS: Record<string, string> = {
  [KEYS.siteName]: '平台名称',
  [KEYS.shortName]: '平台简称',
  [KEYS.language]: '默认语言',
  [KEYS.timezone]: '默认时区',
  [KEYS.icp]: '备案信息',
  [KEYS.loginTitle]: '登录页标题',
  [KEYS.primaryColor]: '品牌主色',
  [KEYS.logo]: '平台 Logo URL',
  [KEYS.favicon]: '浏览器图标 URL',
  [KEYS.phone]: '客服电话',
  [KEYS.email]: '客服邮箱',
}

const tab = ref('basic')
const loading = ref(false)
const saving = ref(false)
const uploadingLogo = ref(false)
const uploadingFavicon = ref(false)
const dirty = ref(false)
const lastSaved = ref('')
const form = reactive({ ...DEFAULTS })
const snapshot = ref({ ...DEFAULTS })

const saveHint = computed(() =>
  dirty.value ? '修改尚未保存到服务器' : lastSaved.value ? '配置已保存' : '加载自 sys_config',
)

function markDirty() {
  dirty.value = true
}

function applyFromMap(map: Record<string, string>) {
  form.siteName = map[KEYS.siteName] ?? DEFAULTS.siteName
  form.shortName = map[KEYS.shortName] ?? DEFAULTS.shortName
  form.language = map[KEYS.language] ?? DEFAULTS.language
  form.timezone = map[KEYS.timezone] ?? DEFAULTS.timezone
  form.icp = map[KEYS.icp] ?? ''
  form.loginTitle = map[KEYS.loginTitle] ?? DEFAULTS.loginTitle
  form.primaryColor = map[KEYS.primaryColor] ?? DEFAULTS.primaryColor
  form.logo = map[KEYS.logo] ?? ''
  form.favicon = map[KEYS.favicon] ?? ''
  form.phone = map[KEYS.phone] ?? ''
  form.email = map[KEYS.email] ?? ''
  snapshot.value = { ...form }
  dirty.value = false
}

async function load() {
  loading.value = true
  try {
    const list = (await listSysConfig()) || []
    const map: Record<string, string> = {}
    for (const row of list) {
      map[String(row.configKey)] = String(row.configValue ?? '')
    }
    applyFromMap(map)
  } catch {
    applyFromMap({})
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, snapshot.value)
  dirty.value = false
}

async function restoreDefaults() {
  try {
    await ElMessageBox.confirm('将表单恢复为内置默认值（需再点保存才会写入服务器）', '恢复默认值', {
      type: 'warning',
    })
  } catch {
    return
  }
  Object.assign(form, DEFAULTS)
  dirty.value = true
}

function beforeLogo(file: File) {
  const okType = /image\/(png|jpeg|svg\+xml|webp)/.test(file.type)
  if (!okType) {
    ElMessage.error('仅支持 PNG / JPG / SVG / WebP')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('Logo 不能超过 2MB')
    return false
  }
  return true
}

function beforeFavicon(file: File) {
  const okType = /image\/(png|x-icon|vnd\.microsoft\.icon|svg\+xml|webp)/.test(file.type) || /\.ico$/i.test(file.name)
  if (!okType) {
    ElMessage.error('仅支持 ICO / PNG / SVG / WebP')
    return false
  }
  if (file.size > 512 * 1024) {
    ElMessage.error('图标不能超过 512KB')
    return false
  }
  return true
}

async function doUpload(option: any, kind: 'logo' | 'favicon') {
  const file = option.file as File
  const loadingRef = kind === 'logo' ? uploadingLogo : uploadingFavicon
  loadingRef.value = true
  try {
    const url = await uploadFile(file)
    if (!url) throw new Error('empty')
    if (kind === 'logo') form.logo = url
    else form.favicon = url
    markDirty()
    ElMessage.success('上传成功，请保存配置')
    option.onSuccess?.(url)
  } catch {
    ElMessage.error('上传失败')
    option.onError?.(new Error('upload failed'))
  } finally {
    loadingRef.value = false
  }
}

function clearLogo() {
  form.logo = ''
  markDirty()
}

function clearFavicon() {
  form.favicon = ''
  markDirty()
}

async function saveAll() {
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.error('客服邮箱格式不正确')
    return
  }
  const pairs: Array<[string, string]> = [
    [KEYS.siteName, form.siteName.trim()],
    [KEYS.shortName, form.shortName.trim()],
    [KEYS.language, form.language],
    [KEYS.timezone, form.timezone],
    [KEYS.icp, form.icp.trim()],
    [KEYS.loginTitle, form.loginTitle.trim()],
    [KEYS.primaryColor, form.primaryColor.trim() || DEFAULTS.primaryColor],
    [KEYS.logo, form.logo.trim()],
    [KEYS.favicon, form.favicon.trim()],
    [KEYS.phone, form.phone.trim()],
    [KEYS.email, form.email.trim()],
  ]
  saving.value = true
  try {
    for (const [key, value] of pairs) {
      await saveSysConfig({ configKey: key, configValue: value, remark: REMARKS[key] })
    }
    snapshot.value = { ...form }
    dirty.value = false
    lastSaved.value = new Date().toLocaleString()
    ElMessage.success('平台信息已保存')
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
  padding-bottom: 72px;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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
.icon-btn {
  width: 36px;
  padding: 8px;
}
.status-alert {
  margin-bottom: 12px;
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
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
  padding: 20px 24px;
}
.form {
  max-width: 720px;
}
.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #667085;
  line-height: 1.4;
}
.color-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-box {
  margin-top: 10px;
  padding: 12px;
  border: 1px dashed #e4e7ec;
  border-radius: 8px;
  background: #fafafa;
}
.logo-preview {
  max-height: 48px;
  max-width: 240px;
  object-fit: contain;
}
.favicon-preview {
  width: 32px;
  height: 32px;
  object-fit: contain;
}
.brand-preview {
  margin-top: 8px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  overflow: hidden;
  max-width: 360px;
}
.preview-bar {
  height: 6px;
}
.preview-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preview-body strong {
  color: #182230;
  font-size: 16px;
}
.preview-body span {
  color: #667085;
  font-size: 13px;
}
.footer-bar {
  position: sticky;
  bottom: 0;
  margin-top: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  z-index: 2;
}
.footer-left {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
.dirty {
  color: #b54708;
}
.clean {
  color: #027a48;
}
.muted {
  color: #667085;
}
.footer-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
  .panel {
    padding: 16px;
  }
}
</style>
