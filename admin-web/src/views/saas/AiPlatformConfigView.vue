<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>全局AI参数配置</h2>
        <p class="sub">平台级 DeepSeek/兼容接口参数（存 sys_config，前缀 ai.）；密钥请妥善保管</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="open()">新增参数</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        常用键：ai.provider · ai.api-url · ai.api-key · ai.model · ai.daily-quota（修改后需 AI 服务读取配置或重启生效）
      </template>
    </el-alert>

    <div class="filter-bar">
      <el-tag type="warning" effect="plain">AI 配置 {{ list.length }} 项</el-tag>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无 AI 参数">
      <el-table-column prop="configKey" label="配置键" min-width="180" />
      <el-table-column label="配置值" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ maskValue(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑 AI 参数' : '新增 AI 参数'" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="配置键" required>
          <el-input v-model="form.configKey" :disabled="editing" placeholder="须以 ai. 开头" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="form.configValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAiPlatformConfig, saveAiPlatformConfig } from '@/api/saas'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const visible = ref(false)
const editing = ref(false)
const form = reactive({ configKey: '', configValue: '', remark: '' })

function maskValue(row: any) {
  const key = String(row.configKey || '')
  const val = String(row.configValue ?? '')
  if (key.includes('key') || key.includes('secret') || key.includes('password')) {
    if (!val) return '—'
    if (val.length <= 6) return '******'
    return val.slice(0, 3) + '****' + val.slice(-2)
  }
  return val || '—'
}

async function load() {
  loading.value = true
  try {
    list.value = (await listAiPlatformConfig()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function open(row?: any) {
  if (row) {
    editing.value = true
    Object.assign(form, {
      configKey: row.configKey,
      configValue: row.configValue ?? '',
      remark: row.remark || '',
    })
  } else {
    editing.value = false
    Object.assign(form, { configKey: 'ai.', configValue: '', remark: '' })
  }
  visible.value = true
}

async function save() {
  if (!form.configKey.startsWith('ai.')) {
    ElMessage.error('配置键须以 ai. 开头')
    return
  }
  saving.value = true
  try {
    await saveAiPlatformConfig({
      configKey: form.configKey.trim(),
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
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.page-header h2 { margin: 0; font-size: 18px; }
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
</style>
