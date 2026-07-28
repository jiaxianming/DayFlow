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

  it('净化 javascript: URI 链接（原始 HTML 由 DOMPurify 剥离）', () => {
    // 用【原始 HTML】向量而非 markdown 链接语法：markdown-it html:true 会原样透传
    // `<a href="javascript:...">`（实测输出仍含 href="javascript:"），由 DOMPurify 剥离
    // href —— 故此用例真正守门 DOMPurify（若移除/改坏 DOMPurify，透传的危险 href 会留在
    // 输出，测试即失败）。markdown 语法 [x](javascript:..) 会被 markdown-it validateLink 在
    // 第一层拒绝、输出字面文本，无法用于验证 DOMPurify（断言会恒真）。
    const wrapper = mount(MarkdownView, {
      props: { content: '<a href="javascript:alert(1)">click</a>' },
    })
    const html = wrapper.html()
    expect(html).not.toContain('href="javascript:')
  })

  it('空内容安全渲染', () => {
    const wrapper = mount(MarkdownView, { props: { content: '' } })
    expect(wrapper.exists()).toBe(true)
  })
})
