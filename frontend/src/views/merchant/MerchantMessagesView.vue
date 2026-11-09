<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell, CalendarDays, CheckCheck, Package, RefreshCw,
  Trash2, UserRound
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { relativeTime } from '../../utils/format'
import { useNotificationStore } from '../../stores/notifications'
import { useConversationStore } from '../../stores/conversations'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ConversationChat from '../../components/ConversationChat.vue'
import CustomerOrdersDialog from '../../components/merchant/CustomerOrdersDialog.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const router = useRouter()
const CONVERSATION_PAGE_SIZE = 20
const ui = useUiStore()
const notificationStore = useNotificationStore()
const conversationStore = useConversationStore()
const view = ref('conversations')
const category = ref('mall')
const conversations = ref([])
const notifications = ref([])
const active = ref(null)
const loading = ref(true)
const page = ref(1)
const totalPages = ref(1)
const conversationPage = ref(1)
const conversationTotalPages = ref(1)
const conversationLoadingMore = ref(false)
let conversationRequestSequence = 0
const unreadSummary = ref(emptyUnreadSummary())
const profileOpen = ref(false)
const ordersDialogOpen = ref(false)
const noticeDetail = ref(null)
const noticeDetailOpen = ref(false)

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
const tabs = computed(() => [
  {
    key: 'conversations',
    label: '客服咨询',
    count: Number(unreadSummary.value.conversations || 0)
  },
  {
    key: 'business',
    label: '通知',
    count: Number(unreadSummary.value.total || 0)
  }
])
const conversationCategories = [
  { value: 'mall', label: '商城客服' },
  { value: 'service', label: '服务咨询' }
]
const hasMore = computed(() => page.value < totalPages.value)
const conversationHasMore = computed(
  () => conversationPage.value < conversationTotalPages.value
)
const conversationEmptyTitle = computed(() => (
  category.value === 'service' ? '暂无服务咨询' : '暂无商城客服咨询'
))
const activeConversationTitle = computed(() => active.value
  ? active.value.otherUserName || '宠物之家用户' : '')
const activeCustomerId = computed(() => active.value?.otherUserId || null)
const noticeEmptyTitle = computed(() => '暂无通知')
const noticeEmptyDescription = computed(() => '新的订单、服务预约和平台消息会在这里提醒。')


function emptyUnreadSummary() {
  return {
    total: 0,
    mall: 0,
    service: 0,
    system: 0,
    conversations: 0,
    communityConversations: 0,
    mallConversations: 0,
    serviceConversations: 0
  }
}

function conversationUnreadCount(conversationCategory) {
  if (conversationCategory === 'mall') {
    return Number(unreadSummary.value.mallConversations || 0)
  }
  if (conversationCategory === 'service') {
    return Number(unreadSummary.value.serviceConversations || 0)
  }
  return Number(unreadSummary.value.communityConversations || 0)
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
    // 未读摘要刷新失败时保留现有计数，等待下次实时事件重试。
  }
}

async function load() {
  loading.value = true
  try {
    if (view.value === 'conversations') {
      await loadConversations(true)
    } else {
      await loadNotifications()
    }
  } catch (error) {
    ui.toast(error.message || '读取消息失败', 'error')
  } finally {
    loading.value = false
  }
  await loadUnreadSummary()
}

async function loadConversations(reset = false) {
  const requestSequence = ++conversationRequestSequence
  const targetPage = reset ? 1 : conversationPage.value + 1
  const params = {
    category: category.value,
    page: targetPage,
    pageSize: CONVERSATION_PAGE_SIZE
  }
  const result = await api.get(`/merchant/conversations${queryString(params)}`)
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
  if (conversations.value.length) {
    const current = conversations.value.find((item) => item.id === active.value?.id)
    active.value = current || conversations.value[0]
  } else {
    active.value = null
  }
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
  const result = await api.get(`/merchant/messages${queryString(params)}`)
  const list = result.data?.list || []
  notifications.value = page.value === 1
    ? list
    : [...notifications.value, ...list]
  totalPages.value = result.data?.totalPages || 1
}

const loadingMore = ref(false)

async function loadMoreNotifications() {
  if (loading.value || loadingMore.value) return
  if (view.value === 'conversations' || page.value >= totalPages.value) return
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

function switchView(next) {
  if (view.value === next) return
  view.value = next
  category.value = next === 'conversations' ? 'mall' : ''
  page.value = 1
  totalPages.value = 1
  conversationPage.value = 1
  conversationTotalPages.value = 1
  load()
}

function switchConversationCategory(nextCategory) {
  if (category.value === nextCategory) return
  category.value = nextCategory
  conversationPage.value = 1
  conversationTotalPages.value = 1
  load()
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

async function openNotification(notification) {
  noticeDetail.value = notification
  noticeDetailOpen.value = true
  if (!notification.isRead) {
    try {
      await api.put(`/notifications/${notification.id}/read`)
      notification.isRead = true
      await loadUnreadSummary()
    } catch (error) {
      ui.toast(error.message || '更新通知状态失败', 'warning')
    }
  }
}

function closeNoticeDetail() {
  noticeDetailOpen.value = false
  noticeDetail.value = null
}

async function markAllNotificationsRead() {
  try {
    await api.put('/notifications/read-all')
    notifications.value = notifications.value.map((item) => ({ ...item, isRead: true }))
    await loadUnreadSummary()
    ui.toast('已全部标记为已读', 'success')
  } catch (error) {
    ui.toast(error.message || '标记已读失败', 'error')
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
    const count = Number(result?.data) || 0
    page.value = 1
    await load()
    ui.toast(count ? `已清空 ${count} 条已读通知` : '没有可清空的已读通知', 'success')
  } catch (error) {
    ui.toast(error.message || '清空已读失败', 'error')
  }
}

async function deleteNotification(notification) {
  const confirmed = await ui.confirm({
    message: '确定删除这条通知吗？',
    danger: true
  })
  if (!confirmed) return
  try {
    await api.delete(`/notifications/${notification.id}`)
    notifications.value = notifications.value.filter((item) => item.id !== notification.id)
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

function selectConversation(conversation) {
  active.value = conversation
  profileOpen.value = false
}

function handleDeleted(conversationId) {
  const index = conversations.value.findIndex(
    (item) => Number(item.id) === Number(conversationId)
  )
  if (index < 0) return
  const wasActive = Number(active.value?.id) === Number(conversationId)
  conversations.value.splice(index, 1)
  if (wasActive) {
    active.value = conversations.value[Math.min(index, conversations.value.length - 1)] || null
  }
  loadUnreadSummary()
}

function handleRealtimeDeleted(event) {
  handleDeleted(event.detail?.conversationId)
}

function openCustomerOrders() {
  if (!activeCustomerId.value) {
    ui.toast(`当前会话缺少客户信息，无法查看${active.value?.category === 'service' ? '服务预约' : '商城订单'}`,'warning')
    return
  }
  ordersDialogOpen.value = true
}

function handleMessage(event) {
  if (view.value !== 'conversations') return
  if (Number(event.detail?.conversationId) === Number(active.value?.id)) return
  loadUnreadSummary()
  loadConversations(true)
}

function handleNotification() {
  loadUnreadSummary()
  if (view.value !== 'conversations') {
    page.value = 1
    load()
  }
}

function handleNotificationSync() {
  loadUnreadSummary()
  if (view.value !== 'conversations') {
    page.value = 1
    load()
  }
}

function handleRead() {
  if (!active.value) return
  const updated = { ...active.value, unreadCount: 0 }
  active.value = updated
  conversations.value = conversations.value.map((item) => (
    item.id === updated.id ? updated : item
  ))
  loadUnreadSummary()
}

onMounted(() => {
  load()
  window.addEventListener('pet-home:conversation-message', handleMessage)
  window.addEventListener('pet-home:conversation-deleted', handleRealtimeDeleted)
  window.addEventListener('pet-home:notification', handleNotification)
  window.addEventListener('pet-home:notification-sync', handleNotificationSync)
})

onBeforeUnmount(() => {
  window.removeEventListener('pet-home:conversation-message', handleMessage)
  window.removeEventListener('pet-home:conversation-deleted', handleRealtimeDeleted)
  window.removeEventListener('pet-home:notification', handleNotification)
  window.removeEventListener('pet-home:notification-sync', handleNotificationSync)
})
</script>

<template>
  <div class="console-page message-center-page">
    <header class="console-head">
      <div>
        <h1>消息中心</h1>
      </div>
      <div class="console-head-actions">
        <span class="connection-state" :class="{ online: connectionOnline, reconnecting: connectionReconnecting }">
          <i aria-hidden="true" />{{ connectionLabel }}
        </span>
        <button class="icon-btn" type="button" aria-label="刷新消息" @click="refreshMessages">
          <RefreshCw :size="17" />
        </button>
      </div>
    </header>

    <div class="message-toolbar">
      <div class="message-tabs" role="tablist" aria-label="消息分类">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="view === tab.key"
          :class="{ active: view === tab.key }"
          @click="switchView(tab.key)"
        >
          {{ tab.label }}<span v-if="tab.count">{{ tab.count }}</span>
        </button>
      </div>
      <div v-if="view === 'conversations'" class="conversation-tabs" role="tablist" aria-label="客服咨询分类">
        <button
          v-for="item in conversationCategories"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="category === item.value"
          :class="{ active: category === item.value }"
          @click="switchConversationCategory(item.value)"
        >
          {{ item.label }}
          <b v-if="conversationUnreadCount(item.value)">{{ conversationUnreadCount(item.value) }}</b>
        </button>
      </div>
      <div v-if="view === 'business'" class="toolbar-actions">
        <button class="tool-button" type="button" @click="markAllNotificationsRead">
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
      <div v-if="view === 'conversations'" class="message-workspace">
        <EmptyState
          v-if="!conversations.length"
          class="message-empty"
          :title="conversationEmptyTitle"
        />
        <section v-else class="messenger-grid">
          <aside class="conversation-pane" @scroll.passive="onConversationScroll">
            <article
              v-for="conversation in conversations"
              :key="conversation.id"
              :class="{ active: active?.id === conversation.id }"
            >
              <button class="conversation-open" type="button" @click="selectConversation(conversation)">
                <span class="conversation-avatar">
                  <img
                    v-if="conversation.otherUserAvatar"
                    :src="assetUrl(conversation.otherUserAvatar)"
                    alt=""
                  />
                  <UserRound v-else :size="18" />
                </span>
                <span class="conversation-copy">
                  <b>{{ conversation.otherUserName || '宠物之家用户' }}</b>
                  <small>{{ conversation.lastMessage || '暂无消息' }}</small>
                </span>
                <span v-if="conversation.unreadCount" class="conversation-unread">
                  {{ conversation.unreadCount }}
                </span>
                <time>{{ relativeTime(conversation.lastMessageAt || conversation.updatedAt) }}</time>
              </button>
            </article>
            <p v-if="conversationLoadingMore" class="conversation-load-status">正在加载更多…</p>
            <p v-else-if="!conversationHasMore" class="conversation-load-status">没有更多会话了</p>
          </aside>

          <div class="chat-wrap">
            <div class="chat-toolbar">
              <button class="profile-toggle" type="button" @click="profileOpen = !profileOpen">
                <UserRound :size="14" />客户档案
              </button>
            </div>
            <ConversationChat
              v-if="active"
              :key="active.id"
              :conversation="active"
              :show-subtitle="false"
              @read="handleRead"
              @deleted="handleDeleted"
            />
          </div>

          <aside class="message-details" :class="{ 'is-open': profileOpen }">
            <h2>客户档案</h2>
            <div class="conversation-profile">
              <span>
                <img v-if="active?.otherUserAvatar" :src="assetUrl(active.otherUserAvatar)" alt="" />
                <UserRound v-else :size="22" />
              </span>
              <div class="profile-copy">
                <b>{{ activeConversationTitle }}</b>
              </div>
            </div>
            <dl class="conversation-facts">
              <div><dt>未读消息</dt><dd>{{ active?.unreadCount || 0 }} 条</dd></div>
              <div><dt>最近消息</dt><dd>{{ relativeTime(active?.lastMessageAt || active?.updatedAt) || '--' }}</dd></div>
            </dl>
            <div class="details-actions">
              <button class="btn btn-primary btn-sm" type="button" @click="openCustomerOrders">
                <component :is="active?.category === 'service' ? CalendarDays : Package" :size="15" />
                {{ active?.category === 'service' ? '查看服务预约' : '查看商城订单' }}
              </button>
            </div>
          </aside>
        </section>
      </div>

      <div v-else class="notification-workspace">
        <section class="notice-panel">
          <EmptyState
            v-if="!notifications.length"
            :title="noticeEmptyTitle"
            :description="noticeEmptyDescription"
          />
          <div v-else class="notice-list" @scroll="onNoticeScroll">
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
                  <b>{{ notification.title }}</b>
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
    </template>

    <CustomerOrdersDialog
      :open="ordersDialogOpen"
      :customer="active"
      @close="ordersDialogOpen = false"
    />

    <ModalDialog
      :open="noticeDetailOpen"
      :title="noticeDetail?.title || '通知详情'"
      @close="closeNoticeDetail"
    >
      <div v-if="noticeDetail" class="notice-detail">
        <div class="notice-detail-meta">
          <time>{{ relativeTime(noticeDetail.createdAt) }}</time>
        </div>
        <p class="notice-detail-body">{{ notificationContent(noticeDetail) }}</p>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.message-center-page {
  height: calc(100dvh - (var(--console-pad) * 2));
  min-height: 620px;
  display: flex;
  flex-direction: column;
}
.message-center-page > .console-head { flex: 0 0 auto; }

.connection-state {
  min-height: 30px;
  padding: 4px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  background: var(--color-surface);
  font-size: var(--font-size-sm);
}
.connection-state i { width: 7px; height: 7px; border-radius: 50%; background: var(--color-text-muted); }
.connection-state.online i { background: var(--color-success); }
.connection-state.reconnecting i { background: var(--color-warning); animation: connection-blink 1s ease infinite; }
@keyframes connection-blink { 50% { opacity: 0.35; } }

.message-toolbar {
  flex: 0 0 auto;
  min-height: 42px;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.message-tabs, .conversation-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.toolbar-actions { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }
.message-tabs button {
  min-height: 36px;
  padding: 6px 13px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}
.message-tabs button.active { border-color: var(--color-text); background: var(--color-text); color: var(--color-white); }
.message-tabs button span {
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: var(--color-danger);
  color: var(--color-white);
  font-size: 0.7rem;
}
.conversation-tabs button {
  min-height: 34px;
  padding: 5px 11px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}
.conversation-tabs button.active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-strong); }
.conversation-tabs b {
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  background: var(--color-danger);
  color: var(--color-white);
  font-size: 0.7rem;
}

.notice-filter {
  height: 34px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
.tool-button {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.75rem;
}
.tool-button:hover:not(:disabled) { background: var(--color-surface-warm); }
.tool-button:disabled { opacity: 0.5; }

.message-workspace, .notification-workspace { flex: 1; display: flex; flex-direction: column; min-height: 0; }

.messenger-grid {
  height: 100%;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 262px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-console-panel);
  overflow: hidden;
  background: var(--color-surface);
  position: relative;
}
.conversation-pane {
  min-height: 0;
  overflow-y: auto;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface-ivory);
}
.conversation-pane article { border-bottom: 1px solid var(--color-border-soft); }
.conversation-load-status { margin: 0; padding: 10px; color: var(--color-text-muted); text-align: center; font-size: 0.7rem; }
.conversation-pane article.active { background: var(--color-surface); box-shadow: inset 3px 0 var(--color-primary); }
.conversation-open {
  width: 100%;
  min-height: 66px;
  padding: 11px 12px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  text-align: left;
}
.conversation-open:hover { background: var(--color-surface); }
.conversation-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--color-white);
  color: var(--color-primary-strong);
}
.conversation-avatar img { width: 100%; height: 100%; object-fit: cover; }
.conversation-copy { min-width: 0; display: grid; gap: 3px; }
.conversation-copy b, .conversation-copy small { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.conversation-copy b { font-size: var(--font-size-sm); }
.conversation-copy small { color: var(--color-text-muted); font-size: 0.75rem; }
.conversation-unread {
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-danger);
  color: var(--color-white);
  display: inline-grid;
  place-items: center;
  font-size: 0.7rem;
}
.conversation-open time { grid-column: 2 / -1; color: var(--color-text-muted); font-size: 0.7rem; }

.chat-wrap { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.chat-toolbar {
  flex: 0 0 auto;
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid var(--color-border-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
/* 宽屏下工具条只剩隐藏的档案按钮，整条隐藏避免头部上方出现空隙 */
@media (min-width: 1281px) {
  .chat-toolbar { display: none; }
}
.profile-toggle {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: none;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  font-size: 0.7rem;
}
.chat-wrap .chat-panel { width: 100%; max-width: none; flex: 1; min-height: 0; border: 0; border-radius: 0; }

.message-details {
  min-height: 0;
  padding: 16px;
  border-left: 1px solid var(--color-border);
  background: var(--color-surface-ivory);
  overflow-y: auto;
}
.message-details h2 { margin: 0 0 14px; font-size: var(--font-size-lg); }
.conversation-profile {
  display: grid;
  justify-items: center;
  gap: 4px;
  padding: 14px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  text-align: center;
}
.conversation-profile > span {
  width: 54px;
  height: 54px;
  margin-bottom: 5px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}
.conversation-profile img { width: 100%; height: 100%; object-fit: cover; }
.conversation-profile b { font-size: var(--font-size-base); }
.conversation-profile small { color: var(--color-text-muted); font-size: 0.75rem; }
.conversation-facts { margin: 12px 0; }
.conversation-facts div {
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-soft);
  display: flex;
  justify-content: space-between;
  gap: 9px;
  font-size: 0.75rem;
}
.conversation-facts dt { color: var(--color-text-muted); }
.conversation-facts dd { margin: 0; color: var(--color-text-secondary); text-align: right; }
.details-actions { display: grid; gap: 7px; }
.details-actions .btn { width: 100%; }
.message-empty { height: 100%; display: grid; place-content: center; }

.notice-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-console-panel);
  background: var(--color-surface);
  overflow: hidden;
}

.notice-list { flex: 1; min-height: 0; overflow-y: auto; padding: 10px 12px 14px; }

.notice-card {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-soft);
  border-left: 3px solid var(--color-border-soft);
  border-radius: 4px 14px 14px 4px;
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

.notice-unread-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary);
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

.notice-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0 2px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.notice-detail { display: grid; gap: 12px; }
.notice-detail-meta { display: flex; align-items: center; gap: 10px; color: var(--color-text-muted); font-size: 0.75rem; }
.notice-detail-body {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1280px) {
  .messenger-grid { grid-template-columns: 240px minmax(0, 1fr); }
  .profile-toggle { display: inline-flex; }
  .message-details {
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 278px;
    z-index: 3;
    transform: translateX(100%);
    transition: transform var(--transition-base);
    box-shadow: var(--shadow-md);
  }
  .message-details.is-open { transform: none; }
}
@media (max-width: 900px) {
  .message-center-page { height: auto; min-height: 0; }
  .message-toolbar { align-items: flex-start; flex-direction: column; }
  .messenger-grid { height: auto; min-height: 640px; }
  .chat-wrap { min-height: 460px; }
}
@media (max-width: 760px) {
  .messenger-grid { grid-template-columns: 1fr; }
  .conversation-pane { max-height: 220px; border-right: 0; border-bottom: 1px solid var(--color-border); }
  .message-details { width: min(300px, 88vw); }
  .notice-delete { opacity: 1; }
}
</style>
