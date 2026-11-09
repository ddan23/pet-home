<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../services/api'
import { useUserAccess } from '../../composables/useUserAccess'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import PageBackButton from '../../components/PageBackButton.vue'
import ConversationChat from '../../components/ConversationChat.vue'

const route = useRoute()
const router = useRouter()
const { isUser } = useUserAccess()
const ui = useUiStore()
const conversation = ref(null)
const loading = ref(false)
const contextCard = ref(null)
const category = String(route.query.category || 'community')
const type = String(route.query.type || (
  category === 'mall' ? 'customer_service' : category === 'service' ? 'consult' : 'private_letter'))

// 咨询来源上下文：商品 / 服务（用于自动发送一次卡片消息；社区不携带帖子上下文）
async function loadContext() {
  const productId = Number(route.query.productId)
  const serviceId = Number(route.query.serviceId)
  try {
    if (productId) {
      const result = await api.get(`/products/${productId}`)
      const item = result.data
      if (item) {
        contextCard.value = {
          kind: 'product', id: item.id, title: item.name, image: item.imageUrl,
          price: item.price, shopId: item.shopId, shopName: item.shopName,
          shopLogo: item.shopLogo || ''
        }
      }
    } else if (serviceId) {
      const result = await api.get(`/services/${serviceId}`)
      const item = result.data
      if (item) {
        contextCard.value = {
          kind: 'service', id: item.id, title: item.name, image: item.imageUrl,
          price: item.price, shopId: item.shopId, shopName: item.shopName,
          shopLogo: item.shopLogo || ''
        }
      }
    }
  } catch (error) {
    contextCard.value = null
  }
}

async function load() {
  if (!isUser.value) return
  loading.value = true
  try {
    if (route.query.conversationId) {
      const result = await api.get(`/conversations/${route.query.conversationId}`)
      conversation.value = result.data
    } else {
      const result = await api.post('/conversations', {
        targetUserId: Number(route.params.userId),
        category,
        type
      })
      conversation.value = result.data
    }
  } catch (error) {
    if (isUser.value) ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function handleRead() {
  if (!isUser.value) return
  if (conversation.value) {
    conversation.value = { ...conversation.value, unreadCount: 0 }
  }
}

function handleDeleted(conversationId) {
  if (Number(conversationId) !== Number(conversation.value?.id)) return
  conversation.value = null
  router.replace('/messages')
}

function handleRealtimeDeleted(event) {
  handleDeleted(event.detail?.conversationId)
}

function resetPrivateState() {
  conversation.value = null
  loading.value = false
}

onMounted(() => {
  if (isUser.value) {
    load()
    loadContext()
  }
  window.addEventListener('pet-home:conversation-deleted', handleRealtimeDeleted)
})

watch(isUser, (allowed) => {
  if (allowed) {
    load()
  } else {
    resetPrivateState()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pet-home:conversation-deleted', handleRealtimeDeleted)
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <div class="chat-page-title">
        <PageBackButton fallback-path="/messages" aria-label="返回消息中心" />
        <h1>会话</h1>
      </div>
    </header>
    <LoadingPanel v-if="loading" label="正在打开对话" />
    <EmptyState v-else-if="!conversation" title="无法打开会话" description="会话对象不存在或暂时不可用。" />
    <ConversationChat
      v-else
      :conversation="conversation"
      :context-card="contextCard"
      @read="handleRead"
      @deleted="handleDeleted"
    />
  </div>
</template>

<style scoped>
/* 聊天页整体锁定一屏：页面不滚动，只有消息区滚动（84px 顶部导航 + 上下留白） */
.page-layout {
  height: calc(100dvh - 110px);
  display: flex;
  flex-direction: column;
  padding-top: 14px;
  padding-bottom: 10px;
  overflow: hidden;
}
.chat-page-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-layout :deep(.page-heading) {
  margin-bottom: 10px;
  align-items: center;
  flex-wrap: nowrap;
}
.page-layout :deep(.page-heading h1) { font-size: 1.2rem; }
.page-layout :deep(.chat-panel) {
  flex: 1;
  min-height: 380px;
  height: auto;
  margin: 0;
}
@media (max-width: 620px) {
  .page-layout { height: 100dvh; padding-top: 10px; }
  .page-layout :deep(.chat-panel) { min-height: 320px; }
}
</style>
