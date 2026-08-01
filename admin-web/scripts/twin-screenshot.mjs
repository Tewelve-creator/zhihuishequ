/**
 * 楼栋态势演示场景截图：1920×1080 / 1440×900 / 1366×768
 */
import { chromium } from 'playwright'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, '../tmp/twin-shots')
mkdirSync(OUT, { recursive: true })

const BASE = process.env.TWIN_BASE || 'http://localhost:5174'
const sizes = [
  { name: '1920x1080', w: 1920, h: 1080 },
  { name: '1440x900', w: 1440, h: 900 },
  { name: '1366x768', w: 1366, h: 768 },
]

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  // 选择物业端入口
  const propBtn = page.getByRole('button', { name: /物业/ }).first()
  if (await propBtn.isVisible().catch(() => false)) {
    await propBtn.click()
  } else {
    // 卡片点击
    const card = page.locator('.portal-card, .pick-card, [data-portal="property"]').first()
    if (await card.count()) await card.click()
    else await page.locator('text=物业').first().click()
  }
  await page.waitForTimeout(400)
  await page.locator('input').nth(0).fill('admin1')
  await page.locator('input[type="password"]').fill('admin1')
  await page.getByRole('button', { name: /登录/ }).click()
  await page.waitForURL(/property/, { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(800)
}

async function sampleCanvas(page) {
  return page.evaluate(() => {
    const canvas = document.querySelector('.twin-host canvas')
    if (!canvas) return { ok: false, reason: 'no-canvas' }
    const w = canvas.width
    const h = canvas.height
    if (w < 10 || h < 10) return { ok: false, reason: 'tiny', w, h }
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    // WebGL canvas 可能无法 getImageData；改用 toDataURL 抽样
    const url = canvas.toDataURL('image/png')
    return { ok: url.length > 5000, reason: url.length > 5000 ? 'ok' : 'empty', bytes: url.length, w, h }
  })
}

const browser = await chromium.launch({ headless: true })
const report = []

for (const s of sizes) {
  const context = await browser.newContext({ viewport: { width: s.w, height: s.h } })
  const page = await context.newPage()
  await login(page)
  await page.goto(`${BASE}/property/cockpit`, { waitUntil: 'networkidle' })
  await page.waitForSelector('.twin-host canvas', { timeout: 20000 })
  await page.waitForTimeout(2500)
  const sample = await sampleCanvas(page)
  const path = join(OUT, `twin-${s.name}.png`)
  // 优先截中栏地图区域
  const host = page.locator('.sit-map-wrap, .twin-host').first()
  if (await host.count()) {
    await host.screenshot({ path })
  } else {
    await page.screenshot({ path, fullPage: false })
  }
  report.push({ size: s.name, path, sample })
  console.log(JSON.stringify({ size: s.name, path, sample }))
  await context.close()
}

writeFileSync(join(OUT, 'report.json'), JSON.stringify(report, null, 2))
await browser.close()
console.log('done', OUT)
