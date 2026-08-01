/**
 * 居民当前房屋上下文（全局）
 * 所有业务请求通过 request.js 自动携带 X-House-Id
 */
import { reactive } from 'vue'
import { getCurrentHouse, listMyHouses, setDefaultHouse } from '../api/house'

const STORAGE_HOUSE_ID = 'currentHouseId'
const STORAGE_HOUSE = 'currentHouse'
const STORAGE_HOUSES = 'boundHouses'

export const houseStore = reactive({
  houses: [],
  currentHouseId: null,
  currentHouse: null,
  loaded: false,

  get label() {
    const h = this.currentHouse
    if (!h) return '请绑定房屋'
    return h.label || `${h.communityName || ''} · ${h.building || ''}${h.unit || ''}${h.roomNo || ''}`
  },

  get shortLabel() {
    const h = this.currentHouse
    if (!h) return '未绑定房屋'
    return `${h.building || ''}${h.unit || ''}${h.roomNo || ''}` || h.label || '当前房屋'
  },

  get hasHouse() {
    return !!this.currentHouseId && this.houses.length > 0
  },

  hydrate() {
    this.currentHouseId = uni.getStorageSync(STORAGE_HOUSE_ID) || null
    this.currentHouse = uni.getStorageSync(STORAGE_HOUSE) || null
    this.houses = uni.getStorageSync(STORAGE_HOUSES) || []
    if (typeof this.houses === 'string') {
      try {
        this.houses = JSON.parse(this.houses)
      } catch (e) {
        this.houses = []
      }
    }
  },

  /** 登录结果写入 */
  applyLogin(data) {
    const houses = Array.isArray(data?.houses) ? data.houses : []
    this.houses = houses
    const id = data?.currentHouseId || (houses[0] && houses[0].id) || null
    const cur =
      data?.currentHouse ||
      houses.find((h) => String(h.id) === String(id)) ||
      houses[0] ||
      null
    this.setCurrent(cur, false)
    this.persistList()
    this.loaded = true
  },

  persistList() {
    uni.setStorageSync(STORAGE_HOUSES, this.houses)
  },

  setCurrent(house, persistDefault = false) {
    if (!house) {
      this.currentHouseId = null
      this.currentHouse = null
      uni.removeStorageSync(STORAGE_HOUSE_ID)
      uni.removeStorageSync(STORAGE_HOUSE)
      return
    }
    this.currentHouseId = house.id
    this.currentHouse = house
    uni.setStorageSync(STORAGE_HOUSE_ID, house.id)
    uni.setStorageSync(STORAGE_HOUSE, house)
    // 同步社区展示名（过滤乱码，避免把「阳光花园」覆盖成 ???）
    const cn = house.communityName
    if (cn && !/[?？]/.test(String(cn)) && String(cn).trim()) {
      uni.setStorageSync('communityName', cn)
    }
    if (persistDefault && house.id) {
      setDefaultHouse(house.id).catch(() => {})
    }
  },

  async refresh() {
    try {
      const data = await getCurrentHouse()
      if (data && data.bound) {
        this.houses = data.houses || []
        const cur =
          data.currentHouse ||
          this.houses.find((h) => String(h.id) === String(data.currentHouseId)) ||
          this.houses[0]
        this.setCurrent(cur, false)
        this.persistList()
      } else {
        const list = (await listMyHouses().catch(() => [])) || []
        this.houses = list
        if (list.length) {
          const def = list.find((h) => h.isDefault === 1) || list[0]
          this.setCurrent(def, false)
        } else {
          this.setCurrent(null, false)
        }
        this.persistList()
      }
    } catch (e) {
      // ignore
    } finally {
      this.loaded = true
    }
  },

  /** 切换已绑定房屋（本地 + 后端默认） */
  async switchTo(houseId) {
    const h = this.houses.find((x) => String(x.id) === String(houseId))
    if (!h) return false
    this.setCurrent(h, true)
    // 刷新 isDefault 标记
    this.houses = this.houses.map((x) => ({
      ...x,
      isDefault: String(x.id) === String(houseId) ? 1 : 0
    }))
    this.persistList()
    return true
  },

  clear() {
    this.houses = []
    this.currentHouseId = null
    this.currentHouse = null
    this.loaded = false
    ;[STORAGE_HOUSE_ID, STORAGE_HOUSE, STORAGE_HOUSES].forEach((k) => uni.removeStorageSync(k))
  },

  /**
   * 核心业务门禁：未绑定房屋时引导去绑定
   * @returns {boolean} 是否放行
   */
  guardBound(tip) {
    if (this.hasHouse) return true
    uni.showModal({
      title: '请先绑定房屋',
      content: tip || '使用报修、缴费、访客预约等功能前，请先绑定您的房屋。',
      confirmText: '去绑定',
      success: (res) => {
        if (res.confirm) {
          uni.navigateTo({ url: '/pages/house/bind' })
        }
      }
    })
    return false
  }
})

export function useHouseStore() {
  return houseStore
}
