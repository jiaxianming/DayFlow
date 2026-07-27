import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InputView from '../InputView.vue'
import * as activityApi from '@/api/activity'
import * as noteApi from '@/api/note'
import * as taskApi from '@/api/task'

const EMPTY_PAGE = { records: [], total: 0, size: 10, current: 1, pages: 0 }

describe('InputView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.spyOn(activityApi, 'listActivities').mockResolvedValue(EMPTY_PAGE)
    vi.spyOn(noteApi, 'listNotes').mockResolvedValue(EMPTY_PAGE)
    vi.spyOn(taskApi, 'listTasks').mockResolvedValue(EMPTY_PAGE)
  })

  it('渲染三个 tab（活动 / 笔记 / 任务）', () => {
    const wrapper = mount(InputView, { global: { plugins: [ElementPlus] } })
    const text = wrapper.text()
    expect(text).toContain('活动')
    expect(text).toContain('笔记')
    expect(text).toContain('任务')
  })
})
