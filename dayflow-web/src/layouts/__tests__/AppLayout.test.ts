import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory, type RouteRecordRaw } from 'vue-router'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it } from 'vitest'
import AppLayout from '../AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

describe('AppLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('显示当前用户昵称 + 导航项，登出清登录态', async () => {
    const store = useAuthStore()
    store.nickname = '小A'
    store.token = 'x'

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/input', component: { template: '<div/>' } },
        { path: '/login', component: { template: '<div/>' } },
      ] as RouteRecordRaw[],
    })
    await router.push('/input')
    await router.isReady()

    const wrapper = mount(AppLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: ['router-view', 'Edit', 'Document'],
      },
    })

    expect(wrapper.text()).toContain('小A')
    expect(wrapper.text()).toContain('数据录入')
    expect(wrapper.text()).toContain('报告中心')

    await wrapper.find('[data-test="logout"]').trigger('click')
    expect(store.token).toBe('')
    expect(store.isAuthed).toBe(false)
  })
})
