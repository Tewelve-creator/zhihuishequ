<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>举报管理</h2>
        <p class="sub">处理业主提交的动态、评论举报工单，记录处理结果；操作全程留痕</p>
      </div>
      <div class="hdr-actions">
        <el-button @click="showLogs = true">操作日志</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="DONE">已处理</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无举报工单">
      <el-table-column prop="id" label="工单号" width="90" />
      <el-table-column prop="reporterName" label="举报人" width="110" />
      <el-table-column prop="targetTypeLabel" label="类型" width="80" />
      <el-table-column prop="targetId" label="对象ID" width="90" />
      <el-table-column prop="reason" label="举报原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="contentSnapshot" label="内容摘要" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'PENDING'" type="warning" size="small">待处理</el-tag>
          <el-tag v-else-if="row.status === 'DONE'" type="success" size="small">已处理</el-tag>
          <el-tag v-else type="info" size="small">已驳回</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handleResult" label="处理结果" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.handleResult || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage && row.status === 'PENDING'">
            <el-button link type="danger" @click="onHandle(row, 'delete_target')">核实删除</el-button>
            <el-button link type="success" @click="onHandle(row, 'done')">标记处理</el-button>
            <el-button link type="info" @click="onHandle(row, 'reject')">驳回</el-button>
          </template>
          <span v-else class="muted">
            {{ row.handlerName ? `由 ${row.handlerName} 处理` : '—' }}
          </span>
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
import { listAppealReports, handleAppealReport, listAppealOpLogs } from '@/api/appeal'

const user = useUserStore()
const canManage = computed(
  () =>
    user.role === 'property_admin' ||
    user.role === 'grid_worker' ||
    user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<any[]>([])
const status = ref('')
const showLogs = ref(false)
const logs = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    list.value = (await listAppealReports(status.value || undefined)) || []
  } finally {
    loading.value = false
  }
}

async function onHandle(row: any, action: 'done' | 'reject' | 'delete_target') {
  const titles: Record<string, string> = {
    delete_target: '核实并删除被举报内容',
    done: '标记为已处理',
    reject: '驳回举报',
  }
  try {
    const { value } = await ElMessageBox.prompt('请填写处理说明', titles[action], {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '处理结果将留存日志…',
      inputValue:
        action === 'delete_target' ? '举报属实，已删除违规内容' : action === 'done' ? '已处理' : '举报不成立',
    })
    await handleAppealReport(row.id, { action, handleResult: value || undefined })
    ElMessage.success('已提交处理结果')
    load()
  } catch {
    /* cancel */
  }
}

async function loadLogs() {
  logs.value = ((await listAppealOpLogs(100)) || []).filter(
    (l: any) =>
      l.action === 'HANDLE_REPORT' ||
      l.action === 'REPORT' ||
      String(l.actionLabel || '').includes('举报'),
  )
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.hdr-actions { display: flex; gap: 8px; }
.filter-bar { margin-bottom: 12px; }
.muted { color: #94a3b8; font-size: 13px; }
.log-row { font-size: 13px; color: #334155; }
.log-detail { margin-top: 4px; color: #64748b; }
</style>
