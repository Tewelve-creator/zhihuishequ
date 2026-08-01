<template>
  <div class="wrap">
    <div class="card">
      <el-icon class="lock"><Lock /></el-icon>
      <h2>当前套餐暂未包含此功能</h2>
      <p class="line">当前社区使用：{{ user.packageName || '当前套餐' }}</p>
      <p v-if="needPkg" class="line muted">该功能需要：{{ needPkg }}及以上</p>
      <p v-else class="line muted">当前套餐未包含此功能。</p>
      <p v-if="featureLabel" class="feat">功能：{{ featureLabel }}</p>
      <div class="actions">
        <el-button type="primary" @click="router.push('/property/dashboard')">返回工作台</el-button>
        <el-button @click="router.push('/property/package/features')">查看当前权益</el-button>
        <el-button @click="onContact">联系平台管理员</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { FEATURE_LABELS, minPackageNameForFeature } from '@/config/features'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const featureCode = computed(() => String(route.query.feature || ''))
const featureLabel = computed(
  () => FEATURE_LABELS[featureCode.value] || featureCode.value || '',
)
const needPkg = computed(() =>
  featureCode.value ? minPackageNameForFeature(featureCode.value) : null,
)

function onContact() {
  ElMessage.info('请联系平台管理员申请升级套餐')
}
</script>

<style scoped>
.wrap {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.card {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  text-align: center;
  box-shadow: 0 1px 4px #0000000a;
}
.lock {
  font-size: 48px;
  color: #8c8c8c;
  margin-bottom: 16px;
}
h2 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.line {
  margin: 6px 0;
  color: #606266;
  font-size: 14px;
}
.muted {
  color: #909399;
}
.feat {
  margin-top: 12px;
  font-size: 13px;
  color: #a8abb2;
}
.actions {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
</style>
