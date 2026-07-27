/**
 * 日期格式化工具
 * 后端 LocalDateTime 经 Jackson 序列化为 ISO 字符串（如 '2026-07-10T12:30:00'）；
 * Date 构造器可直接解析 ISO 字符串，解析失败则原样返回避免崩 UI。
 */

/** 格式化为 'YYYY-MM-DD HH:mm' */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 格式化为 'YYYY-MM-DD' */
export function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 返回今天的 'YYYY-MM-DD'，用于 date-picker 默认值 */
export function todayString(): string {
  return formatDate(new Date().toISOString())
}
