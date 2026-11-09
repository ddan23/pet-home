<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Minus, Plus, ShoppingBag, Trash2 } from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { formatPrice, parseJson } from '../../utils/format'
import { useUserAccess } from '../../composables/useUserAccess'
import { useCartStore } from '../../stores/cart'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import PageBackButton from '../../components/PageBackButton.vue'

const router = useRouter()
const route = useRoute()
const { isUser } = useUserAccess()
const cartStore = useCartStore()
const ui = useUiStore()
const items = ref([])
const loading = ref(false)
const checkoutOpen = ref(false)
const addresses = ref([])
const selectedAddress = ref(0)
const busy = ref(false)

function isAvailable(item) {
  return item.available ?? item.productStatus !== 'off'
}

const checkedItems = computed(() => items.value.filter(
  (item) => item.checked && isAvailable(item)))
const total = computed(() => checkedItems.value.reduce(
  (sum, item) => sum + Number(item.productPrice || 0) * item.quantity, 0))
const availableItems = computed(() => items.value.filter(isAvailable))
const unavailableCount = computed(() => items.value.filter(
  (item) => !isAvailable(item)).length)
const allChecked = computed(() => availableItems.value.length > 0
  && availableItems.value.every((item) => item.checked))
const grouped = computed(() => {
  const result = new Map()
  for (const item of items.value) {
    if (!result.has(item.shopId)) result.set(item.shopId, { name: item.shopName, items: [] })
    result.get(item.shopId).items.push(item)
  }
  return [...result.entries()].map(([id, value]) => ({ id, ...value }))
})

async function load() {
  if (!isUser.value) return
  loading.value = true
  try {
    const result = await api.get('/cart')
    if (!isUser.value) return
    items.value = result.data || []
    cartStore.syncFromItems(items.value)
  } catch (error) {
    if (isUser.value) ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function resetPrivateState() {
  items.value = []
  addresses.value = []
  selectedAddress.value = 0
  checkoutOpen.value = false
  loading.value = false
  cartStore.reset()
}

async function toggleAll() {
  try {
    await api.put('/cart/checkAll', { checked: !allChecked.value })
    await load()
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function toggleItem(item) {
  try {
    await api.put(`/cart/${item.id}/check`, { checked: !item.checked })
    item.checked = !item.checked
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function toggleShop(shop) {
  const target = !shopAllChecked(shop)
  try {
    await api.put(`/cart/checkByShop/${shop.id}`, { checked: target })
    shop.items.forEach((item) => {
      if (isAvailable(item)) item.checked = target
    })
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

function shopAllChecked(shop) {
  const available = shop.items.filter((item) => item.productStatus !== 'off')
  return available.length > 0 && available.every((item) => item.checked && isAvailable(item))
}

async function updateQuantity(item, delta) {
  const next = Math.max(1, item.quantity + delta)
  if (next === item.quantity) return
  try {
    await api.put(`/cart/${item.id}/quantity`, { quantity: next })
    item.quantity = next
    cartStore.syncFromItems(items.value)
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function remove(item) {
  const confirmed = await ui.confirm({ message: '确定从购物车删除这件商品吗？', danger: true })
  if (!confirmed) return
  try {
    await api.delete(`/cart/${item.id}`)
    items.value = items.value.filter((entry) => entry.id !== item.id)
    cartStore.syncFromItems(items.value)
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function openCheckout() {
  if (!checkedItems.value.length) {
    ui.toast('请选择要结算的商品', 'warning')
    return
  }
  try {
    const result = await api.get('/user/addresses')
    addresses.value = parseJson(result.data?.addresses, [])
    if (!addresses.value.length) {
      const confirmed = await ui.confirm({
        title: '缺少收货地址',
        message: '提交订单前需要先添加收货地址。',
        confirmText: '去添加地址'
      })
      if (confirmed) {
        await router.push({
          name: 'me',
          query: { tab: 'addresses', return: route.fullPath }
        })
      }
      return
    }
    selectedAddress.value = Math.max(0, addresses.value.findIndex((item) => item.isDefault))
    checkoutOpen.value = true
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function checkout() {
  if (!addresses.value[selectedAddress.value]) return
  busy.value = true
  try {
    const result = await api.post('/orders', {
      shippingAddress: JSON.stringify(addresses.value[selectedAddress.value])
    })
    const orderIds = result.data?.orderIds || []
    const skippedCount = Number(result.data?.skippedCount || 0)
    if (!orderIds.length) {
      throw new Error('订单创建失败，请稍后重试')
    }
    let paymentFailed = false
    for (const id of orderIds) {
      try {
        await api.put(`/orders/${id}/pay`)
      } catch (error) {
        paymentFailed = true
      }
    }
    checkoutOpen.value = false
    if (paymentFailed) {
      ui.toast('订单已创建，部分订单支付未完成，请在订单中心继续处理', 'warning')
      await router.replace({ name: 'me', query: { tab: 'orders' } })
      return
    }
    ui.toast(
      skippedCount > 0
        ? `下单成功，${skippedCount} 件失效商品未参与结算`
        : '下单成功，订单已支付',
      skippedCount > 0 ? 'warning' : 'success'
    )
    await router.replace({
      name: 'me',
      query: { tab: 'orders', status: 'paid' }
    })
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  if (isUser.value) load()
})

watch(isUser, (allowed) => {
  if (allowed) {
    load()
  } else {
    resetPrivateState()
  }
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <div class="cart-page-title">
        <PageBackButton fallback-path="/mall" aria-label="返回商城" />
        <h1>购物车</h1>
      </div>
    </header>
    <LoadingPanel v-if="loading" label="正在整理购物车" />
    <EmptyState v-else-if="!items.length" title="购物车还是空的">
      <RouterLink class="btn btn-primary" to="/mall">去逛商城</RouterLink>
    </EmptyState>
    <template v-else>
      <section class="cart-layout">
        <div class="cart-list">
          <div class="cart-select-all panel">
            <label><input type="checkbox" :checked="allChecked" :disabled="!availableItems.length" @change="toggleAll" /><span>全选</span></label>
            <span>共 {{ items.length }} 件</span>
          </div>
          <article v-for="shop in grouped" :key="shop.id" class="shop-cart panel">
            <header>
              <label>
                <input
                  type="checkbox"
                  :checked="shopAllChecked(shop)"
                  :disabled="!shop.items.some(isAvailable)"
                  @change="toggleShop(shop)"
                />
                <b>{{ shop.name || '店铺' }}</b>
              </label>
            </header>
            <div v-for="item in shop.items" :key="item.id" class="cart-item">
              <input
                type="checkbox"
                :checked="item.checked && isAvailable(item)"
                :disabled="!isAvailable(item)"
                aria-label="选择商品"
                @change="toggleItem(item)"
              />
              <RouterLink class="cart-image" :to="`/mall/${item.productId}`">
                <img v-if="item.productImage" :src="assetUrl(item.productImage)" :alt="item.productName" />
                <ShoppingBag v-else :size="22" />
              </RouterLink>
              <div class="cart-info">
                <RouterLink :to="`/mall/${item.productId}`">{{ item.productName }}</RouterLink>
                <span>{{ isAvailable(item) ? item.shopName : item.unavailableReason || '商品不可售' }}</span>
                <strong>¥{{ formatPrice(item.productPrice) }}</strong>
              </div>
              <div class="cart-quantity">
                <button type="button" :disabled="!isAvailable(item) || item.quantity <= 1" @click="updateQuantity(item, -1)"><Minus :size="14" /></button>
                <b>{{ item.quantity }}</b>
                <button type="button" :disabled="!isAvailable(item)" @click="updateQuantity(item, 1)"><Plus :size="14" /></button>
              </div>
              <button class="cart-delete" type="button" aria-label="删除商品" @click="remove(item)"><Trash2 :size="17" /></button>
            </div>
          </article>
        </div>
        <aside class="cart-summary panel">
          <h2>订单小计</h2>
          <div><span>已选商品</span><b>{{ checkedItems.length }} 件</b></div>
          <div><span>商品金额</span><b>¥{{ formatPrice(total) }}</b></div>
          <div class="cart-total"><span>合计</span><strong>¥{{ formatPrice(total) }}</strong></div>
          <button class="btn btn-primary" type="button" :disabled="!checkedItems.length" @click="openCheckout">
            去结算
          </button>
          <p v-if="unavailableCount" class="unavailable-note">
            {{ unavailableCount }} 件失效商品不会参与本次结算
          </p>
          <p>提交订单后将按店铺拆分，并完成模拟支付。</p>
        </aside>
      </section>
    </template>

    <ModalDialog
      :open="checkoutOpen"
      title="确认收货地址"
      width="520px"
      :close-disabled="busy"
      @close="checkoutOpen = false"
    >
      <label class="field">
        <span>收货地址</span>
        <select v-model="selectedAddress" class="select">
          <option v-for="(address, index) in addresses" :key="index" :value="index">
            {{ [address.name, address.phone, address.region, address.detail || address.address].filter(Boolean).join(' ') }}
          </option>
        </select>
      </label>
      <div class="summary-lines">
        <span>{{ checkedItems.length }} 件商品</span>
        <strong>¥{{ formatPrice(total) }}</strong>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="checkoutOpen = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="checkout">{{ busy ? '正在提交…' : '确认并支付' }}</button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.cart-page-title { display: flex; align-items: center; gap: 14px; }
.cart-layout { display: grid; grid-template-columns: 1fr 340px; gap: 20px; align-items: start; }
.cart-list { display: grid; gap: 13px; }
.cart-select-all { min-height: 54px; padding: 0 18px; display: flex; justify-content: space-between; align-items: center; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.cart-select-all label, .shop-cart header label { display: flex; align-items: center; gap: 9px; }
.shop-cart { overflow: hidden; }
.shop-cart header { padding: 15px 18px; border-bottom: 1px solid var(--color-border); background: var(--color-surface-warm); }
.cart-item { display: grid; grid-template-columns: auto 84px 1fr auto auto; gap: 14px; align-items: center; padding: 16px 18px; border-bottom: 1px solid var(--color-border); }
.cart-item:last-child { border-bottom: 0; }
.cart-image { width: 84px; height: 76px; border-radius: 13px; overflow: hidden; background: var(--color-primary-soft); display: grid; place-items: center; color: var(--color-primary-strong); }
.cart-image img { width: 100%; height: 100%; object-fit: cover; }
.cart-info { min-width: 0; }
.cart-info a { display: block; font-weight: var(--font-weight-extra-bold); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cart-info span { display: block; color: var(--color-text-muted); font-size: var(--font-size-sm); margin: 3px 0; }
.cart-info strong { color: var(--color-primary-strong); }
.cart-quantity { display: flex; align-items: center; border: 1px solid var(--color-border); border-radius: 9px; overflow: hidden; }
.cart-quantity button { width: 30px; height: 32px; border: 0; background: var(--color-white); display: grid; place-items: center; }
.cart-quantity b { min-width: 30px; text-align: center; font-size: var(--font-size-sm); }
.cart-delete { border: 0; background: transparent; color: var(--color-text-muted); padding: 7px; }
.cart-summary { position: sticky; top: 100px; padding: 22px; }
.cart-summary h2 { font-size: var(--font-size-3xl); }
.cart-summary > div { display: flex; justify-content: space-between; padding: 8px 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.cart-total { margin-top: 12px; padding: 16px 0 !important; border-top: 1px solid var(--color-border); align-items: baseline; }
.cart-total strong { color: var(--color-primary-strong); font-family: var(--font-family-display); font-size: var(--font-size-3xl); }
.cart-summary .btn { width: 100%; margin-top: 10px; }
.cart-summary > p { margin: 12px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; }
.summary-lines { display: flex; justify-content: space-between; padding: 16px 0; border-top: 1px solid var(--color-border); margin-top: 10px; }
.summary-lines strong { color: var(--color-primary-strong); font-size: var(--font-size-2xl); }
@media (max-width: 850px) {
  .cart-layout { grid-template-columns: 1fr; }
  .cart-summary { position: static; }
}
@media (max-width: 600px) {
  .cart-page-title { align-items: flex-start; flex-direction: column; gap: 8px; }
  .cart-item { grid-template-columns: auto 64px 1fr auto; }
  .cart-image { width: 64px; height: 64px; }
  .cart-quantity { grid-column: 3 / 4; justify-self: start; }
  .cart-delete { grid-column: 4; grid-row: 1; }
}
</style>
