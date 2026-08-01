<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>招领信息列表</h2>
        <p class="sub">居民发布的寻物/拾物信息（经 AI 审核）；按小区隔离，可标记已找到或删除</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="filter-bar">
      <el-select v-model="itemType" clearable placeholder="类型" style="width: 120px" @change="load">
        <el-option label="失物" value="lost" />
        <el-option label="招领" value="found" />
      </el-select>
      <el-select v-model="category" clearable placeholder="分类" style="width: 140px" @change="load">
        <el-option v-for="c in categories" :key="c.name" :label="c.name" :value="c.name" />
      </el-select>
      <el-select v-model="status" clearable placeholder="状态" style="width: 120px" @change="load">
        <el-option label="进行中" value="open" />
        <el-option label="已找到/认领" value="found" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="标题/描述/联系人"
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe empty-text="暂无记录">
      <el-table-column prop="houseLabel" label="发布房屋" width="140">
        <template #default="{ row }">{{ row.houseLabel || '—' }}</template>
      </el-table-column>
      <el-table-column prop="contactName" label="联系人" width="100">
        <template #default="{ row }">{{ row.contactName || row.publisherName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="title" label="物品名称" min-width="120" />
      <el-table-column prop="category" label="分类" width="100">
        <template #default="{ row }">{{ row.category || '—' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.imageUrl"
            :src="fileUrl(row.imageUrl)"
            :preview-src-list="[fileUrl(row.imageUrl)]"
            style="width: 40px; height: 40px"
            fit="cover"
          >
            <template #error>
              <div class="img-fail">失败</div>
            </template>
          </el-image>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="itemTypeLabel" label="类型" width="80" />
      <el-table-column label="审核" width="80">
        <template #default="{ row }">
          <el-tag :type="row.aiAuditPass === 1 ? 'success' : 'info'" size="small">
            {{ row.aiAuditPass === 1 ? '通过' : '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="claimCount" label="认领" width="70" />
      <el-table-column prop="statusLabel" label="状态" width="110" />
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button
            v-if="canManage && row.status === 'open'"
            link
            type="success"
            @click="mark(row)"
          >标记已找到</el-button>
          <el-button
            v-if="canManage"
            link
            type="danger"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="失物详情" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="物品">{{ cur?.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ cur?.itemTypeLabel }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ cur?.category || '—' }}</el-descriptions-item>
        <el-descriptions-item label="房屋">{{ cur?.houseLabel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ cur?.contactName || cur?.publisherName }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ cur?.contact || '—' }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ cur?.description }}</el-descriptions-item>
        <el-descriptions-item v-if="cur?.imageUrl" label="图片">
          <el-image :src="fileUrl(cur.imageUrl)" style="max-width: 240px" fit="contain" />
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fileUrl } from '@/api/file'
import { deleteLost, listLost, listLostCategories, markLostFound } from '@/api/lost'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const list = ref<any[]>([])
const categories = ref<any[]>([])
const itemType = ref('')
const category = ref('')
const status = ref('')
const keyword = ref('')
const dlg = ref(false)
const cur = ref<any>(null)

async function loadCategories() {
  categories.value = (await listLostCategories(true).catch(() => [])) || []
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listLost({
        itemType: itemType.value || undefined,
        category: category.value || undefined,
        status: status.value || undefined,
        keyword: keyword.value.trim() || undefined,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function detail(row: any) {
  cur.value = row
  dlg.value = true
}

async function mark(row: any) {
  await ElMessageBox.confirm(`确认将「${row.title}」标记为已找到？`, '提示', { type: 'warning' })
  await markLostFound(row.id)
  ElMessage.success('已标记')
  load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除', { type: 'warning' })
  await deleteLost(row.id)
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
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.img-fail {
  width: 40px; height: 40px; font-size: 11px; color: #94a3b8;
  display: flex; align-items: center; justify-content: center; background: #f1f5f9;
}
</style>
