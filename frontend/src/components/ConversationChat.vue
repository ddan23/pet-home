<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, MessageCircle, Send, Trash2 } from 'lucide-vue-next'
import { api, assetUrl } from '../services/api'
import { relativeTime } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { useConversationStore } from '../stores/conversations'
import { useUiStore } from '../stores/ui'
import LoadingPanel from './LoadingPanel.vue'
import UserProfileDrawer from './UserProfileDrawer.vue'

const props = defineProps({
  conversation: { type: Object, required: true },
  showBack: { type: Boolean, default: false },
  showSubtitle: { type: Boolean, default: true },
  contextCard: { type: Object, default: null }
})
const emit = defineEmits(['back', 'read', 'deleted'])

const router = useRouter()
const auth = useAuthStore()
const conversationStore = useConversationStore()
const ui = useUiStore()

const avatarBroken = ref(false)
const peerProfileOpen = ref(false)
// 商家会话优先展示店铺 logo，用户会话用个人头像
const peerAvatar = computed(() => (
  props.conversation?.otherShopLogo || props.conversation?.otherUserAvatar || ''
))
watch(() => [props.conversation?.otherShopLogo, props.conversation?.otherUserAvatar], () => {
  avatarBroken.value = false
})

// 点击对方头像或名字：打开对方主页（TA 的全部帖子）
function openPeerProfile() {
  if (!props.conversation?.otherUserId) return
  peerProfileOpen.value = true
}

const messages = ref([])
const content = ref('')
const loading = ref(true)
const sending = ref(false)
const deleting = ref(false)
const loadingOlder = ref(false)
const hasMore = ref(false)
const beforeId = ref(null)
const box = ref(null)
const otherLastReadMessageId = ref(0)
const unreadCount = ref(Number(props.conversation.unreadCount || 0))
const chatVisible = ref(false)
let visibilityObserver = null

const title = computed(() => props.conversation.otherShopName
  || props.conversation.otherUserName || '宠物之家用户')
const subtitle = computed(() => {
  if (props.conversation.category === 'mall') return '商城客服'
  if (props.conversation.category === 'service') return '服务咨询'
  return '社区私信'
})

async function loadMessages() {
  if (!props.conversation?.id) return
  loading.value = true
  try {
    const result = await api.get(`/conversations/${props.conversation.id}/messages?limit=30`)
    messages.value = result.data?.messages || []
    hasMore.value = Boolean(result.data?.hasMore)
    beforeId.value = result.data?.nextBeforeId
    otherLastReadMessageId.value = Number(
      props.conversation.otherLastReadMessageId || 0)
    scrollBottom()
    await markRead()
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
    observeChatBox()
    maybeSendContextCard()
  }
}

// ============ 咨询上下文卡片消息（product_card / service_card） ============
function isCardMessage(message) {
  return message.contentType === 'product_card' || message.contentType === 'service_card'
}

function cardData(message) {
  try {
    const data = JSON.parse(message.content || '{}')
    return {
      kind: message.contentType === 'product_card' ? 'product' : 'service',
      id: Number(message.relatedId) || Number(data.id),
      name: data.name || '已下架内容',
      price: data.price,
      image: data.image || ''
    }
  } catch (error) {
    return { kind: 'product', id: Number(message.relatedId), name: '已下架内容', price: null, image: '' }
  }
}

function goCardPage(message) {
  const data = cardData(message)
  router.push(data.kind === 'product' ? `/products/${data.id}` : `/services/${data.id}`)
}

async function maybeSendContextCard() {
  const card = props.contextCard
  if (!card || (card.kind !== 'product' && card.kind !== 'service')) return
  if (!props.conversation?.id) return
  if (messages.value.some((item) => (
    item.contentType === `${card.kind}_card` && Number(item.relatedId) === Number(card.id)
  ))) return
  try {
    const result = await api.post(`/conversations/${props.conversation.id}/messages`, {
      clientMessageId: `ctx_${card.kind}_${card.id}_${props.conversation.id}`,
      contentType: card.kind === 'product' ? 'product_card' : 'service_card',
      relatedType: card.kind,
      relatedId: card.id,
      content: JSON.stringify({ id: card.id, name: card.title, price: card.price, image: card.image })
    })
    if (result.data?.id && !messages.value.some((item) => item.id === result.data.id)) {
      messages.value.push(result.data)
      scrollBottom()
    }
  } catch (error) {
    // 卡片消息发送失败不影响正常聊天。
  }
}

watch(() => props.contextCard, () => {
  if (!loading.value) maybeSendContextCard()
})

async function loadOlder() {
  if (!hasMore.value || loadingOlder.value || !beforeId.value) return
  const previousHeight = box.value?.scrollHeight || 0
  const previousTop = box.value?.scrollTop || 0
  loadingOlder.value = true
  try {
    const result = await api.get(
      `/conversations/${props.conversation.id}/messages?limit=30&beforeId=${beforeId.value}`)
    const older = result.data?.messages || []
    messages.value = [...older, ...messages.value]
    hasMore.value = Boolean(result.data?.hasMore)
    beforeId.value = result.data?.nextBeforeId
    nextTick(() => {
      if (box.value) {
        box.value.scrollTop = previousTop + box.value.scrollHeight - previousHeight
      }
    })
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loadingOlder.value = false
  }
}

async function markRead() {
  if (!props.conversation?.id || !canMarkRead()) return
  const lastMessage = messages.value[messages.value.length - 1]
  try {
    const result = await api.put(`/conversations/${props.conversation.id}/read`, {
      lastMessageId: lastMessage?.id || null
    })
    unreadCount.value = 0
    conversationStore.setCount(result.data)
    emit('read', result.data)
  } catch (error) {
    // 已读失败不影响继续阅读，下一次打开会话时会重新同步。
  }
}

async function send() {
  const text = content.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    const result = await api.post(`/conversations/${props.conversation.id}/messages`, {
      clientMessageId: createMessageId(),
      contentType: 'text',
      content: text
    })
    if (!messages.value.some((item) => item.id === result.data?.id)) {
      messages.value.push(result.data)
    }
    content.value = ''
    scrollBottom()
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    sending.value = false
  }
}

function handleMessage(event) {
  const payload = event.detail
  if (Number(payload?.conversationId) !== Number(props.conversation.id)) return
  const message = payload.message
  if (!message || messages.value.some((item) => item.id === message.id)) return
  const shouldFollow = isNearBottom()
  messages.value.push(message)
  if (shouldFollow) scrollBottom()
  if (Number(message.senderId) !== Number(auth.user?.id) && shouldFollow && canMarkRead()) {
    markRead()
  }
}

function handleRead(event) {
  const payload = event.detail
  if (Number(payload?.conversationId) !== Number(props.conversation.id)) return
  if (Number(payload.readerId) !== Number(auth.user?.id)) {
    otherLastReadMessageId.value = Number(payload.lastReadMessageId || 0)
  }
}

function createMessageId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function canMarkRead() {
  return document.visibilityState === 'visible' && chatVisible.value && isNearBottom()
}

function isNearBottom() {
  if (!box.value) return false
  const distance = box.value.scrollHeight - box.value.scrollTop - box.value.clientHeight
  return distance < 80
}

function handleScroll() {
  if (canMarkRead()) markRead()
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && chatVisible.value) markRead()
}

async function deleteConversation() {
  if (!props.conversation?.id || deleting.value) return
  const confirmed = await ui.confirm({
    title: '删除对话',
    message: '删除后，本账号将不再显示该会话及此前消息；对方记录不受影响，新消息到达时会话会重新出现。',
    confirmText: '删除对话',
    danger: true
  })
  if (!confirmed) return
  deleting.value = true
  try {
    await api.delete(`/conversations/${props.conversation.id}`)
    await conversationStore.refresh()
    ui.toast('对话已删除', 'success')
    emit('deleted', props.conversation.id)
  } catch (error) {
    ui.toast(error.message || '删除对话失败', 'error')
  } finally {
    deleting.value = false
  }
}

function observeChatBox() {
  nextTick(() => {
    if (!box.value || !visibilityObserver) return
    visibilityObserver.disconnect()
    visibilityObserver.observe(box.value)
  })
}

function scrollBottom() {
  nextTick(() => {
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  })
}

watch(() => props.conversation?.id, () => {
  unreadCount.value = Number(props.conversation?.unreadCount || 0)
  loadMessages()
})
watch(() => props.conversation?.unreadCount, (value) => {
  unreadCount.value = Number(value || 0)
})
onMounted(() => {
  loadMessages()
  visibilityObserver = new IntersectionObserver((entries) => {
    chatVisible.value = entries.some((entry) => entry.isIntersecting)
    if (chatVisible.value && document.visibilityState === 'visible') markRead()
  }, { threshold: 0.25 })
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('pet-home:conversation-message', handleMessage)
  window.addEventListener('pet-home:conversation-read', handleRead)
})
onBeforeUnmount(() => {
  visibilityObserver?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('pet-home:conversation-message', handleMessage)
  window.removeEventListener('pet-home:conversation-read', handleRead)
})
</script>

<template>
  <section class="chat-panel panel">
    <header class="chat-header">
      <button v-if="showBack" class="icon-btn" type="button" aria-label="返回消息中心" @click="emit('back')">
        <ArrowLeft :size="18" />
      </button>
      <button class="peer-hit" type="button" :title="`查看 ${title} 的主页`" @click="openPeerProfile">
        <img
          v-if="peerAvatar && !avatarBroken"
          :src="assetUrl(peerAvatar)"
          alt=""
          @error="avatarBroken = true"
        />
        <span v-else class="chat-avatar"><MessageCircle :size="20" /></span>
      </button>
      <div class="peer-copy">
        <b class="peer-hit" @click="openPeerProfile">{{ title }}</b>
        <small v-if="showSubtitle">{{ subtitle }}</small>
      </div>
      <div class="chat-header-actions">
        <span v-if="unreadCount" class="chat-unread">{{ unreadCount }}</span>
        <button
          class="icon-btn chat-delete"
          type="button"
          :disabled="deleting || sending"
          :aria-label="deleting ? '正在删除对话' : '删除对话'"
          title="删除对话"
          @click="deleteConversation"
        >
          <Trash2 :size="17" />
        </button>
      </div>
    </header>
    <LoadingPanel v-if="loading" label="正在打开对话" />
    <div v-else ref="box" class="chat-messages" @scroll.passive="handleScroll">
      <button v-if="hasMore" class="load-older" type="button" :disabled="loadingOlder" @click="loadOlder">
        {{ loadingOlder ? '加载中…' : '加载更早消息' }}
      </button>
      <div v-if="!messages.length" class="chat-empty">
        <MessageCircle :size="26" />
        <p>还没有消息，礼貌地打个招呼吧。</p>
      </div>
      <article
        v-for="message in messages"
        :key="message.id || message.clientMessageId"
        class="chat-message"
        :class="{ mine: message.senderId === auth.user?.id }"
      >
        <button
          v-if="message.senderId !== auth.user?.id"
          class="msg-avatar"
          type="button"
          :aria-label="`查看 ${title} 的主页`"
          @click="openPeerProfile"
        >
          <img v-if="peerAvatar && !avatarBroken" :src="assetUrl(peerAvatar)" alt="" @error="avatarBroken = true" />
          <span v-else>{{ title.slice(0, 1) || '友' }}</span>
        </button>
        <button
          v-if="isCardMessage(message)"
          class="msg-card"
          type="button"
          @click="goCardPage(message)"
        >
          <img v-if="cardData(message).image" :src="assetUrl(cardData(message).image)" alt="" @error="$event.target.style.display = 'none'" />
          <span v-else class="msg-card-fallback"><MessageCircle :size="18" /></span>
          <span class="msg-card-copy">
            <small>{{ cardData(message).kind === 'product' ? '商品' : '服务' }}</small>
            <b>{{ cardData(message).name }}</b>
            <em v-if="cardData(message).price != null">¥{{ cardData(message).price }}</em>
          </span>
          <small class="msg-card-time">
            {{ relativeTime(message.createdAt) }}
            <template v-if="message.senderId === auth.user?.id && message.id <= otherLastReadMessageId"> · 已读</template>
          </small>
        </button>
        <div v-else>
          {{ message.content }}
          <small>
            {{ relativeTime(message.createdAt) }}
            <template v-if="message.senderId === auth.user?.id && message.id <= otherLastReadMessageId"> · 已读</template>
          </small>
        </div>
      </article>
    </div>
    <form class="chat-compose" @submit.prevent="send">
      <textarea v-model="content" maxlength="1000" rows="1" placeholder="输入消息…" />
      <button class="send-btn" type="submit" :disabled="sending || !content.trim()" :aria-label="sending ? '发送中' : '发送'">
        <Send :size="18" />
      </button>
    </form>

    <UserProfileDrawer
      :open="peerProfileOpen"
      :user-id="conversation.otherUserId"
      :nickname="title"
      :avatar="peerAvatar"
      @close="peerProfileOpen = false"
    />
  </section>
</template>

<style scoped>
.chat-panel { width: 100%; max-width: none; height: min(720px, calc(100vh - 140px)); margin: 0 auto; overflow: hidden; display: flex; flex-direction: column; }
.chat-header { min-height: 56px; padding: 8px 14px; display: flex; align-items: center; gap: 10px; border-bottom: 1px solid var(--color-border); background: var(--color-surface-warm); }
.chat-header img, .chat-avatar { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); box-shadow: 0 0 0 2px var(--color-white); }
.chat-header b, .chat-header small { display: block; }
.chat-header b { font-size: var(--font-size-base); }
.chat-header small { margin-top: 2px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.peer-hit { border: 0; padding: 0; background: transparent; cursor: pointer; display: inline-flex; align-items: center; }
.peer-copy .peer-hit { display: inline-block; text-align: left; }
.chat-header .peer-copy b.peer-hit { transition: color 0.16s ease; }
.chat-header .peer-copy b.peer-hit:hover { color: var(--color-primary); }
.peer-hit:hover img, .peer-hit:hover .chat-avatar { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.chat-header-actions { margin-left: auto; display: flex; align-items: center; gap: 7px; }
.chat-unread { min-width: 24px; height: 24px; padding: 0 7px; border-radius: 999px; display: grid; place-items: center; background: var(--color-danger); color: var(--color-white); font-size: var(--font-size-sm); }
.chat-delete { color: var(--color-text-muted); }
.chat-delete:hover:not(:disabled) { color: var(--color-danger); }
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px 12px;
  background:
    radial-gradient(circle at 20% 0%, rgba(255, 214, 130, 0.14), transparent 42%),
    radial-gradient(circle at 85% 100%, rgba(255, 214, 130, 0.12), transparent 40%),
    var(--color-surface-chat);
}
.load-older {
  display: block;
  margin: 0 auto 16px;
  padding: 5px 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-white);
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  transition: var(--transition-base);
}
.load-older:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary-strong); }
.load-older:disabled { opacity: 0.55; }
.chat-empty { text-align: center; color: var(--color-text-muted); padding: 46px 0; }
.chat-empty p { margin: 8px 0 0; font-size: var(--font-size-sm); }
.chat-message { display: flex; align-items: flex-end; gap: 6px; margin-bottom: 10px; }
.msg-avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  display: grid;
  place-items: center;
  font-size: 0.72rem;
  font-weight: var(--font-weight-bold);
  cursor: pointer;
  transition: box-shadow 0.16s ease;
}
.msg-avatar:hover { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.msg-avatar img { width: 100%; height: 100%; object-fit: cover; }
.chat-message.mine { justify-content: flex-end; }
.chat-message > div {
  max-width: 76%;
  padding: 8px 12px;
  border-radius: 18px 18px 18px 6px;
  background: var(--color-white);
  border: 1px solid var(--color-border-soft);
  box-shadow: 0 2px 8px rgba(92, 63, 14, 0.05);
  white-space: pre-wrap;
  font-size: var(--font-size-sm);
  line-height: 1.65;
}
.chat-message.mine > div {
  background: var(--gradient-primary);
  color: var(--color-text);
  border-color: transparent;
  box-shadow: 0 6px 14px var(--color-shadow-gold-soft);
  border-radius: 18px 18px 6px 18px;
}
.chat-message small { display: block; margin-top: 5px; color: var(--color-text-muted); font-size: 0.68rem; }
.chat-message.mine small { color: var(--color-text-secondary-translucent); text-align: right; }
.msg-card {
  max-width: 260px;
  display: grid;
  grid-template-columns: 56px 1fr;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 18px 18px 18px 6px;
  background: var(--color-white);
  box-shadow: 0 2px 8px rgba(92, 63, 14, 0.05);
  cursor: pointer;
  text-align: left;
  transition: var(--transition-base);
  position: relative;
}
.chat-message.mine .msg-card {
  border-radius: 18px 18px 6px 18px;
  background: var(--gradient-primary);
  border-color: transparent;
  box-shadow: 0 6px 14px var(--color-shadow-gold-soft);
}
.msg-card:hover { transform: translateY(-1px); }
.msg-card img, .msg-card-fallback { width: 56px; height: 56px; border-radius: 10px; object-fit: cover; }
.msg-card-fallback { display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.msg-card-copy { min-width: 0; display: grid; gap: 2px; }
.msg-card-copy small { margin: 0; font-size: 0.65rem; letter-spacing: 0.05em; color: var(--color-text-muted); }
.msg-card-copy b { font-size: var(--font-size-sm); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.msg-card-copy em { font-style: normal; color: var(--color-primary-strong); font-weight: var(--font-weight-bold); font-size: var(--font-size-sm); }
.msg-card-time { grid-column: 1 / -1; }
.chat-compose {
  padding: 8px 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: end;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}
.chat-compose textarea {
  resize: none;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  padding: 7px 11px;
  min-height: 36px;
  max-height: 110px;
  outline: 0;
  background: var(--color-white);
  font: inherit;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}
.chat-compose textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}
.send-btn {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--gradient-primary);
  color: var(--color-text);
  box-shadow: 0 6px 14px var(--color-shadow-gold-soft);
  transition: var(--transition-base);
}
.send-btn svg { width: 16px; height: 16px; }
.send-btn:hover:not(:disabled) { transform: translateY(-1px); }
.send-btn:disabled { opacity: 0.45; box-shadow: none; cursor: not-allowed; }
@media (max-width: 620px) {
  .chat-panel { height: calc(100vh - 130px); border-radius: 18px; }
  .chat-compose { grid-template-columns: 1fr; }
  .send-btn { width: 100%; height: 36px; border-radius: 10px; }
}
</style>
