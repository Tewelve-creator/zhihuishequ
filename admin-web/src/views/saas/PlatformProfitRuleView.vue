<template>
  <div class="ops-page">
    <div class="ops-shell">
      <header class="page-hd">
        <div>
          <h1 class="page-title">提成规则</h1>
          <p class="page-desc">
            社区向商户计提比例 · 平台抽成为 0% · 历史账单不因改规则重算
          </p>
        </div>
        <div class="page-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" @click="openEdit()">新增规则</el-button>
        </div>
      </header>

      <el-alert
        class="mb"
        type="info"
        show-icon
        :closable="false"
        title="全局默认(*)在未配置具体类型时生效；商户档案单独提成比例优先。平台抽成固定为 0%，社区提成归物业（占比 100%）。"
      />

      <div class="panel">
        <el-table v-loading="loading" :data="list" empty-text="暂无提成规则">
          <el-table-column label="规则名称" min-width="140">
            <template #default="{ row }">
              {{ row.shopTypeLabel || row.shopType || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="适用范围" min-width="120">
            <template #default="{ row }">
              {{ row.shopType === '*' ? '全局默认' : `类型：${row.shopType}` }}
            </template>
          </el-table-column>
          <el-table-column label="平台抽成" width="110" align="right">
            <template #default="{ row }">{{ pct(row.platformShareRate) }}</template>
          </el-table-column>
          <el-table-column label="社区/物业占比" width="130" align="right">
            <template #default="{ row }">{{ pct(row.propertyShareRate) }}</template>
          </el-table-column>
          <el-table-column label="提成比例" width="110" align="right">
            <template #default="{ row }">{{ pct(row.commissionRate) }}</template>
          </el-table-column>
          <el-table-column label="生效时间" width="168">
            <template #default="{ row }">{{ fmtTime(row.updateTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="版本" width="80" align="right">
            <template #default>
              <span class="muted" title="后端无版本字段">--</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <span class="pill" :class="row.enabled === 1 ? 'ok' : 'off'">
                {{ row.enabledLabel || (row.enabled === 1 ? '启用' : '停用') }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button link class="ops-link" @click="openEdit(row)">编辑</el-button>
                <el-dropdown
                  v-if="row.shopType !== '*'"
                  trigger="click"
                  @command="(c: string) => onMore(row, c)"
                >
                  <el-button link class="ops-more"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="delete" class="danger-item">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑提成规则' : '新增提成规则'"
      width="480px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="适用范围 / 规则名称" required>
          <el-select
            v-model="form.shopType"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            :disabled="form.id != null && form.shopType === '*'"
          >
            <el-option label="全局默认 (*)" value="*" />
            <el-option label="社区超市" value="社区超市" />
          </el-select>
        </el-form-item>
        <el-form-item label="社区提成比例%">
          <el-input-number
            v-model="form.commissionRate"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="平台抽成%">
          <el-input-number
            v-model="form.platformShareRate"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
            @change="syncShare('platform')"
          />
        </el-form-item>
        <el-form-item label="社区/物业占比%">
          <el-input-number
            v-model="form.propertyShareRate"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
            @change="syncShare('property')"
          />
        </el-form-item>
        <p class="sum" :class="{ bad: !shareOk }">
          占比合计：{{ shareSum.toFixed(2) }}% {{ shareOk ? '（合法）' : '（须等于 100%）' }} · 建议平台 0%
        </p>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledOn" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <p class="hint">保存后仅影响后续新账单；接口不支持生效时间与版本号字段。</p>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!shareOk" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import {
  deletePlatformProfitRule,
  listPlatformProfitRules,
  savePlatformProfitRule,
} from '@/api/store'
import { fmtTime } from '@/utils/opsFormat'

const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const visible = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  shopType: '社区超市',
  commissionRate: 5,
  platformShareRate: 0,
  propertyShareRate: 100,
  enabledOn: true,
  remark: '',
})

const shareSum = computed(
  () => Number(form.platformShareRate || 0) + Number(form.propertyShareRate || 0),
)
const shareOk = computed(() => Math.abs(shareSum.value - 100) < 0.001)

function pct(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '--'
  return `${n.toFixed(2)}%`
}

async function load() {
  loading.value = true
  try {
    list.value = (await listPlatformProfitRules()) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openEdit(row?: any) {
  if (row) {
    form.id = row.id
    form.shopType = row.shopType
    form.commissionRate = Number(row.commissionRate)
    form.platformShareRate = Number(row.platformShareRate)
    form.propertyShareRate = Number(row.propertyShareRate)
    form.enabledOn = row.enabled === 1
    form.remark = row.remark || ''
  } else {
    form.id = undefined
    form.shopType = '社区超市'
    form.commissionRate = 5
    form.platformShareRate = 0
    form.propertyShareRate = 100
    form.enabledOn = true
    form.remark = '平台不抽成'
  }
  visible.value = true
}

function syncShare(who: 'platform' | 'property') {
  if (who === 'platform') {
    form.propertyShareRate = Number((100 - form.platformShareRate).toFixed(2))
  } else {
    form.platformShareRate = Number((100 - form.propertyShareRate).toFixed(2))
  }
}

async function save() {
  if (!form.shopType) {
    ElMessage.warning('请选择适用范围')
    return
  }
  if (!shareOk.value) {
    ElMessage.warning('平台抽成与社区占比合计须为 100%')
    return
  }
  saving.value = true
  try {
    await savePlatformProfitRule({
      id: form.id,
      shopType: form.shopType,
      commissionRate: form.commissionRate,
      platformShareRate: form.platformShareRate,
      propertyShareRate: form.propertyShareRate,
      enabled: form.enabledOn ? 1 : 0,
      remark: form.remark,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onMore(row: any, cmd: string) {
  if (cmd !== 'delete') return
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.shopTypeLabel || row.shopType}」提成规则？`,
      '删除确认',
      { type: 'warning', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  await deletePlatformProfitRule(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.ops-page {
  margin: -20px;
  padding: 24px;
  min-height: calc(100% + 40px);
  background: #f5f6f8;
  box-sizing: border-box;
}
.ops-shell {
  max-width: 1440px;
  margin: 0 auto;
}
.page-hd {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #182230;
}
.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}
.page-actions {
  display: flex;
  gap: 8px;
}
.mb {
  margin-bottom: 12px;
}
.panel {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  padding: 14px 16px;
}
.pill {
  display: inline-flex;
  height: 22px;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
}
.pill.ok {
  background: #ecfdf3;
  color: #067647;
}
.pill.off {
  background: #f2f4f7;
  color: #667085;
}
.muted {
  color: #98a2b3;
}
.ops {
  display: inline-flex;
  gap: 2px;
}
.ops-link {
  color: #2563eb !important;
}
.ops-more {
  color: #667085 !important;
}
:deep(.danger-item) {
  color: #b42318 !important;
}
.sum {
  margin: -4px 0 12px;
  font-size: 13px;
  color: #067647;
}
.sum.bad {
  color: #b42318;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: #98a2b3;
}
@media (max-width: 768px) {
  .ops-page {
    padding: 16px 12px;
  }
}
</style>
