import { request } from '../utils/request'

export const getPublicPackages = () =>
  request({ url: '/saas/packages/public', auth: false })

export const getCurrentTenant = () => request({ url: '/saas/tenant/current' })
