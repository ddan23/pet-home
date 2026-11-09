<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HeartPulse, Home, LogOut, Menu, MessageCircle, PawPrint,
  ShieldCheck, Store, UserRound, X
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'
import { useNotificationStore } from '../stores/notifications'
import { useConversationStore } from '../stores/conversations'
import { useUserAccess } from '../composables/useUserAccess'
import { useUserAccessPromptStore } from '../stores/userAccessPrompt'
import { assetUrl } from '../services/api'
import UserAccessDialog from './UserAccessDialog.vue'

const auth = useAuthStore()
const cart = useCartStore()
const notifications = useNotificationStore()
const conversations = useConversationStore()
const { requireUser, redirectToLogin } = useUserAccess()
const accessPrompt = useUserAccessPromptStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const loggingOut = ref(false)

const nav = [
  { path: '/', label: '首页', icon: Home },
  { path: '/community', label: '社区', icon: MessageCircle },
  { path: '/mall', label: '商城', icon: Store },
  { path: '/services', label: '服务', icon: PawPrint },
  { path: '/health', label: '养护', icon: HeartPulse }
]

const currentPath = computed(() => route.path)
const routeClass = computed(() => route.name ? `route-${String(route.name)}` : '')
const consolePath = computed(() => auth.homePathFor(auth.role))
const canOpenConsole = computed(() => auth.role === 'admin' || auth.role === 'business')
const isUser = computed(() => auth.role === 'user')
const messageCount = computed(() => notifications.unreadCount + conversations.unreadCount)
const profileLabel = computed(() => {
  if (auth.role === 'admin') return '管理后台'
  if (auth.role === 'business') return '商家中心'
  if (auth.role === 'user') return auth.user?.nickname || '我的'
  return '登录'
})
const profileAriaLabel = computed(() => {
  if (auth.role === 'admin') return '进入管理后台'
  if (auth.role === 'business') return '进入商家中心'
  if (auth.role === 'user') return '进入个人中心'
  return '登录普通用户账号'
})

onMounted(async () => {
  await auth.checkSession()
  if (isUser.value) {
    notifications.connect()
    conversations.connect()
  }
})

watch(isUser, (hasUserAccess) => {
  if (hasUserAccess) {
    notifications.connect()
    conversations.connect()
    return
  }
  notifications.disconnect()
  conversations.disconnect()
  notifications.clear()
  conversations.setCount(0)
  cart.reset()
})

watch(() => auth.isAuthenticated, (authenticated) => {
  const requiresUser = route.matched.some((record) => record.meta.requiresUser)
  if (!authenticated && requiresUser && !loggingOut.value) {
    const featureName = route.meta.featureName || '该页面'
    accessPrompt.openGuest({
      action: featureName,
      redirect: route.fullPath
    })
    router.replace('/')
  }
})

onBeforeUnmount(() => {
  notifications.disconnect()
  conversations.disconnect()
})

function navigate(path) {
  menuOpen.value = false
  router.push(path)
}

async function openMessages() {
  if (!await requireUser({ action: '消息中心' })) return
  navigate('/messages')
}

async function openProfile() {
  if (!auth.isAuthenticated) {
    redirectToLogin({ redirect: route.fullPath })
    return
  }
  if (auth.role === 'admin' || auth.role === 'business') {
    navigate(consolePath.value)
    return
  }
  navigate('/me')
}

async function logout() {
  loggingOut.value = true
  try {
    try {
      await auth.logout()
    } catch {
      // 服务端退出失败时也会清除本地会话，继续返回游客首页。
    }
    cart.reset()
    notifications.clear()
    conversations.setCount(0)
    await router.replace('/')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <div class="nav-shell">
        <button class="brand" type="button" @click="navigate('/')">
          <span class="brand-mark"><PawPrint :size="22" /></span>
          <span><b>猫狗宠物之家</b></span>
        </button>
        <nav class="desktop-nav" aria-label="主导航">
          <RouterLink
            v-for="item in nav"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: currentPath === item.path || (item.path !== '/' && currentPath.startsWith(item.path)) }"
          >
            <component :is="item.icon" :size="17" />
            {{ item.label }}
          </RouterLink>
        </nav>
        <div class="nav-actions">
          <button
            class="icon-btn"
            type="button"
            :aria-label="isUser
              ? `消息，通知${notifications.unreadCount}，会话${conversations.unreadCount}`
              : '打开消息中心'"
            @click="openMessages"
          >
            <MessageCircle :size="19" />
            <span v-if="isUser && messageCount" class="nav-notice-badge">
              {{ messageCount > 99 ? '99+' : messageCount }}
            </span>
          </button>
          <button
            class="profile-chip"
            type="button"
            :aria-label="profileAriaLabel"
            @click="openProfile"
          >
            <img
              v-if="auth.user?.avatar"
              :src="assetUrl(auth.user.avatar)"
              :alt="`${auth.user?.nickname || '用户'}的头像`"
            />
            <span v-else><UserRound :size="18" /></span>
            <b>{{ profileLabel }}</b>
          </button>
          <button
            v-if="auth.isAuthenticated"
            class="icon-btn"
            type="button"
            aria-label="退出登录"
            @click="logout"
          >
            <LogOut :size="18" />
          </button>
          <button class="icon-btn mobile-menu-btn" type="button" aria-label="打开菜单" @click="menuOpen = !menuOpen">
            <X v-if="menuOpen" :size="21" /><Menu v-else :size="21" />
          </button>
        </div>
      </div>
      <div v-if="menuOpen" class="mobile-nav">
        <button v-for="item in nav" :key="item.path" type="button" @click="navigate(item.path)">
          <component :is="item.icon" :size="18" />{{ item.label }}
        </button>
        <button type="button" @click="openMessages">
          <MessageCircle :size="18" />消息中心
        </button>
        <button type="button" @click="openProfile">
          <UserRound :size="18" />个人中心
        </button>
        <button v-if="canOpenConsole" type="button" @click="navigate(consolePath)">
          <ShieldCheck :size="18" />{{ auth.role === 'admin' ? '管理后台' : '商家中心' }}
        </button>
        <button v-if="auth.isAuthenticated" type="button" @click="logout">
          <LogOut :size="18" />退出登录
        </button>
      </div>
    </header>
    <main class="site-main" :class="routeClass">
      <RouterView />
    </main>
    <footer class="site-footer">
      <div>
        <b>猫狗宠物之家</b>
      </div>
    </footer>
    <UserAccessDialog />
  </div>
</template>
