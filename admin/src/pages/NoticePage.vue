<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createNotice,
  getNoticePage,
  updateNotice,
  updateNoticeStatus,
  type NoticePayload,
  type NoticeRecord,
} from '@/api/notices'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const notices = ref<NoticeRecord[]>([])

const filters = reactive({
  keyword: '',
  status: '' as '' | 'PUBLISHED' | 'HIDDEN',
})

const form = reactive<NoticePayload>({
  title: '',
  summary: '',
  content: '',
  status: 'PUBLISHED',
  sortOrder: 10,
})

const pager = useCursorPager()

async function loadNotices(page = 1) {
  loading.value = true
  try {
    const result = await getNoticePage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
    })
    notices.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadNotices(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadNotices(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadNotices(pager.currentPage.value + 1)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  resetAndLoad()
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
    resetAndLoad()
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
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '状态更新失败')
  }
}

onMounted(() => {
  resetAndLoad()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>公告列表按固定每页 10 条展示，切页仍然走游标分页，不会因为页数变深就越来越慢。</p>
          <h1>公告管理</h1>
        </div>
        <el-button type="primary" @click="openCreate">新建公告</el-button>
      </div>

      <div class="toolbar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索公告标题或摘要" @keyup.enter="resetAndLoad" />
        <el-select v-model="filters.status" clearable placeholder="按状态筛选">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已隐藏" value="HIDDEN" />
        </el-select>
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="notices" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="300" show-overflow-tooltip />
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

      <div class="pager-bar">
        <span class="pager-bar__summary">第 {{ pager.currentPage }} 页，每页 {{ PAGE_SIZE }} 条</span>
        <div class="pager-bar__actions">
          <el-button :disabled="!pager.canPrev || loading" @click="goPrevPage">上一页</el-button>
          <el-button type="primary" :disabled="!pager.canNext || loading" @click="goNextPage">下一页</el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" width="min(1080px, 96vw)" :title="editingId ? '编辑公告' : '新建公告'">
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
          <el-input
            v-model="form.summary"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="255"
            show-word-limit
            placeholder="这里适合放前台列表展示的摘要，会自动换行显示"
          />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :autosize="{ minRows: 12, maxRows: 24 }"
            maxlength="5000"
            show-word-limit
            placeholder="支持多行输入，前台详情会保留换行格式显示"
          />
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

.pager-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
}

.pager-bar__summary {
  color: #5f7285;
  font-size: 13px;
}

.pager-bar__actions {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .notice-form-grid {
    grid-template-columns: 1fr;
  }

  .pager-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .pager-bar__actions {
    width: 100%;
  }

  .pager-bar__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>