<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>评论管理</h2>
        <p class="sub">统一管理所有动态评论，可删除不良评论；操作全程留痕</p>
      </div>
      <div class="hdr-actions">
        <el-button @click="showLogs = true">操作日志</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索评论/昵称/动态内容"
        style="width: 260px"
        @keyup.enter="load"
      />
      <el-select v-model="deletedFilter" clearable placeholder="状态" style="width: 140px" @change="load">
        <el-option label="正常评论" :value="false" />
        <el-option label="已删除" :value="true" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无评论">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="nickname" label="评论人" width="110" />
      <el-table-column prop="houseLabel" label="房屋" width="130">
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="content" label="评论内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="postContent" label="所属动态" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="muted">{{ row.postNickname ? row.postNickname + '：' : '' }}{{ row.postContent || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.deleted === 1" type="info" size="small">已删除</el-tag>
          <el-tag v-else type="success" size="small">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canManage && row.deleted !== 1"
            link
            type="danger"
            @click="onDelete(row)"
          >删除</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

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
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无操作记录" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listAppealCommentsAdmin,
  deleteAppealCommentAdmin,
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
const keyword = ref('')
const deletedFilter = ref<boolean | undefined>(false)
const showLogs = ref(false)
const logs = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    list.value =
      (await listAppealCommentsAdmin({
        keyword: keyword.value || undefined,
        deletedOnly: deletedFilter.value,
      })) || []
  } finally {
    loading.value = false
  }
}

async function onDelete(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写删除原因（可选）', '删除不良评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      inputPlaceholder: '辱骂/广告/不当言论…',
    })
    await deleteAppealCommentAdmin(row.id, value || '不良评论')
    ElMessage.success('已删除')
    load()
  } catch {
    /* cancel */
  }
}

async function loadLogs() {
  logs.value = ((await listAppealOpLogs(100)) || []).filter(
    (l: any) => l.action === 'DELETE_COMMENT' || String(l.actionLabel || '').includes('评论'),
  )
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.hdr-actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; font-size: 13px; }
.log-row { font-size: 13px; color: #334155; }
.log-detail { margin-top: 4px; color: #64748b; }
</style>
