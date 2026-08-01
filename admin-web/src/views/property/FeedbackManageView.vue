<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>意见反馈</h2>
        <p class="sub">隶属于报事报修 · 通过后自动生成报事工单，进入工单列表处理</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="处理状态" style="width: 140px" @change="onSearch">
        <el-option label="待处理" value="PENDING" />
        <el-option label="处理中" value="PROCESSING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已办结" value="DONE" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="编号/姓名/房号/电话/标题"
        style="width: 240px"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedList" stripe empty-text="暂无反馈">
      <el-table-column prop="feedbackNo" label="反馈编号" width="150" />
      <el-table-column prop="ownerName" label="业主姓名" width="100">
        <template #default="{ row }">{{ row.ownerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="houseLabel" label="楼栋房号" width="140">
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" width="120">
        <template #default="{ row }">{{ row.phone || '—' }}</template>
      </el-table-column>
      <el-table-column prop="feedbackType" label="反馈类型" width="110" />
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="关联工单" width="100">
        <template #default="{ row }">
          <el-button v-if="row.workOrderId" link type="primary" @click="goOrder(row.workOrderId)">
            #{{ row.workOrderId }}
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="处理状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row.statusTagType as any) || statusTag(row.status)" size="small" effect="light">
            {{ row.statusLabel || statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          <el-button
            v-if="row.status !== 'APPROVED' && row.status !== 'DONE'"
            link
            type="success"
            @click="quickApprove(row)"
          >通过</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :total="list.length"
      />
    </div>

    <el-dialog v-model="visible" title="反馈详情" width="720px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="反馈编号">{{ detail.feedbackNo }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <el-tag :type="(detail.statusTagType as any) || statusTag(detail.status)" size="small">
              {{ detail.statusLabel || statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="业主姓名">{{ detail.ownerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="楼栋房号">{{ detail.houseLabel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="反馈类型">{{ detail.feedbackType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间" :span="2">{{ detail.createTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联工单" :span="2">
            <el-button v-if="detail.workOrderId" link type="primary" @click="goOrder(detail.workOrderId)">
              工单 #{{ detail.workOrderId }}（已进入工单列表）
            </el-button>
            <span v-else>尚未通过转工单</span>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="反馈内容" :span="2">
            <div class="content-text">{{ detail.content || '—' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="业主图片" :span="2">
            <div v-if="detail.imageList?.length" class="imgs">
              <el-image
                v-for="(img, i) in detail.imageList"
                :key="i"
                :src="fileUrl(img)"
                :preview-src-list="detail.imageList.map(fileUrl)"
                :initial-index="i"
                fit="cover"
                class="img"
              />
            </div>
            <span v-else>—</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section">
          <h4>物业处理</h4>
          <el-form label-width="90px">
            <el-form-item label="工单状态">
              <el-select v-model="form.status" style="width: 220px">
                <el-option label="待处理" value="PENDING" />
                <el-option label="处理中" value="PROCESSING" />
                <el-option label="已通过（转工单）" value="APPROVED" />
                <el-option label="已办结" value="DONE" />
              </el-select>
            </el-form-item>
            <el-form-item label="回复内容">
              <el-input
                v-model="form.replyContent"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                placeholder="填写对业主的回复说明"
              />
            </el-form-item>
          </el-form>
        </div>

        <div class="section">
          <h4>处理记录</h4>
          <el-timeline v-if="detail.logs?.length">
            <el-timeline-item
              v-for="log in detail.logs"
              :key="log.id"
              :timestamp="log.createTime"
              placement="top"
            >
              <div class="log-title">
                {{ log.actionLabel || log.action }}
                <span v-if="log.fromStatusLabel || log.toStatusLabel" class="log-status">
                  （{{ log.fromStatusLabel || '—' }} → {{ log.toStatusLabel || '—' }}）
                </span>
              </div>
              <div class="log-meta">操作人：{{ log.operatorName || '—' }}</div>
              <div class="log-content">{{ log.content || '—' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无处理记录" :image-size="64" />
        </div>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.status !== 'APPROVED' && detail.status !== 'DONE'"
          type="success"
          :loading="approving"
          @click="submitApprove"
        >通过并转工单</el-button>
        <el-button type="primary" :loading="saving" @click="submitHandle">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import { fileUrl } from '@/api/file'
import {
  approveFeedback,
  getFeedback,
  handleFeedback,
  listFeedback,
  type FeedbackItem,
} from '@/api/feedback'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const approving = ref(false)
const list = ref<FeedbackItem[]>([])
const status = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const detail = ref<FeedbackItem | null>(null)
const form = reactive({
  status: 'PROCESSING',
  replyContent: '',
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

function statusLabel(s?: string) {
  return (
    ({ PENDING: '待处理', PROCESSING: '处理中', APPROVED: '已通过', DONE: '已办结' } as Record<string, string>)[
      s || ''
    ] || s || '—'
  )
}
function statusTag(s?: string) {
  if (s === 'PENDING') return 'warning'
  if (s === 'PROCESSING') return 'primary'
  if (s === 'APPROVED') return 'success'
  if (s === 'DONE') return 'info'
  return 'info'
}

function goOrder(id?: number) {
  if (!id) return
  router.push({ path: '/property/report', query: { highlight: String(id) } })
}

function onSearch() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listFeedback({
        status: status.value || undefined,
        keyword: keyword.value || undefined,
      })) || []
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(row: FeedbackItem) {
  visible.value = true
  detail.value = null
  try {
    const d = await getFeedback(row.id)
    detail.value = d
    form.status = d.status || 'PROCESSING'
    form.replyContent = d.replyContent || ''
  } catch {
    detail.value = row
    form.status = row.status || 'PROCESSING'
    form.replyContent = row.replyContent || ''
  }
}

async function submitHandle() {
  if (!detail.value?.id) return
  if (!form.replyContent.trim() && form.status === detail.value.status) {
    ElMessage.warning('请填写回复内容或更改状态')
    return
  }
  saving.value = true
  try {
    const d = await handleFeedback(detail.value.id, {
      replyContent: form.replyContent.trim() || undefined,
      status: form.status,
    })
    detail.value = d
    form.replyContent = d.replyContent || form.replyContent
    form.status = d.status || form.status
    ElMessage.success(
      d.workOrderId && form.status === 'APPROVED'
        ? `已通过，工单 #${d.workOrderId} 已进入工单列表`
        : '已提交',
    )
    await load()
  } finally {
    saving.value = false
  }
}

async function submitApprove() {
  if (!detail.value?.id) return
  await ElMessageBox.confirm(
    '确认通过该意见反馈？通过后将自动生成报事工单，可在「工单列表」中查看和处理。',
    '通过并转工单',
    { type: 'warning' },
  )
  approving.value = true
  try {
    const d = await approveFeedback(detail.value.id, {
      replyContent: form.replyContent.trim() || undefined,
    })
    detail.value = d
    form.status = d.status || 'APPROVED'
    form.replyContent = d.replyContent || form.replyContent
    ElMessage.success(`已通过，工单 #${d.workOrderId || '—'} 已进入工单列表`)
    await load()
  } finally {
    approving.value = false
  }
}

async function quickApprove(row: FeedbackItem) {
  await ElMessageBox.confirm(
    `确认通过「${row.title}」？将自动生成报事工单进入工单列表。`,
    '通过并转工单',
    { type: 'warning' },
  )
  const d = await approveFeedback(row.id)
  ElMessage.success(`已通过，工单 #${d.workOrderId || '—'} 已进入工单列表`)
  await load()
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      反馈编号: r.feedbackNo || '',
      业主姓名: r.ownerName || '',
      楼栋房号: r.houseLabel || '',
      联系电话: r.phone || '',
      反馈类型: r.feedbackType || '',
      标题: r.title || '',
      提交时间: r.createTime || '',
      处理状态: r.statusLabel || statusLabel(r.status),
      关联工单: r.workOrderId || '',
      回复内容: r.replyContent || '',
    })),
    '意见反馈.xlsx',
  )
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.content-text { white-space: pre-wrap; line-height: 1.6; }
.imgs { display: flex; flex-wrap: wrap; gap: 8px; }
.img { width: 72px; height: 72px; border-radius: 8px; }
.section { margin-top: 18px; }
.section h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #334155;
}
.log-title { font-weight: 600; color: #0f172a; }
.log-status { color: #64748b; font-weight: 400; font-size: 13px; }
.log-meta { color: #94a3b8; font-size: 12px; margin: 2px 0 4px; }
.log-content { color: #475569; font-size: 13px; line-height: 1.5; white-space: pre-wrap; }
.muted { color: #94a3b8; }
</style>
