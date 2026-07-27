import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import AgentTimeline from '../AgentTimeline.vue'
import type { IAgentTraceVO } from '@/types/report'

/** 构造一条轨迹，覆盖默认值 */
function makeTrace(over: Partial<IAgentTraceVO>): IAgentTraceVO {
  return {
    id: '1',
    reportId: 'r',
    agentName: 'PLANNER',
    step: 1,
    inputSummary: '输入摘要',
    outputSummary: '输出摘要',
    tokens: 100,
    latencyMs: 50,
    retryCount: 0,
    createdAt: '',
    ...over,
  }
}

describe('AgentTimeline', () => {
  it('按 step 升序渲染', () => {
    const wrapper = mount(AgentTimeline, {
      props: {
        traces: [
          makeTrace({ id: '3', step: 3 }),
          makeTrace({ id: '1', step: 1 }),
          makeTrace({ id: '2', step: 2 }),
        ],
      },
      global: { plugins: [ElementPlus] },
    })
    const steps = wrapper.findAll('.trace-step').map((el) => el.text())
    expect(steps).toEqual(['Step 1', 'Step 2', 'Step 3'])
  })

  it('retryCount>0 显示返工徽章', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [makeTrace({ id: '1', step: 1, retryCount: 2 })] },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('返工 #2')
  })

  it('按 agentName 渲染中文角色名', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [makeTrace({ id: '1', agentName: 'WRITER', step: 1 })] },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('撰写员')
  })

  it('空轨迹 + active 显示协作占位', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [], active: true },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('Agent 即将开始协作')
  })
})
