<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>系统参数配置</h2>
        <p class="sub">平台级键值配置（业务参数）；AI 运行参数请优先在「全局AI参数配置」维护</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openDialog()">新增配置</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        常用键：saas.expire.warn.days · ai.quota.default · platform.site.name
      </template>
    </el-alert>

    <div class="preset-row">
      <span class="muted">快捷新增：</span>
      <el-button size="small" @click="openPreset('saas.expire.warn.days', '30', '到期预警天数')">到期预警天数</el-button>
      <el-button size="small" @click="openPreset('ai.quota.default', '1000', '默认 AI 额度')">默认 AI 额度</el-button>
      <el-button size="small" @click="openPreset('platform.site.name', '智慧社区 SaaS', '平台名称')">平台名称</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无配置">
      <el-table-column prop="configKey" label="配置键" min-width="180" />
      <el-table-column prop="configValue" label="配置值" min-width="160" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑配置' : '新增配置'" width="480px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="配置键" required>
          <el-input v-model="form.configKey" :disabled="!!editing" placeholder="如 saas.expire.warn.days" />
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
import { listSysConfig, saveSysConfig } from '@/api/saas'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const visible = ref(false)
const editing = ref(false)
const form = reactive({ configKey: '', configValue: '', remark: '' })

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
  editing.value = !!row
  form.configKey = row?.configKey || ''
  form.configValue = row?.configValue || ''
  form.remark = row?.remark || ''
  visible.value = true
}

function openPreset(key: string, value: string, remark: string) {
  const exist = list.value.find((r) => r.configKey === key)
  if (exist) {
    openDialog(exist)
    return
  }
  editing.value = false
  form.configKey = key
  form.configValue = value
  form.remark = remark
  visible.value = true
}

async function save() {
  const configKey = form.configKey.trim()
  if (!configKey) {
    ElMessage.error('请填写配置键')
    return
  }
  saving.value = true
  try {
    await saveSysConfig({
      configKey,
      configValue: form.configValue,
      remark: form.remark.trim() || undefined,
    })
    ElMessage.success('配置已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.preset-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 12px; }
.muted { color: #94a3b8; font-size: 13px; }
</style>
