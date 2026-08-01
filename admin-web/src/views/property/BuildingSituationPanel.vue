<template>
  <div class="sit">
    <header class="sit-head">
      <h3 class="sit-title">楼栋态势</h3>
    </header>
    <div class="sit-map-wrap">
      <div ref="mapHost" class="twin-host" />
      <Transition name="tip">
        <div v-if="tip" class="sit-tip">{{ tip }}</div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 固定演示版楼栋态势：不接真实楼栋/事件数据，不展示右侧详情面板。
 * 保留 props 签名以兼容驾驶舱挂载，但不消费业务字段。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { CommunityTwinScene } from '@/views/property/twin/CommunityTwinScene'

defineProps<{
  buildings?: unknown
  mapMarkers?: unknown
  recentEvents?: unknown
  todo?: unknown
  community?: unknown
  loading?: boolean
  error?: boolean
  updatedAt?: string | number | Date
}>()

defineEmits<{ detail: [biz: string] }>()

const mapHost = ref<HTMLElement | null>(null)
const tip = ref('')
let scene: CommunityTwinScene | null = null
let ro: ResizeObserver | null = null
let tipTimer: ReturnType<typeof setTimeout> | null = null

function showTip(text: string) {
  tip.value = text
  if (tipTimer) clearTimeout(tipTimer)
  tipTimer = setTimeout(() => {
    tip.value = ''
  }, 1800)
}

onMounted(() => {
  if (!mapHost.value) return
  scene = new CommunityTwinScene(mapHost.value)
  scene.setTipHandler(showTip)
  scene.init()
  ro = new ResizeObserver(() => scene?.resize())
  ro.observe(mapHost.value)
  requestAnimationFrame(() => {
    requestAnimationFrame(() => scene?.resize())
  })
})

onUnmounted(() => {
  if (tipTimer) clearTimeout(tipTimer)
  ro?.disconnect()
  scene?.dispose()
  scene = null
})
</script>

<style scoped>
.sit {
  --bg: #03131c;
  --panel: #071d27;
  --border: #1a4a56;
  --text: #dffcff;
  --muted: #7aa8b0;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--panel);
  border: 1px solid #123844;
  border-radius: 6px;
  overflow: hidden;
}
.sit-head {
  display: flex;
  align-items: center;
  padding: 8px 12px 6px;
  border-bottom: 1px solid #123844;
  flex-shrink: 0;
}
.sit-title {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  color: var(--text);
  letter-spacing: 0.04em;
}
.sit-map-wrap {
  position: relative;
  flex: 1;
  min-height: 280px;
  min-width: 0;
  overflow: hidden;
}
.twin-host {
  position: absolute;
  inset: 0;
  background: var(--bg);
  overflow: hidden;
}
.twin-host :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
}
.sit-tip {
  position: absolute;
  left: 50%;
  bottom: 18px;
  transform: translateX(-50%);
  z-index: 6;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #2a8a9a;
  background: rgba(7, 29, 39, 0.92);
  color: var(--text);
  font-size: 13px;
  pointer-events: none;
  letter-spacing: 0.04em;
}
.tip-enter-active,
.tip-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.tip-enter-from,
.tip-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px);
}
</style>

<!-- CSS2D 动态节点（无 scoped） -->
<style>
.twin-gate-label {
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(7, 29, 39, 0.9);
  border: 1px solid #58eff2;
  color: #dffcff;
  font-size: 12px;
  letter-spacing: 0.08em;
  pointer-events: none;
  box-shadow: 0 0 12px rgba(88, 239, 242, 0.25);
}
.twin-hud-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 88px;
  padding: 8px 10px;
  border-radius: 4px;
  background: rgba(8, 40, 48, 0.78);
  border: 1px solid rgba(88, 239, 242, 0.55);
  color: #dffcff;
  font-size: 11px;
  line-height: 1.35;
  pointer-events: none;
  box-shadow: 0 0 14px rgba(39, 220, 230, 0.18);
}
.twin-hud-card span:first-child {
  font-weight: 650;
  font-size: 12px;
  color: #9ef4f8;
}
</style>
