<script setup lang="ts">
/**
 * 应用主布局：左侧边栏导航 + 顶部用户信息/登出 + 主区路由视图
 */
import { useRoute, useRouter } from 'vue-router'
import { Edit, Document } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/** 登出：清登录态并跳登录页 */
function onLogout(): void {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="200px" class="app-aside">
      <div class="app-logo">DayFlow</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/input">
          <el-icon><Edit /></el-icon>
          <span>数据录入</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <span>报告中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="app-user">{{ authStore.nickname || authStore.username || '用户' }}</span>
        <el-button data-test="logout" link type="primary" @click="onLogout">登出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-aside {
  background: #fff;
  border-right: 1px solid #e6e8eb;
}
.app-logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--dayflow-primary, #409eff);
}
.app-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e6e8eb;
}
.app-user {
  font-size: 14px;
  color: #606266;
}
</style>
