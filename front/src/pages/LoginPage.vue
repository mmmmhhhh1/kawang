<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, UserFilled } from '@element-plus/icons-vue'
import { loginMember } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

async function submit() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await loginMember({
      username: form.username.trim(),
      password: form.password,
    })
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/orders/me')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="shell-body">
    <section class="auth-grid">
      <article class="glass-card auth-intro-card">
        <span class="section-kicker">登录</span>
        <h1>登录会员账号</h1>
        <p>登录后下单会自动绑定到当前账号，并在“我的订单”里统一查看。</p>
        <ul class="auth-feature-list">
          <li>
            <el-icon><UserFilled /></el-icon>
            支持会员订单自动绑定
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            密码按哈希方式保存
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            前台不会显示真实账号明文
          </li>
        </ul>
      </article>

      <article class="auth-form-card">
        <div class="auth-form-card__head">
          <span class="section-chip">账号登录</span>
          <h2>欢迎回来</h2>
          <p>输入用户名和密码，继续查询已绑定订单或完成新的下单流程。</p>
        </div>

        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="form.username" maxlength="32" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
        </el-form>

        <button class="primary-action auth-submit" type="button" :disabled="loading" @click="submit">
          {{ loading ? '登录中...' : '立即登录' }}
        </button>

        <div class="auth-foot-links">
          <span>还没有账号？</span>
          <router-link to="/register">去注册</router-link>
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

.auth-intro-card h1 {
  margin: 10px 0 12px;
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

.auth-form-card__head h2 {
  margin: 12px 0 8px;
  font-size: 30px;
}

.auth-form-card__head p {
  margin: 0 0 22px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
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
