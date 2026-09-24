import { reactive } from 'vue'

/**
 * 应用内对话框。
 *
 * 原来全站用浏览器原生 confirm/prompt 收确认与理由，问题有三个：
 * 长相不属于这套界面；prompt 不能校验（空理由也交得上去）；也无法说明"为什么"。
 * 这里给一个 promise 化的接口，调用处写起来和原来一样短：
 *
 *   if (!(await confirmDialog({ title: '撤回这张申请？' }))) return
 *   const reason = await promptDialog({ title: '驳回理由', required: true })
 *
 * 只保留两种：确认与单行/多行输入。复杂表单仍走页面本身，不塞进对话框。
 */

export interface ConfirmOptions {
  title: string
  body?: string
  confirmText?: string
  cancelText?: string
  /** 危险操作（删除、失效）：确认键用警示色 */
  danger?: boolean
}

export interface PromptOptions {
  title: string
  body?: string
  label?: string
  value?: string
  placeholder?: string
  confirmText?: string
  /** 多行输入，用于"写清理由"这类场景 */
  multiline?: boolean
  /** 必填：留空时不允许提交，并给出提示 */
  required?: boolean
  requiredHint?: string
  /** 危险操作（强行发布、标记失效）：确认键用警示色 */
  danger?: boolean
}

type Resolver = (value: string | boolean | null) => void

export const dialogState = reactive({
  kind: '' as '' | 'confirm' | 'prompt',
  title: '',
  body: '',
  label: '',
  value: '',
  placeholder: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
  multiline: false,
  error: '',
  required: false,
  requiredHint: '这一项必填',
})

let resolve: Resolver | null = null

function open(kind: 'confirm' | 'prompt', options: ConfirmOptions | PromptOptions) {
  return new Promise<string | boolean | null>((res) => {
    resolve = res
    const o = options as PromptOptions
    dialogState.kind = kind
    dialogState.title = options.title
    dialogState.body = options.body ?? ''
    dialogState.label = o.label ?? ''
    dialogState.value = o.value ?? ''
    dialogState.placeholder = o.placeholder ?? ''
    dialogState.confirmText = options.confirmText ?? '确定'
    dialogState.cancelText = (options as ConfirmOptions).cancelText ?? '取消'
    dialogState.danger = (options as ConfirmOptions).danger ?? false
    dialogState.multiline = o.multiline ?? false
    dialogState.required = o.required ?? false
    dialogState.requiredHint = o.requiredHint ?? '这一项必填'
    dialogState.error = ''
  })
}

/** 确认返回 true，取消返回 false。 */
export async function confirmDialog(options: ConfirmOptions): Promise<boolean> {
  return (await open('confirm', options)) === true
}

/** 输入返回文字，取消返回 null。 */
export async function promptDialog(options: PromptOptions): Promise<string | null> {
  const value = await open('prompt', options)
  return typeof value === 'string' ? value : null
}

/** 对话框内部用：确认。 */
export function settleDialog(ok: boolean) {
  if (!resolve) return
  if (ok && dialogState.kind === 'prompt' && dialogState.required && !dialogState.value.trim()) {
    dialogState.error = dialogState.requiredHint
    return
  }
  const value = dialogState.kind === 'prompt' ? dialogState.value : true
  const r = resolve
  resolve = null
  dialogState.kind = ''
  r(ok ? value : null)
}
