import request from '@/utils/request'

export interface StaffUser {
  id: number
  tenantId?: number
  phone?: string
  nickname?: string
  role?: string
  roomNo?: string
  status?: number
  createTime?: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export function listStaffUsers(params: {
  page?: number
  size?: number
  role?: string
  staffOnly?: boolean
}) {
  return request.get('/auth/users', {
    params: {
      page: params.page ?? 1,
      size: params.size ?? 20,
      role: params.role || undefined,
      staffOnly: params.staffOnly ?? true,
    },
  }) as Promise<PageResult<StaffUser>>
}

export function createStaffUser(body: {
  phone: string
  password?: string
  nickname?: string
  role: string
}) {
  return request.post('/auth/users', body) as Promise<StaffUser>
}

export function updateUserStatus(id: number, status: number) {
  return request.put(`/auth/users/${id}/status`, null, {
    params: { status },
  }) as Promise<void>
}

export function resetStaffPassword(id: number) {
  return request.put(`/auth/users/${id}/password/reset`) as Promise<void>
}

export function deleteStaffUser(id: number) {
  return request.delete(`/auth/users/${id}`) as Promise<void>
}
