/**
 * 固定演示布局（假数据）— 视觉对齐参考图十字社区
 * 坐标系：Y↑，-Z 北（画面上方）
 */

export type DemoBuilding = {
  id: string
  x: number
  z: number
  w: number
  d: number
  floors: number
  rotY: number
  depth: number
  /** 是否显示青色选中框 */
  highlight?: boolean
}

export type DemoEvent = {
  id: string
  x: number
  y: number
  z: number
  level: 'normal' | 'warn'
  text: '普通事件' | '设备提醒' | '访客事件' | '预警事件'
  phase: number
}

export type DemoHud = {
  id: string
  x: number
  y: number
  z: number
  lines: string[]
}

/** 左上4 · 右上3 · 左下3 · 右下3 ≈ 参考图组团密度 */
export const DEMO_BUILDINGS: DemoBuilding[] = [
  // 左上
  { id: 'nw1', x: -15.8, z: -15.8, w: 6.8, d: 5.2, floors: 7, rotY: 0.03, depth: 0.5, highlight: true },
  { id: 'nw2', x: -8.0, z: -16.6, w: 6.0, d: 4.8, floors: 6, rotY: -0.04, depth: 0.48 },
  { id: 'nw3', x: -16.4, z: -9.2, w: 5.4, d: 6.4, floors: 8, rotY: 0.07, depth: 0.32 },
  { id: 'nw4', x: -8.6, z: -9.4, w: 5.6, d: 5.0, floors: 5, rotY: -0.02, depth: 0.28 },
  // 右上
  { id: 'ne1', x: 8.6, z: -16.2, w: 5.8, d: 5.0, floors: 6, rotY: 0.05, depth: 0.5 },
  { id: 'ne2', x: 16.2, z: -15.4, w: 6.6, d: 5.4, floors: 7, rotY: -0.03, depth: 0.55, highlight: true },
  { id: 'ne3', x: 12.4, z: -9.0, w: 7.2, d: 5.2, floors: 6, rotY: 0.06, depth: 0.34 },
  // 左下
  { id: 'sw1', x: -16.0, z: 9.0, w: 6.2, d: 5.2, floors: 6, rotY: -0.05, depth: 0.18 },
  { id: 'sw2', x: -8.2, z: 8.8, w: 5.6, d: 5.6, floors: 7, rotY: 0.04, depth: 0.14 },
  { id: 'sw3', x: -12.8, z: 16.0, w: 9.0, d: 5.4, floors: 5, rotY: 0.02, depth: 0.04 },
  // 右下
  { id: 'se1', x: 8.8, z: 8.6, w: 5.4, d: 5.8, floors: 7, rotY: -0.04, depth: 0.18 },
  { id: 'se2', x: 16.0, z: 9.2, w: 6.0, d: 5.0, floors: 6, rotY: 0.06, depth: 0.22 },
  { id: 'se3', x: 13.2, z: 15.8, w: 9.6, d: 5.6, floors: 5, rotY: -0.02, depth: 0.04, highlight: true },
]

export const DEMO_TREES: Array<{ x: number; z: number; s: number }> = [
  { x: -12.2, z: -12.8, s: 1.05 },
  { x: -6.2, z: -12.2, s: 0.9 },
  { x: -13.5, z: -5.8, s: 0.95 },
  { x: 6.2, z: -12.5, s: 0.85 },
  { x: 12.5, z: -12.8, s: 1.1 },
  { x: 14.8, z: -6.2, s: 0.9 },
  { x: -12.8, z: 6.0, s: 0.95 },
  { x: -6.0, z: 12.5, s: 1.05 },
  { x: -17.5, z: 12.8, s: 0.8 },
  { x: 6.2, z: 12.8, s: 0.95 },
  { x: 12.2, z: 5.8, s: 0.88 },
  { x: 17.5, z: 12.5, s: 1.0 },
  { x: -19.5, z: -19.0, s: 0.75 },
  { x: 19.5, z: -18.5, s: 0.8 },
  { x: -19.2, z: 18.8, s: 0.75 },
  { x: 19.0, z: 18.5, s: 0.85 },
  { x: -5.5, z: -5.5, s: 0.7 },
  { x: 5.5, z: 5.5, s: 0.7 },
]

export const DEMO_EVENTS: DemoEvent[] = [
  { id: 'e1', x: -14.5, y: 10.8, z: -14.5, level: 'normal', text: '普通事件', phase: 0.2 },
  { id: 'e2', x: -7.2, y: 9.6, z: -15.5, level: 'normal', text: '设备提醒', phase: 0.6 },
  { id: 'e3', x: -15.5, y: 12.2, z: -8.0, level: 'warn', text: '预警事件', phase: 1.0 },
  { id: 'e4', x: 9.2, y: 10.0, z: -15.2, level: 'normal', text: '访客事件', phase: 1.4 },
  { id: 'e5', x: 16.0, y: 10.8, z: -14.2, level: 'normal', text: '普通事件', phase: 1.8 },
  { id: 'e6', x: 12.0, y: 10.4, z: -8.2, level: 'warn', text: '预警事件', phase: 2.2 },
  { id: 'e7', x: 0.0, y: 4.2, z: -21.5, level: 'normal', text: '访客事件', phase: 0.4 },
  { id: 'e8', x: -15.0, y: 9.8, z: 9.5, level: 'normal', text: '设备提醒', phase: 2.6 },
  { id: 'e9', x: -7.5, y: 11.0, z: 9.0, level: 'normal', text: '普通事件', phase: 3.0 },
  { id: 'e10', x: -11.5, y: 8.8, z: 16.2, level: 'warn', text: '预警事件', phase: 0.9 },
  { id: 'e11', x: 9.5, y: 10.8, z: 9.0, level: 'normal', text: '普通事件', phase: 3.4 },
  { id: 'e12', x: 15.5, y: 9.6, z: 9.6, level: 'normal', text: '设备提醒', phase: 3.8 },
  { id: 'e13', x: 13.5, y: 8.6, z: 16.0, level: 'normal', text: '普通事件', phase: 4.2 },
  { id: 'e14', x: 0.0, y: 4.2, z: 21.5, level: 'normal', text: '访客事件', phase: 1.2 },
]

export const DEMO_HUDS: DemoHud[] = [
  { id: 'h1', x: -15.8, y: 11.8, z: -15.8, lines: ['1栋组团', '运行正常'] },
  { id: 'h2', x: 16.2, y: 11.4, z: -15.4, lines: ['东区组团', '设备在线'] },
  { id: 'h3', x: -12.8, y: 9.6, z: 16.0, lines: ['南苑组团', '通行畅通'] },
  { id: 'h4', x: 13.2, y: 9.4, z: 15.8, lines: ['东南组团', '态势平稳'] },
]

/** 道路光轨点（沿十字路） */
export const DEMO_TRAILS: Array<{ x: number; z: number; axis: 'v' | 'h' }> = [
  { x: 0, z: -20, axis: 'v' },
  { x: 0, z: -14, axis: 'v' },
  { x: 0, z: -8, axis: 'v' },
  { x: 0, z: 8, axis: 'v' },
  { x: 0, z: 14, axis: 'v' },
  { x: 0, z: 20, axis: 'v' },
  { x: -20, z: 0, axis: 'h' },
  { x: -14, z: 0, axis: 'h' },
  { x: -8, z: 0, axis: 'h' },
  { x: 8, z: 0, axis: 'h' },
  { x: 14, z: 0, axis: 'h' },
  { x: 20, z: 0, axis: 'h' },
]

export const DEMO_COLORS = {
  bg: 0x03131c,
  ground: 0x071d27,
  road: 0x0b5260,
  building: 0x0d5662,
  edge: 0x58eff2,
  event: 0x27dce6,
  warn: 0xffad32,
  tree: 0x0b3a35,
  text: '#dffcff',
}
