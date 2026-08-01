<template>
  <span
    class="feature-gate"
    :class="{ 'is-locked': locked }"
    @click.capture="onCapture"
  >
    <el-tooltip
      :disabled="!locked || !hint"
      :content="hint"
      placement="top"
    >
      <span class="feature-gate__inner">
        <slot :locked="locked" :state="state" />
        <el-icon v-if="locked && showLock" class="feature-gate__lock"><Lock /></el-icon>
      </span>
    </el-tooltip>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    feature: string
    /** 是否显示锁图标 */
    showLock?: boolean
  }>(),
  { showLock: true },
)

const user = useUserStore()
const state = computed(() =>
  props.feature ? user.getFeatureState(props.feature) : ('available' as const),
)
const locked = computed(() => !!props.feature && state.value !== 'available')
const hint = computed(() =>
  locked.value ? user.featureHint(props.feature) || '当前套餐未包含此功能' : '',
)

function onCapture(e: Event) {
  if (!locked.value || !props.feature) return
  e.preventDefault()
  e.stopPropagation()
  user.openFeatureLimit(props.feature)
}
</script>

<style scoped>
.feature-gate.is-locked {
  cursor: not-allowed;
  opacity: 0.72;
}
.feature-gate.is-locked :deep(.el-button),
.feature-gate.is-locked :deep(button) {
  pointer-events: none;
  filter: grayscale(0.35);
}
.feature-gate__inner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.feature-gate__lock {
  color: #8c8c8c;
  font-size: 14px;
}
</style>
