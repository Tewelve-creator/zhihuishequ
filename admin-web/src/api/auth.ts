import request from '@/utils/request'

export interface LoginBody {
  phone: string
  password: string
  tenantId?: number
  clientType?: string
  loginType?: string
}

export function loginApi(body: LoginBody) {
  return request.post('/auth/login', {
    loginType: 'password',
    clientType: 'ADMIN',
    ...body,
  }) as Promise<{
    token: string
    userId: number
    tenantId: number
    role: string
    nickname?: string
    features?: string | string[]
    tenantStatus?: string
  }>
}
