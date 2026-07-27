import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownView from '../MarkdownView.vue'

describe('MarkdownView', () => {
  it('把 markdown 渲染为 HTML', () => {
    const wrapper = mount(MarkdownView, { props: { content: '# 标题\n\n正文段落' } })
    const html = wrapper.html()
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<p>正文段落</p>')
  })

  it('净化潜在 XSS（不含 script / onerror）', () => {
    const wrapper = mount(MarkdownView, {
      props: { content: '<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>' },
    })
    const html = wrapper.html()
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('onerror')
  })

  it('空内容安全渲染', () => {
    const wrapper = mount(MarkdownView, { props: { content: '' } })
    expect(wrapper.exists()).toBe(true)
  })
})
