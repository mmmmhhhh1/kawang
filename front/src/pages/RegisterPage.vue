<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Message, UserFilled } from '@element-plus/icons-vue'
import { registerMember, registerMemberByEmail, sendEmailCode } from '@/api/auth'

type RegisterMode = 'password' | 'email'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
const mode = ref<RegisterMode>('password')

const passwordForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const emailForm = reactive({
  email: '',
  code: '',
  username: '',
  password: '',
  confirmPassword: '',
})

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const codePattern = /^\d{6}$/
const usernamePattern = /^[A-Za-z0-9_]{4,32}$/
let timerId: number | null = null

const passwordHint = computed(() => {
  const currentPassword = mode.value === 'password' ? passwordForm.password : emailForm.password
  if (!currentPassword) {
    return '密码长度需在 6 到 64 位之间'
  }
  if (currentPassword.length < 6) {
    return '当前密码长度不足 6 位'
  }
  return '密码长度符合要求'
})

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

function redirectAfterRegister() {
  router.push((route.query.redirect as string) || '/orders/me')
}

function validateUsername(username: string) {
  if (!usernamePattern.test(username.trim())) {
    ElMessage.warning('用户名需为 4-32 位字母、数字或下划线')
    return false
  }
  return true
}

function validatePassword(password: string, confirmPassword: string) {
  if (!password.trim()) {
    ElMessage.warning('请输入密码')
    return false
  }
  if (password.length < 6 || password.length > 64) {
    ElMessage.warning('密码长度需在 6 到 64 位之间')
    return false
  }
  if (password !== confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  return true
}

function validateEmail(email: string) {
  if (!emailPattern.test(email.trim())) {
    ElMessage.warning('请输入正确的邮箱地址')
    return false
  }
  return true
}

function validatePasswordRegister() {
  if (!validateUsername(passwordForm.username)) {
    return false
  }
  return validatePassword(passwordForm.password, passwordForm.confirmPassword)
}

function validateEmailRegister() {
  if (!validateEmail(emailForm.email)) {
    return false
  }
  if (!codePattern.test(emailForm.code.trim())) {
    ElMessage.warning('请输入 6 位数字验证码')
    return false
  }
  if (!validateUsername(emailForm.username)) {
    return false
  }
  return validatePassword(emailForm.password, emailForm.confirmPassword)
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
      scene: 'register',
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
  if (mode.value === 'password' && !validatePasswordRegister()) {
    return
  }
  if (mode.value === 'email' && !validateEmailRegister()) {
    return
  }

  loading.value = true
  try {
    if (mode.value === 'password') {
      await registerMember({
        username: passwordForm.username.trim(),
        password: passwordForm.password,
      })
    } else {
      await registerMemberByEmail({
        email: emailForm.email.trim(),
        code: emailForm.code.trim(),
        username: emailForm.username.trim(),
        password: emailForm.password,
      })
    }
    ElMessage.success('注册成功，已自动登录')
    redirectAfterRegister()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '注册失败')
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
        <span class="auth-card-title">注册</span>
        <h1>创建会员账号</h1>
        <p>注册完成后会自动登录，后续每次下单都可以自动绑定到当前账号。</p>
        <ul class="auth-feature-list">
          <li>
            <el-icon><UserFilled /></el-icon>
            保留原有用户名密码注册
          </li>
          <li>
            <el-icon><Message /></el-icon>
            支持邮箱验证码注册
          </li>
          <li>
            <el-icon><Lock /></el-icon>
            注册成功后可直接查看我的订单
          </li>
        </ul>
      </article>

      <article class="auth-form-card">
        <div class="auth-form-card__head auth-form-card__head--center">
          <span class="auth-card-title">创建账号</span>
          <h2>{{ mode === 'password' ? '普通注册' : '邮箱验证注册' }}</h2>
          <p>
            {{
              mode === 'password'
                ? '使用用户名和密码快速创建会员账号。'
                : '先完成邮箱验证码校验，再创建用户名和密码。'
            }}
          </p>
        </div>

        <div class="auth-mode-switch" role="tablist" aria-label="注册方式切换">
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'password' }"
            type="button"
            @click="mode = 'password'"
          >
            普通注册
          </button>
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'email' }"
            type="button"
            @click="mode = 'email'"
          >
            邮箱验证注册
          </button>
        </div>

        <el-form v-if="mode === 'password'" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="passwordForm.username" maxlength="32" placeholder="例如 kawang_user" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="passwordForm.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入密码"
            />
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
          <el-form-item label="用户名">
            <el-input v-model="emailForm.username" maxlength="32" placeholder="例如 kawang_user" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="emailForm.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="emailForm.confirmPassword"
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