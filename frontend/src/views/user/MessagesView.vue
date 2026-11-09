<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell, CheckCheck, MessageCircle, RefreshCw, Trash2
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { relativeTime } from '../../utils/format'
import { useUserAccess } from '../../composables/useUserAccess'
import { useUiStore } from '../../stores/ui'
import { useNotificationStore } from '../../stores/notifications'
import { useConversationStore } from '../../stores/conversations'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const router = useRouter()
const CONVERSATION_PAGE_SIZE = 20
const { isUser } = useUserAccess()
const ui = useUiStore()
const notificationStore = useNotificationStore()
const conversationStore = useConversationStore()
const view = ref('conversations')
const category = ref('')
const notifications = ref([])
const conversations = ref([])
const loading = ref(false)
const selected = ref(null)
const detailOpen = ref(false)
const page = ref(1)
const totalPages = ref(1)
const loadingMore = ref(false)
const conversationPage = ref(1)
const conversationTotalPages = ref(1)
const conversationLoadingMore = ref(false)
let conversationRequestSequence = 0
const unreadSummary = ref(emptyUnreadSummary())
const unreadCount = computed(() => Number(unreadSummary.value.total || 0))
const hasMore = computed(() => page.value < totalPages.value)
const conversationHasMore = computed(
  () => conversationPage.value < conversationTotalPages.value
)
const connectionOnline = computed(() => (
  notificationStore.connected && conversationStore.connected
))
const connectionReconnecting = computed(() => (
  notificationStore.reconnecting || conversationStore.reconnecting
))
const connectionLabel = computed(() => {
  if (connectionOnline.value) return '实时连接'
  if (connectionReconnecting.value) return '正在重连'
  return '连接离线'
})

const conversationCategories = [
  { value: '', label: '全部' },
  { value: 'community', label: '社区私信' },
  { value: 'mall', label: '商城客服' },
  { value: 'service', label: '服务咨询' }
]

function emptyUnreadSummary() {
  return {
    total: 0,
    community: 0,
    mall: 0,
    service: 0,
    health: 0,
    system: 0,
    conversations: 0,
    communityConversations: 0,
    mallConversations: 0,
    serviceConversations: 0
  }
}

async function loadUnreadSummary() {
  try {
    const result = await api.get('/notifications/unread-summary')
    unreadSummary.value = {
      ...emptyUnreadSummary(),
      ...(result.data || {})
    }
    notificationStore.setCount(unreadSummary.value.total)
    conversationStore.setCount(unreadSummary.value.conversations)
  } catch (error) {
    // 未读摘要刷新失败时保留当前值，等待下次实时事件重试。
  }
}

function conversationUnreadCount(value) {
  if (!value) return Number(unreadSummary.value.conversations || 0)
  if (value === 'community') {
    return Number(unreadSummary.value.communityConversations || 0)
  }
  if (value === 'mall') {
    return Number(unreadSummary.value.mallConversations || 0)
  }
  if (value === 'service') {
    return Number(unreadSummary.value.serviceConversations || 0)
  }
  return 0
}


async function load() {
  if (!isUser.value) return
  loading.value = true
  try {
    if (view.value === 'notifications') {
      await loadNotifications()
    } else {
      await loadConversations(true)
    }
    await loadUnreadSummary()
  } catch (error) {
    if (isUser.value) ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loadConversations(reset = false) {
  const requestSequence = ++conversationRequestSequence
  const targetPage = reset ? 1 : conversationPage.value + 1
  const params = {
    page: targetPage,
    pageSize: CONVERSATION_PAGE_SIZE
  }
  if (category.value) params.category = category.value
  const result = await api.get(`/conversations${queryString(params)}`)
  if (requestSequence !== conversationRequestSequence) return
  const list = result.data?.list || []
  if (reset) {
    conversations.value = list
  } else {
    const existingIds = new Set(conversations.value.map((item) => Number(item.id)))
    conversations.value.push(...list.filter((item) => !existingIds.has(Number(item.id))))
  }
  conversationPage.value = result.data?.page || targetPage
  conversationTotalPages.value = result.data?.totalPages || 1
}

async function loadMoreConversations() {
  if (loading.value || conversationLoadingMore.value || !conversationHasMore.value) return
  conversationLoadingMore.value = true
  try {
    await loadConversations()
  } catch (error) {
    ui.toast(error.message || '加载更多会话失败', 'error')
  } finally {
    conversationLoadingMore.value = false
  }
}

function onConversationScroll(event) {
  const element = event.target
  if (element.scrollTop + element.clientHeight >= element.scrollHeight - 80) {
    loadMoreConversations()
  }
}

async function loadNotifications() {
  const params = { page: page.value, pageSize: 20 }
  const result = await api.get(`/notifications${queryString(params)}`)
  const list = result.data?.list || []
  notifications.value = page.value === 1
    ? list
    : [...notifications.value, ...list]
  totalPages.value = result.data?.totalPages || 1
}

async function loadMoreNotifications() {
  if (loading.value || loadingMore.value) return
  if (view.value !== 'notifications' || page.value >= totalPages.value) return
  page.value += 1
  loadingMore.value = true
  try {
    await loadNotifications()
  } catch (error) {
    page.value -= 1
    ui.toast(error.message || '加载更多失败', 'error')
  } finally {
    loadingMore.value = false
  }
}

function onNoticeScroll(event) {
  const el = event.target
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) {
    loadMoreNotifications()
  }
}

async function refreshMessages() {
  page.value = 1
  conversationPage.value = 1
  await load()
}

// 订单通知里的收货地址是序列化 JSON，直接展示会露出转义字符
function notificationContent(notification) {
  const raw = String(notification.content || '')
  let marker = raw.indexOf('{"')
  if (marker < 0) marker = raw.indexOf('{\\"')
  if (marker < 0) return raw
  const tail = raw.slice(marker).replace(/\\"/g, '"').replace(/"\s*$/, '')
  try {
    const address = JSON.parse(tail)
    const region = [address.province, address.city, address.district, address.detail]
      .filter(Boolean).join('')
    const contact = [address.name, address.phone].filter(Boolean).join(' ')
    return `${raw.slice(0, marker).trimEnd()}${region}${contact ? `（${contact}）` : ''}`
  } catch (error) {
    return raw.replace(/\\"/g, '"')
  }
}

async function openNotification(message) {
  selected.value = message
  detailOpen.value = true
  if (!message.isRead) {
    try {
      await api.put(`/notifications/${message.id}/read`)
      message.isRead = true
      await loadUnreadSummary()
    } catch (error) {
      ui.toast(error.message, 'warning')
    }
  }
}

function openNotificationTarget() {
  const target = String(selected.value?.actionUrl || '')
  if (!target.startsWith('/') || target.startsWith('//')) return
  detailOpen.value = false
  router.push(target)
}

async function markAll() {
  try {
    const result = await api.put('/notifications/read-all')
    notifications.value = notifications.value.map((item) => ({ ...item, isRead: true }))
    await loadUnreadSummary()
    ui.toast(result.msg || '已全部标为已读', 'success')
  } catch (error) {
    ui.toast(error.message || '标记已读失败', 'error')
  }
}

async function deleteNotification(message) {
  const confirmed = await ui.confirm({
    message: '确定删除这条通知吗？',
    danger: true
  })
  if (!confirmed) return
  try {
    await api.delete(`/notifications/${message.id}`)
    notifications.value = notifications.value.filter((item) => item.id !== message.id)
    if (!notifications.value.length && page.value > 1) {
      page.value = 1
      await load()
    } else {
      await loadUnreadSummary()
    }
    ui.toast('通知已删除', 'success')
  } catch (error) {
    ui.toast(error.message || '删除通知失败', 'error')
  }
}

async function clearReadNotifications() {
  const confirmed = await ui.confirm({
    message: '确定清空全部已读通知吗？清空后不可恢复。',
    danger: true
  })
  if (!confirmed) return
  try {
    const result = await api.delete('/notifications/read')
    page.value = 1
    await load()
    ui.toast(result.msg || '已读通知已清空', 'success')
  } catch (error) {
    ui.toast(error.message || '清空已读失败', 'error')
  }
}

function switchView(next) {
  if (view.value === next) return
  view.value = next
  category.value = ''
  page.value = 1
  totalPages.value = 1
  conversationPage.value = 1
  conversationTotalPages.value = 1
  if (isUser.value) load()
}

function changeCategory() {
  page.value = 1
  conversationPage.value = 1
  conversationTotalPages.value = 1
  if (isUser.value) load()
}

function openConversation(conversation) {
  router.push({
    path: `/messages/${conversation.otherUserId}`,
    query: {
      category: conversation.category,
      name: conversation.otherShopName || conversation.otherUserName || ''
    }
  })
}

function handleRealtime() {
  if (!isUser.value) return
  loadUnreadSummary()
  if (view.value === 'notifications') {
    page.value = 1
    load()
  }
}

function handleNotificationSync() {
  if (!isUser.value) return
  loadUnreadSummary()
  if (view.value === 'notifications') {
    page.value = 1
    load()
  }
}

function handleConversationRealtime() {
  if (!isUser.value) return
  loadUnreadSummary()
  if (view.value === 'conversations') loadConversations(true)
}

function handleConversationDeleted(event) {
  if (!isUser.value) return
  conversations.value = conversations.value.filter(
    (item) => Number(item.id) !== Number(event.detail?.conversationId)
  )
  loadUnreadSummary()
}

function resetPrivateState() {
  notifications.value = []
  conversations.value = []
  selected.value = null
  detailOpen.value = false
  unreadSummary.value = emptyUnreadSummary()
  page.value = 1
  totalPages.value = 1
  conversationPage.value = 1
  conversationTotalPages.value = 1
  conversationLoadingMore.value = false
  loading.value = false
  notificationStore.clear()
  conversationStore.setCount(0)
}

onMounted(() => {
  if (isUser.value) load()
  window.addEventListener('pet-home:notification', handleRealtime)
  window.addEventListener('pet-home:notification-sync', handleNotificationSync)
  window.addEventListener('pet-home:conversation-message', handleConversationRealtime)
  window.addEventListener('pet-home:conversation-deleted', handleConversationDeleted)
})

watch(isUser, (allowed) => {
  if (allowed) {
    load()
  } else {
    resetPrivateState()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pet-home:notification', handleRealtime)
  window.removeEventListener('pet-home:notification-sync', handleNotificationSync)
  window.removeEventListener('pet-home:conversation-message', handleConversationRealtime)
  window.removeEventListener('pet-home:conversation-deleted', handleConversationDeleted)
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <div><h1>消息中心</h1></div>
      <div v-if="isUser" class="message-head-actions">
        <span
          class="connection-state"
          :class="{ online: connectionOnline, reconnecting: connectionReconnecting }"
        >
          <i aria-hidden="true" />
          {{ connectionLabel }}
        </span>
        <button class="icon-btn" type="button" aria-label="刷新消息" @click="refreshMessages">
          <RefreshCw :size="16" />
        </button>
      </div>
    </header>
    <div class="message-toolbar">
      <div class="message-tabs">
        <button type="button" :class="{ active: view === 'conversations' }" @click="switchView('conversations')">
          会话 <span v-if="isUser && conversationStore.unreadCount">{{ conversationStore.unreadCount }}</span>
        </button>
        <button type="button" :class="{ active: view === 'notifications' }" @click="switchView('notifications')">
          通知 <span v-if="isUser && unreadCount">{{ unreadCount }}</span>
        </button>
      </div>
      <div v-if="view === 'conversations'" class="conversation-tools">
        <div class="category-unread-tabs conversation-category-tabs" role="tablist" aria-label="会话分类">
          <button
            v-for="item in conversationCategories"
            :key="item.value"
            type="button"
            role="tab"
            :aria-selected="category === item.value"
            :class="{ active: category === item.value }"
            @click="category = item.value; changeCategory()"
          >
            {{ item.label }}
            <b v-if="conversationUnreadCount(item.value)">
              {{ conversationUnreadCount(item.value) }}
            </b>
          </button>
        </div>
      </div>
      <div v-else class="toolbar-actions">
        <button class="tool-button" type="button" @click="markAll">
          <CheckCheck :size="14" />
          全部已读
        </button>
        <button class="tool-button" type="button" @click="clearReadNotifications">
          <Trash2 :size="14" />
          清空已读
        </button>
      </div>
    </div>
    <LoadingPanel v-if="loading" label="正在读取消息" />
    <template v-else>
      <div v-if="view === 'notifications'">
        <EmptyState
          v-if="!notifications.length"
          title="暂无通知"
          description="新的订单、服务预约和平台消息会在这里提醒。"
        />
        <section v-else class="notice-panel panel">
          <div class="notice-list" @scroll.passive="onNoticeScroll">
            <article
              v-for="notification in notifications"
              :key="notification.id"
              class="notice-card"
              :class="{ unread: !notification.isRead }"
              role="button"
              tabindex="0"
              @click="openNotification(notification)"
              @keydown.enter="openNotification(notification)"
            >
              <span class="notice-icon">
                <Bell :size="18" />
              </span>
              <div class="notice-main">
                <div class="notice-top">
                  <b>{{ notification.title || '系统通知' }}</b>
                  <time>{{ relativeTime(notification.createdAt) }}</time>
                  <i v-if="!notification.isRead" class="notice-unread-dot" aria-hidden="true" />
                  <button
                    class="notice-delete"
                    type="button"
                    aria-label="删除通知"
                    @click.stop="deleteNotification(notification)"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>
                <p>{{ notificationContent(notification) }}</p>
              </div>
            </article>
            <p v-if="loadingMore" class="notice-loading-more">正在加载更多…</p>
            <p v-else-if="!hasMore" class="notice-end">没有更多通知了</p>
          </div>
        </section>
      </div>
      <div v-else>
        <div
          v-if="conversations.length"
          class="conversation-list panel"
          @scroll.passive="onConversationScroll"
        >
          <article v-for="conversation in conversations" :key="conversation.id">
            <button class="conversation-open" type="button" @click="openConversation(conversation)">
              <span class="conversation-icon">
                <img v-if="conversation.otherShopLogo" :src="assetUrl(conversation.otherShopLogo)" alt="" />
                <img v-else-if="conversation.otherUserAvatar" :src="assetUrl(conversation.otherUserAvatar)" alt="" />
                <MessageCircle v-else :size="20" />
              </span>
              <span class="conversation-main">
                <b>{{ conversation.otherShopName || conversation.otherUserName || '宠物之家用户' }}</b>
                <span>{{ conversation.lastMessage || '开始对话' }}</span>
              </span>
              <span v-if="conversation.unreadCount" class="conversation-unread">{{ conversation.unreadCount }}</span>
              <small>{{ relativeTime(conversation.lastMessageAt || conversation.updatedAt) }}</small>
            </button>
          </article>
          <p v-if="conversationLoadingMore" class="conversation-load-status">正在加载更多…</p>
          <p v-else-if="!conversationHasMore" class="conversation-load-status">没有更多会话了</p>
        </div>
        <EmptyState v-else title="暂无会话" />
      </div>
    </template>

    <ModalDialog :open="detailOpen" :title="selected?.title || '消息详情'" @close="detailOpen = false">
      <div v-if="selected" class="message-detail">
        <div class="message-detail-meta">
          <time>{{ relativeTime(selected.createdAt) }}</time>
        </div>
        <p class="message-detail-body">{{ notificationContent(selected) }}</p>
      </div>
      <template v-if="selected?.actionUrl" #footer>
        <button class="btn btn-ghost" type="button" @click="detailOpen = false">关闭</button>
        <button class="btn btn-primary" type="button" @click="openNotificationTarget">查看相关页面</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.message-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 15px; margin-bottom: 18px; }
.message-head-actions { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.connection-state { min-height: 32px; padding: 5px 10px; border: 1px solid var(--color-border); border-radius: 999px; display: inline-flex; align-items: center; gap: 6px; color: var(--color-text-muted); background: var(--color-surface); font-size: var(--font-size-sm); }
.connection-state i { width: 8px; height: 8px; border-radius: 50%; background: var(--color-text-muted); }
.connection-state.online i { background: var(--color-success); }
.connection-state.reconnecting i { background: var(--color-warning); animation: connection-blink 1s ease infinite; }
@keyframes connection-blink { 50% { opacity: .35; } }
.message-tabs { display: flex; gap: 5px; }
.message-tabs button { border: 1px solid var(--color-border); background: var(--color-white); min-height: 40px; padding: 7px 14px; border-radius: 999px; color: var(--color-text-secondary); display: flex; align-items: center; gap: 6px; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.message-tabs button.active { background: var(--color-text); color: var(--color-white); border-color: var(--color-text); }
.message-tabs button span { min-width: 24px; height: 24px; border-radius: 50%; display: inline-grid; place-items: center; background: var(--color-danger); color: var(--color-white); font-size: var(--font-size-sm); }

.tool-button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-white);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
}
.tool-button:hover { border-color: var(--color-border-strong); background: var(--color-surface-warm); }
.toolbar-actions { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }

.conversation-tools { min-width: 0; display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.conversation-category-tabs { justify-content: flex-end; }
.conversation-list { max-height: calc(100dvh - 250px); overflow-y: auto; overscroll-behavior: contain; }
.conversation-list > article { width: 100%; border-bottom: 1px solid var(--color-border); background: var(--color-white); display: flex; align-items: flex-start; }
.conversation-list > article:last-child { border-bottom: 0; }
.conversation-open { min-width: 0; flex: 1; padding: 16px 12px 16px 20px; border: 0; background: transparent; display: flex; align-items: flex-start; gap: 12px; text-align: left; color: var(--color-text); }
.conversation-list > article:hover { background: var(--color-surface-warm); }
.conversation-icon { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); flex-shrink: 0; }
.conversation-icon img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.conversation-main { flex: 1; min-width: 0; display: grid; gap: 3px; }
.conversation-main b { font-size: var(--font-size-sm); }
.conversation-main small, .conversation-main span, .conversation-list small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.conversation-main span { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.conversation-unread { min-width: 24px; height: 24px; padding: 0 6px; border-radius: 999px; display: inline-grid; place-items: center; background: var(--color-danger); color: var(--color-white); font-size: var(--font-size-sm); }
.conversation-open { align-items: center; }
.conversation-load-status { margin: 0; padding: 12px; color: var(--color-text-muted); text-align: center; font-size: var(--font-size-sm); }

/* 通知列表（与商家端消息中心同布局） */
.notice-panel { overflow: hidden; }
.notice-list { max-height: calc(100dvh - 260px); min-height: 320px; overflow-y: auto; padding: 10px 12px 14px; }
.notice-card {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-soft);
  border-radius: 14px;
  background: var(--color-surface);
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}
.notice-card:hover { border-color: var(--color-border-strong); box-shadow: 0 6px 16px rgba(92, 63, 14, 0.07); }
.notice-card:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.notice-card.unread {
  background: var(--color-surface-warm);
  box-shadow: inset 0 0 0 1px var(--color-border-strong);
}
.notice-icon {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}
.notice-main { flex: 1; min-width: 0; display: grid; gap: 4px; }
.notice-top { display: flex; align-items: center; gap: 8px; min-width: 0; }
.notice-top b { font-size: var(--font-size-sm); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.notice-top time { margin-left: auto; flex: 0 0 auto; color: var(--color-text-muted); font-size: 0.7rem; }
.notice-unread-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary);
}
.notice-delete {
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
  transition: var(--transition-base);
}
.notice-delete:hover { background: var(--color-danger-soft); color: var(--color-danger); }
.notice-main p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notice-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0 2px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
.notice-end {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 4px 4px;
  color: var(--color-text-muted);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
}
.notice-end::before,
.notice-end::after { content: ""; flex: 1; height: 1px; background: var(--color-border-soft); }

/* 通知详情弹窗（纯查看） */
.message-detail { display: grid; gap: 12px; }
.message-detail-meta { display: flex; align-items: center; gap: 10px; color: var(--color-text-muted); font-size: 0.75rem; }
.message-detail-body {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 760px) {
  .message-toolbar { align-items: flex-start; flex-direction: column; }
  .toolbar-actions { margin-left: 0; }
  .notice-list { max-height: none; }
}
</style>
