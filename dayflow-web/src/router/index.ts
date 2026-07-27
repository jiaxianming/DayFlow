import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 守卫判定纯函数（独立于 vue-router，可单测）
 * - 未登录访问受保护页 → '/login?redirect=<原路径>'
 * - 已登录访问 /login 或 /register → '/input'
 * - 其余放行（true）
 *
 * @param to 目标路由（path + meta.public）
 * @param isAuthed 当前是否已登录
 * @returns 跳转路径字符串或 true（放行）
 */
export function resolveRoute(
  to: { path: string; meta?: { public?: boolean } },
  isAuthed: boolean,
): string | boolean {
  const isPublic = to.meta?.public === true
  if (!isPublic && !isAuthed) {
    return `/login?redirect=${encodeURIComponent(to.path)}`
  }
  if (isAuthed && (to.path === '/login' || to.path === '/register')) {
    return '/input'
  }
  return true
}

/**
 * 路由表
 * - /login /register：公开页（顶层）
 * - /：AppLayout 布局，children 为受保护业务页
 * - /:pathMatch(.*)*：404（公开）
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/input' },
      { path: 'input', name: 'input', component: () => import('@/views/input/InputView.vue') },
      { path: 'reports', name: 'history', component: () => import('@/views/history/HistoryView.vue') },
      { path: 'reports/:id', name: 'report', component: () => import('@/views/report/ReportView.vue') },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** 全局前置守卫：基于 resolveRoute 判定 */
router.beforeEach((to) => resolveRoute(to, useAuthStore().isAuthed))
