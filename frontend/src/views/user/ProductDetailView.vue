<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, MessageCircle, Minus, Plus, ShoppingCart, Star } from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { formatPrice, parseJson } from '../../utils/format'
import { lifeStageLabel, parseHealthTags, targetPetLabel } from '../../utils/productPresentation'
import { useCartStore } from '../../stores/cart'
import ShopCatalogDialog from '../../components/ShopCatalogDialog.vue'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import PageBackButton from '../../components/PageBackButton.vue'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const ui = useUiStore()
const { requireUser } = useUserAccess()
const product = ref(null)
const loading = ref(true)
const quantity = ref(1)
const busy = ref(false)
const purchaseOpen = ref(false)
const addresses = ref([])
const selectedAddress = ref(0)

const available = computed(() => product.value?.status === 'on' && Number(product.value?.stock || 0) > 0)
const healthTagList = computed(() => parseHealthTags(product.value?.healthTags))
const addressText = computed(() => {
  const item = addresses.value[selectedAddress.value]
  if (!item) return ''
  return [item.name, item.phone, item.region || item.city, item.detail || item.address].filter(Boolean).join(' ')
})

async function load() {
  loading.value = true
  try {
    const result = await api.get(`/products/${route.params.id}`)
    product.value = result.data
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function addCart() {
  if (!await requireUser({ action: '加入购物车' })) return
  busy.value = true
  try {
    const result = await cartStore.addItem(product.value.id, quantity.value)
    ui.toast(result.msg || '已加入购物车', 'success')
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    busy.value = false
  }
}

async function openPurchase() {
  if (!await requireUser({ action: '购买商品' })) return
  try {
    const result = await api.get('/user/addresses')
    addresses.value = parseJson(result.data?.addresses, [])
    if (!addresses.value.length) {
      const confirmed = await ui.confirm({
        title: '缺少收货地址',
        message: '购买商品前需要先添加收货地址。',
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
    purchaseOpen.value = true
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function purchase() {
  if (!addressText.value || busy.value) return
  busy.value = true
  try {
    const result = await api.post('/orders/direct', {
      productId: product.value.id,
      quantity: quantity.value,
      shippingAddress: JSON.stringify(addresses.value[selectedAddress.value])
    })
    const orderId = result.data?.orderId
    if (!orderId) {
      throw new Error('订单创建失败，请稍后重试')
    }
    try {
      await api.put(`/orders/${orderId}/pay`)
    } catch (error) {
      purchaseOpen.value = false
      ui.toast('订单已创建，但支付未完成，请在订单中心继续处理', 'warning')
      await router.replace({
        name: 'me',
        query: { tab: 'orders', status: 'pending_payment' }
      })
      return
    }
    purchaseOpen.value = false
    ui.toast('购买成功，订单已支付', 'success')
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

const shopCatalogOpen = ref(false)

async function contactShop() {
  if (!await requireUser({ action: '联系商家' })) return
  if (!product.value?.shopOwnerId) {
    ui.toast('暂时无法联系商家', 'warning')
    return
  }
  router.push({ path: `/messages/${product.value.shopOwnerId}`, query: { category: 'mall', type: 'customer_service', productId: product.value.id } })
}

function step(delta) {
  quantity.value = Math.max(1, Math.min(quantity.value + delta, Number(product.value?.stock || 1)))
}

onMounted(load)
</script>

<template>
  <div class="page-layout">
    <div class="detail-back">
      <PageBackButton fallback-path="/mall" aria-label="返回商城" />
    </div>
    <LoadingPanel v-if="loading" label="正在打开商品" />
    <EmptyState v-else-if="!product" title="商品不存在" description="它可能已经下架。" />
    <template v-else>
      <section class="detail-layout">
        <div class="detail-image panel">
          <img v-if="product.imageUrl" :src="assetUrl(product.imageUrl)" :alt="product.name" />
          <span v-else>{{ product.name?.slice(0, 1) }}</span>
        </div>
        <div class="detail-info">
          <p class="eyebrow">{{ product.categoryName || product.category }}</p>
          <h1>{{ product.name }}</h1>
          <div class="detail-shop">
            <button
              v-if="product.shopId"
              class="shop-link"
              type="button"
              title="查看店铺全部商品"
              @click="shopCatalogOpen = true"
            >
              <img v-if="product.shopLogo" class="shop-logo" :src="assetUrl(product.shopLogo)" alt="" />
              <span v-else class="shop-logo shop-logo-fallback">{{ (product.shopName || '店').slice(0, 1) }}</span>
              <span class="shop-name">{{ product.shopName }}</span>
              <ArrowRight :size="13" class="shop-arrow" />
            </button>
            <span v-else>{{ product.shopName }}</span>
            <span v-if="product.shopRating"><Star :size="13" />{{ product.shopRating }}</span>
          </div>
          <p class="detail-description">{{ product.description || '商品详情暂未补充。' }}</p>
          <div class="care-summary" aria-label="商品养护属性">
            <span>{{ targetPetLabel(product.targetPet) }}</span>
            <span>{{ lifeStageLabel(product.lifeStage) }}</span>
            <span v-for="tag in healthTagList" :key="tag">{{ tag }}</span>
          </div>
          <div class="price-block">
            <strong>¥{{ formatPrice(product.price) }}</strong>
            <span>库存 {{ product.stock ?? 0 }}</span>
          </div>
          <div class="quantity-block">
            <span>数量</span>
            <button type="button" :disabled="quantity <= 1" @click="step(-1)"><Minus :size="15" /></button>
            <b>{{ quantity }}</b>
            <button type="button" :disabled="quantity >= (product.stock || 1)" @click="step(1)"><Plus :size="15" /></button>
          </div>
          <div class="detail-actions">
            <button class="btn btn-ghost" type="button" :disabled="!available || busy" @click="addCart">
              <ShoppingCart :size="17" />加入购物车
            </button>
            <button class="btn btn-primary" type="button" :disabled="!available || busy" @click="openPurchase">立即购买</button>
            <button class="btn btn-ghost" type="button" :disabled="busy" @click="contactShop">
              <MessageCircle :size="17" />联系客服
            </button>
          </div>
          <p v-if="!available" class="unavailable">商品暂时不可购买</p>
          <dl class="detail-facts">
            <div><dt>店铺</dt><dd>{{ product.shopName || '--' }}</dd></div>
            <div><dt>营业时间</dt><dd>{{ product.shopBusinessHours || '--' }}</dd></div>
            <div><dt>店铺地址</dt><dd>{{ product.shopAddress || '--' }}</dd></div>
          </dl>
        </div>
      </section>
    </template>

    <ModalDialog
      :open="purchaseOpen"
      title="确认收货信息"
      width="520px"
      :close-disabled="busy"
      @close="purchaseOpen = false"
    >
      <div class="field">
        <label>收货地址</label>
        <select v-model="selectedAddress" class="select">
          <option v-for="(item, index) in addresses" :key="index" :value="index">
            {{ [item.name, item.phone, item.region || item.city, item.detail || item.address].filter(Boolean).join(' ') }}
          </option>
        </select>
      </div>
      <div class="order-summary">
        <span>{{ product?.name }} × {{ quantity }}</span>
        <strong>¥{{ formatPrice(Number(product?.price || 0) * quantity) }}</strong>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="purchaseOpen = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="purchase">
          {{ busy ? '正在下单…' : '确认并支付' }}
        </button>
      </template>
    </ModalDialog>

    <ShopCatalogDialog
      :open="shopCatalogOpen"
      :shop-id="product?.shopId"
      :shop-name="product?.shopName || '本店'"
      product-type="goods"
      @close="shopCatalogOpen = false"
    />
  </div>
</template>

<style scoped>
.detail-back { margin-bottom: 20px; }
.detail-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(340px, .9fr); gap: 36px; align-items: start; }
.detail-image { min-height: 520px; overflow: hidden; display: grid; place-items: center; background: linear-gradient(135deg, var(--color-primary-soft), var(--color-surface-accent)); }
.detail-image img { width: 100%; height: 100%; max-height: 620px; object-fit: cover; }
.detail-image > span { font-family: var(--font-family-display); font-size: 90px; color: var(--color-primary); }
.detail-info { padding-top: 12px; }
.detail-info h1 { font-size: var(--font-size-detail-title); margin-bottom: 10px; }
.detail-shop { display: flex; gap: 10px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.detail-shop span { display: flex; gap: 4px; align-items: center; }
.shop-link {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 2px 6px 2px 3px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary-strong);
  font: inherit;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease;
}
.shop-link:hover { border-color: var(--color-primary-soft); background: var(--color-primary-soft); }
.shop-logo { width: 22px; height: 22px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border-soft); }
.shop-logo-fallback { display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: 0.72rem; font-weight: var(--font-weight-bold); }
.shop-name { border-bottom: 1px dashed var(--color-primary); }
.shop-link:hover { color: var(--color-primary); }
.shop-link svg { color: var(--color-text-muted); transition: transform 0.16s ease, color 0.16s ease; }
.shop-link:hover svg { color: var(--color-primary); transform: translateX(2px); }
.detail-description { margin: 25px 0; color: var(--color-text-secondary); line-height: 1.8; }
.care-summary { display: flex; flex-wrap: wrap; gap: 6px; margin: -9px 0 20px; }
.care-summary span { padding: 4px 9px; border: 1px solid var(--color-border-primary-subtle); border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.price-block { padding: 17px 0; display: flex; justify-content: space-between; align-items: baseline; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.price-block strong { color: var(--color-primary-strong); font-family: var(--font-family-display); font-size: var(--font-size-5xl); }
.price-block span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.quantity-block { display: flex; align-items: center; gap: 12px; margin: 22px 0; }
.quantity-block > span { color: var(--color-text-muted); font-size: var(--font-size-sm); margin-right: 6px; }
.quantity-block button { width: 34px; height: 34px; border: 1px solid var(--color-border); background: var(--color-white); border-radius: 10px; display: grid; place-items: center; }
.detail-actions { display: flex; gap: 8px; }
.detail-actions .btn-primary { flex: 1; }
.unavailable { color: var(--color-danger); font-size: var(--font-size-sm); }
.detail-facts { margin: 28px 0 0; padding: 0; display: grid; gap: 11px; }
.detail-facts div { display: flex; justify-content: space-between; gap: 16px; border-bottom: 1px dashed var(--color-border); padding-bottom: 10px; }
.detail-facts dt { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.detail-facts dd { margin: 0; text-align: right; font-size: var(--font-size-sm); }
.order-summary { padding: 16px 0 0; display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--color-border); }
.order-summary strong { color: var(--color-primary-strong); font-size: var(--font-size-xl); }
@media (max-width: 820px) {
  .detail-layout { grid-template-columns: 1fr; }
  .detail-image { min-height: 340px; }
}
@media (max-width: 480px) {
  .detail-actions { flex-wrap: wrap; }
  .detail-actions .btn-ghost, .detail-actions .btn-primary { flex: 1; min-width: 130px; }
}
</style>
