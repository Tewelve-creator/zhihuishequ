import request from '@/utils/request'

export interface ResidentCar {
  id: number
  houseId?: number
  houseLabel?: string
  residentId?: number
  userId?: number
  ownerName?: string
  ownerPhone?: string
  plateNo: string
  brandModel?: string
  color?: string
  status?: string
  spaceId?: number
  spaceNo?: string
  monthlyRent?: number
  accessRestricted?: number
  remark?: string
}

export function listResidentCars(params?: {
  houseId?: number
  residentId?: number
  userId?: number
  status?: string
  keyword?: string
}) {
  return request.get('/resident-car/list', { params }) as Promise<ResidentCar[]>
}

export function saveResidentCar(
  data: Partial<ResidentCar> & { plateNo: string; houseId: number },
) {
  if (data.id) return request.put(`/resident-car/${data.id}`, data) as Promise<ResidentCar>
  return request.post('/resident-car', data) as Promise<ResidentCar>
}

export function removeResidentCar(id: number) {
  return request.delete(`/resident-car/${id}`)
}

export function setResidentCarAccessRestrict(id: number, restricted: boolean) {
  return request.put(`/resident-car/${id}/access-restrict`, { restricted }) as Promise<ResidentCar>
}
