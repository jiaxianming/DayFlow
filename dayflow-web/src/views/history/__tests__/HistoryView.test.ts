import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import HistoryView from '../HistoryView.vue'
import * as reportApi from '@/api/report'
import type { IReportVO } from '@/types/report'

describe('HistoryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('加载并渲染报告列表 + 状态文案', async () => {
    const report: IReportVO = {
      id: '1',
      userId: 'u',
      type: 'DAILY',
      periodStart: '2026-07-10',
      periodEnd: '2026-07-10',
      title: '7月10日日报',
      content: '报告正文',
      status: 'GENERATED',
      errorMsg: null,
      tokenUsage: 500,
      createdAt: '2026-07-10T10:00:00',
    }
    vi.spyOn(reportApi, 'pageReports').mockResolvedValue({
      records: [report],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    })

    // HistoryView 用 useRouter()，注入 memory router 避免 "Symbol(router)" 警告
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:rest(.*)*', component: { template: '<div/>' } }],
    })

    const wrapper = mount(HistoryView, { global: { plugins: [router, ElementPlus] } })
    await nextTick()
    await nextTick()

    expect(reportApi.pageReports).toHaveBeenCalled()
    expect(wrapper.text()).toContain('7月10日日报')
    expect(wrapper.text()).toContain('已完成')
  })
})
