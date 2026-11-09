<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BellRing, BookOpenCheck, Eye, LayoutDashboard, LogOut, MessageCircle, ShieldCheck, Store,
  UsersRound, Warehouse, Wrench
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import { useConversationStore } from '../stores/conversations'
import { api } from '../services/api'

const props = defineProps({ mode: { type: String, default: 'merchant' } })
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const notifications = useNotificationStore()
const conversations = useConversationStore()
const moderationTodoCount = ref(0)
let moderationTimer = null

const merchantGroups = [
  {
    label: '',
    items: [
      { path: '/merchant/dashboard', label: '经营总览', icon: LayoutDashboard },
      { path: '/merchant/shops', label: '店铺管理', icon: Store },
      { path: '/merchant/mall', label: '商城管理', icon: Warehouse },
      { path: '/merchant/services', label: '服务管理', icon: Wrench },
      { path: '/merchant/messages', label: '消息中心', icon: MessageCircle }
    ]
  }
]
const adminGroups = [
  {
    label: '',
    items: [
      { path: '/admin/overview', label: '运营总览', icon: LayoutDashboard },
      { path: '/admin/users', label: '用户管理', icon: UsersRound },
      { path: '/admin/merchants', label: '商家管理', icon: ShieldCheck },
      { path: '/admin/community', label: '社区管理', icon: MessageCircle },
      { path: '/admin/knowledge', label: '养护知识库', icon: BookOpenCheck },
      { path: '/admin/messages', label: '通知发布', icon: BellRing }
    ]
  }
]
const groups = computed(() => props.mode === 'admin' ? adminGroups : merchantGroups)
const modeLabel = computed(() => props.mode === 'admin' ? '管理后台' : '商家中心')
const pageTitle = computed(() => {
  const matched = groups.value
    .flatMap((group) => group.items)
    .find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))
  return matched?.label || modeLabel.value
})

async function logout() {
  try {
    await auth.logout()
  } catch {
    // 服务端退出失败时也会清除本地会话，继续返回登录页。
  }
  await router.replace('/login')
}

function isActive(item) {
  return route.path === item.path || route.path.startsWith(`${item.path}/`)
}

function go(item) {
  // 服务管理必须携带服务门店上下文，优先复用上一次选择
  if (item.path === '/merchant/services') {
    const lastOutletId = window.localStorage.getItem('merchant.serviceOutletId')
    router.push({
      path: item.path,
      query: lastOutletId ? { outletId: lastOutletId } : {}
    })
    return
  }
  router.push(item.path)
}

async function loadModerationTodoCount() {
  if (props.mode !== 'admin') return
  try {
    const result = await api.get('/admin/moderation/unread-summary')
    moderationTodoCount.value = Number(result.data?.total || 0)
  } catch (error) {
    moderationTodoCount.value = 0
  }
}

function navBadge(item) {
  if (props.mode === 'admin' && item.path === '/admin/community') {
    return moderationTodoCount.value
  }
  if (props.mode === 'merchant' && item.path === '/merchant/messages') {
    return notifications.unreadCount + conversations.unreadCount
  }
  return 0
}

onMounted(() => {
  if (props.mode === 'merchant') {
    notifications.connect()
    conversations.connect()
    return
  }
  loadModerationTodoCount()
  moderationTimer = window.setInterval(loadModerationTodoCount, 30000)
})

onBeforeUnmount(() => {
  notifications.disconnect()
  conversations.disconnect()
  if (moderationTimer) window.clearInterval(moderationTimer)
})
</script>

<template>
  <div class="console-shell" :class="`${mode}-console`">
    <aside class="console-side">
      <div class="console-brand">
        <span><component :is="mode === 'admin' ? ShieldCheck : Store" :size="22" /></span>
        <div class="console-brand-copy">
          <b>{{ modeLabel }}</b>
        </div>
        <button
          class="brand-preview"
          type="button"
          title="查看用户端"
          aria-label="查看用户端"
          @click="router.push('/')"
        >
          <Eye :size="16" />
        </button>
      </div>
      <nav>
        <template v-for="group in groups" :key="group.label || group.items[0].path">
          <p v-if="group.label" class="console-nav-label">{{ group.label }}</p>
          <button
            v-for="item in group.items"
            :key="item.path"
            type="button"
            :aria-label="item.label"
            :aria-current="isActive(item) ? 'page' : undefined"
            :title="item.label"
            :class="{ active: isActive(item) }"
            @click="go(item)"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
            <b v-if="navBadge(item)" class="console-nav-badge">
              {{ navBadge(item) > 99 ? '99+' : navBadge(item) }}
            </b>
          </button>
        </template>
      </nav>
      <div class="console-account">
        <span v-if="mode === 'merchant'" class="account-name">
          {{ auth.user?.nickname || auth.user?.username || '商家' }}
        </span>
        <button class="account-logout" type="button" title="退出登录" aria-label="退出登录" @click="logout">
          <LogOut :size="16" />
        </button>
        <button
          class="account-preview"
          type="button"
          title="查看用户端"
          aria-label="查看用户端"
          @click="router.push('/')"
        >
          <Eye :size="16" />
        </button>
      </div>
    </aside>
    <main class="console-main">
      <header class="console-mobile-head">
        <b>{{ pageTitle }}</b>
        <div class="mobile-head-actions">
          <button class="icon-btn" type="button" aria-label="查看用户端" @click="router.push('/')">
            <Eye :size="18" />
          </button>
          <button class="icon-btn" type="button" aria-label="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.console-nav-label {
  margin: 12px 10px 4px;
  font-family: var(--font-family-body);
  font-size: 0.625rem;
  font-weight: var(--font-weight-extra-bold);
  letter-spacing: 0.14em;
  color: var(--color-console-text-muted);
}

.console-nav-badge {
  min-width: 22px;
  height: 22px;
  margin-left: auto;
  padding: 0 5px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: var(--color-danger);
  color: var(--color-white);
  font-size: var(--font-size-sm);
}

.mobile-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 品牌行：商家中心 + 右侧游客预览入口 */
.console-brand {
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-console-divider);
}

.console-side nav {
  margin-top: 12px;
}

.brand-preview {
  margin-left: auto;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  padding: 0;
  display: inline-grid;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 50%;
  background: var(--color-console-hover);
  color: var(--color-console-text);
  cursor: pointer;
  transition: var(--transition-base);
}

.brand-preview:hover {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-text);
  transform: translateY(-1px);
}

/* 底部：仅保留退出登录 */
.console-account {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  padding-top: 14px;
}

/* 商家名称 + 右侧退出图标（仅宽屏侧栏展示） */
.account-name {
  flex: 1;
  min-width: 0;
  padding: 0 2px;
  color: var(--color-console-text-muted);
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.account-logout,
.account-preview {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  padding: 0;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--color-console-divider);
  border-radius: 50%;
  background: transparent;
  color: var(--color-console-text);
  cursor: pointer;
  transition: var(--transition-base);
}

.account-logout {
  margin-left: auto;
}

.account-logout:hover,
.account-preview:hover {
  border-color: var(--color-butter);
  background: var(--color-console-hover);
  color: var(--color-white);
}

.account-preview {
  display: none;
  width: 34px;
  padding: 0;
}

.console-brand-copy {
  min-width: 0;
  display: block;
}

.console-brand-copy > b {
  display: block;
  white-space: nowrap;
}

@media (max-width: 1024px) {
  .brand-preview {
    display: none;
  }

  .account-preview {
    display: inline-flex;
  }

  .account-name {
    display: none;
  }

  .console-account {
    justify-content: center;
    padding-left: 0;
    padding-right: 0;
  }

  .account-logout {
    margin-left: 0;
  }

  .console-side nav {
    margin-top: 10px;
  }

  .console-nav-badge {
    position: absolute;
    top: 3px;
    right: 3px;
    min-width: 19px;
    height: 19px;
    font-size: 0.625rem;
  }

  nav button {
    position: relative;
  }

  .console-brand-copy {
    display: none;
  }

  .console-account-actions button {
    width: 34px;
    height: 34px;
    padding: 0;
    justify-content: center;
    font-size: 0;
    border-radius: 50%;
  }
}

@media (max-width: 768px) {
  .console-nav-label {
    display: none;
  }

  .console-side nav {
    margin-top: 0;
  }
}
</style>
