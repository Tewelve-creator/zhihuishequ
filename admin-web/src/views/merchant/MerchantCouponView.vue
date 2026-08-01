<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>优惠券管理</h2>
        <p class="sub">本店优惠券发放与下架 · 仅限当前店铺商品使用</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="优惠券名称"
        style="width: 200px"
        @keyup.enter="onSearch"
      />
      <el-select v-model="filterStatus" placeholder="优惠券状态" style="width: 150px" @change="onSearch">
        <el-option label="全部" value="ALL" />
        <el-option label="进行中" value="ONGOING" />
        <el-option label="未开始" value="NOT_STARTED" />
        <el-option label="已结束" value="ENDED" />
        <el-option label="已下架" value="OFF" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button class="btn-add" type="primary" @click="openEdit()">新增优惠券</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedList" stripe empty-text="暂无优惠券">
      <el-table-column label="优惠券图片" width="88">
        <template #default="{ row }">
          <el-image
            v-if="row.coverUrl"
            :src="fileUrl(row.coverUrl)"
            :preview-src-list="[fileUrl(row.coverUrl)]"
            fit="cover"
            class="thumb"
          />
          <div v-else class="thumb empty">无图</div>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="券名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="couponTypeLabel" label="优惠类型" width="100" />
      <el-table-column prop="ruleText" label="优惠规则" min-width="120" show-overflow-tooltip />
      <el-table-column prop="thresholdText" label="使用门槛" width="110" />
      <el-table-column prop="validPeriod" label="有效期" min-width="200" show-overflow-tooltip />
      <el-table-column prop="totalCount" label="发放总量" width="90" align="right" />
      <el-table-column prop="claimedCount" label="已领取" width="90" align="right" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row.statusTagType as any) || 'info'" size="small">
            {{ row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.canOffShelf"
            link
            type="warning"
            @click="onOff(row)"
          >下架</el-button>
          <el-button link type="primary" @click="openClaims(row)">领取记录</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        :total="list.length"
      />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑优惠券' : '新增优惠券'"
      width="560px"
      destroy-on-close
    >
      <el-alert
        v-if="form.amountLocked"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
        title="进行中的优惠券无法修改优惠金额/类型，仅可调整展示信息或下架"
      />
      <el-form label-width="110px">
        <el-form-item label="优惠券名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="如：新客满减券" />
        </el-form-item>
        <el-form-item label="优惠券类型" required>
          <el-select v-model="form.couponType" style="width: 100%" :disabled="form.amountLocked">
            <el-option label="满减券" value="FULL_REDUCE" />
            <el-option label="无门槛券" value="NO_THRESHOLD" />
            <el-option label="折扣券" value="DISCOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.couponType === 'FULL_REDUCE' || form.couponType === 'DISCOUNT'"
          label="使用门槛"
        >
          <el-input-number
            v-model="form.thresholdAmount"
            :min="0"
            :precision="2"
            :disabled="form.amountLocked"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          v-if="form.couponType === 'FULL_REDUCE' || form.couponType === 'NO_THRESHOLD'"
          :label="form.couponType === 'NO_THRESHOLD' ? '立减金额' : '减免金额'"
          required
        >
          <el-input-number
            v-model="form.discountAmount"
            :min="0.01"
            :precision="2"
            :disabled="form.amountLocked"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="form.couponType === 'DISCOUNT'" label="折扣（折）" required>
          <el-input-number
            v-model="form.discountRate"
            :min="0.1"
            :max="9.9"
            :precision="1"
            :step="0.1"
            :disabled="form.amountLocked"
            style="width: 100%"
          />
          <div class="form-hint">例如 8.5 表示 8.5 折</div>
        </el-form-item>
        <el-form-item label="发放总量" required>
          <el-input-number v-model="form.totalCount" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单人限领" required>
          <el-input-number v-model="form.limitPerUser" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="活动时间" required>
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="使用备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="200"
            placeholder="仅限本店商品使用"
          />
        </el-form-item>
        <el-form-item label="优惠券图片">
          <div class="cover-row">
            <el-image
              v-if="form.coverUrl"
              :src="fileUrl(form.coverUrl)"
              fit="cover"
              class="cover-preview"
            />
            <div v-else class="cover-preview empty">暂无图片</div>
            <div class="cover-actions">
              <el-upload
                :show-file-list="false"
                accept="image/jpeg,image/png,image/webp,image/gif"
                :http-request="onUploadCover"
                :disabled="uploading"
              >
                <el-button type="primary" :loading="uploading">
                  {{ form.coverUrl ? '重新上传' : '上传图片' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.coverUrl" link type="danger" @click="form.coverUrl = ''">清除</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 领取记录 -->
    <el-dialog v-model="claimsVisible" :title="`领取记录 · ${claimsTitle}`" width="720px" destroy-on-close>
      <el-table v-loading="claimsLoading" :data="claims" stripe empty-text="暂无领取记录" max-height="420">
        <el-table-column prop="userName" label="用户" width="120">
          <template #default="{ row }">{{ row.userName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="userPhone" label="手机号" width="130">
          <template #default="{ row }">{{ row.userPhone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="claimStatusLabel" label="状态" width="100" />
        <el-table-column prop="claimTime" label="领取时间" min-width="160" />
        <el-table-column prop="useTime" label="使用时间" min-width="160">
          <template #default="{ row }">{{ row.useTime || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  listMallCouponClaims,
  listMallCoupons,
  offMallCoupon,
  saveMallCoupon,
} from '@/api/mall'
import { fileUrl, uploadFile } from '@/api/file'

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const claimsLoading = ref(false)
const list = ref<any[]>([])
const claims = ref<any[]>([])
const keyword = ref('')
const filterStatus = ref('ALL')
const page = ref(1)
const pageSize = ref(10)
const visible = ref(false)
const claimsVisible = ref(false)
const claimsTitle = ref('')

const form = reactive({
  id: undefined as number | undefined,
  name: '',
  couponType: 'FULL_REDUCE',
  thresholdAmount: 0,
  discountAmount: 10,
  discountRate: 8.5,
  totalCount: 100,
  limitPerUser: 1,
  timeRange: null as string[] | null,
  remark: '仅限本店商品使用',
  coverUrl: '',
  amountLocked: false,
})

const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

function onSearch() {
  page.value = 1
  load()
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await listMallCoupons({
        keyword: keyword.value || undefined,
        status: filterStatus.value === 'ALL' ? undefined : filterStatus.value,
      })) || []
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openEdit(row?: any) {
  if (row) {
    form.id = row.id
    form.name = row.name || ''
    form.couponType = row.couponType || 'FULL_REDUCE'
    form.thresholdAmount = Number(row.thresholdAmount || 0)
    form.discountAmount = Number(row.discountAmount || 0)
    form.discountRate = Number(row.discountRate || 8.5)
    form.totalCount = Number(row.totalCount || 0)
    form.limitPerUser = Number(row.limitPerUser || 1)
    form.remark = row.remark || '仅限本店商品使用'
    form.coverUrl = row.coverUrl || ''
    form.amountLocked = !!row.amountLocked
    form.timeRange =
      row.startTime && row.endTime
        ? [String(row.startTime).slice(0, 19), String(row.endTime).slice(0, 19)]
        : null
  } else {
    form.id = undefined
    form.name = ''
    form.couponType = 'FULL_REDUCE'
    form.thresholdAmount = 50
    form.discountAmount = 10
    form.discountRate = 8.5
    form.totalCount = 100
    form.limitPerUser = 1
    form.remark = '仅限本店商品使用'
    form.coverUrl = ''
    form.amountLocked = false
    form.timeRange = null
  }
  visible.value = true
}

async function onUploadCover(opt: UploadRequestOptions) {
  const raw = opt.file as File
  if (!raw) return
  uploading.value = true
  try {
    const url = await uploadFile(raw)
    form.coverUrl = url
    ElMessage.success('图片已上传')
    opt.onSuccess?.(url as any)
  } catch (e: any) {
    ElMessage.error(e?.msg || '上传失败')
    opt.onError?.(e)
  } finally {
    uploading.value = false
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写优惠券名称')
    return
  }
  if (!form.timeRange || form.timeRange.length < 2) {
    ElMessage.warning('请选择活动起止时间')
    return
  }
  saving.value = true
  try {
    await saveMallCoupon({
      id: form.id,
      name: form.name.trim(),
      couponType: form.couponType,
      thresholdAmount: form.couponType === 'NO_THRESHOLD' ? 0 : form.thresholdAmount,
      discountAmount: form.couponType === 'DISCOUNT' ? 0 : form.discountAmount,
      discountRate: form.couponType === 'DISCOUNT' ? form.discountRate : null,
      totalCount: form.totalCount,
      limitPerUser: form.limitPerUser,
      startTime: form.timeRange[0],
      endTime: form.timeRange[1],
      remark: form.remark || '仅限本店商品使用',
      coverUrl: form.coverUrl,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onOff(row: any) {
  await ElMessageBox.confirm(`确认下架优惠券「${row.name}」？下架后用户将无法继续领取。`, '下架确认', {
    type: 'warning',
  })
  await offMallCoupon(row.id)
  ElMessage.success('已下架')
  await load()
}

async function openClaims(row: any) {
  claimsTitle.value = row.name || ''
  claimsVisible.value = true
  claimsLoading.value = true
  try {
    claims.value = (await listMallCouponClaims(row.id)) || []
  } catch {
    claims.value = []
  } finally {
    claimsLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.sub { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.btn-add {
  --el-button-bg-color: #1677ff;
  --el-button-border-color: #1677ff;
  --el-button-hover-bg-color: #4096ff;
  --el-button-hover-border-color: #4096ff;
  --el-button-active-bg-color: #0958d9;
  --el-button-active-border-color: #0958d9;
  background: #1677ff;
  border-color: #1677ff;
}
.btn-add:hover,
.btn-add:focus {
  background: #4096ff;
  border-color: #4096ff;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: #f1f5f9;
}
.thumb.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
}
.cover-row { display: flex; align-items: center; gap: 12px; }
.cover-preview {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  background: #f1f5f9;
}
.cover-preview.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #94a3b8;
}
.cover-actions { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.form-hint { margin: 4px 0 0; color: #94a3b8; font-size: 12px; }
</style>
