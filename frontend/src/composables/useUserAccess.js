import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUserAccessPromptStore } from '../stores/userAccessPrompt'

/**
 * 用户端身份与登录门禁。
 *
 * 商家、管理员浏览用户端时按游客处理，避免其误用普通用户专属功能。
 */
export function useUserAccess() {
  const auth = useAuthStore()
  const accessPrompt = useUserAccessPromptStore()
  const route = useRoute()
  const router = useRouter()

  const isUser = computed(() => auth.role === 'user')
  const isVisitor = computed(() => !isUser.value)
  const isNonUserAccount = computed(() => auth.isAuthenticated && !isUser.value)

  function buildLoginTarget(options = {}) {
    const redirect = options.redirect || route.fullPath
    return {
      name: 'login',
      query: {
        mode: 'login',
        role: 'user',
        redirect
      }
    }
  }

  function redirectToLogin(options = {}) {
    return router.push(buildLoginTarget(options))
  }

  async function requireUser(options = {}) {
    if (isUser.value) return true
    const redirect = options.redirect || route.fullPath
    const action = String(options.action || '此功能')
    if (isNonUserAccount.value) {
      accessPrompt.openRole({ action, redirect, role: auth.role })
      return false
    }
    accessPrompt.openGuest({ action, redirect })
    return false
  }

  return {
    isUser,
    isVisitor,
    isNonUserAccount,
    redirectToLogin,
    requireUser
  }
}
