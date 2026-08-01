/**
 * 物业工作人员 — 设备维保（计划性保养，居民不可见）
 */
import { request } from '../utils/request'

export function listEquipDevices(params = {}) {
  return request({
    url: '/equip/devices',
    data: params
  })
}

export function getEquipDevice(id) {
  if (id == null || id === '' || String(id) === 'undefined') {
    return Promise.reject({ code: 400, msg: '设备ID无效' })
  }
  return request({ url: `/equip/devices/${id}` })
}

export function listEquipRecords(deviceId) {
  return request({ url: `/equip/devices/${deviceId}/records` })
}

export function addEquipRecord(data) {
  return request({
    url: '/equip/records',
    method: 'POST',
    data
  })
}

/** 首页待办：待维保+已逾期 */
export function getEquipTodoSummary() {
  return request({ url: '/equip/todo-summary', showError: false })
}

export function listEquipDue() {
  return request({ url: '/equip/due-list', showError: false })
}
