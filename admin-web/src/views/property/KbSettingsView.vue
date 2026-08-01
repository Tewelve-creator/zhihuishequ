<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>知识库设置</h2>
        <p class="sub">配置检索参数、系统提示词，管控 AI 应答策略</p>
      </div>
      <div class="ops">
        <el-button :loading="loading" @click="load">重新加载</el-button>
        <el-button v-if="canManage" type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
      title="网格员仅可查看设置，不可修改"
    />

    <el-form v-loading="loading" label-width="140px" class="form" :disabled="!canManage">
      <el-divider content-position="left">检索参数</el-divider>
      <el-form-item label="最低相似度">
        <el-slider v-model="form.minScore" :min="0.05" :max="0.95" :step="0.01" show-input style="width: 420px" />
        <div class="hint">低于该阈值则不强制采用知识库内容（当前 {{ form.minScore }}）</div>
      </el-form-item>
      <el-form-item label="检索条数 Top-K">
        <el-input-number v-model="form.topK" :min="1" :max="10" />
        <div class="hint">多路召回候选数，实际应答仍优先最佳命中</div>
      </el-form-item>
      <el-form-item label="同分优先文档">
        <el-switch v-model="form.preferDocument" />
        <div class="hint">问答对与文档得分接近时，优先采用文档摘录</div>
      </el-form-item>

      <el-divider content-position="left">应答策略</el-divider>
      <el-form-item label="策略">
        <el-radio-group v-model="form.replyStrategy">
          <el-radio value="rag_first">知识库优先（推荐）</el-radio>
          <el-radio value="kb_only">仅知识库（未命中则拒答）</el-radio>
          <el-radio value="llm_first">大模型优先</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="非官方温馨提示">
        <el-switch v-model="form.enableUnofficialTip" />
        <div class="hint">知识库未命中、由大模型作答时，是否追加「非本小区官方规定」提示</div>
      </el-form-item>

      <el-divider content-position="left">系统提示词</el-divider>
      <el-form-item label="System Prompt">
        <el-input
          v-model="form.systemPrompt"
          type="textarea"
          :rows="12"
          placeholder="AI 管家人设与作答规则…"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getKbSettings, saveKbSettings } from '@/api/kbDoc'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const saving = ref(false)
const form = reactive({
  minScore: 0.32,
  topK: 3,
  systemPrompt: '',
  enableUnofficialTip: true,
  preferDocument: true,
  replyStrategy: 'rag_first',
})

function apply(data: any) {
  form.minScore = Number(data?.minScore ?? 0.32)
  form.topK = Number(data?.topK ?? 3)
  form.systemPrompt = data?.systemPrompt || ''
  form.enableUnofficialTip =
    data?.enableUnofficialTip === 1 ||
    data?.enableUnofficialTip === true ||
    data?.enableUnofficialTip === '1'
  form.preferDocument =
    data?.preferDocument === 1 ||
    data?.preferDocument === true ||
    data?.preferDocument === '1' ||
    data?.preferDocument == null
  form.replyStrategy = data?.replyStrategy || 'rag_first'
}

async function load() {
  loading.value = true
  try {
    apply((await getKbSettings()) || {})
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const data = await saveKbSettings({
      minScore: form.minScore,
      topK: form.topK,
      systemPrompt: form.systemPrompt,
      enableUnofficialTip: form.enableUnofficialTip,
      preferDocument: form.preferDocument,
      replyStrategy: form.replyStrategy,
    })
    apply(data || {})
    ElMessage.success('设置已保存并立即生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; }
.form { max-width: 860px; }
.hint { color: #94a3b8; font-size: 12px; margin-top: 4px; line-height: 1.4; }
</style>
