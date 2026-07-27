<script setup lang="ts">
/**
 * Markdown 渲染组件
 * markdown-it 转 HTML 后必经 DOMPurify.sanitize 净化（防 LLM 产出恶意 HTML）
 */
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const props = defineProps<{
  /** markdown 原文（后端 report.content） */
  content: string
}>()

/**
 * markdown-it 实例：允许原始 HTML 透传，由 DOMPurify 统一净化（安全闸门）。
 * 选 html: true 而非 false：markdown 语法仍可能产出 javascript: 链接等危险结构，
 * 且 LLM 产出常含原生 HTML —— 必须让 DOMPurify 实际生效（铁律：净化后再 v-html）。
 */
const md = new MarkdownIt({ html: true, linkify: true, breaks: false })

/** 渲染 + 净化后的安全 HTML */
const html = computed<string>(() => {
  const raw = md.render(props.content || '')
  return DOMPurify.sanitize(raw)
})
</script>

<template>
  <div class="markdown-view" v-html="html" />
</template>

<style scoped>
.markdown-view {
  line-height: 1.7;
  font-size: 14px;
}
.markdown-view :deep(h1),
.markdown-view :deep(h2),
.markdown-view :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
}
.markdown-view :deep(p) {
  margin: 8px 0;
}
.markdown-view :deep(ul),
.markdown-view :deep(ol) {
  padding-left: 24px;
}
.markdown-view :deep(code) {
  background: #f5f7fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}
</style>
