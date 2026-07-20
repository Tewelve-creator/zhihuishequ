import * as XLSX from 'xlsx'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { FEATURES } from '@/config/features'

/** 将表格数据导出为 Excel（套餐无 data_export 时拦截） */
export function exportExcel<T extends Record<string, unknown>>(
  rows: T[],
  filename: string,
  sheetName = 'Sheet1',
) {
  const user = useUserStore()
  if (user.portal === 'property' && !user.can(FEATURES.DATA_EXPORT)) {
    ElMessage.warning('当前套餐未开放 Excel 导出，请升级或续费')
    return
  }
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName)
  XLSX.writeFile(wb, filename.endsWith('.xlsx') ? filename : `${filename}.xlsx`)
}
