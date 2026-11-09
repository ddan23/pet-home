import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'

export const useConversationStore = defineStore('conversations', () => {
  const unreadCount = ref(0)
  const connected = ref(false)
  const reconnecting = ref(false)
  let source = null

  async function refresh() {
    try {
      const result = await api.get('/conversations/unread')
      unreadCount.value = Number(result.data || 0)
    } catch (error) {
      // 顶部未读徽标失败不阻断页面使用，打开会话时会重新同步。
      unreadCount.value = 0
    }
  }

  function connect() {
    if (source) return
    const url = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api/v1/conversations/stream`
    source = new EventSource(url, { withCredentials: true })
    source.onopen = () => {
      connected.value = true
      reconnecting.value = false
    }
    source.addEventListener('connected', () => {
      connected.value = true
      reconnecting.value = false
      refresh()
    })
    source.addEventListener('heartbeat', () => {
      connected.value = true
      reconnecting.value = false
    })
    source.addEventListener('conversation-message', (event) => {
      try {
        const payload = JSON.parse(event.data)
        refresh()
        window.dispatchEvent(new CustomEvent('pet-home:conversation-message', {
          detail: payload
        }))
      } catch (error) {
        // 忽略格式异常事件，继续保留 SSE 连接。
      }
    })
    source.addEventListener('conversation-read', (event) => {
      try {
        const payload = JSON.parse(event.data)
        window.dispatchEvent(new CustomEvent('pet-home:conversation-read', {
          detail: payload
        }))
      } catch (error) {
        // 已读回执属于非关键增强，格式异常时等待接口重新同步。
      }
    })
    source.addEventListener('conversation-deleted', (event) => {
      try {
        const payload = JSON.parse(event.data)
        window.dispatchEvent(new CustomEvent('pet-home:conversation-deleted', {
          detail: payload
        }))
      } catch (error) {
        // 删除同步失败时由页面主动刷新兜底。
      }
    })
    source.onerror = () => {
      connected.value = false
      reconnecting.value = true
    }
  }

  function disconnect() {
    if (source) source.close()
    source = null
    connected.value = false
    reconnecting.value = false
  }

  function setCount(count) {
    unreadCount.value = Math.max(0, Number(count || 0))
  }

  return {
    unreadCount,
    connected,
    reconnecting,
    refresh,
    connect,
    disconnect,
    setCount
  }
})
