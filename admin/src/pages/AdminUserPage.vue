<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminProfileState,
  hasAdminPermission,
  type AdminPermission,
} from '@/api/auth'
import {
  createAdmin,
  deleteAdmin,
  getAdmins,
  updateAdminPermissions,
  type AdminUserItem,
} from '@/api/admins'

const loading = ref(false)
const saving = ref(false)
const permissionSaving = ref(false)
const createVisible = ref(false)
const permissionVisible = ref(false)
const admins = ref<AdminUserItem[]>([])
const editingAdmin = ref<AdminUserItem | null>(null)
const profile = adminProfileState

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

async function loadAdmins() {
  if (!canManageAdmins.value) {
    return
  }

  loading.value = true
  try {
    admins.value = await getAdmins()
  } finally {
    loading.value = false
  }
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
    await loadAdmins()
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
    await loadAdmins()
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
    await loadAdmins()
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
    if (allowed && !admins.value.length) {
      loadAdmins()
    }
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
            <p>创建新的管理员账号，并管理普通管理员的权限。最高权限管理员不可删除，也不可修改权限。</p>
            <h1>管理员管理</h1>
          </div>
          <el-button type="primary" @click="openCreate">新建管理员</el-button>
        </div>

        <el-table :data="admins" v-loading="loading" border>
          <el-table-column prop="username" label="用户名" min-width="160" />
          <el-table-column prop="displayName" label="显示名" min-width="160" />
          <el-table-column label="权限级别" width="130">
            <template #default="{ row }">
              <el-tag :type="row.isSuperAdmin ? 'danger' : 'info'">
                {{ row.isSuperAdmin ? '最高权限' : '普通管理员' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限标签" min-width="260">
            <template #default="{ row }">
              <div class="permission-tags">
                <el-tag
                  v-for="permission in row.permissions"
                  :key="permission"
                  type="primary"
                  effect="plain"
                >
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
</style>
