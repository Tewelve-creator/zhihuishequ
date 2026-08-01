<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>巡检记录</h2>
        <p class="sub">汇总打卡历史台账：点位、时间、现场照片、检查结果（正常/异常），支持筛选与导出</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="onExport">导出Excel</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="任务/点位/打卡人/异常描述"
        style="width: 260px"
        @keyup.enter="load"
      />
      <el-select v-model="result" clearable placeholder="检查结果" style="width: 130px" @change="load">
        <el-option label="正常" value="NORMAL" />
        <el-option label="异常" value="ABNORMAL" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无打卡记录">
      <el-table-column prop="taskTitle" label="所属任务" min-width="140" show-overflow-tooltip />
      <el-table-column prop="pointName" label="巡检点位" width="140" show-overflow-tooltip />
      <el-table-column prop="userName" label="打卡人" width="100" />
      <el-table-column prop="checkTime" label="打卡时间" width="170" />
      <el-table-column label="检查结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'ABNORMAL' ? 'danger' : 'success'" size="small">
            {{ row.resultLabel || (row.result === 'ABNORMAL' ? '异常' : '正常') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hazardDesc" label="异常说明" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.hazardDesc || '—' }}</template>
      </el-table-column>
      <el-table-column label="现场照片" width="140">
        <template #default="{ row }">
          <div v-if="(row.photos || []).length" class="thumbs">
            <el-image
              v-for="(u, i) in (row.photos || []).slice(0, 3)"
              :key="i"
              :src="mediaUrl(u)"
              :preview-src-list="(row.photos || []).map(mediaUrl)"
              :initial-index="i"
              fit="cover"
              class="thumb"
            />
          </div>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canManage && row.result === 'ABNORMAL'"
            link
            type="warning"
            @click="genWo(row)"
          >生成工单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="打卡详情" width="560px" destroy-on-close>
      <el-descriptions v-if="cur" :column="1" border>
        <el-descriptions-item label="任务">{{ cur.taskTitle }}</el-descriptions-item>
        <el-descriptions-item label="点位">{{ cur.pointName }}</el-descriptions-item>
        <el-descriptions-item label="打卡人">{{ cur.userName }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ cur.checkTime }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ cur.resultLabel }}</el-descriptions-item>
        <el-descriptions-item v-if="cur.hazardDesc" label="异常">{{ cur.hazardDesc }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ cur.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="(cur.photos || []).length" label="照片">
          <div class="thumbs big">
            <el-image
              v-for="(u, i) in cur.photos"
              :key="i"
              :src="mediaUrl(u)"
              :preview-src-list="cur.photos.map(mediaUrl)"
              :initial-index="i"
              fit="cover"
              class="thumb-lg"
            />
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { exportExcel } from '@/utils/exportExcel'
import { listInspCheckins, type InspCheckin } from '@/api/inspection'
import { createWorkOrderFromCheckin } from '@/api/equip'

const user = useUserStore()
const router = useRouter()
const canManage = computed(
  () => user.hasRealToken && (user.role === 'property_admin' || user.role === 'platform_admin'),
)

const loading = ref(false)
const list = ref<InspCheckin[]>([])
const keyword = ref('')
const result = ref('')
const dlg = ref(false)
const cur = ref<InspCheckin | null>(null)

function mediaUrl(u: string) {
  if (!u) return ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  return `/api${u.startsWith('/') ? u : '/' + u}`
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listInspCheckins({
        keyword: keyword.value || undefined,
        result: result.value || undefined,
        limit: 300,
      })) || []
  } finally {
    loading.value = false
  }
}

function openDetail(row: InspCheckin) {
  cur.value = row
  dlg.value = true
}

function onExport() {
  exportExcel(
    (list.value || []).map((r) => ({
      所属任务: r.taskTitle || '',
      巡检点位: r.pointName || '',
      打卡人: r.userName || '',
      打卡时间: r.checkTime || '',
      检查结果: r.resultLabel || '',
      异常说明: r.hazardDesc || '',
      备注: r.remark || '',
    })),
    '巡检记录.xlsx',
  )
}

async function genWo(row: InspCheckin) {
  await ElMessageBox.confirm(
    `确认为异常「${row.pointName}」生成设备维保工单？`,
    '巡检→维保闭环',
  )
  await createWorkOrderFromCheckin(row.id, {
    title: `巡检异常维修：${row.pointName || ''}`,
    deviceName: row.pointName,
    location: row.pointName,
  })
  ElMessage.success('工单已生成，可前往「设备维保」处理')
  router.push('/property/maintenance')
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; }
.thumbs { display: flex; gap: 4px; }
.thumb { width: 36px; height: 36px; border-radius: 4px; }
.thumb-lg { width: 96px; height: 96px; border-radius: 6px; margin-right: 8px; }
.thumbs.big { flex-wrap: wrap; }
</style>
