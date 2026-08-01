/**
 * 常驻住户车辆 API
 */
import { request } from '../utils/request'

export function listResidentCars(params = {}) {
  return request({ url: '/resident-car/list', data: params })
}

export function saveResidentCar(data) {
  if (data.id) {
    return request({ url: `/resident-car/${data.id}`, method: 'PUT', data })
  }
  return request({ url: '/resident-car', method: 'POST', data })
}

export function removeResidentCar(id) {
  return request({ url: `/resident-car/${id}`, method: 'DELETE' })
}
