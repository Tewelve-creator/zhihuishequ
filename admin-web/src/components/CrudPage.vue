<template>
  <div class="page-card">
    <div class="page-header">
      <h2>{{ title }}</h2>
      <div class="actions">
        <slot name="actions">
          <el-button v-if="showAdd" type="primary" @click="$emit('add')">新增</el-button>
          <el-button v-if="showExport" @click="$emit('export')">导出 Excel</el-button>
        </slot>
      </div>
    </div>
    <div v-if="$slots.filters || showDateRange" class="filter-bar">
      <slot name="filters" />
      <el-date-picker
        v-if="showDateRange"
        v-model="innerRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="onRange"
      />
      <el-button type="primary" @click="$emit('search')">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
    </div>
    <el-table :data="data" stripe border style="width: 100%">
      <slot />
    </el-table>
    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    data: Record<string, unknown>[]
    total?: number
    pageSize?: number
    showAdd?: boolean
    showExport?: boolean
    showDateRange?: boolean
  }>(),
  {
    total: 0,
    pageSize: 10,
    showAdd: true,
    showExport: true,
    showDateRange: false,
  },
)

const emit = defineEmits<{
  add: []
  export: []
  search: []
  reset: []
  'update:range': [value: [string, string] | null]
}>()

const page = ref(1)
const innerRange = ref<[string, string] | null>(null)

function onRange(v: [string, string] | null) {
  emit('update:range', v)
}

watch(
  () => props.data,
  () => {
    if (!props.total) page.value = 1
  },
)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
