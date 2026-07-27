<script setup lang="ts">
/**
 * 登录页：用户名 + 密码 → authStore.login → 成功跳 redirect 或 /input
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login({ username: form.username, password: form.password })
      const redirect = (route.query.redirect as string) || '/input'
      router.push(redirect)
    } catch {
      // 响应拦截器已 ElMessage 提示错误
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="auth-view">
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">登录 DayFlow</h2>
      </template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="auth-submit" @click="onSubmit">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-link">
        还没账号？<router-link to="/register">去注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--dayflow-bg, #f5f7fa);
}
.auth-card {
  width: 360px;
}
.auth-title {
  margin: 0;
  font-size: 18px;
  text-align: center;
}
.auth-submit {
  width: 100%;
}
.auth-link {
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
}
</style>
