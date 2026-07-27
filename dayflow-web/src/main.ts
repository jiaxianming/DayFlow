/**
 * 应用入口
 * Task 1 仅装 ElementPlus 挂载根组件；
 * Task 4 接入 Pinia + Router 后会在此补 use(pinia)/use(router)。
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'

const app = createApp(App)
app.use(ElementPlus)
app.mount('#app')
