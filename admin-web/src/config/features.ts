/** 与后端 FeatureCodes / sys_package_feature 对齐 */
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
  /** @deprecated 不再作为套餐卖点；切换社区靠同账号多租户，不按套餐解锁 */
  MULTI_COMMUNITY: 'multi_community',
} as const

export type FeatureCode = (typeof FEATURES)[keyof typeof FEATURES]

export type FeatureState = 'available' | 'unavailable' | 'expired' | 'disabled' | 'loading'

/** 四级套餐默认功能（与 sql/phase4_saas、patch_community_packages 一致） */
export const PACKAGE_TIERS: Array<{
  code: string
  name: string
  features: FeatureCode[]
}> = [
  {
    code: 'free',
    name: '免费版',
    features: [FEATURES.BASIC_REPAIR, FEATURES.BASIC_NOTICE],
  },
  {
    code: 'basic',
    name: '基础版',
    features: [
      FEATURES.BASIC_REPAIR,
      FEATURES.BASIC_NOTICE,
      FEATURES.AI_CHAT,
      FEATURES.AI_IMAGE_REPAIR,
    ],
  },
  {
    code: 'pro',
    name: '专业版',
    features: [
      FEATURES.BASIC_REPAIR,
      FEATURES.BASIC_NOTICE,
      FEATURES.AI_CHAT,
      FEATURES.AI_IMAGE_REPAIR,
      FEATURES.AI_EMOTION,
      FEATURES.AI_LOST_MATCH,
      FEATURES.DATA_DASHBOARD,
      FEATURES.DATA_EXPORT,
    ],
  },
  {
    code: 'flagship',
    name: '旗舰版',
    features: [
      FEATURES.BASIC_REPAIR,
      FEATURES.BASIC_NOTICE,
      FEATURES.AI_CHAT,
      FEATURES.AI_IMAGE_REPAIR,
      FEATURES.AI_EMOTION,
      FEATURES.AI_LOST_MATCH,
      FEATURES.DATA_DASHBOARD,
      FEATURES.DATA_EXPORT,
      FEATURES.VENDOR_MANAGE,
    ],
  },
]

export const FEATURE_LABELS: Record<string, string> = {
  [FEATURES.BASIC_REPAIR]: '基础报修',
  [FEATURES.BASIC_NOTICE]: '基础公告',
  [FEATURES.AI_CHAT]: 'AI问答',
  [FEATURES.AI_IMAGE_REPAIR]: '识图报修',
  [FEATURES.AI_EMOTION]: '情绪倾诉',
  [FEATURES.AI_LOST_MATCH]: '失物匹配',
  [FEATURES.DATA_DASHBOARD]: '数据大屏',
  [FEATURES.DATA_EXPORT]: '数据导出',
  [FEATURES.VENDOR_MANAGE]: '社区商超',
}

/** 套餐/模板权限编辑分组 */
export const FEATURE_GROUPS: { key: string; label: string; codes: FeatureCode[] }[] = [
  {
    key: 'basic',
    label: '基础服务',
    codes: [FEATURES.BASIC_REPAIR, FEATURES.BASIC_NOTICE],
  },
  {
    key: 'ops',
    label: '运营能力',
    codes: [FEATURES.VENDOR_MANAGE],
  },
  {
    key: 'data',
    label: '数据能力',
    codes: [FEATURES.DATA_DASHBOARD, FEATURES.DATA_EXPORT],
  },
  {
    key: 'advanced',
    label: '高级能力',
    codes: [
      FEATURES.AI_CHAT,
      FEATURES.AI_IMAGE_REPAIR,
      FEATURES.AI_EMOTION,
      FEATURES.AI_LOST_MATCH,
    ],
  },
]

export function parseFeatures(features?: string | string[] | null): string[] {
  if (!features) return []
  if (Array.isArray(features)) return features.map(String).map((s) => s.trim()).filter(Boolean)
  return String(features)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function hasFeature(features: string | string[] | null | undefined, code: string) {
  return parseFeatures(features).includes(code)
}

/** 根据套餐阶梯推断「最低所需套餐」名称；无法判断则返回 null */
export function minPackageNameForFeature(featureCode: string): string | null {
  for (const tier of PACKAGE_TIERS) {
    if (tier.features.includes(featureCode as FeatureCode)) return tier.name
  }
  return null
}

export function packageTagType(code?: string): 'info' | 'success' | 'warning' | '' {
  if (code === 'basic') return 'info'
  if (code === 'pro') return 'success'
  if (code === 'flagship') return 'warning'
  return 'info'
}

/** 低饱和套餐标签 class：free / basic / pro / flagship */
export function packageTagClass(code?: string): string {
  const c = String(code || 'free').toLowerCase()
  if (c === 'basic' || c === 'pro' || c === 'flagship') return `pkg-tag pkg-${c}`
  return 'pkg-tag pkg-free'
}

/** 演示账号默认功能码 */
export const DEMO_FEATURES: Record<string, { features: string; tenantStatus: string; role: string }> = {
  admin2: {
    role: 'platform_admin',
    tenantStatus: 'active',
    features: Object.values(FEATURES).join(','),
  },
  admin1: {
    role: 'property_admin',
    tenantStatus: 'active',
    features: PACKAGE_TIERS.find((p) => p.code === 'flagship')!.features.join(','),
  },
  degraded1: {
    role: 'property_admin',
    tenantStatus: 'degraded',
    features: `${FEATURES.BASIC_REPAIR},${FEATURES.BASIC_NOTICE}`,
  },
}

/** 演示社区 → 套餐（仅演示登录用；真实登录以后端为准） */
export const DEMO_COMMUNITY_PACKAGES: Record<
  string,
  { packageCode: string; packageName: string; features: string }
> = {
  翠湖湾社区: {
    packageCode: 'free',
    packageName: '免费版',
    features: PACKAGE_TIERS[0].features.join(','),
  },
  鹏程小区: {
    packageCode: 'basic',
    packageName: '基础版',
    features: PACKAGE_TIERS[1].features.join(','),
  },
  星河湾社区: {
    packageCode: 'pro',
    packageName: '专业版',
    features: PACKAGE_TIERS[2].features.join(','),
  },
  阳光花园社区: {
    packageCode: 'flagship',
    packageName: '旗舰版',
    features: PACKAGE_TIERS[3].features.join(','),
  },
  '演示社区（物业）': {
    packageCode: 'flagship',
    packageName: '旗舰版',
    features: PACKAGE_TIERS[3].features.join(','),
  },
}
