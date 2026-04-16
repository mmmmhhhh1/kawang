<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProfileState, hasAdminPermission, type AdminPermission } from '@/api/auth'
import { createAdmin, deleteAdmin, getAdminPage, updateAdminPermissions, type AdminUserItem } from '@/api/admins'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const saving = ref(false)
const permissionSaving = ref(false)
const createVisible = ref(false)
const permissionVisible = ref(false)
const admins = ref<AdminUserItem[]>([])
const editingAdmin = ref<AdminUserItem | null>(null)
const profile = adminProfileState
const pager = useCursorPager()

const filters = reactive({
  keyword: '',
})

const canManageAdmins = computed(() => hasAdminPermission('CREATE_ADMIN', profile.value))

const permissionOptions: Array<{ value: AdminPermission; label: string }> = [
  { value: 'DISABLE_USER', label: '停用用户' },
  { value: 'DELETE_PRODUCT', label: '删除商品' },
  { value: 'DELETE_ORDER', label: '删除订单' },
  { value: 'CREATE_ADMIN', label: '创建管理员' },
]

const createForm = reactive({
  username: '',
  displayName: '',
  password: '',
  permissions: [] as AdminPermission[],
})

const permissionForm = ref<AdminPermission[]>([])

function permissionLabel(permission: AdminPermission) {
  return permissionOptions.find((item) => item.value === permission)?.label ?? permission
}

function resetCreateForm() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.password = ''
  createForm.permissions = []
}

async function loadAdmins(page = 1) {
  if (!canManageAdmins.value) {
    admins.value = []
    pager.reset()
    return
  }

  loading.value = true
  try {
    const result = await getAdminPage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      keyword: filters.keyword || undefined,
    })
    admins.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadAdmins(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadAdmins(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadAdmins(pager.currentPage.value + 1)
}

function resetFilters() {
  filters.keyword = ''
  resetAndLoad()
}

function openCreate() {
  resetCreateForm()
  createVisible.value = true
}

async function submitCreate() {
  saving.value = true
  try {
    await createAdmin({
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      password: createForm.password,
      permissions: createForm.permissions,
    })
    ElMessage.success('管理员创建成功')
    createVisible.value = false
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '管理员创建失败')
  } finally {
    saving.value = false
  }
}

function openPermissions(row: AdminUserItem) {
  editingAdmin.value = row
  permissionForm.value = [...(row.permissions ?? [])]
  permissionVisible.value = true
}

async function submitPermissions() {
  if (!editingAdmin.value) {
    return
  }

  permissionSaving.value = true
  try {
    await updateAdminPermissions(editingAdmin.value.id, permissionForm.value)
    ElMessage.success('权限更新成功')
    permissionVisible.value = false
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '权限更新失败')
  } finally {
    permissionSaving.value = false
  }
}

async function removeAdmin(row: AdminUserItem) {
  if (row.isSuperAdmin) {
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除管理员“${row.displayName || row.username}”吗？`, '删除管理员', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteAdmin(row.id)
    ElMessage.success('管理员已删除')
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '删除管理员失败')
  }
}

watch(
  canManageAdmins,
  (allowed) => {
    if (allowed) {
      resetAndLoad()
      return
    }
    admins.value = []
    pager.reset()
  },
  { immediate: true },
)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <template v-if="canManageAdmins">
        <div class="page-header">
          <div>
            <p>管理员列表现在按固定每页 10 条展示，翻页底层仍然是游标分页，深分页不会走高成本 offset。超级管理员不可删除，也不可修改权限。</p>
            <h1>管理员管理</h1>
          </div>
          <el-button type="primary" @click="openCreate">新建管理员</el-button>
        </div>

        <div class="toolbar">
          <el-input v-model="filters.keyword" clearable placeholder="搜索用户名或显示名" @keyup.enter="resetAndLoad" />
          <el-button type="primary" @click="resetAndLoad">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <el-table :data="admins" v-loading="loading" border>
          <el-table-column prop="username" label="用户名" min-width="160" />
          <el-table-column prop="displayName" label="显示名" min-width="160" />
          <el-table-column label="权限级别" width="130">
            <template #default="{ row }">
              <el-tag :type="row.isSuperAdmin ? 'danger' : 'info'">
                {{ row.isSuperAdmin ? '超级管理员' : '普通管理员' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限标签" min-width="260">
            <template #default="{ row }">
              <div class="permission-tags">
                <el-tag v-for="permission in row.permissions" :key="permission" type="primary" effect="plain">
                  {{ permissionLabel(permission) }}
                </el-tag>
                <span v-if="!row.permissions?.length" class="muted">无额外权限</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!row.isSuperAdmin" link type="primary" @click="openPermissions(row)">
                编辑权限
              </el-button>
              <el-button v-if="!row.isSuperAdmin" link type="danger" @click="removeAdmin(row)">
                删除
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
      </template>

      <el-result
        v-else
        icon="warning"
        title="暂无权限"
        sub-title="当前管理员没有“创建管理员”权限，不能访问管理员管理。"
      />
    </el-card>

    <el-dialog v-model="createVisible" width="min(680px, 92vw)" title="新建管理员">
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" placeholder="管理员登录用户名" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="createForm.displayName" placeholder="后台展示名称" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="权限配置">
          <el-checkbox-group v-model="createForm.permissions">
            <el-checkbox v-for="item in permissionOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" width="min(640px, 92vw)" title="编辑管理员权限">
      <template v-if="editingAdmin">
        <p class="muted">当前管理员：{{ editingAdmin.displayName || editingAdmin.username }}</p>
        <el-checkbox-group v-model="permissionForm">
          <el-checkbox v-for="item in permissionOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-checkbox>
        </el-checkbox-group>
      </template>
      <template #footer>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" :loading="permissionSaving" @click="submitPermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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