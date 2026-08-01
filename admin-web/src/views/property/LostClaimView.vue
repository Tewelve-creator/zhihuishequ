<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>认领核实</h2>
        <p class="sub">核验居民认领申请，通过后标记物品为已认领，并自动驳回同条其他待审申请</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="status" clearable placeholder="状态" style="width: 130px" @change="load">
        <el-option label="待核实" value="pending" />
        <el-option label="已通过" value="approved" />
        <el-option label="已驳回" value="rejected" />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="姓名/手机/物品"
        style="width: 200px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe empty-text="暂无认领申请">
      <el-table-column prop="lostTitle" label="物品" min-width="140" show-overflow-tooltip />
      <el-table-column prop="itemTypeLabel" label="类型" width="80" />
      <el-table-column prop="category" label="分类" width="100">
        <template #default="{ row }">{{ row.category || '—' }}</template>
      </el-table-column>
      <el-table-column prop="userName" label="申请人" width="100" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="remark" label="认领说明" min-width="140" show-overflow-tooltip />
      <el-table-column prop="statusLabel" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage && row.status === 'pending'">
            <el-button link type="success" @click="verify(row, 'approved')">通过</el-button>
            <el-button link type="danger" @click="verify(row, 'rejected')">驳回</el-button>
          </template>
          <span v-else class="muted">{{ row.auditRemark || '—' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listLostClaims, verifyLostClaim } from '@/api/lost'

const user = useUserStore()
const canManage = computed(
  () => user.role === 'property_admin' || user.role === 'platform_admin',
)
const loading = ref(false)
const list = ref<any[]>([])
const status = ref('pending')
const keyword = ref('')

function statusType(s: string) {
  if (s === 'pending') return 'warning'
  if (s === 'approved') return 'success'
  if (s === 'rejected') return 'info'
  return ''
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listLostClaims({
        status: status.value || undefined,
        keyword: keyword.value.trim() || undefined,
      })) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function verify(row: any, action: 'approved' | 'rejected') {
  let auditRemark = ''
  if (action === 'rejected') {
    const { value } = await ElMessageBox.prompt('请填写驳回原因（可选）', '驳回认领', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '原因',
    }).catch(() => ({ value: null as string | null }))
    if (value === null) return
    auditRemark = value || ''
  } else {
    await ElMessageBox.confirm(
      `确认通过「${row.userName}」对「${row.lostTitle}」的认领？通过后该信息将标记为已认领。`,
      '通过核实',
      { type: 'warning' },
    )
  }
  await verifyLostClaim(row.id, { status: action, auditRemark: auditRemark || undefined })
  ElMessage.success(action === 'approved' ? '已通过' : '已驳回')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 4px; }
.sub { margin: 0; color: #64748b; font-size: 13px; }
.filter-bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #94a3b8; font-size: 12px; }
</style>
