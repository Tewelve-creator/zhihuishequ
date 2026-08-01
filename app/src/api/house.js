import { request } from '../utils/request'

/** 我绑定的房屋 */
export const listMyHouses = () => request({ url: '/auth/house/mine' })

/** 当前房屋 + 列表 */
export const getCurrentHouse = () => request({ url: '/auth/house/current', showError: false })

export const listBuildings = () => request({ url: '/auth/house/options/buildings' })

export const listUnits = (building) =>
  request({ url: '/auth/house/options/units', data: { building } })

export const listRooms = (building, unit) =>
  request({ url: '/auth/house/options/rooms', data: { building, unit } })

export const bindHouse = (data) => request({ url: '/auth/house/bind', method: 'POST', data })

export const unbindHouse = (houseId, remark) =>
  request({
    url: '/auth/house/apply/unbind',
    method: 'POST',
    data: { houseId, remark: remark || '' }
  })

export const listMyApplies = () => request({ url: '/auth/house/apply/mine', showError: false })

export const setDefaultHouse = (houseId) =>
  request({ url: `/auth/house/${houseId}/default`, method: 'PUT' })

export const listHouseCatalog = () => request({ url: '/auth/house/catalog' })
