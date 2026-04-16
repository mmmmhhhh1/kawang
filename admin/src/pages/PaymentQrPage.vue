<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  activatePaymentQr,
  disablePaymentQr,
  getPaymentQrImageBlob,
  getPaymentQrPage,
  uploadPaymentQr,
  type PaymentQrRecord,
} from '@/api/paymentQr'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const uploading = ref(false)
const previewLoading = ref(false)
const previewVisible = ref(false)
const previewImageUrl = ref('')
const previewTitle = ref('')
const fileRef = ref<File | null>(null)
const records = ref<PaymentQrRecord[]>([])
const pager = useCursorPager()

const form = reactive({
  name: '',
})

function revokePreviewImage() {
  if (previewImageUrl.value) {
    window.URL.revokeObjectURL(previewImageUrl.value)
    previewImageUrl.value = ''
  }
}

async function loadRecords(page = 1) {
  loading.value = true
  try {
    const result = await getPaymentQrPage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
    })
    records.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '收款码列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadRecords(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadRecords(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadRecords(pager.currentPage.value + 1)
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  fileRef.value = target.files?.[0] ?? null
}

async function submitUpload() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入收款码名称')
    return
  }
  if (!fileRef.value) {
    ElMessage.warning('请先选择二维码图片')
    return
  }

  uploading.value = true
  try {
    await uploadPaymentQr(form.name.trim(), fileRef.value)
    form.name = ''
    fileRef.value = null
    ElMessage.success('收款码上传成功')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '收款码上传失败')
  } finally {
    uploading.value = false
  }
}

async function openPreview(row: PaymentQrRecord) {
  previewVisible.value = true
  previewTitle.value = row.name
  previewLoading.value = true
  revokePreviewImage()
  try {
    const blob = await getPaymentQrImageBlob(row.imageUrl)
    previewImageUrl.value = window.URL.createObjectURL(blob)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '收款码图片加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function handleActivate(row: PaymentQrRecord) {
  try {
    await activatePaymentQr(row.id)
    ElMessage.success('收款码已切换为当前生效')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '激活收款码失败')
  }
}

async function handleDisable(row: PaymentQrRecord) {
  try {
    await disablePaymentQr(row.id)
    ElMessage.success('收款码已停用')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '停用收款码失败')
  }
}

onMounted(() => {
  resetAndLoad()
})

onBeforeUnmount(() => {
  revokePreviewImage()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>收款码管理现在固定每页 10 条，翻页继续走游标分页，避免记录增多后一次性全量拉取。只有超级管理员可以维护前台生效的付款二维码。</p>
          <h1>收款码管理</h1>
        </div>
      </div>

      <div class="qr-upload-panel">
        <el-form label-position="top">
          <el-form-item label="收款码名称">
            <el-input v-model="form.name" placeholder="例如：2026 春季主收款码" />
          </el-form-item>
          <el-form-item label="二维码图片">
            <input class="qr-file-input" type="file" accept="image/*" @change="handleFileChange" />
            <small v-if="fileRef">已选择：{{ fileRef.name }}</small>
          </el-form-item>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传收款码</el-button>
        </el-form>
      </div>

      <el-table :data="records" v-loading="loading" border>
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '生效中' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="二维码" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">查看图片</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="上传人" min-width="140" />
        <el-table-column prop="activatedByName" label="最后激活人" min-width="140">
          <template #default="{ row }">{{ row.activatedByName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="activatedAt" label="激活时间" min-width="180">
          <template #default="{ row }">{{ row.activatedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'ACTIVE'" link type="primary" @click="handleActivate(row)">设为生效</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="handleDisable(row)">停用</el-button>
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

    <el-dialog
      v-model="previewVisible"
      width="min(720px, 92vw)"
      :title="previewTitle ? `收款码预览：${previewTitle}` : '收款码预览'"
      @closed="revokePreviewImage"
    >
      <div class="qr-preview-panel" v-loading="previewLoading">
        <img v-if="previewImageUrl" :src="previewImageUrl" alt="收款码图片" />
        <el-empty v-else description="暂无可预览的收款码图片" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.qr-upload-panel {
  margin-bottom: 20px;
  padding: 20px;
  border-radius: 20px;
  background: #f6fbff;
  border: 1px solid #dfeaf3;
}

.qr-file-input {
  width: 100%;
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

.qr-preview-panel {
  min-height: 320px;
  display: grid;
  place-items: center;
  padding: 20px;
  border-radius: 20px;
  background: #f6fbff;
  border: 1px solid #dfeaf3;
}

.qr-preview-panel img {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  border-radius: 18px;
}

@media (max-width: 768px) {
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