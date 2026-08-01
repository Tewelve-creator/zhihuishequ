<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>文档管理</h2>
        <p class="sub">支持上传 PDF、Word、Markdown 等文件；文本管理、解析与向量入库</p>
      </div>
      <div class="ops">
        <el-button v-if="canManage" type="primary" @click="pickFile">上传文档</el-button>
        <el-button v-if="canManage" @click="openText">粘贴文本</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题 / 文件名"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-select v-model="category" clearable placeholder="全部分类" style="width: 160px" @change="load">
        <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
      </el-select>
      <el-select v-model="docStatus" clearable placeholder="文档状态" style="width: 140px" @change="load">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="生效中" value="ACTIVE" />
        <el-option label="已废止" value="REVOKED" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <template v-if="canManage">
        <el-button :disabled="!selectedIds.length" @click="openBatchIndex">批量重建索引</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="batchRemove">批量删除</el-button>
      </template>
    </div>

    <el-alert
      v-if="!canManage"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
      title="网格员仅可浏览文档与索引日志"
    />

    <input
      ref="fileRef"
      type="file"
      accept=".md,.txt,.pdf,.docx,text/markdown,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      style="display: none"
      @change="onFile"
    />

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      empty-text="暂无文档"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="canManage" type="selection" width="48" />
      <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="110">
        <template #default="{ row }">{{ row.categoryName || row.category || '—' }}</template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件名" min-width="140" show-overflow-tooltip />
      <el-table-column prop="charCount" label="字符数" width="90" />
      <el-table-column prop="chunkCount" label="切片数" width="80" />
      <el-table-column label="文档状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.docStatusTag || statusTag(row.docStatus)" size="small">
            {{ row.docStatusLabel || statusLabel(row.docStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="indexStatus" label="索引" width="100">
        <template #default="{ row }">{{ indexLabel(row.indexStatus) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewDoc(row)">查看/编辑</el-button>
          <el-button link @click="viewLogs(row)">索引日志</el-button>
          <el-button v-if="canManage" link type="success" @click="openBuild(row, false)">解析入库</el-button>
          <el-button v-if="canManage" link type="warning" @click="openBuild(row, true)">重建索引</el-button>
          <el-button v-if="canManage" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看/编辑 + Markdown 预览 -->
    <el-dialog v-model="dlgView" :title="current?.title || '文档'" width="920px" destroy-on-close>
      <el-form v-if="canManage" label-width="70px" class="edit-form">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" style="width: 200px">
            <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.docStatus" style="width: 200px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="生效中" value="ACTIVE" />
            <el-option label="已废止" value="REVOKED" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文">
          <div class="md-split">
            <el-input
              v-model="editForm.content"
              type="textarea"
              :rows="16"
              placeholder="支持 Markdown…"
              class="md-editor"
            />
            <div class="md-preview" v-html="mdHtml(editForm.content)" />
          </div>
        </el-form-item>
      </el-form>
      <div v-else class="md-preview readonly" v-html="mdHtml(current?.content || '')" />
      <template v-if="canManage" #footer>
        <el-button @click="dlgView = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存文本</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlgText" title="粘贴文本入库" width="640px">
      <el-form label-width="70px">
        <el-form-item label="标题">
          <el-input v-model="textForm.title" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="textForm.category" style="width: 200px">
            <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="textForm.content" type="textarea" :rows="12" placeholder="粘贴 Markdown 或纯文本…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgText = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitText">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlgUpload" title="选择分类后上传" width="420px">
      <el-form label-width="70px">
        <el-form-item label="分类">
          <el-select v-model="uploadCategory" style="width: 100%">
            <el-option v-for="c in categories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <span>{{ pendingFile?.name || '—' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgUpload = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- 解析入库 / 重建索引：切片参数 -->
    <el-dialog
      v-model="dlgBuild"
      :title="buildForm.rebuild ? '重建向量索引' : '解析入库'"
      width="480px"
      destroy-on-close
    >
      <el-alert
        :type="buildForm.rebuild ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
        :title="buildForm.rebuild
          ? '将清理该文档旧切片与向量，再按新参数重新切分入库'
          : '按下方参数切分正文并写入向量索引'"
      />
      <el-form label-width="100px">
        <el-form-item label="文档">
          <span>{{ buildForm.title || '—' }}</span>
        </el-form-item>
        <el-form-item label="切片大小" required>
          <el-input-number v-model="buildForm.chunkSize" :min="80" :max="4000" :step="20" />
          <span class="unit">字符</span>
        </el-form-item>
        <el-form-item label="重叠长度" required>
          <el-input-number v-model="buildForm.overlap" :min="0" :max="2000" :step="10" />
          <span class="unit">字符</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgBuild = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmBuild">开始执行</el-button>
      </template>
    </el-dialog>

    <!-- 批量重建 -->
    <el-dialog v-model="dlgBatch" title="批量重建向量索引" width="480px" destroy-on-close>
      <p class="batch-tip">已选 {{ selectedIds.length }} 份文档，将逐个清理旧切片并重建。</p>
      <el-form label-width="100px">
        <el-form-item label="切片大小">
          <el-input-number v-model="batchForm.chunkSize" :min="80" :max="4000" :step="20" />
        </el-form-item>
        <el-form-item label="重叠长度">
          <el-input-number v-model="batchForm.overlap" :min="0" :max="2000" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgBatch = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmBatchIndex">开始重建</el-button>
      </template>
    </el-dialog>

    <!-- 索引日志（含切片明细） -->
    <el-dialog v-model="dlgLog" title="索引构建日志" width="900px" destroy-on-close>
      <el-tabs v-model="logTab">
        <el-tab-pane label="构建步骤" name="steps">
          <el-table :data="logs" stripe max-height="420" empty-text="暂无日志">
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column prop="step" label="步骤" width="100" />
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.resultLabel || (row.success ? '成功' : '失败') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="90">
              <template #default="{ row }">{{ row.durationMs ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="chunkNo" label="切片#" width="70">
              <template #default="{ row }">{{ row.chunkNo != null ? row.chunkNo : '—' }}</template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column prop="chunkPreview" label="切片内容" min-width="200" show-overflow-tooltip />
            <el-table-column prop="errorDetail" label="异常信息" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="{ err: row.errorDetail }">{{ row.errorDetail || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="切片明细" name="chunks">
          <el-table :data="chunks" stripe max-height="420" empty-text="暂无切片（请先解析入库）">
            <el-table-column prop="chunkNo" label="#" width="60" />
            <el-table-column prop="tokenEst" label="估Token" width="90" />
            <el-table-column prop="embeddingDim" label="维度" width="80" />
            <el-table-column label="向量化" width="100">
              <template #default="{ row }">
                <el-tag :type="row.hasVector ? 'success' : 'info'" size="small">
                  {{ row.vectorResult || (row.hasVector ? '已向量化' : '无向量') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="切片内容" min-width="320" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  batchBuildKbIndex,
  batchDeleteKbDocs,
  buildKbIndex,
  deleteKbDoc,
  getKbDoc,
  listKbCategories,
  listKbDocChunks,
  listKbDocs,
  listKbIndexLogs,
  updateKbDoc,
  uploadKbDoc,
  uploadKbDocFile,
} from '@/api/kbDoc'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const categories = ref<any[]>([])
const category = ref('')
const keyword = ref('')
const docStatus = ref('')
const selectedRows = ref<any[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.id).filter(Boolean))
const fileRef = ref<HTMLInputElement | null>(null)
const dlgView = ref(false)
const dlgLog = ref(false)
const dlgText = ref(false)
const dlgUpload = ref(false)
const dlgBuild = ref(false)
const dlgBatch = ref(false)
const current = ref<any>(null)
const logs = ref<any[]>([])
const chunks = ref<any[]>([])
const logTab = ref('steps')
const pendingFile = ref<File | null>(null)
const uploadCategory = ref('policy')
const editForm = reactive({ title: '', category: 'policy', content: '', docStatus: 'DRAFT' })
const textForm = reactive({ title: '', category: 'policy', content: '' })
const buildForm = reactive({
  id: 0,
  title: '',
  rebuild: false,
  chunkSize: 400,
  overlap: 80,
})
const batchForm = reactive({ chunkSize: 400, overlap: 80 })

function indexLabel(s?: string) {
  if (s === 'READY') return '已就绪'
  if (s === 'BUILDING') return '构建中'
  if (s === 'FAILED') return '失败'
  return '未构建'
}
function statusLabel(s?: string) {
  if (s === 'DRAFT') return '草稿'
  if (s === 'REVOKED') return '已废止'
  return '生效中'
}
function statusTag(s?: string) {
  if (s === 'DRAFT') return 'info'
  if (s === 'REVOKED') return 'danger'
  return 'success'
}

/** 轻量 Markdown → HTML（预览用，无第三方依赖） */
function mdHtml(src?: string) {
  const raw = String(src || '')
  const escaped = raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const html = escaped
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^\- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>\n?)+/g, (m) => `<ul>${m}</ul>`)
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/\n/g, '<br/>')
  return `<p>${html}</p>`
}

function onSelectionChange(rows: any[]) {
  selectedRows.value = rows || []
}

async function loadCats() {
  categories.value = (await listKbCategories(true)) || []
  if (!uploadCategory.value && categories.value[0]) {
    uploadCategory.value = categories.value[0].code
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listKbDocs({
        category: category.value || undefined,
        keyword: keyword.value.trim() || undefined,
        docStatus: docStatus.value || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

function pickFile() {
  fileRef.value?.click()
}

function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  pendingFile.value = file
  uploadCategory.value = category.value || categories.value[0]?.code || 'policy'
  dlgUpload.value = true
}

async function confirmUpload() {
  if (!pendingFile.value) return
  saving.value = true
  try {
    await uploadKbDocFile(pendingFile.value, uploadCategory.value)
    ElMessage.success('上传并解析成功，请点击「解析入库」构建向量')
    dlgUpload.value = false
    pendingFile.value = null
    load()
  } finally {
    saving.value = false
  }
}

function openText() {
  textForm.title = ''
  textForm.content = ''
  textForm.category = category.value || categories.value[0]?.code || 'policy'
  dlgText.value = true
}

async function submitText() {
  if (!textForm.content.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  saving.value = true
  try {
    await uploadKbDoc({
      title: textForm.title || '文本文档',
      fileName: (textForm.title || 'text') + '.md',
      content: textForm.content,
      category: textForm.category,
    })
    ElMessage.success('上传成功')
    dlgText.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function viewDoc(row: any) {
  current.value = await getKbDoc(row.id)
  editForm.title = current.value?.title || ''
  editForm.category = current.value?.category || 'policy'
  editForm.content = current.value?.content || ''
  editForm.docStatus = current.value?.docStatus || 'ACTIVE'
  dlgView.value = true
}

async function saveEdit() {
  if (!current.value?.id) return
  saving.value = true
  try {
    await updateKbDoc(current.value.id, { ...editForm })
    ElMessage.success('已保存，旧向量已清理，请「重建索引」')
    dlgView.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function viewLogs(row: any) {
  logTab.value = 'steps'
  logs.value = (await listKbIndexLogs(row.id).catch(() => [])) || []
  chunks.value = (await listKbDocChunks(row.id).catch(() => [])) || []
  dlgLog.value = true
}

function openBuild(row: any, rebuild: boolean) {
  buildForm.id = row.id
  buildForm.title = row.title
  buildForm.rebuild = rebuild
  buildForm.chunkSize = 400
  buildForm.overlap = 80
  dlgBuild.value = true
}

async function confirmBuild() {
  if (!buildForm.id) return
  if (buildForm.overlap >= buildForm.chunkSize) {
    ElMessage.warning('重叠长度须小于切片大小')
    return
  }
  saving.value = true
  try {
    await buildKbIndex(buildForm.id, {
      chunkSize: buildForm.chunkSize,
      overlap: buildForm.overlap,
      rebuild: buildForm.rebuild,
    })
    ElMessage.success(buildForm.rebuild ? '索引已重建' : '索引构建完成')
    dlgBuild.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '构建失败')
  } finally {
    saving.value = false
  }
}

function openBatchIndex() {
  if (!selectedIds.value.length) return
  batchForm.chunkSize = 400
  batchForm.overlap = 80
  dlgBatch.value = true
}

async function confirmBatchIndex() {
  saving.value = true
  try {
    const res: any = await batchBuildKbIndex({
      ids: selectedIds.value,
      chunkSize: batchForm.chunkSize,
      overlap: batchForm.overlap,
      rebuild: true,
    })
    ElMessage.success(res?.message || '批量重建完成')
    dlgBatch.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '批量重建失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '提示')
  await deleteKbDoc(row.id)
  ElMessage.success('已删除')
  load()
}

async function batchRemove() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 份文档？`, '批量删除')
  const res: any = await batchDeleteKbDocs(selectedIds.value)
  ElMessage.success(res?.message || '已删除')
  load()
}

onMounted(async () => {
  await loadCats()
  await load()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; align-items: center; }
.unit { margin-left: 8px; color: #94a3b8; font-size: 13px; }
.batch-tip { margin: 0 0 12px; color: #64748b; font-size: 13px; }
.err { color: #dc2626; }
.edit-form { margin-top: 4px; }
.md-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}
.md-editor :deep(textarea) { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.md-preview {
  min-height: 340px;
  max-height: 420px;
  overflow: auto;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.65;
  color: #1f2a28;
}
.md-preview.readonly { max-height: 60vh; }
.md-preview :deep(h1) { font-size: 20px; margin: 0 0 10px; }
.md-preview :deep(h2) { font-size: 17px; margin: 14px 0 8px; }
.md-preview :deep(h3) { font-size: 15px; margin: 12px 0 6px; }
.md-preview :deep(ul) { padding-left: 20px; margin: 8px 0; }
.md-preview :deep(code) {
  background: #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.md-preview :deep(p) { margin: 0 0 8px; }
@media (max-width: 900px) {
  .md-split { grid-template-columns: 1fr; }
}
</style>
