/** 与后端 FeatureCodes 对齐 */
export const FEATURES = {
  BASIC_REPAIR: 'basic_repair',
  BASIC_NOTICE: 'basic_notice',
  AI_CHAT: 'ai_chat',
  AI_IMAGE_REPAIR: 'ai_image_repair',
  AI_EMOTION: 'ai_emotion',
  AI_LOST_MATCH: 'ai_lost_match',
  DATA_DASHBOARD: 'data_dashboard',
  DATA_EXPORT: 'data_export',
  VENDOR_MANAGE: 'vendor_manage',
  MULTI_COMMUNITY: 'multi_community',
} as const

export type FeatureCode = (typeof FEATURES)[keyof typeof FEATURES]

export function parseFeatures(features?: string | string[] | null): string[] {
  if (!features) return []
  if (Array.isArray(features)) return features
  return String(features)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function hasFeature(features: string | string[] | null | undefined, code: string) {
  return parseFeatures(features).includes(code)
}

/** 演示账号默认功能码（对接真实登录后由接口 features 覆盖） */
export const DEMO_FEATURES: Record<string, { features: string; tenantStatus: string; role: string }> = {
  admin2: {
    role: 'platform_admin',
    tenantStatus: 'active',
    features: Object.values(FEATURES).join(','),
  },
  admin1: {
    role: 'property_admin',
    tenantStatus: 'active',
    features: [
      FEATURES.BASIC_REPAIR,
      FEATURES.BASIC_NOTICE,
      FEATURES.AI_CHAT,
      FEATURES.AI_IMAGE_REPAIR,
      FEATURES.DATA_DASHBOARD,
      FEATURES.DATA_EXPORT,
    ].join(','),
  },
  /** 模拟到期降级账号：仅公告+报修 */
  degraded1: {
    role: 'property_admin',
    tenantStatus: 'degraded',
    features: `${FEATURES.BASIC_REPAIR},${FEATURES.BASIC_NOTICE}`,
  },
}
