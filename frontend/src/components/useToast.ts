import { reactive } from 'vue'

export interface Toast {
  id: number
  text: string
  tone: 'ok' | 'bad' | 'info'
}

let seq = 0
export const toasts = reactive<Toast[]>([])

/**
 * 轻提示只用于"操作已完成"这类瞬时反馈。
 * 表单校验与可恢复的错误一律就地显示，不丢给 toast。
 */
export function toast(text: string, tone: Toast['tone'] = 'info', ms = 3600) {
  const id = ++seq
  toasts.push({ id, text, tone })
  setTimeout(() => {
    const i = toasts.findIndex((t) => t.id === id)
    if (i >= 0) toasts.splice(i, 1)
  }, ms)
}
