<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createNotice, getNotices, updateNotice, updateNoticeStatus, type NoticePayload, type NoticeRecord } from '@/api/notices'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const notices = ref<NoticeRecord[]>([])

const form = reactive<NoticePayload>({
  title: '',
  summary: '',
  content: '',
  status: 'PUBLISHED',
  sortOrder: 10,
})

async function loadNotices() {
  loading.value = true
  try {
    notices.value = await getNotices()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.summary = ''
  form.content = ''
  form.status = 'PUBLISHED'
  form.sortOrder = 10
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: NoticeRecord) {
  editingId.value = row.id
  form.title = row.title
  form.summary = row.summary
  form.content = row.content
  form.status = row.status
  form.sortOrder = row.sortOrder
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateNotice(editingId.value, form)
      ElMessage.success('公告更新成功')
    } else {
      await createNotice(form)
      ElMessage.success('公告创建成功')
    }
    dialogVisible.value = false
    await loadNotices()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '公告保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: NoticeRecord) {
  const nextStatus = row.status === 'PUBLISHED' ? 'HIDDEN' : 'PUBLISHED'
  try {
    await updateNoticeStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'PUBLISHED' ? '公告已发布' : '公告已隐藏')
    await loadNotices()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '状态更新失败')
  }
}

onMounted(loadNotices)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>前台首页公告区的数据源，支持独立发布、隐藏和编辑内容。</p>
          <h1>公告管理</h1>
        </div>
        <el-button type="primary" @click="openCreate">新建公告</el-button>
      </div>

      <el-table :data="notices" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '已发布' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" min-width="180" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'PUBLISHED' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'PUBLISHED' ? '隐藏' : '发布' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" width="min(760px, 94vw)" :title="editingId ? '编辑公告' : '新建公告'">
      <el-form label-position="top">
        <div class="notice-form-grid">
          <el-form-item label="公告标题">
            <el-input v-model="form.title" maxlength="120" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          </el-form-item>
        </div>
        <el-form-item label="公告摘要">
          <el-input v-model="form.summary" maxlength="255" />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="PUBLISHED">发布</el-radio-button>
            <el-radio-button value="HIDDEN">隐藏</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notice-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 0 16px;
}

@media (max-width: 768px) {
  .notice-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
