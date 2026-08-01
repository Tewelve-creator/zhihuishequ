<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>动态管理</h2>
        <p class="sub">查看业主发布的全部图文动态，支持置顶、屏蔽、删除违规帖子；操作全程留痕</p>
      </div>
      <div class="hdr-actions">
        <el-button @click="showLogs = true">操作日志</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="filter" @change="load">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="normal">正常</el-radio-button>
        <el-radio-button value="pinned">已置顶</el-radio-button>
        <el-radio-button value="hidden">已屏蔽</el-radio-button>
        <el-radio-button value="deleted">已删除</el-radio-button>
        <el-radio-button value="muted">禁言用户</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无动态">
      <el-table-column prop="nickname" label="发布住户" width="110" />
      <el-table-column prop="houseLabel" label="楼栋房号" width="140">
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column prop="content" label="动态正文" min-width="200" show-overflow-tooltip />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <div v-if="row.images?.length" class="thumbs">
            <el-image
              v-for="(img, i) in row.images.slice(0, 3)"
              :key="i"
              :src="fileUrl(img)"
              :preview-src-list="row.images.map(fileUrl)"
              :initial-index="i"
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="likeCount" label="点赞" width="70" />
      <el-table-column prop="commentCount" label="评论" width="70" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.deleted === 1" type="info" size="small">已删除</el-tag>
          <el-tag v-else-if="row.hidden" type="warning" size="small">已屏蔽</el-tag>
          <el-tag v-else-if="row.pinned" type="danger" size="small">置顶</el-tag>
          <el-tag v-else-if="row.userMuted" type="danger" size="small">已禁言</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canManage && row.deleted !== 1"
            link
            type="primary"
            @click="onPin(row)"
          >{{ row.pinned ? '取消置顶' : '置顶' }}</el-button>
          <el-button
            v-if="canManage && row.deleted !== 1"
            link
            type="warning"
            @click="onHide(row)"
          >{{ row.hidden ? '取消屏蔽' : '屏蔽' }}</el-button>
          <el-button
            v-if="canManage && row.deleted !== 1"
            link
            type="danger"
            @click="onDeletePost(row)"
          >删除</el-button>
          <el-button
            v-if="canManage"
            link
            type="warning"
            @click="openMute(row)"
          >{{ row.userMuted ? '解禁' : '禁言' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailDlg" title="动态详情" width="640px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="住户">{{ detail.nickname }}</el-descriptions-item>
          <el-descriptions-item label="房屋">{{ detail.houseLabel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="互动">赞 {{ detail.likeCount || 0 }} · 评 {{ detail.commentCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="正文" :span="2">
            <div class="detail-content">{{ detail.content }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.images?.length" label="图片" :span="2">
            <div class="detail-imgs">
              <el-image
                v-for="(img, i) in detail.images"
                :key="i"
                :src="fileUrl(img)"
                :preview-src-list="detail.images.map(fileUrl)"
                :initial-index="i"
                fit="cover"
                class="detail-img"
              />
            </div>
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="c-title">全部评论</h4>
        <div v-if="!(detail.comments || []).length" class="empty-c">暂无评论</div>
        <div v-for="c in detail.comments || []" :key="c.id" class="c-item" :class="{ del: c.deleted === 1 }">
          <div class="c-hd">
            <span class="c-name">{{ c.nickname }}</span>
            <span class="c-time">{{ c.createTime }}</span>
            <el-tag v-if="c.deleted === 1" size="small" type="info">已删除</el-tag>
            <el-button
              v-else-if="canManage"
              link
              type="danger"
              size="small"
              @click="onDeleteComment(c)"
            >删除评论</el-button>
          </div>
          <div class="c-body">{{ c.content }}</div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="muteDlg" title="住户禁言" width="440px">
      <el-form label-width="90px">
        <el-form-item label="住户">{{ muteTarget?.nickname }}（#{{ muteTarget?.userId }}）</el-form-item>
        <el-form-item label="禁言时长">
          <el-radio-group v-model="muteForm.duration">
            <el-radio value="forever">永久</el-radio>
            <el-radio value="3d">3天</el-radio>
            <el-radio value="7d">7天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="muteForm.reason" type="textarea" :rows="3" placeholder="广告/争吵/违规等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="muteDlg = false">取消</el-button>
        <el-button type="warning" :loading="muteLoading" @click="submitMute">确认禁言</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showLogs" title="操作日志" size="480px" @open="loadLogs">
      <el-timeline v-if="logs.length">
        <el-timeline-item
          v-for="l in logs"
          :key="l.id"
          :timestamp="l.createTime"
          placement="top"
        >
          <div class="log-row">
            <strong>{{ l.operatorName || '管理员' }}</strong>
            · {{ l.actionLabel || l.action }}
            <div class="log-detail">{{ l.detail }}</div>
            <div v-if="l.targetUserId" class="log-meta">目标住户 #{{ l.targetUserId }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作记录" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileUrl } from '@/api/file'
import {
  listAppealAdmin,
  getAppealAdminDetail,
  deleteAppealAdmin,
  deleteAppealCommentAdmin,
  pinAppealAdmin,
  hideAppealAdmin,
  muteAppealUser,
  unmuteAppealUser,
  listAppealOpLogs,
} from '@/api/appeal'

const user = useUserStore()
const canManage = computed(
  () =>
    user.role === 'property_admin' ||
    user.role === 'grid_worker' ||
    user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<any[]>([])
const filter = ref('all')

const detailDlg = ref(false)
const detail = ref<any>(null)

const muteDlg = ref(false)
const muteTarget = ref<any>(null)
const muteLoading = ref(false)
const muteForm = reactive({ duration: '7d', reason: '' })

const showLogs = ref(false)
const logs = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    list.value = (await listAppealAdmin(filter.value)) || []
  } finally {
    loading.value = false
  }
}

async function openDetail(row: any) {
  detail.value = await getAppealAdminDetail(row.id)
  detailDlg.value = true
}

async function onPin(row: any) {
  const next = !row.pinned
  await pinAppealAdmin(row.id, next)
  ElMessage.success(next ? '已置顶' : '已取消置顶')
  load()
}

async function onHide(row: any) {
  if (row.hidden) {
    await hideAppealAdmin(row.id, false)
    ElMessage.success('已取消屏蔽')
    load()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请填写屏蔽原因（可选）', '屏蔽动态', {
      confirmButtonText: '屏蔽',
      cancelButtonText: '取消',
      inputPlaceholder: '广告/不当内容…',
    })
    await hideAppealAdmin(row.id, true, value || '屏蔽')
    ElMessage.success('已屏蔽')
    load()
  } catch {
    /* cancel */
  }
}

async function onDeletePost(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写删除原因（可选）', '违规删除动态', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      inputPlaceholder: '广告/争吵/违规言论…',
    })
    await deleteAppealAdmin(row.id, value || '违规删除')
    ElMessage.success('已删除')
    load()
  } catch {
    /* cancel */
  }
}

async function onDeleteComment(c: any) {
  await ElMessageBox.confirm('确认删除该评论？', '提示')
  await deleteAppealCommentAdmin(c.id, '违规评论')
  ElMessage.success('评论已删除')
  if (detail.value?.id) {
    detail.value = await getAppealAdminDetail(detail.value.id)
  }
  load()
}

async function openMute(row: any) {
  if (row.userMuted) {
    await ElMessageBox.confirm(`确认解除住户「${row.nickname}」的禁言？`, '解除禁言')
    await unmuteAppealUser(row.userId)
    ElMessage.success('已解除禁言')
    load()
    return
  }
  muteTarget.value = row
  muteForm.duration = '7d'
  muteForm.reason = ''
  muteDlg.value = true
}

async function submitMute() {
  if (!muteTarget.value?.userId) return
  muteLoading.value = true
  try {
    await muteAppealUser({
      userId: muteTarget.value.userId,
      duration: muteForm.duration,
      reason: muteForm.reason,
    })
    ElMessage.success('已禁言')
    muteDlg.value = false
    load()
  } finally {
    muteLoading.value = false
  }
}

async function loadLogs() {
  logs.value = (await listAppealOpLogs(100)) || []
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.hdr-actions { display: flex; gap: 8px; }
.filter-bar { margin-bottom: 12px; }
.thumbs { display: flex; gap: 4px; }
.thumb { width: 36px; height: 36px; border-radius: 4px; }
.detail-content { white-space: pre-wrap; line-height: 1.6; color: #334155; }
.detail-imgs { display: flex; flex-wrap: wrap; gap: 8px; }
.detail-img { width: 96px; height: 96px; border-radius: 6px; }
.c-title { margin: 20px 0 10px; font-size: 15px; }
.empty-c { color: #94a3b8; font-size: 13px; }
.c-item { padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.c-item.del { opacity: 0.55; }
.c-hd { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.c-name { font-weight: 600; color: #334155; }
.c-time { color: #94a3b8; font-size: 12px; flex: 1; }
.c-body { color: #475569; font-size: 13px; line-height: 1.5; }
.log-row { font-size: 13px; color: #334155; }
.log-detail { margin-top: 4px; color: #64748b; }
.log-meta { margin-top: 2px; color: #94a3b8; font-size: 12px; }
</style>
