import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'

// 会话标记：游客（从未登录）不发 /auth/me 探测，避免每个页面加载都产生一条 401 网络日志。
// 标记可能在 token 过期后残留，此时探测返回 401，api 层会清标记，下一次加载恢复为游客路径。
const SESSION_FLAG = 'pet_home_has_session'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const checked = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(user.value))
  const role = computed(() => user.value?.role || '')
  const isUser = computed(() => role.value === 'user')
  const isNonUserAccount = computed(() => isAuthenticated.value && !isUser.value)

  function persist(nextUser) {
    user.value = nextUser || null
  }

  async function login(credentials) {
    loading.value = true
    try {
      const result = await api.post('/auth/login', credentials, {
        suppressUnauthorized: true
      })
      persist(result.data)
      localStorage.setItem(SESSION_FLAG, '1')
      checked.value = true
      return result.data
    } finally {
      loading.value = false
    }
  }

  async function register(payload) {
    loading.value = true
    try {
      const result = await api.post('/auth/register', payload, {
        suppressUnauthorized: true
      })
      persist(result.data)
      localStorage.setItem(SESSION_FLAG, '1')
      checked.value = true
      return result.data
    } finally {
      loading.value = false
    }
  }

  async function checkSession() {
    // 访客身份同样属于已确认状态，避免每次路由切换都重复请求 /auth/me。
    if (checked.value) return user.value
    // 从未登录过的浏览器没有会话标记，直接按游客处理：省一次注定 401 的探测请求。
    if (!localStorage.getItem(SESSION_FLAG)) {
      persist(null)
      checked.value = true
      return null
    }
    try {
      const result = await api.get('/auth/me', {
        suppressUnauthorized: true
      })
      persist(result.data)
      return result.data
    } catch (error) {
      // 未登录或会话过期属于正常状态：标记已随 token 失效，清掉后本轮按游客处理，
      // 下次加载不再发注定 401 的探测请求。
      localStorage.removeItem(SESSION_FLAG)
      persist(null)
      return null
    } finally {
      checked.value = true
    }
  }

  function updateProfile(patch) {
    if (!user.value) return
    persist({ ...user.value, ...patch })
  }

  async function logout() {
    try {
      await api.post('/auth/logout', {}, {
        suppressUnauthorized: true
      })
    } finally {
      // 标记与后端黑名单同步清理：登出后浏览器回到游客路径，不再发会话探测请求。
      localStorage.removeItem(SESSION_FLAG)
      persist(null)
      checked.value = true
    }
  }

  function homePathFor(roleValue = role.value) {
    if (roleValue === 'admin') return '/admin/overview'
    if (roleValue === 'business') return '/merchant/dashboard'
    return '/'
  }

  function homePath() {
    return homePathFor()
  }

  window.addEventListener('pet-home:unauthorized', logout)

  return {
    user, role, checked, loading, isAuthenticated, isUser, isNonUserAccount,
    login, register, checkSession, updateProfile, logout, homePath, homePathFor
  }
})
