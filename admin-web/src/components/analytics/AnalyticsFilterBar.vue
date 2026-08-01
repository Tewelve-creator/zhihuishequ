<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DAYS_OPTIONS } from '@/config/analytics'
import { pageTenants } from '@/api/saas'

export type AnalyticsFilter = {
  days: number
  tenantId?: number
  compare?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: AnalyticsFilter
    loading?: boolean
    exporting?: boolean
    showTenant?: boolean
    showRegion?: boolean
    showCompare?: boolean
    showExport?: boolean
    canExport?: boolean
    syncQuery?: boolean
  }>(),
  {
    loading: false,
    exporting: false,
    showTenant: true,
    showRegion: false,
    showCompare: false,
    showExport: true,
    canExport: true,
    syncQuery: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [AnalyticsFilter]
  query: []
  reset: []
  export: []
}>()

const route = useRoute()
const router = useRouter()
const tenants = ref<any[]>([])
const drawer = ref(false)
const local = reactive<AnalyticsFilter>({
  days: props.modelValue.days || 30,
  tenantId: props.modelValue.tenantId,
  compare: props.modelValue.compare,
})

watch(
  () => props.modelValue,
  (v) => {
    local.days = v.days
    local.tenantId = v.tenantId
    local.compare = v.compare
  },
  { deep: true },
)

function pushModel() {
  emit('update:modelValue', {
    days: local.days,
    tenantId: local.tenantId,
    compare: local.compare || undefined,
  })
}

function syncToUrl() {
  if (!props.syncQuery) return
  const q: Record<string, any> = { ...route.query }
  q.days = String(local.days)
  if (local.tenantId) q.tenantId = String(local.tenantId)
  else delete q.tenantId
  if (local.compare) q.compare = local.compare
  else delete q.compare
  router.replace({ query: q })
}

function emitQuery() {
  pushModel()
  syncToUrl()
  emit('query')
}

function emitReset() {
  local.days = 30
  local.tenantId = undefined
  local.compare = undefined
  pushModel()
  syncToUrl()
  emit('reset')
  emit('query')
}

function onDrawerQuery() {
  drawer.value = false
  emitQuery()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.target as HTMLElement)?.tagName !== 'TEXTAREA') {
    emitQuery()
  }
}

onMounted(async () => {
  if (props.syncQuery) {
    const d = Number(route.query.days)
    if ([7, 30, 90].includes(d)) local.days = d
    const tid = Number(route.query.tenantId)
    if (Number.isFinite(tid) && tid > 0) local.tenantId = tid
    if (route.query.compare) local.compare = String(route.query.compare)
    pushModel()
  }
  if (props.showTenant) {
    try {
      const res = await pageTenants({ page: 1, size: 500 })
      tenants.value = res?.records || []
    } catch {
      tenants.value = []
    }
  }
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="afb">
    <div class="afb-desktop">
      <el-select v-model="local.days" class="afb-item" style="width: 120px">
        <el-option v-for="o in DAYS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>

      <el-select
        v-if="showTenant"
        v-model="local.tenantId"
        clearable
        filterable
        placeholder="租户 / 社区"
        class="afb-item"
        style="width: 180px"
      >
        <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>

      <el-tooltip v-if="showRegion" content="当前接口不支持区域筛选" placement="top">
        <span>
          <el-select disabled placeholder="区域" class="afb-item" style="width: 120px" />
        </span>
      </el-tooltip>

      <el-select
        v-if="showCompare"
        v-model="local.compare"
        clearable
        placeholder="对比周期"
        class="afb-item"
        style="width: 140px"
      >
        <el-option label="环比上期" value="mom" />
        <el-option label="同比去年" value="yoy" disabled />
      </el-select>

      <slot name="extra" />

      <el-button type="primary" :loading="loading" @click="emitQuery">查询</el-button>
      <el-button :disabled="loading" @click="emitReset">重置</el-button>
      <el-button
        v-if="showExport"
        :loading="exporting"
        :disabled="!canExport"
        @click="$emit('export')"
      >
        导出
      </el-button>
    </div>

    <div class="afb-mobile">
      <el-button @click="drawer = true">筛选条件</el-button>
      <el-button type="primary" :loading="loading" @click="emitQuery">查询</el-button>
      <el-button
        v-if="showExport"
        :loading="exporting"
        :disabled="!canExport"
        @click="$emit('export')"
      >
        导出
      </el-button>
    </div>

    <el-drawer v-model="drawer" title="筛选条件" size="88%" direction="rtl">
      <div class="afb-drawer">
        <label>时间范围</label>
        <el-select v-model="local.days" style="width: 100%">
          <el-option v-for="o in DAYS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <template v-if="showTenant">
          <label>租户 / 社区</label>
          <el-select
            v-model="local.tenantId"
            clearable
            filterable
            placeholder="全部"
            style="width: 100%"
          >
            <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </template>
        <template v-if="showCompare">
          <label>对比周期</label>
          <el-select v-model="local.compare" clearable placeholder="不对比" style="width: 100%">
            <el-option label="环比上期" value="mom" />
          </el-select>
        </template>
        <p v-if="showRegion" class="hint">区域筛选：接口暂不支持</p>
        <div class="drawer-actions">
          <el-button @click="emitReset">重置</el-button>
          <el-button type="primary" :loading="loading" @click="onDrawerQuery">查询</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.afb {
  margin-bottom: 14px;
}
.afb-desktop {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.afb-mobile {
  display: none;
  gap: 8px;
  flex-wrap: wrap;
}
.afb-drawer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.afb-drawer label {
  font-size: 13px;
  color: #667085;
  margin-top: 4px;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: #98a2b3;
}
.drawer-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
@media (max-width: 768px) {
  .afb-desktop {
    display: none;
  }
  .afb-mobile {
    display: flex;
  }
}
</style>
