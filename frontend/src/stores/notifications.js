import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'
import { useUiStore } from './ui'

export const useNotificationStore = defineStore('notifications', () => {
  const unreadCount = ref(0)
  const connected = ref(false)
  const reconnecting = ref(false)
  let source = null

  async function refresh() {
    try {
      const result = await api.get('/notifications/unread')
      unreadCount.value = Number(result.data || 0)
    } catch (error) {
      // 通知数量失败不应阻断页面，下一次重连会再次同步。
      unreadCount.value = 0
    }
  }

  function connect() {
    if (source) return
    const url = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api/v1/notifications/stream`
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
    source.addEventListener('notification', (event) => {
      try {
        const notification = JSON.parse(event.data)
        unreadCount.value += 1
        useUiStore().toast(notification.title || '收到新通知', notification.level === 'warning' ? 'warning' : 'info')
        window.dispatchEvent(new CustomEvent('pet-home:notification', { detail: notification }))
      } catch (error) {
        // 忽略格式异常的 SSE 数据，避免中断后续通知连接。
      }
    })
    source.addEventListener('notification-read', () => {
      refresh()
      window.dispatchEvent(new CustomEvent('pet-home:notification-sync'))
    })
    source.addEventListener('notification-read-all', () => {
      refresh()
      window.dispatchEvent(new CustomEvent('pet-home:notification-sync'))
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

  function decrement(amount = 1) {
    unreadCount.value = Math.max(0, unreadCount.value - amount)
  }

  function clear() {
    unreadCount.value = 0
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
    decrement,
    clear,
    setCount
  }
})
