<template>
  <CrudPage
    v-if="config"
    :title="config.title"
    :data="config.rows"
    :total="config.rows.length"
    :show-date-range="config.showDateRange"
    @add="onAdd"
    @export="onExport"
    @search="ElMessage.success('已按条件查询（演示）')"
    @reset="ElMessage.info('已重置')"
  >
    <el-table-column
      v-for="col in config.columns"
      :key="col.prop"
      :prop="col.prop"
      :label="col.label"
      :width="col.width"
    >
      <template v-if="col.tag" #default="{ row }">
        <el-tag :type="tagType(row[col.prop])" size="small">{{ row[col.prop] }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right">
      <template #default>
        <el-button link type="primary">编辑</el-button>
        <el-button link type="danger">删除</el-button>
      </template>
    </el-table-column>
  </CrudPage>
  <el-empty v-else description="页面配置不存在" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CrudPage from '@/components/CrudPage.vue'
import { getListPage } from '@/config/listPages'
import { exportExcel } from '@/utils/exportExcel'

const route = useRoute()
const config = computed(() => getListPage(route.path))

function onAdd() {
  ElMessage.info('演示环境：可对接网关新增接口')
}

function onExport() {
  if (!config.value) return
  exportExcel(config.value.rows, `${config.value.title}_${Date.now()}`)
  ElMessage.success('导出成功')
}

function tagType(v: unknown) {
  const s = String(v)
  if (['正常', '启用', '在线', '已缴', '已完成', '已通过', '生效', '可租', '空闲', '已发布', '已发送', '已放行', '运营中'].includes(s))
    return 'success'
  if (['待接单', '待派单', '待审批', '审批中', '未缴', '待抄', '待维保', '预订', '进行中', '未开始', '筹建中'].includes(s))
    return 'warning'
  if (['离线', '故障', '逾期', '告警', '报废', '已暂停'].includes(s)) return 'danger'
  return 'info'
}
</script>
