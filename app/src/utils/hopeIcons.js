/**
 * 希望云风格图标：上排白图标(色块底) / 下排彩色图标(无底)
 */
function svgUri(body) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none">${body}</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/** 上排：白图标（放在彩色圆角色块上） */
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

/** 希望云风格 Banner 插画（夜景简绘） */
export function hopeBannerSvg() {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="750" height="520" viewBox="0 0 750 520">
    <defs>
      <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#1a3a5c"/>
        <stop offset="45%" stop-color="#2d5a7a"/>
        <stop offset="100%" stop-color="#7eb8a4"/>
      </linearGradient>
      <linearGradient id="hill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#5a9e78"/>
        <stop offset="100%" stop-color="#3d7a5c"/>
      </linearGradient>
    </defs>
    <rect width="750" height="520" fill="url(#sky)"/>
    <circle cx="620" cy="90" r="36" fill="#f5e6a8" opacity=".9"/>
    <circle cx="120" cy="70" r="2" fill="#fff" opacity=".8"/>
    <circle cx="200" cy="110" r="1.5" fill="#fff" opacity=".7"/>
    <circle cx="480" cy="60" r="2" fill="#fff" opacity=".85"/>
    <circle cx="540" cy="130" r="1.5" fill="#fff" opacity=".6"/>
    <path d="M380 80l8 22 23 0-18 14 7 22-20-13-20 13 7-22-18-14h23z" fill="#ffe9a8" opacity=".75"/>
    <path d="M450 50l5 14 15 0-12 9 4 14-12-8-12 8 4-14-12-9h15z" fill="#ffe9a8" opacity=".55"/>
    <path d="M0 320 Q180 240 360 300 T750 280 L750 520 L0 520Z" fill="url(#hill)"/>
    <path d="M0 380 Q200 320 400 370 T750 350 L750 520 L0 520Z" fill="#4a8f6a" opacity=".85"/>
    <rect x="40" y="250" width="70" height="90" rx="4" fill="#2a4a5c"/>
    <rect x="55" y="265" width="16" height="16" rx="2" fill="#f5d76e"/>
    <rect x="85" y="265" width="16" height="16" rx="2" fill="#f5d76e"/>
    <rect x="55" y="295" width="16" height="16" rx="2" fill="#7ec8e8"/>
    <ellipse cx="220" cy="360" rx="28" ry="12" fill="#2d5a45"/>
    <circle cx="210" cy="340" r="14" fill="#f0c9a0"/>
    <circle cx="245" cy="338" r="12" fill="#f0c9a0"/>
    <circle cx="275" cy="345" r="11" fill="#f0c9a0"/>
    <path d="M198 355c0-10 8-16 20-16s18 8 18 16" fill="#5b8def"/>
    <path d="M235 352c0-8 7-14 16-14s14 7 14 14" fill="#ec6b9b"/>
    <path d="M265 358c0-7 6-12 14-12s12 6 12 12" fill="#f0a04b"/>
    <ellipse cx="620" cy="300" rx="55" ry="18" fill="#3d6b55" opacity=".5"/>
    <circle cx="620" cy="250" r="42" fill="#f5d76e"/>
    <circle cx="605" cy="242" r="5" fill="#3a3a3a"/>
    <circle cx="635" cy="242" r="5" fill="#3a3a3a"/>
    <path d="M608 262c6 6 18 6 24 0" stroke="#3a3a3a" stroke-width="3" fill="none" stroke-linecap="round"/>
    <ellipse cx="620" cy="210" rx="28" ry="12" fill="#47b686"/>
    <text x="48" y="200" font-size="64" font-weight="700" fill="#fff" font-family="PingFang SC, Microsoft YaHei, sans-serif">生活</text>
    <text x="48" y="275" font-size="64" font-weight="700" fill="#fff" font-family="PingFang SC, Microsoft YaHei, sans-serif">安心美好</text>
  </svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}
