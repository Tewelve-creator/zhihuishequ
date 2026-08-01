<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>邻里互助管理</h2>
        <p class="sub">审核邻里互助帖子：查看内容、下架违规信息 · 与邻里倾诉相互独立</p>
      </div>
      <div class="hdr-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="helpType" clearable placeholder="帖子类型" style="width: 140px" @change="onSearch">
        <el-option label="借物求助" value="借物求助" />
        <el-option label="技能互助" value="技能互助" />
        <el-option label="拼车出行" value="拼车出行" />
        <el-option label="照看宠物" value="照看宠物" />
        <el-option label="其他" value="其他" />
      </el-select>
      <el-select v-model="status" clearable placeholder="状态" style="width: 130px" @change="onSearch">
        <el-option label="正常展示" value="NORMAL" />
        <el-option label="已下架" value="OFF" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="编号/业主/房号/标题"
        style="width: 220px"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedList" stripe empty-text="暂无互助帖子">
      <el-table-column prop="postNo" label="帖子编号" width="150" />
      <el-table-column prop="ownerName" label="发布业主" width="110">
        <template #default="{ row }">{{ row.ownerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="houseLabel" label="房号" width="140">
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="helpType" label="互助类型" width="110" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="发布时间" width="170" />
      <el-table-column label="展示状态" width="110">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'OFF' ? 'info' : 'success'"
            size="small"
            effect="light"
          >
            {{ row.statusLabel || (row.status === 'OFF' ? '已下架' : '正常展示') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          <el-button
            v-if="canManage && row.status !== 'OFF'"
            link
            type="danger"
            @click="openOff(row)"
          >下架违规</el-button>
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

    <!-- 详情 -->
    <el-dialog v-model="detailDlg" title="互助帖子详情" width="640px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="帖子编号">{{ detail.postNo }}</el-descriptions-item>
          <el-descriptions-item label="展示状态">
            <el-tag :type="detail.status === 'OFF' ? 'info' : 'success'" size="small">
              {{ detail.statusLabel || (detail.status === 'OFF' ? '已下架' : '正常展示') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布业主">{{ detail.ownerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="房号">{{ detail.houseLabel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="互助类型">{{ detail.helpType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detail.createTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="正文" :span="2">
            <div class="detail-content">{{ detail.content || '—' }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.imageList?.length" label="图片" :span="2">
            <div class="detail-imgs">
              <el-image
                v-for="(img, i) in detail.imageList"
                :key="i"
                :src="fileUrl(img)"
                :preview-src-list="detail.imageList.map(fileUrl)"
                :initial-index="i"
                fit="cover"
                class="detail-img"
              />
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.status === 'OFF'" label="违规备注" :span="2">
            <div class="off-reason">{{ detail.offReason || '—' }}</div>
            <div class="off-meta">
              下架人：{{ detail.offByName || '—' }} · {{ detail.offTime || '' }}
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailDlg = false">关闭</el-button>
        <el-button
          v-if="canManage && detail && detail.status !== 'OFF'"
          type="danger"
          @click="openOff(detail)"
        >下架违规</el-button>
      </template>
    </el-dialog>

    <!-- 下架 -->
    <el-dialog v-model="offDlg" title="下架违规帖子" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="帖子">{{ offTarget?.title }}（{{ offTarget?.postNo }}）</el-form-item>
        <el-form-item label="违规备注" required>
          <el-input
            v-model="offReason"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="请填写下架原因，如：广告引流 / 虚假信息 / 不当言论…"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offDlg = false">取消</el-button>
        <el-button type="danger" :loading="offLoading" @click="submitOff">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileUrl } from '@/api/file'
import {
  getNeighborHelpAdmin,
  listNeighborHelpAdmin,
  takeDownNeighborHelp,
  type NeighborHelpPost,
} from '@/api/neighborHelp'

const user = useUserStore()
const canManage = computed(
  () =>
    user.role === 'property_admin' ||
    user.role === 'grid_worker' ||
    user.role === 'platform_admin',
)

const loading = ref(false)
const list = ref<NeighborHelpPost[]>([])
const helpType = ref('')
const status = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const detailDlg = ref(false)
const detail = ref<NeighborHelpPost | null>(null)

const offDlg = ref(false)
const offTarget = ref<NeighborHelpPost | null>(null)
const offReason = ref('')
const offLoading = ref(false)

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

function onSearch() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listNeighborHelpAdmin({
        helpType: helpType.value || undefined,
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

async function openDetail(row: NeighborHelpPost) {
  detail.value = await getNeighborHelpAdmin(row.id)
  detailDlg.value = true
}

function openOff(row: NeighborHelpPost) {
  offTarget.value = row
  offReason.value = ''
  offDlg.value = true
}

async function submitOff() {
  if (!offTarget.value?.id) return
  if (!offReason.value.trim()) {
    ElMessage.warning('请填写违规备注')
    return
  }
  offLoading.value = true
  try {
    const updated = await takeDownNeighborHelp(offTarget.value.id, offReason.value.trim())
    ElMessage.success('已下架')
    offDlg.value = false
    if (detailDlg.value && detail.value?.id === updated.id) {
      detail.value = updated
    }
    await load()
  } finally {
    offLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.hdr-actions { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-content { white-space: pre-wrap; line-height: 1.6; color: #334155; }
.detail-imgs { display: flex; flex-wrap: wrap; gap: 8px; }
.detail-img { width: 96px; height: 96px; border-radius: 6px; }
.off-reason { color: #b45309; line-height: 1.5; }
.off-meta { margin-top: 6px; color: #94a3b8; font-size: 12px; }
</style>
