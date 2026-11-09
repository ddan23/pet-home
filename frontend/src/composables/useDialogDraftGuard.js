import { useUiStore } from '../stores/ui'

function serialize(value) {
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

/**
 * 为表单弹窗提供未保存内容保护和提交中关闭保护。
 *
 * @param {object} options 弹窗保护配置
 * @param {Function} options.getSnapshot 获取当前表单快照
 * @param {Function} options.getBusy 获取是否正在提交
 * @param {Function} options.close 关闭弹窗
 * @param {string} options.message 放弃修改提示
 * @returns {{markClean: Function, requestClose: Function}} 弹窗保护方法
 */
export function useDialogDraftGuard(options) {
  const ui = useUiStore()
  let baseline = serialize(options.getSnapshot())

  function markClean() {
    baseline = serialize(options.getSnapshot())
  }

  async function requestClose() {
    if (options.getBusy?.() === true) return
    const isDirty = serialize(options.getSnapshot()) !== baseline
    if (isDirty) {
      const confirmed = await ui.confirm({
        title: '放弃未保存的修改？',
        message: options.message || '当前填写的内容尚未保存，关闭后将丢失。',
        confirmText: '放弃修改',
        danger: true
      })
      if (!confirmed) return
    }
    await options.close()
  }

  return { markClean, requestClose }
}
