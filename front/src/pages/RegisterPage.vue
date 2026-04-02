<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, UserFilled } from '@element-plus/icons-vue'
import { registerMember } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const passwordHint = computed(() => {
  if (!form.password) {
    return '密码长度需在 6 到 64 位之间'
  }
  if (form.password.length < 6) {
    return '当前密码长度不足 6 位'
  }
  return '密码长度符合要求'
})

async function submit() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请先填写用户名和密码')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await registerMember({
      username: form.username.trim(),
      password: form.password,
    })
    ElMessage.success('注册成功，已自动登录')
    router.push((route.query.redirect as string) || '/orders/me')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="shell-body">
    <section class="auth-grid">
      <article class="glass-card auth-intro-card">
        <span class="auth-card-title">注册</span>
        <h1>创建会员账号</h1>
        <p>注册完成后会自动登录，后续每次下单都可以自动绑定到当前账号。</p>
        <ul class="auth-feature-list">
          <li>
            <el-icon><UserFilled /></el-icon>
            用户名支持字母、数字和下划线
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            密码由后端统一哈希保存
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            注册后可直接查看我的订单
          </li>
        </ul>
      </article>

      <article class="auth-form-card">
        <div class="auth-form-card__head auth-form-card__head--center">
          <span class="auth-card-title">创建账号</span>
          <h2>开始使用</h2>
          <p>只需要一个用户名和密码，就可以获得完整的下单与查单能力。</p>
        </div>

        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" maxlength="32" placeholder="例如 kawang_user" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入密码"
            />
          </el-form-item>
        </el-form>

        <el-alert :closable="false" type="info" show-icon :title="passwordHint" />

        <button class="primary-action auth-submit" type="button" :disabled="loading" @click="submit">
          {{ loading ? '注册中...' : '注册并登录' }}
        </button>

        <div class="auth-foot-links">
          <span>已经有账号？</span>
          <router-link to="/login">去登录</router-link>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.auth-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 420px);
  gap: 22px;
}

.auth-intro-card {
  text-align: center;
}

.auth-card-title {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 32px;
  margin: 0 auto 14px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(18px);
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-decoration: none;
}

.auth-intro-card h1 {
  margin: 0 0 12px;
  font-size: clamp(30px, 4vw, 42px);
}

.auth-intro-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.auth-feature-list {
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
  text-align: left;
}

.auth-feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.auth-form-card {
  padding: 26px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.34);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(28px);
}

.auth-form-card__head--center {
  text-align: center;
}

.auth-form-card__head h2 {
  margin: 0 0 8px;
  font-size: 30px;
}

.auth-form-card__head p {
  margin: 0 0 22px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.auth-submit {
  width: 100%;
  margin-top: 18px;
}

.auth-foot-links {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 18px;
  color: var(--text-secondary);
}

.auth-foot-links a {
  color: var(--accent);
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 920px) {
  .auth-grid {
    grid-template-columns: 1fr;
  }
}
</style>