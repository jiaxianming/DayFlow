/**
 * 应用入口
 * 装 Pinia + Router + ElementPlus；注册 401 handler（清 authStore + 跳 /login）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './api/index'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

/**
 * 401 处理：清登录态 + 跳 /login
 * 运行时（HTTP 401）触发，此时 pinia 已 active
 */
setUnauthorizedHandler(() => {
  useAuthStore().logout()
  router.push('/login')
})

app.mount('#app')
