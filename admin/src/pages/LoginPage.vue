<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'Admin@123456',
})

async function submit() {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push('/products')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <section class="login-hero">
      <p class="login-eyebrow">卡王后台</p>
      <h1>AI 会员商城后台</h1>
      <p class="login-description">
        统一管理商品、卡密、订单、会员与管理员权限。页面只做体验优化，不改变现有业务链路。
      </p>

      <div class="login-highlights">
        <article class="login-highlight">
          <strong>卡密发货</strong>
          <span>商品库存、已售和卡密状态统一管理</span>
        </article>
        <article class="login-highlight">
          <strong>权限控制</strong>
          <span>按管理员权限开放删除、停用与创建操作</span>
        </article>
        <article class="login-highlight">
          <strong>订单闭环</strong>
          <span>支持关闭订单、删除订单和订单详情查看</span>
        </article>
      </div>
    </section>

    <el-card class="login-card" shadow="never">
      <template #header>
        <div>
          <h2>管理员登录</h2>
          <p class="muted">登录后进入后台工作台。</p>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="管理员用户名">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="管理员密码">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
      </el-form>

      <el-button type="primary" class="login-button" :loading="loading" @click="submit">进入后台</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(360px, 430px);
  gap: 28px;
  align-items: center;
  width: min(1200px, calc(100vw - 40px));
  margin: 0 auto;
}

.login-hero {
  position: relative;
  padding: 34px;
  border-radius: 34px;
  background:
    radial-gradient(circle at top right, rgba(125, 211, 252, 0.18), transparent 26%),
    linear-gradient(145deg, rgba(15, 23, 42, 0.96), rgba(14, 116, 144, 0.92));
  color: #fff;
  box-shadow: 0 28px 68px rgba(15, 23, 42, 0.18);
}

.login-eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.28em;
  color: #8bd5ff;
}

.login-hero h1 {
  margin: 16px 0 14px;
  font-size: clamp(38px, 5.2vw, 64px);
  line-height: 1.02;
  letter-spacing: -0.05em;
}

.login-description {
  margin: 0;
  max-width: 560px;
  font-size: 16px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.8);
}

.login-highlights {
  display: grid;
  gap: 14px;
  margin-top: 30px;
}

.login-highlight {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.login-highlight strong,
.login-highlight span {
  display: block;
}

.login-highlight strong {
  font-size: 15px;
}

.login-highlight span {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.6;
}

.login-card {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(20px);
  box-shadow: 0 20px 55px rgba(15, 23, 42, 0.1);
}

.login-card :deep(.el-card__header) {
  padding-bottom: 8px;
}

.login-card h2 {
  margin: 0 0 8px;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.login-button {
  width: 100%;
  height: 46px;
  margin-top: 6px;
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding: 26px 0;
  }
}
</style>