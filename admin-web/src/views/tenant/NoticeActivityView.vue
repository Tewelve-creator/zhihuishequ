<template>
  <div class="page-card">
    <div class="page-header">
      <h2>公告活动管理</h2>
      <el-button type="primary" @click="openDialog()">发布</el-button>
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="公告" name="notice" />
      <el-tab-pane label="活动" name="activity" />
    </el-tabs>
    <el-table :data="filtered" stripe>
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="publishAt" label="发布时间" width="160" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editing ? '编辑' : '发布'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="公告">公告</el-radio>
            <el-radio value="活动">活动</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

interface Item {
  id: number
  title: string
  type: string
  publishAt: string
  status: string
  content?: string
}

const tab = ref('notice')
const list = ref<Item[]>([
  { id: 1, title: '停水通知', type: '公告', publishAt: '2026-07-16', status: '已发布' },
  { id: 2, title: '夏季纳凉活动', type: '活动', publishAt: '2026-07-14', status: '已发布' },
  { id: 3, title: '电梯维保公告', type: '公告', publishAt: '2026-07-12', status: '已发布' },
])

const filtered = computed(() =>
  list.value.filter((i) => (tab.value === 'notice' ? i.type === '公告' : i.type === '活动')),
)

const visible = ref(false)
const editing = ref<Item | null>(null)
const form = reactive({ title: '', type: '公告', content: '' })

function openDialog(row?: Item) {
  editing.value = row || null
  form.title = row?.title || ''
  form.type = row?.type || (tab.value === 'notice' ? '公告' : '活动')
  form.content = row?.content || ''
  visible.value = true
}

function save() {
  if (editing.value) {
    Object.assign(editing.value, { title: form.title, type: form.type, content: form.content })
  } else {
    list.value.unshift({
      id: Date.now(),
      title: form.title,
      type: form.type,
      content: form.content,
      publishAt: dayjs().format('YYYY-MM-DD'),
      status: '已发布',
    })
  }
  visible.value = false
  ElMessage.success('已保存')
}

function remove(id: number) {
  list.value = list.value.filter((i) => i.id !== id)
  ElMessage.success('已下架')
}
</script>
