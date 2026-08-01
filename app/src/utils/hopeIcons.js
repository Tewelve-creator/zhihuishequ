/**
 * 希望云风格图标：彩色色块底 + 白图标
 */
function svgUri(body) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none">${body}</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/** 白图标（放在彩色圆角色块上） */
export const ICON_WHITE = {
  // 对话气泡 · AI（对齐门禁位视觉权重）
  ai: svgUri(`
    <rect x="9" y="10" width="30" height="22" rx="8" fill="#fff"/>
    <path d="M18 32l-6 7 10-5h8c4 0 8-3.5 8-8V18" fill="#fff"/>
    <circle cx="19" cy="21" r="2.3" fill="#47b686"/>
    <circle cx="24" cy="21" r="2.3" fill="#47b686"/>
    <circle cx="29" cy="21" r="2.3" fill="#47b686"/>
  `),
  // 扳手螺丝刀交叉 · 报修
  repair: svgUri(`
    <path d="M18 30l10-10 5 5-10 10-6 1.5 1.5-6.5z" fill="#fff"/>
    <path d="M29 9.5a7.5 7.5 0 0 1 7.5 7.5c0 1.2-.3 2.4-.8 3.4L29 27l-4.5-4.5 6.7-6.7A4.5 4.5 0 0 0 29 9.5z" fill="#fff"/>
    <rect x="11" y="31" width="3.5" height="11" rx="1.5" transform="rotate(-40 12.5 36.5)" fill="#fff"/>
  `),
  // 访客证 · 访客
  visitor: svgUri(`
    <rect x="8" y="12" width="32" height="24" rx="5" fill="#fff"/>
    <circle cx="19" cy="22" r="5" fill="#4ea8e8"/>
    <path d="M12 32c1.2-3.5 3.8-5 7-5s5.8 1.5 7 5" fill="#4ea8e8"/>
    <path d="M29 20h8M29 25h8M29 30h5" stroke="#4ea8e8" stroke-width="2.2" stroke-linecap="round"/>
  `),
  // 账单+币 · 缴费
  pay: svgUri(`
    <rect x="10" y="8" width="20" height="28" rx="3" fill="#fff"/>
    <path d="M15 15h10M15 21h10M15 27h7" stroke="#f5a623" stroke-width="2.3" stroke-linecap="round"/>
    <circle cx="33" cy="33" r="9" fill="#fff"/>
    <circle cx="33" cy="33" r="6.5" fill="#f5a623"/>
    <text x="33" y="36.5" text-anchor="middle" font-size="11" font-weight="700" fill="#fff">¥</text>
  `),
  // 购物袋 · 商城
  mall: svgUri(`
    <path d="M14 18h20l-1.5 20a3 3 0 0 1-3 2.8H18.5a3 3 0 0 1-3-2.8L14 18z" fill="#fff"/>
    <path d="M18 18v-3.5a6 6 0 0 1 12 0V18" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none"/>
    <circle cx="20" cy="28" r="1.8" fill="#f0a04b"/>
    <circle cx="28" cy="28" r="1.8" fill="#f0a04b"/>
  `),
  // 旗帜 · 活动
  activity: svgUri(`
    <path d="M14 8v32" stroke="#fff" stroke-width="3.2" stroke-linecap="round"/>
    <path d="M16 10h18c1 0 1.6 1.1.9 1.9L31 18l3.9 6.1c.7.8.1 1.9-.9 1.9H16V10z" fill="#fff"/>
  `),
  // 信封 · 公告
  notice: svgUri(`
    <rect x="7" y="13" width="34" height="22" rx="4" fill="#fff"/>
    <path d="M7 17l17 11 17-11" stroke="#f5c242" stroke-width="2.6" fill="none" stroke-linejoin="round"/>
  `),
  // 房子 · 失物
  lost: svgUri(`
    <path d="M8 22L24 9l16 13v16a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V22z" fill="#fff"/>
    <path d="M20 40V28h8v12" fill="#47b686"/>
  `),
  // 心形 · 倾诉/邻里圈
  appeal: svgUri(`
    <path d="M24 38S10 28.5 10 20C10 14.5 14.2 12 18.5 12c2.6 0 4.5 1.2 5.5 2.6C25 13.2 26.9 12 29.5 12 33.8 12 38 14.5 38 20 38 28.5 24 38 24 38z" fill="#fff"/>
  `),
  // 相机 · 拍照报修
  camera: svgUri(`
    <rect x="8" y="16" width="32" height="22" rx="5" fill="#fff"/>
    <path d="M16 16l3-5h10l3 5" fill="#fff"/>
    <circle cx="24" cy="27" r="7" fill="#4f8fe8"/>
    <circle cx="24" cy="27" r="3.5" fill="#fff"/>
  `),
  // 喇叭 · 意见反馈
  feedback: svgUri(`
    <path d="M10 20h8l14-8v24L18 28h-8a4 4 0 0 1-4-4v0a4 4 0 0 1 4-4z" fill="#fff"/>
    <path d="M36 18c2.5 2 2.5 10 0 12" stroke="#fff" stroke-width="2.6" stroke-linecap="round" fill="none"/>
  `),
  // 清单 · 情绪调解
  emotion: svgUri(`
    <rect x="10" y="8" width="28" height="32" rx="4" fill="#fff"/>
    <path d="M16 16h16M16 24h16M16 32h10" stroke="#5b7de0" stroke-width="2.6" stroke-linecap="round"/>
  `),
  // 互助小家
  help: svgUri(`
    <path d="M8 22L24 9l16 13v16a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V22z" fill="#fff"/>
    <path d="M20 40V28h8v12" fill="#47b686"/>
  `),
  // 路线点 · 巡检
  inspection: svgUri(`
    <circle cx="14" cy="14" r="5" fill="#fff"/>
    <circle cx="34" cy="24" r="5" fill="#fff"/>
    <circle cx="18" cy="36" r="5" fill="#fff"/>
    <path d="M18 16l12 6M30 28l-8 6" stroke="#fff" stroke-width="3" stroke-linecap="round"/>
  `),
  // 齿轮 · 维保
  equip: svgUri(`
    <circle cx="24" cy="24" r="7" fill="none" stroke="#fff" stroke-width="3.2"/>
    <path d="M24 8v5M24 35v5M8 24h5M35 24h5M12.5 12.5l3.5 3.5M32 32l3.5 3.5M12.5 35.5l3.5-3.5M32 16l3.5-3.5" stroke="#fff" stroke-width="3.2" stroke-linecap="round"/>
  `)
}

/** 下排：彩色扁平图标（无底色块，直接贴白卡片） */
export const ICON_COLOR = {
  // 活动 · 旗帜/星星
  activity: svgUri(`
    <path d="M14 8v32" stroke="#f0a04b" stroke-width="3.2" stroke-linecap="round"/>
    <path d="M16 10h18c1 0 1.6 1.1.9 1.9L31 18l3.9 6.1c.7.8.1 1.9-.9 1.9H16V10z" fill="#f0a04b"/>
    <circle cx="36" cy="34" r="7" fill="#ffd08a"/>
    <path d="M36 30.5v7M32.5 34h7" stroke="#f0a04b" stroke-width="2" stroke-linecap="round"/>
  `),
  // 公告 · 信封+笔
  notice: svgUri(`
    <rect x="7" y="13" width="30" height="22" rx="4" fill="#f5c242"/>
    <path d="M7 17l15 10 15-10" stroke="#fff" stroke-width="2.4" fill="none" stroke-linejoin="round"/>
    <path d="M30 30l10-14 3 2-10 14-4.5.8L30 30z" fill="#e8a317"/>
    <path d="M31.5 31.5l2 2" stroke="#fff" stroke-width="1.5"/>
  `),
  // 失物 · 房子+心（对齐房屋体检视觉）
  lost: svgUri(`
    <path d="M8 22L24 9l16 13v16a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V22z" fill="#47b686"/>
    <path d="M20 40V28h8v12" fill="#fff"/>
    <path d="M24 22.5c-2.8-2.6-7 0-4.2 3.6L24 30l4.2-3.9c2.8-3.6-1.4-6.2-4.2-3.6z" fill="#fff"/>
  `),
  // 倾诉 · 双心（对齐表扬）
  appeal: svgUri(`
    <path d="M28 36S16 28.5 16 21.5C16 17.2 19.2 15 22.5 15c2 0 3.5 1 4.2 2.1.7-1.1 2.3-2.1 4.3-2.1C34.3 15 37.5 17.2 37.5 21.5 37.5 28.5 28 36 28 36z" fill="#c084fc"/>
    <path d="M18 32S8 25.5 8 19.8C8 16.2 10.6 14 13.4 14c1.7 0 3 .8 3.6 1.8.4-.6 1.2-1.2 2.2-1.5-2.2 2.3-1.5 5.6.8 7.8L18 32z" fill="#a855f7"/>
  `)
}

export function whiteIcon(key) {
  return ICON_WHITE[key] || ICON_WHITE.ai
}

export function colorIcon(key) {
  return ICON_COLOR[key] || ICON_COLOR.activity
}

/** 首页顶栏插画：浅绿渐变 + 星月 + 小屋 */
export function hopeBannerSvg() {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="750" height="420" viewBox="0 0 750 420">
    <defs>
      <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#7ed6b0"/>
        <stop offset="55%" stop-color="#5ec994"/>
        <stop offset="100%" stop-color="#47b686"/>
      </linearGradient>
      <linearGradient id="house" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#fff8e8"/>
        <stop offset="100%" stop-color="#ffe4b5"/>
      </linearGradient>
    </defs>
    <rect width="750" height="420" fill="url(#sky)"/>
    <circle cx="560" cy="72" r="28" fill="#fff6c8" opacity=".95"/>
    <path d="M560 44a28 28 0 1 0 24 48 22 22 0 1 1-24-48z" fill="#7ed6b0" opacity=".35"/>
    <path d="M120 48l6 16 17 0-14 10 5 16-14-10-14 10 5-16-14-10h17z" fill="#ffe9a0" opacity=".9"/>
    <path d="M200 90l4 11 12 0-10 7 4 11-10-7-10 7 4-11-10-7h12z" fill="#ffe9a0" opacity=".7"/>
    <path d="M480 40l5 13 14 0-11 8 4 13-12-8-12 8 4-13-11-8h14z" fill="#ffe9a0" opacity=".8"/>
    <path d="M640 110l3 9 10 0-8 6 3 9-8-6-8 6 3-9-8-6h10z" fill="#ffe9a0" opacity=".65"/>
    <g transform="translate(520,150)">
      <ellipse cx="90" cy="160" rx="70" ry="18" fill="#3f9e72" opacity=".35"/>
      <path d="M40 120 L90 55 L140 120 Z" fill="#f5a86c"/>
      <rect x="55" y="120" width="70" height="55" rx="4" fill="url(#house)"/>
      <rect x="78" y="140" width="24" height="35" rx="3" fill="#6bb8e8"/>
      <rect x="62" y="130" width="16" height="16" rx="2" fill="#8fd3f0"/>
      <rect x="102" y="130" width="16" height="16" rx="2" fill="#8fd3f0"/>
      <circle cx="40" cy="105" r="10" fill="#7ed6b0"/>
      <circle cx="145" cy="112" r="14" fill="#6ed3a8"/>
      <rect x="28" y="105" width="8" height="40" fill="#8b6914"/>
      <rect x="140" y="112" width="8" height="48" fill="#8b6914"/>
    </g>
    <text x="40" y="210" font-size="56" font-weight="800" fill="#fff" font-family="PingFang SC, Microsoft YaHei, sans-serif">生活</text>
    <text x="40" y="280" font-size="56" font-weight="800" fill="#fff" font-family="PingFang SC, Microsoft YaHei, sans-serif">安心美好</text>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}
