import { ref } from 'vue'
import { defineStore } from 'pinia'

function safeRedirect(value) {
  return typeof value === 'string'
    && value.startsWith('/')
    && !value.startsWith('//')
    ? value
    : '/'
}

export const useUserAccessPromptStore = defineStore('userAccessPrompt', () => {
  const prompt = ref(null)

  function openGuest(options = {}) {
    prompt.value = {
      kind: 'guest',
      action: String(options.action || '此功能'),
      redirect: safeRedirect(options.redirect)
    }
  }

  function openRole(options = {}) {
    prompt.value = {
      kind: 'role',
      action: String(options.action || '此功能'),
      role: String(options.role || ''),
      redirect: safeRedirect(options.redirect)
    }
  }

  function close() {
    prompt.value = null
  }

  return { prompt, openGuest, openRole, close }
})
