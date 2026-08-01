<template>
  <div class="saas-page">
    <div class="saas-shell">
      <header class="page-hd">
        <div class="page-hd-text">
          <h1 class="page-title">AI 服务配置</h1>
          <p class="page-desc">仅展示后端 ai.* 参数 · API Key 脱敏且不回填明文</p>
        </div>
        <div class="page-actions">
          <el-button class="icon-btn" :loading="loading" aria-label="刷新" @click="load">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </div>
      </header>

      <el-alert
        :title="dirty ? '有未保存更改' : '配置已与服务器同步（修改后需 AI 服务读取配置或重启生效）'"
        :type="dirty ? 'warning' : 'info'"
        :closable="false"
        show-icon
        class="status-alert"
      />

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="服务接入" name="access" />
        <el-tab-pane label="模型策略" name="model" />
        <el-tab-pane label="调用限制" name="limit" />
        <el-tab-pane label="安全策略" name="safety" />
      </el-tabs>

      <div class="panel form-panel" v-loading="loading">
        <el-form v-show="tab === 'access'" label-position="top" class="form">
          <el-form-item label="服务商">
            <el-select v-model="form.provider" style="width: 100%" @change="markDirty">
              <el-option label="mock（本地模拟）" value="mock" />
              <el-option label="http（DeepSeek 兼容）" value="http" />
            </el-select>
            <p class="hint">ai.provider</p>
          </el-form-item>
          <el-form-item label="接口地址">
            <el-input v-model="form.apiUrl" placeholder="https://api.deepseek.com/v1" @input="markDirty" />
            <p class="hint">ai.api-url</p>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKeyInput"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="hasApiKey ? '已配置（脱敏）· 留空保持原值 · 填写则覆盖' : '未配置，请填写'"
              @input="markDirty"
            />
            <p class="hint">ai.api-key · 保存后列表不再明文回显 · 不写入 URL / 前端日志</p>
          </el-form-item>
          <el-form-item label="连接状态">
            <span class="pill" :class="hasApiKey && form.apiUrl ? 'ok' : 'off'">
              {{ hasApiKey && form.apiUrl ? '已填写接入信息' : '未完整配置' }}
            </span>
            <p class="hint">后端未提供「测试连接」接口，此处仅根据是否已配置判断，非实时探测。</p>
          </el-form-item>
          <el-collapse>
            <el-collapse-item title="高级说明" name="adv">
              <p class="hint">超时时间等运行参数由 AI 服务 application.yml 控制，不在本页的 sys_config 键集合中。</p>
            </el-collapse-item>
          </el-collapse>
        </el-form>

        <el-form v-show="tab === 'model'" label-position="top" class="form">
          <el-form-item label="默认模型">
            <el-input v-model="form.model" placeholder="deepseek-chat" @input="markDirty" />
            <p class="hint">ai.model</p>
          </el-form-item>
          <el-form-item label="RAG 召回条数">
            <el-input-number v-model="form.ragTopK" :min="1" :max="50" @change="markDirty" />
            <p class="hint">ai.rag.top-k · 建议 1–20，过高可能增加延迟与费用</p>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="备用模型、温度、最大输出长度、失败重试与降级策略当前无对应 ai.* 配置键，不提供可编辑项。"
          />
        </el-form>

        <el-form v-show="tab === 'limit'" label-position="top" class="form">
          <el-form-item label="单租户日调用上限（业务参考）">
            <el-input-number v-model="form.dailyQuota" :min="0" :max="1000000" @change="markDirty" />
            <p class="hint">ai.daily-quota</p>
          </el-form-item>
          <el-form-item label="AI 接口 QPS 限流参考">
            <el-input-number v-model="form.qps" :min="1" :max="10000" @change="markDirty" />
            <p class="hint">ai.sentinel.qps · 请按网关/Sentinel 实际能力设置</p>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="平台并发上限、超额处理方式、预警阈值等若无独立键，不在此伪造。"
          />
        </el-form>

        <div v-show="tab === 'safety'" class="safety">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="内容安全检测、敏感信息脱敏、日志保留周期当前无 ai.* 配置接口，不提供开关。"
          />
          <p class="hint mt">平台敏感词库请前往「AI 管理 → 敏感词库」维护。</p>
        </div>

        <el-collapse v-if="extraKeys.length" class="extra">
          <el-collapse-item title="其他 ai.* 参数" name="extra">
            <el-table :data="extraKeys" size="small" empty-text="无">
              <el-table-column prop="configKey" label="键" min-width="160" />
              <el-table-column label="值" min-width="160">
                <template #default="{ row }">{{ displayValue(row) }}</template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link class="ops-link" @click="editExtra(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>

      <footer class="footer-bar">
        <div class="footer-left">
          <span :class="dirty ? 'dirty' : 'clean'">{{ dirty ? '有未保存更改' : '已同步' }}</span>
        </div>
        <div class="footer-right">
          <el-button :disabled="!dirty" @click="resetForm">撤销更改</el-button>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="saveAll">保存</el-button>
        </div>
      </footer>
    </div>

    <el-dialog v-model="extraVisible" title="编辑 AI 参数" width="480px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="配置键"><el-input :model-value="extraForm.configKey" disabled /></el-form-item>
        <el-form-item label="配置值">
          <el-input
            v-model="extraForm.configValue"
            type="textarea"
            :rows="3"
            :placeholder="isSensitiveKey(extraForm.configKey) ? '留空保持原值；填写则覆盖' : ''"
          />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="extraForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extraVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExtra">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listAiPlatformConfig, saveAiPlatformConfig } from '@/api/saas'

const KNOWN = {
  provider: 'ai.provider',
  apiUrl: 'ai.api-url',
  apiKey: 'ai.api-key',
  model: 'ai.model',
  dailyQuota: 'ai.daily-quota',
  ragTopK: 'ai.rag.top-k',
  qps: 'ai.sentinel.qps',
} as const

const tab = ref('access')
const loading = ref(false)
const saving = ref(false)
const dirty = ref(false)
const hasApiKey = ref(false)
const list = ref<any[]>([])
const form = reactive({
  provider: 'http',
  apiUrl: '',
  apiKeyInput: '',
  model: 'deepseek-chat',
  dailyQuota: 1000,
  ragTopK: 5,
  qps: 20,
})
const snapshot = ref({ ...form, hasApiKey: false })
const extraVisible = ref(false)
const extraForm = reactive({ configKey: '', configValue: '', remark: '', hadValue: false })

const knownSet = new Set(Object.values(KNOWN))
const extraKeys = computed(() => list.value.filter((r) => !knownSet.has(String(r.configKey))))

function isSensitiveKey(key: string) {
  const k = String(key || '').toLowerCase()
  return k.includes('key') || k.includes('secret') || k.includes('password') || k.includes('token')
}

function displayValue(row: any) {
  const val = String(row.configValue ?? '')
  if (!isSensitiveKey(row.configKey)) return val || '—'
  if (!val) return '—'
  if (val.length <= 6) return '******'
  return val.slice(0, 3) + '****' + val.slice(-2)
}

function markDirty() {
  dirty.value = true
}

function applyList(rows: any[]) {
  list.value = rows
  const map: Record<string, string> = {}
  for (const r of rows) map[String(r.configKey)] = String(r.configValue ?? '')
  form.provider = map[KNOWN.provider] || 'http'
  form.apiUrl = map[KNOWN.apiUrl] || ''
  form.model = map[KNOWN.model] || 'deepseek-chat'
  form.dailyQuota = Number(map[KNOWN.dailyQuota] || 1000)
  form.ragTopK = Number(map[KNOWN.ragTopK] || 5)
  form.qps = Number(map[KNOWN.qps] || 20)
  hasApiKey.value = !!map[KNOWN.apiKey]
  form.apiKeyInput = ''
  snapshot.value = { ...form, hasApiKey: hasApiKey.value }
  dirty.value = false
}

async function load() {
  loading.value = true
  try {
    applyList((await listAiPlatformConfig()) || [])
  } catch {
    applyList([])
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    provider: snapshot.value.provider,
    apiUrl: snapshot.value.apiUrl,
    apiKeyInput: '',
    model: snapshot.value.model,
    dailyQuota: snapshot.value.dailyQuota,
    ragTopK: snapshot.value.ragTopK,
    qps: snapshot.value.qps,
  })
  hasApiKey.value = snapshot.value.hasApiKey
  dirty.value = false
}

async function upsert(key: string, value: string, remark?: string) {
  await saveAiPlatformConfig({ configKey: key, configValue: value, remark })
}

async function saveAll() {
  if (form.ragTopK < 1 || form.ragTopK > 50) {
    ElMessage.error('RAG 召回条数需在 1–50')
    return
  }
  saving.value = true
  try {
    await upsert(KNOWN.provider, form.provider, 'AI 提供商：mock / http')
    await upsert(KNOWN.apiUrl, form.apiUrl.trim(), '大模型 API 地址')
    if (form.apiKeyInput.trim()) {
      await upsert(KNOWN.apiKey, form.apiKeyInput.trim(), '大模型 API Key（仅运营可见，勿泄露）')
      hasApiKey.value = true
    }
    await upsert(KNOWN.model, form.model.trim(), '默认模型名')
    await upsert(KNOWN.dailyQuota, String(form.dailyQuota), '单租户日默认调用上限（业务参考）')
    await upsert(KNOWN.ragTopK, String(form.ragTopK), 'RAG 召回条数')
    await upsert(KNOWN.qps, String(form.qps), 'AI 接口 QPS 限流参考')
    form.apiKeyInput = ''
    snapshot.value = { ...form, hasApiKey: hasApiKey.value }
    dirty.value = false
    ElMessage.success('AI 配置已保存')
    await load()
  } finally {
    saving.value = false
  }
}

function editExtra(row: any) {
  extraForm.configKey = row.configKey
  extraForm.remark = row.remark || ''
  extraForm.hadValue = !!row.configValue
  extraForm.configValue = isSensitiveKey(row.configKey) ? '' : String(row.configValue ?? '')
  extraVisible.value = true
}

async function saveExtra() {
  if (isSensitiveKey(extraForm.configKey) && !extraForm.configValue.trim() && extraForm.hadValue) {
    ElMessage.info('未填写新密钥，已取消覆盖')
    extraVisible.value = false
    return
  }
  saving.value = true
  try {
    await upsert(extraForm.configKey, extraForm.configValue, extraForm.remark || undefined)
    ElMessage.success('已保存')
    extraVisible.value = false
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
  padding-bottom: 72px;
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
.icon-btn {
  width: 36px;
  padding: 8px;
}
.status-alert {
  margin-bottom: 12px;
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
.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #667085;
  line-height: 1.45;
}
.hint.mt {
  margin-top: 12px;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #027a48;
}
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.extra {
  margin-top: 16px;
  max-width: 720px;
}
.ops-link {
  color: #2563eb !important;
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
.dirty {
  color: #b54708;
  font-size: 13px;
}
.clean {
  color: #027a48;
  font-size: 13px;
}
.footer-right {
  display: flex;
  gap: 8px;
}
@media (max-width: 768px) {
  .saas-page {
    margin: -12px;
    padding: 16px;
  }
}
</style>
