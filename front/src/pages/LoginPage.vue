<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message, UserFilled } from '@element-plus/icons-vue'
import { loginMember, loginMemberByEmail, sendEmailCode } from '@/api/auth'

type LoginMode = 'password' | 'email'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
const mode = ref<LoginMode>('password')

const passwordForm = reactive({
  username: '',
  password: '',
})

const emailForm = reactive({
  email: '',
  code: '',
})

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const codePattern = /^\d{6}$/
let timerId: number | null = null

function stopCountdown() {
  if (timerId !== null) {
    window.clearInterval(timerId)
    timerId = null
  }
}

function startCountdown() {
  stopCountdown()
  codeCountdown.value = 60
  timerId = window.setInterval(() => {
    if (codeCountdown.value <= 1) {
      codeCountdown.value = 0
      stopCountdown()
      return
    }
    codeCountdown.value -= 1
  }, 1000)
}

function redirectAfterLogin() {
  router.push((route.query.redirect as string) || '/orders/me')
}

function validateEmail(email: string) {
  if (!emailPattern.test(email.trim())) {
    ElMessage.warning('请输入正确的邮箱地址')
    return false
  }
  return true
}

function validatePasswordLogin() {
  if (!passwordForm.username.trim() || !passwordForm.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return false
  }
  return true
}

function validateEmailLogin() {
  if (!validateEmail(emailForm.email)) {
    return false
  }
  if (!codePattern.test(emailForm.code.trim())) {
    ElMessage.warning('请输入 6 位数字验证码')
    return false
  }
  return true
}

async function handleSendCode() {
  if (sendingCode.value || codeCountdown.value > 0) {
    return
  }
  if (!validateEmail(emailForm.email)) {
    return
  }

  sendingCode.value = true
  try {
    await sendEmailCode({
      email: emailForm.email.trim(),
      scene: 'login',
    })
    ElMessage.success('验证码已发送，请查收邮箱')
    startCountdown()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '验证码发送失败')
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  if (mode.value === 'password' && !validatePasswordLogin()) {
    return
  }
  if (mode.value === 'email' && !validateEmailLogin()) {
    return
  }

  loading.value = true
  try {
    if (mode.value === 'password') {
      await loginMember({
        username: passwordForm.username.trim(),
        password: passwordForm.password,
      })
    } else {
      await loginMemberByEmail({
        email: emailForm.email.trim(),
        code: emailForm.code.trim(),
      })
    }
    ElMessage.success('登录成功')
    redirectAfterLogin()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '登录失败')
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(stopCountdown)
</script>

<template>
  <div class="shell-body">
    <section class="auth-grid">
      <article class="glass-card auth-intro-card">
        <span class="auth-card-title">登录</span>
        <h1>登录会员账号</h1>
        <p>登录后下单会自动绑定到当前账号，并统一在“我的订单”里查看历史记录和已发放内容。</p>
        <ul class="auth-feature-list">
          <li>
            <el-icon><UserFilled /></el-icon>
            支持会员订单自动绑定
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            保留原有账号密码登录
          </li>
          <li>
            <el-icon><Message /></el-icon>
            新增邮箱验证码快捷登录
          </li>
        </ul>
      </article>

      <article class="auth-form-card">
        <div class="auth-form-card__head auth-form-card__head--center">
          <span class="auth-card-title">会员登录</span>
          <h2>{{ mode === 'password' ? '账号密码登录' : '邮箱验证码登录' }}</h2>
          <p>
            {{
              mode === 'password'
                ? '输入用户名和密码，继续查询已绑定订单或完成新的下单流程。'
                : '输入邮箱并完成验证码校验，无需密码即可进入会员账号。'
            }}
          </p>
        </div>

        <div class="auth-mode-switch" role="tablist" aria-label="登录方式切换">
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'password' }"
            type="button"
            @click="mode = 'password'"
          >
            账号密码登录
          </button>
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'email' }"
            type="button"
            @click="mode = 'email'"
          >
            邮箱验证码登录
          </button>
        </div>

        <el-form v-if="mode === 'password'" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="passwordForm.username" maxlength="32" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="passwordForm.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
        </el-form>

        <el-form v-else label-position="top">
          <el-form-item label="邮箱">
            <el-input v-model="emailForm.email" maxlength="80" placeholder="请输入邮箱地址" />
          </el-form-item>
          <el-form-item label="邮箱验证码">
            <div class="auth-code-row">
              <el-input class="auth-code-input" v-model="emailForm.code" maxlength="6" placeholder="请输入 6 位验证码" />
              <el-button class="auth-code-button" :disabled="sendingCode || codeCountdown > 0" @click="handleSendCode">
                {{ sendingCode ? '发送中...' : codeCountdown > 0 ? `${codeCountdown}s 后重试` : '发送验证码' }}
              </el-button>
            </div>
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

.auth-mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 18px;
  padding: 6px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.auth-mode-switch__item {
  min-height: 42px;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.auth-mode-switch__item.is-active {
  background: rgba(255, 255, 255, 0.24);
  color: var(--text-primary);
  box-shadow: 0 12px 20px rgba(120, 138, 160, 0.16);
}

.auth-code-row {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.auth-code-input {
  flex: 1;
}

.auth-code-button {
  min-width: 132px;
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

@media (max-width: 640px) {
  .auth-mode-switch {
    grid-template-columns: 1fr;
  }

  .auth-code-row {
    flex-direction: column;
    align-items: stretch;
  }

  .auth-code-button {
    width: 100%;
  }
}
</style>