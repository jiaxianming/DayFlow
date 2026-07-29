import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ReportView from '../ReportView.vue'
import * as reportApi from '@/api/report'
import { todayString } from '@/utils/format'
import type { IReportVO } from '@/types/report'

describe('ReportView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  /** 一份已完成的 report VO，用于 mock 生成成功后的轮询，避免真实调 API */
  const generatedReport: IReportVO = {
    id: 'r-1',
    userId: 'u',
    type: 'DAILY',
    periodStart: '2026-07-28',
    periodEnd: '2026-07-28',
    title: '日报',
    content: '# 日报',
    status: 'GENERATED',
    errorMsg: null,
    tokenUsage: 100,
    createdAt: '2026-07-28T10:00:00',
  }

  /**
   * mount 到无 :id 路由（reportId 为 undefined，不触发轮询），
   * 并预 mock 生成 / 轮询 API。生成成功后路由会跳 /reports/r-1，
   * 此时轮询会启动，getReport 返回 GENERATED 使其单次后 stop。
   */
  async function mountView() {
    vi.spyOn(reportApi, 'generateReport').mockResolvedValue('r-1')
    vi.spyOn(reportApi, 'getReport').mockResolvedValue(generatedReport)
    vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:rest(.*)*', component: { template: '<div/>' } }],
    })
    await router.push('/reports')
    await router.isReady()
    const wrapper = mount(ReportView, { global: { plugins: [router, ElementPlus] } })
    await nextTick()
    return wrapper
  }

  it('默认「今日」：点生成用当天日期触发', async () => {
    const wrapper = await mountView()
    const genBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    await genBtn.trigger('click')
    await nextTick()
    expect(reportApi.generateReport).toHaveBeenCalledWith({ type: 'DAILY', date: todayString() })
  })

  it('默认今日模式不显示日期选择器；切「指定日期」后出现', async () => {
    const wrapper = await mountView()
    // 初始今日模式：无 date-picker
    expect(wrapper.find('.el-date-editor').exists()).toBe(false)
    // 切换 radio-group 到「指定日期」（jsdom 下直接 emit update:modelValue 最稳）
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'custom')
    await nextTick()
    // 出现 date-picker
    expect(wrapper.find('.el-date-editor').exists()).toBe(true)
  })

  it('选「本周」生成周报：用当天日期 + WEEKLY 类型', async () => {
    const wrapper = await mountView()
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'week')
    await nextTick()
    const genBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    await genBtn.trigger('click')
    await nextTick()
    expect(reportApi.generateReport).toHaveBeenCalledWith({ type: 'WEEKLY', date: todayString() })
  })
})
