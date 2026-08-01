<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>活动列表</h2>
        <p class="sub">图文发布、AI 审核摘要、标签定向推送；数据按小区隔离</p>
      </div>
      <div class="ops">
        <el-button v-if="canManage" type="primary" @click="open()">新增活动</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterCategory" clearable placeholder="分类" style="width: 140px" @change="load">
        <el-option v-for="c in categories" :key="c.name" :label="c.name" :value="c.name" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题/内容/标签"
        style="width: 220px"
        @keyup.enter="load"
      />
      <el-button @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无活动">
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.coverUrl"
            :src="fileUrl(row.coverUrl)"
            :preview-src-list="[fileUrl(row.coverUrl)]"
            class="cover-thumb"
            fit="cover"
          >
            <template #error>
              <div class="img-fail">失败</div>
            </template>
          </el-image>
          <span v-else class="no-cover">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="活动标题" min-width="140" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="summary" label="AI摘要" min-width="160" show-overflow-tooltip />
      <el-table-column prop="tags" label="标签" width="120" show-overflow-tooltip />
      <el-table-column prop="location" label="地点" width="110" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始" width="160" />
      <el-table-column label="报名" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="goSignups(row)">
            {{ row.signedCount || 0 }}/{{ row.maxPeople || '-' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="phase" label="阶段" width="90" />
      <el-table-column label="审核" width="80">
        <template #default="{ row }">
          <el-tag :type="row.aiAuditPass === 1 ? 'success' : 'info'" size="small">
            {{ row.aiAuditPass === 1 ? '通过' : '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pushCount" label="推送" width="70" />
      <el-table-column label="上架" width="70">
        <template #default="{ row }">{{ row.status === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goSignups(row)">报名名单</el-button>
          <el-button v-if="canManage" link type="primary" @click="open(row)">编辑</el-button>
          <el-button v-if="canManage" link type="success" @click="doPush(row)">定向推送</el-button>
          <el-button
            v-if="canManage"
            link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggle(row)"
          >{{ row.status === 1 ? '下架' : '启用' }}</el-button>
          <el-button v-if="canManage" link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dlg"
      :title="form.id ? '编辑活动' : '新增活动'"
      width="720px"
      destroy-on-close
      class="activity-dlg"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" label-position="right">
        <div class="form-group">
          <div class="form-group-title">基础信息</div>
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="请输入活动标题" />
          </el-form-item>
          <el-form-item label="活动分类" prop="category">
            <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%" @change="onCategoryChange">
              <el-option v-for="c in categories" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="封面图片">
            <div class="cover-row">
              <el-image
                v-if="form.coverUrl"
                :src="fileUrl(form.coverUrl)"
                fit="cover"
                class="cover-preview"
                :preview-src-list="[fileUrl(form.coverUrl)]"
              />
              <div v-else class="cover-preview empty">暂无封面</div>
              <div class="cover-actions">
                <el-upload
                  :show-file-list="false"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  :http-request="onUploadCover"
                  :disabled="uploading"
                >
                  <el-button type="primary" :loading="uploading">
                    {{ form.coverUrl ? '重新上传' : '上传封面' }}
                  </el-button>
                </el-upload>
                <el-button v-if="form.coverUrl" link type="danger" @click="form.coverUrl = ''">清除</el-button>
                <el-input
                  v-model="form.coverUrl"
                  class="cover-url"
                  clearable
                  placeholder="也可粘贴封面 URL"
                />
              </div>
            </div>
          </el-form-item>
          <el-form-item label="地点" prop="location">
            <el-input v-model="form.location" placeholder="如 小区广场 / 活动中心" />
          </el-form-item>
        </div>

        <div class="form-group">
          <div class="form-group-title">时间与名额</div>
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择开始时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择结束时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="报名截止">
            <el-date-picker
              v-model="form.signupEnd"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="可选，默认同开始时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="人数上限" prop="maxPeople">
            <el-input-number v-model="form.maxPeople" :min="1" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="允许取消报名">
            <el-switch
              v-model="form.allowCancel"
              :active-value="1"
              :inactive-value="0"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>
        </div>

        <div class="form-group">
          <div class="form-group-title">内容与 AI</div>
          <el-form-item label="活动详情" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="6"
              maxlength="5000"
              show-word-limit
              placeholder="支持图文描述：活动亮点、流程、注意事项等"
            />
          </el-form-item>
          <el-form-item label="AI摘要">
            <el-input v-model="form.summary" maxlength="100" show-word-limit placeholder="发布时自动生成，可手动修改" />
          </el-form-item>
          <el-form-item label="活动标签">
            <el-input v-model="form.tags" placeholder="逗号分隔，用于 AI 定向推送，如 亲子,文体" />
          </el-form-item>
          <el-form-item label="定向规则">
            <el-select v-model="form.targetRule" clearable placeholder="不选则按标签匹配 / 全员" style="width: 100%">
              <el-option label="全员" value="all" />
              <el-option label="老年住户" value="senior" />
              <el-option label="有孩家庭" value="hasChild" />
              <el-option label="亲子兴趣" value="亲子" />
            </el-select>
          </el-form-item>
          <el-form-item label="活动状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">上架（发布审核）</el-radio>
              <el-radio :value="0">下架草稿</el-radio>
            </el-radio-group>
          </el-form-item>
          <p class="tip">上架保存将触发 AI 内容审核并自动生成摘要；报名信息会同步至 AI 智能日程。</p>
        </div>
      </el-form>
      <template #footer>
        <el-button :loading="auditing" @click="runAudit">AI 审核+生成摘要</el-button>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="needAudit && !auditOk" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileUrl, uploadFile } from '@/api/file'
import {
  auditActivityContent,
  deleteActivity,
  listActivities,
  listActivityCategories,
  saveActivity,
  setActivityStatus,
  targetPushActivity,
} from '@/api/activity'

const user = useUserStore()
const router = useRouter()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)

function goSignups(row: any) {
  router.push({ path: '/property/activity/signups', query: { activityId: String(row.id) } })
}
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const auditing = ref(false)
const auditOk = ref(false)
const list = ref<any[]>([])
const categories = ref<any[]>([])
const filterCategory = ref('')
const keyword = ref('')
const dlg = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: null as number | null,
  title: '',
  category: '' as string,
  coverUrl: '',
  location: '',
  startTime: '' as string,
  endTime: '' as string,
  signupEnd: '' as string,
  maxPeople: 50,
  allowCancel: 1,
  content: '',
  summary: '',
  tags: '',
  targetRule: '' as string,
  status: 1,
})

const needAudit = computed(() => !form.id && form.status === 1)

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择活动分类', trigger: 'change' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_r, v, cb) => {
        if (v && form.startTime && String(v) < String(form.startTime)) {
          cb(new Error('结束时间不能早于开始时间'))
        } else {
          cb()
        }
      },
      trigger: 'change',
    },
  ],
  maxPeople: [{ required: true, message: '请设置人数上限', trigger: 'change' }],
  content: [{ required: true, message: '请填写活动详情', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function emptyForm() {
  Object.assign(form, {
    id: null,
    title: '',
    category: '',
    coverUrl: '',
    location: '',
    startTime: '',
    endTime: '',
    signupEnd: '',
    maxPeople: 50,
    allowCancel: 1,
    content: '',
    summary: '',
    tags: '',
    targetRule: '',
    status: 1,
  })
  auditOk.value = false
}

function normTime(v?: string | null) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 19)
}

async function loadCategories() {
  try {
    categories.value = (await listActivityCategories(true)) || []
  } catch {
    categories.value = []
  }
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listActivities(true, {
        category: filterCategory.value || undefined,
        keyword: keyword.value.trim() || undefined,
      })) || []
  } finally {
    loading.value = false
  }
}

function onCategoryChange(name: string) {
  const c = categories.value.find((x) => x.name === name)
  if (c?.tags && !form.tags) {
    form.tags = c.tags
  }
}

function open(row?: any) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      category: row.category || '',
      coverUrl: row.coverUrl || '',
      location: row.location || '',
      startTime: normTime(row.startTime),
      endTime: normTime(row.endTime),
      signupEnd: normTime(row.signupEnd),
      maxPeople: row.maxPeople || 50,
      allowCancel: row.allowCancel == null ? 1 : Number(row.allowCancel),
      content: row.content || '',
      summary: row.summary || '',
      tags: row.tags || '',
      targetRule: row.targetRule || '',
      status: row.status == null ? 1 : Number(row.status),
    })
    auditOk.value = true
  } else {
    emptyForm()
  }
  dlg.value = true
}

async function onUploadCover(opt: UploadRequestOptions) {
  const raw = opt.file as File
  if (!raw) return
  uploading.value = true
  try {
    const url = await uploadFile(raw)
    form.coverUrl = url
    ElMessage.success('封面已上传')
    opt.onSuccess?.(url as any)
  } catch (e) {
    opt.onError?.(e as Error)
  } finally {
    uploading.value = false
  }
}

async function runAudit() {
  const text = `${form.title}\n${form.content}`.trim()
  if (!text) {
    ElMessage.error('请先填写标题和详情')
    return
  }
  auditing.value = true
  try {
    const res = await auditActivityContent(text)
    if (!res?.pass) {
      auditOk.value = false
      ElMessage.error('审核不通过：' + (res?.hitWords || []).join(','))
      return
    }
    form.summary = (res.summary || '').slice(0, 100)
    auditOk.value = true
    ElMessage.success('审核通过，已生成摘要')
  } catch {
    auditOk.value = false
  } finally {
    auditing.value = false
  }
}

async function save() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  if (form.endTime && form.startTime && form.endTime < form.startTime) {
    ElMessage.warning('结束时间不能早于开始时间')
    return
  }
  if (needAudit.value && !auditOk.value) {
    ElMessage.error('上架发布前请先通过 AI 审核')
    return
  }
  saving.value = true
  try {
    await saveActivity({
      id: form.id || undefined,
      title: form.title.trim(),
      category: form.category,
      coverUrl: form.coverUrl || '',
      location: form.location.trim(),
      startTime: form.startTime,
      endTime: form.endTime,
      signupEnd: form.signupEnd || undefined,
      maxPeople: form.maxPeople,
      allowCancel: form.allowCancel,
      content: form.content,
      summary: form.summary.trim() || undefined,
      tags: form.tags.trim() || undefined,
      targetRule: form.targetRule || undefined,
      status: form.status,
    })
    dlg.value = false
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: any) {
  await setActivityStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已下架' : '已启用')
  load()
}

async function doPush(row: any) {
  const rule = row.targetRule || row.tags || 'all'
  const res = await targetPushActivity(row.id, rule)
  ElMessage.success(`已定向推送 ${res?.pushed || 0} 人`)
  load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(
    `确认删除活动「${row.title}」？报名记录将一并删除，且不可恢复。`,
    '删除确认',
    { type: 'warning' },
  )
  await deleteActivity(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.form-group {
  margin-bottom: 8px;
  padding: 12px 12px 4px;
  background: #f8fafc;
  border-radius: 10px;
}
.form-group-title {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  margin: 0 0 12px 4px;
}
.tip { margin: 0 0 8px 110px; color: #64748b; font-size: 12px; }
.cover-thumb {
  width: 48px;
  height: 36px;
  border-radius: 4px;
  display: block;
}
.img-fail,
.no-cover {
  width: 48px;
  height: 36px;
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  border-radius: 4px;
}
.cover-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  width: 100%;
}
.cover-preview {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  background: #eef2f7;
  flex-shrink: 0;
  overflow: hidden;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 12px;
  border: 1px dashed #cbd5e1;
}
.cover-actions {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.cover-url { width: 100%; margin-top: 4px; }
</style>
