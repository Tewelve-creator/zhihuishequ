import { exportExcel } from '@/utils/exportExcel'
import { ElMessage } from 'element-plus'

export function useCrudActions<T extends Record<string, unknown>>(rows: () => T[], name: string) {
  function onExport() {
    exportExcel(rows(), `${name}_${Date.now()}`)
    ElMessage.success('导出成功')
  }
  function onAdd() {
    ElMessage.info('演示环境：表单弹窗可对接网关新增接口')
  }
  return { onExport, onAdd }
}
