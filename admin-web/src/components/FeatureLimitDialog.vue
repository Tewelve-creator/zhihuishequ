<template>
  <el-dialog
    v-model="visible"
    title="功能暂未开通"
    width="440px"
    append-to-body
    destroy-on-close
  >
    <div class="body">
      <el-icon class="lock"><Lock /></el-icon>
      <p class="title">当前套餐暂未包含此功能</p>
      <p class="line">当前社区使用：{{ user.packageName || '当前套餐' }}</p>
      <p v-if="needPkg" class="line muted">该功能通常需要：{{ needPkg }}及以上</p>
      <p v-else class="line muted">当前套餐未包含该功能。</p>
      <p class="tip">
        当前社区使用{{ user.packageName || '当前套餐' }}，该功能暂未开通。
      </p>
      <p v-if="featureLabel" class="feat-name">功能：{{ featureLabel }}</p>
    </div>
    <template #footer>
      <el-button @click="goDashboard">返回工作台</el-button>
      <el-button @click="goPackage">查看当前权益</el-button>
      <el-button type="primary" @click="contactAdmin">联系平台管理员</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { FEATURE_LABELS, minPackageNameForFeature } from '@/config/features'

const user = useUserStore()
const router = useRouter()

const visible = computed({
  get: () => user.featureLimitVisible,
  set: (v: boolean) => {
    if (!v) user.closeFeatureLimit()
  },
})

const needPkg = computed(() =>
  user.featureLimitCode ? minPackageNameForFeature(user.featureLimitCode) : null,
)
const featureLabel = computed(() =>
  user.featureLimitCode
    ? FEATURE_LABELS[user.featureLimitCode] || user.featureLimitCode
    : '',
)

function goDashboard() {
  user.closeFeatureLimit()
  router.push('/property/dashboard')
}

function goPackage() {
  user.closeFeatureLimit()
  router.push('/property/package/features')
}

function contactAdmin() {
  ElMessage.info('请联系平台管理员申请升级套餐')
}
</script>

<style scoped>
.body {
  text-align: center;
  padding: 8px 0 4px;
}
.lock {
  font-size: 42px;
  color: #8c8c8c;
  margin-bottom: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px;
}
.line {
  margin: 4px 0;
  color: #606266;
  font-size: 14px;
}
.muted {
  color: #909399;
}
.tip {
  margin-top: 14px;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
.feat-name {
  margin-top: 8px;
  font-size: 12px;
  color: #a8abb2;
}
</style>
