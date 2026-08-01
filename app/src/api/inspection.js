/**
 * 物业工作人员 — 巡检任务 App API
 */
import { request } from '../utils/request'

/** status: PENDING|DOING|DONE|all；scope: assigned=仅本人指派 */
export function listMyInspectionTasks(status, scope) {
  const data = { status: status || 'all' }
  if (scope) data.scope = scope
  return request({
    url: '/inspection/mine/tasks',
    data
  })
}

/** 首页待办：本人名下待执行巡检数量 */
export function getInspectionTodoSummary() {
  return request({ url: '/inspection/mine/todo-summary', showError: false })
}

function validId(id) {
  return id != null && id !== '' && String(id) !== 'undefined' && String(id) !== 'null'
}

export function getMyInspectionTask(id) {
  if (!validId(id)) {
    return Promise.reject({ code: 400, msg: '任务ID无效' })
  }
  return request({ url: `/inspection/mine/tasks/${id}` })
}

export function checkinInspection(taskId, body) {
  if (!validId(taskId)) {
    return Promise.reject({ code: 400, msg: '任务ID无效' })
  }
  return request({
    url: `/inspection/mine/tasks/${taskId}/checkin`,
    method: 'POST',
    data: body
  })
}

/** 隐患台账：管理员看本社区全部，网格员看本人上报 */
export function listMyInspectionHazards(status) {
  return request({
    url: '/inspection/mine/hazards',
    data: status ? { status } : {}
  })
}
