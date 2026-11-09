import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', () => {
  const toasts = ref([])
  const confirmState = ref(null)
  let confirmResolve = null

  function toast(message, type = 'info', duration = 2800) {
    const id = `${Date.now()}-${Math.random()}`
    toasts.value.push({ id, message, type })
    window.setTimeout(() => dismiss(id), duration)
  }

  function dismiss(id) {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }

  function confirm(options = {}) {
    if (confirmState.value) {
      return Promise.resolve(false)
    }
    confirmState.value = {
      title: options.title || '确认操作',
      message: options.message || '确定继续吗？',
      confirmText: options.confirmText || '确认',
      cancelText: options.cancelText || '取消',
      danger: options.danger || false
    }
    return new Promise((resolve) => {
      confirmResolve = resolve
    })
  }

  function settle(value) {
    confirmState.value = null
    confirmResolve?.(value)
    confirmResolve = null
  }

  return { toasts, confirmState, toast, dismiss, confirm, settle }
})
