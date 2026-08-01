<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>报名管理</h2>
        <p class="sub">先选择活动，再查看该活动的报名人员；支持签到/取消，报名已同步 AI 智能日程</p>
      </div>
      <div class="ops">
        <el-button :loading="loadingActs" @click="loadActs">刷新活动</el-button>
        <el-button :disabled="!selectedId" @click="onExport">导出 Excel</el-button>
      </div>
    </div>

    <h3 class="section-title">1. 选择活动</h3>
    <el-table
      ref="actTableRef"
      v-loading="loadingActs"
      :data="activities"
      stripe
      highlight-current-row
      empty-text="暂无活动"
      class="act-table"
      @current-change="onSelectActivity"
      @row-click="onRowClick"
    >
      <el-table-column prop="title" label="活动标题" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="selectActivity(row)">{{ row.title }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="location" label="地点" width="120" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始时间" width="160" />
      <el-table-column prop="phase" label="阶段" width="90" />
      <el-table-column label="报名人数" width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="selectActivity(row)">
            {{ row.signedCount || 0 }}/{{ row.maxPeople || '-' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="selectActivity(row)">查看报名</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="people-panel">
      <div class="people-head">
        <h3 class="section-title">
          2. 报名人员
          <span v-if="selected" class="sel-name">「{{ selected.title }}」</span>
          <span v-else class="sel-hint">请先点击上方活动</span>
        </h3>
        <div v-if="selected" class="filter-bar inline">
          <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="loadSignups">
            <el-option label="已报名" value="signed" />
            <el-option label="已签到" value="checked" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="姓名/手机"
            style="width: 180px"
            @keyup.enter="loadSignups"
          />
          <el-button type="primary" :loading="loading" @click="loadSignups">查询</el-button>
        </div>
      </div>

      <el-table
        v-if="selectedId"
        v-loading="loading"
        :data="list"
        stripe
        empty-text="该活动暂无报名"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="userName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="报名时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'signed'"
              link
              type="success"
              @click="onCheck(row)"
            >签到</el-button>
            <el-button
              v-if="row.status === 'signed' || row.status === 'checked'"
              link
              type="danger"
              @click="onCancel(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="点击上方活动标题或「查看报名」，即可看到该活动的报名人员" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import { exportExcel } from '@/utils/exportExcel'
import {
  cancelActivitySignup,
  checkActivitySignup,
  listActivities,
  listAllActivitySignups,
} from '@/api/activity'

const route = useRoute()
const router = useRouter()

const loadingActs = ref(false)
const loading = ref(false)
const activities = ref<any[]>([])
const list = ref<any[]>([])
const selectedId = ref<number | undefined>()
const status = ref('')
const keyword = ref('')
const actTableRef = ref<TableInstance>()

const selected = computed(() => activities.value.find((a) => a.id === selectedId.value) || null)

function statusLabel(s: string) {
  if (s === 'signed') return '已报名'
  if (s === 'checked') return '已签到'
  if (s === 'cancelled') return '已取消'
  return s || '-'
}

function statusType(s: string) {
  if (s === 'signed') return 'primary'
  if (s === 'checked') return 'success'
  if (s === 'cancelled') return 'info'
  return ''
}

async function loadActs() {
  loadingActs.value = true
  try {
    activities.value = (await listActivities(true).catch(() => [])) || []
  } finally {
    loadingActs.value = false
  }
}

async function loadSignups() {
  if (!selectedId.value) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value =
      (await listAllActivitySignups({
        activityId: selectedId.value,
        status: status.value || undefined,
        keyword: keyword.value.trim() || undefined,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function selectActivity(row: any) {
  if (!row?.id) return
  selectedId.value = row.id
  actTableRef.value?.setCurrentRow(row)
  router.replace({ query: { ...route.query, activityId: String(row.id) } })
  loadSignups()
}

function onSelectActivity(row: any) {
  if (row) selectActivity(row)
}

function onRowClick(row: any) {
  selectActivity(row)
}

async function onCheck(row: any) {
  await checkActivitySignup(row.id)
  ElMessage.success('已签到')
  await loadSignups()
  await loadActs()
}

async function onCancel(row: any) {
  await ElMessageBox.confirm(`确认取消「${row.userName}」的报名？`, '取消报名', { type: 'warning' })
  await cancelActivitySignup(row.id)
  ElMessage.success('已取消')
  await loadSignups()
  await loadActs()
}

function onExport() {
  if (!selected.value) return
  const rows = list.value.map((s) => ({
    活动: selected.value!.title,
    姓名: s.userName,
    手机: s.phone,
    状态: statusLabel(s.status),
    报名时间: s.createTime,
  }))
  exportExcel(rows, `活动报名_${selected.value.title}_${Date.now()}`)
  ElMessage.success('已导出')
}

onMounted(async () => {
  await loadActs()
  const q = Number(route.query.activityId)
  const hit = q ? activities.value.find((a) => a.id === q) : null
  if (hit) {
    await nextTick()
    selectActivity(hit)
  }
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.ops { display: flex; gap: 8px; }
.section-title {
  margin: 8px 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}
.sel-name { color: #2563eb; font-weight: 600; margin-left: 6px; }
.sel-hint { color: #94a3b8; font-weight: 400; margin-left: 8px; font-size: 13px; }
.act-table { margin-bottom: 8px; cursor: pointer; }
.people-panel {
  margin-top: 20px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}
.people-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.people-head .section-title { margin: 0; }
.filter-bar.inline { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
</style>
