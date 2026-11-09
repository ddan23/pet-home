import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '../services/api'

export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  async function refresh() {
    try {
      const result = await api.get('/cart/count')
      count.value = Math.max(0, Number(result.data || 0))
    } catch (error) {
      count.value = 0
    }
  }

  async function addItem(productId, quantity) {
    const result = await api.post('/cart', { productId, quantity })
    await refresh()
    return result
  }

  function syncFromItems(items) {
    count.value = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  }

  function reset() {
    count.value = 0
  }

  return { count, refresh, addItem, syncFromItems, reset }
})
