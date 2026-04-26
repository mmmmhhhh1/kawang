<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { loginMember, loginMemberByEmail, sendEmailCode } from '@/api/auth'

type LoginMode = 'password' | 'email'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
const mode = ref<LoginMode>('email')

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
    ElMessage.success('验证码已发送')
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
    <section class="auth-scene page-reveal" :style="{ '--delay': '0.04s' }">
      <article class="glass-card auth-scene__story">
        <span class="section-kicker">登录</span>
        <h1>进入会员中心</h1>
      </article>

      <article class="auth-panel">
        <div class="auth-panel__head">
          <span class="soft-chip">
            <el-icon><ChatDotRound /></el-icon>
            会员登录
          </span>
          <h2>{{ mode === 'email' ? '邮箱验证码登录' : '账号密码登录' }}</h2>
        </div>

        <div class="auth-mode-switch" role="tablist" aria-label="登录方式切换">
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'email' }"
            type="button"
            @click="mode = 'email'"
          >
            邮箱验证码
          </button>
          <button
            class="auth-mode-switch__item"
            :class="{ 'is-active': mode === 'password' }"
            type="button"
            @click="mode = 'password'"
          >
            账号密码
          </button>
        </div>

        <el-form v-if="mode === 'email'" label-position="top">
          <el-form-item label="邮箱地址">
            <el-input v-model="emailForm.email" maxlength="80" />
          </el-form-item>
          <el-form-item label="邮箱验证码">
            <div class="auth-code-row">
              <el-input class="auth-code-input" v-model="emailForm.code" maxlength="6" />
              <el-button class="auth-code-button" :disabled="sendingCode || codeCountdown > 0" @click="handleSendCode">
                {{ sendingCode ? '发送中...' : codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <el-form v-else label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="passwordForm.username" maxlength="32" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="passwordForm.password" type="password" show-password />
          </el-form-item>
        </el-form>

        <button class="primary-action auth-panel__submit" type="button" :disabled="loading" @click="submit">
          {{ loading ? '登录中...' : '登录' }}
        </button>

        <div class="auth-panel__foot">
          <span>还没有账号？</span>
          <router-link to="/register">注册</router-link>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.auth-scene {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
  gap: 22px;
}

.auth-scene__story {
  display: grid;
  align-content: center;
  min-height: 100%;
}

.auth-scene__story h1 {
  margin: 12px 0 0;
  font-size: clamp(34px, 5vw, 54px);
  line-height: 1.06;
}

.auth-panel {
  padding: 28px;
  border-radius: 30px;
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.26), transparent 30%),
    linear-gradient(180deg, rgba(255, 251, 253, 0.94), rgba(248, 244, 255, 0.9));
  border: 1px solid rgba(255, 255, 255, 0.86);
  box-shadow: 0 26px 70px rgba(102, 79, 129, 0.16);
}

.auth-panel__head {
  display: grid;
  gap: 12px;
  margin-bottom: 22px;
}

.auth-panel__head h2 {
  margin: 0;
  font-size: 32px;
}

.auth-mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 18px;
  padding: 6px;
  border-radius: 18px;
  background: rgba(247, 238, 247, 0.88);
  border: 1px solid rgba(238, 215, 228, 0.92);
}

.auth-mode-switch__item {
  min-height: 44px;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: transform 0.22s ease, background-color 0.22s ease, box-shadow 0.22s ease;
}

.auth-mode-switch__item.is-active {
  background: rgba(255, 255, 255, 0.9);
  color: var(--text-primary);
  box-shadow: 0 12px 24px rgba(126, 102, 154, 0.14);
  transform: translateY(-1px);
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
  min-width: 136px;
}

.auth-panel__submit {
  width: 100%;
  margin-top: 8px;
}

.auth-panel__foot {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 18px;
  color: var(--text-secondary);
}

.auth-panel__foot a {
  color: var(--accent-pink-strong);
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 960px) {
  .auth-scene {
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
