import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import HistoryView from '../HistoryView.vue'
import * as reportApi from '@/api/report'
import { todayString } from '@/utils/format'
import type { IReportVO } from '@/types/report'

describe('HistoryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    // 清理 teleport 到 body 的 dialog 残留 + 还原 spy，避免跨用例污染
    vi.restoreAllMocks()
    document.body.innerHTML = ''
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

  it('点「生成报告」打开对话框（先选再生成，不再直接生成今日）', async () => {
    vi.spyOn(reportApi, 'pageReports').mockResolvedValue({
      records: [],
      total: 0,
      size: 10,
      current: 1,
      pages: 0,
    })
    // spy 生成接口，用于断言「打开对话框不应立即触发生成」
    const genSpy = vi.spyOn(reportApi, 'generateReport').mockResolvedValue('new-1')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:rest(.*)*', component: { template: '<div/>' } }],
    })
    const wrapper = mount(HistoryView, { global: { plugins: [router, ElementPlus] } })
    await nextTick()
    await nextTick()

    // 顶部按钮文案「生成报告」（改造后）
    const openBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    expect(openBtn).toBeTruthy()
    // 初始对话框未打开
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(false)
    // 点击打开对话框（先选再生成，而非一进来就触发）
    await openBtn.trigger('click')
    await nextTick()
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
    // 未点对话框内「生成」前，生成接口不应被调用
    expect(genSpy).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('对话框选「本周」确认 → 以 WEEKLY + 当天触发生成', async () => {
    vi.spyOn(reportApi, 'pageReports').mockResolvedValue({
      records: [], total: 0, size: 10, current: 1, pages: 0,
    })
    const genSpy = vi.spyOn(reportApi, 'generateReport').mockResolvedValue('w-1')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:rest(.*)*', component: { template: '<div/>' } }],
    })
    const wrapper = mount(HistoryView, { global: { plugins: [router, ElementPlus] } })
    await nextTick()
    await nextTick()

    // 打开对话框
    const openBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    await openBtn.trigger('click')
    await nextTick()
    // 选「本周」
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'week')
    await nextTick()
    // 点对话框内「生成」
    const confirmBtn = wrapper.findAll('button').find((b) => b.text().trim() === '生成')!
    await confirmBtn.trigger('click')
    await nextTick()

    expect(genSpy).toHaveBeenCalledWith({ type: 'WEEKLY', date: todayString() })
    wrapper.unmount()
  })
})
