<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, ShoppingCart } from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { useCartStore } from '../../stores/cart'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import CatalogCategoryRail from '../../components/CatalogCategoryRail.vue'
import MallProductCard from '../../components/MallProductCard.vue'
import BackToTopButton from '../../components/BackToTopButton.vue'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'

const categoryTree = ref([])
const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const ui = useUiStore()
const { isUser, requireUser } = useUserAccess()
const categoryCode = ref(String(route.query.categoryCode || ''))
const keyword = ref('')

const page = ref(0)
const pageSize = 12
const list = ref([])
const totalPages = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const loadSentinel = ref(null)
let loadObserver = null
let requestSequence = 0
const categories = computed(() => [{
  code: '',
  name: '全部'
}, ...categoryTree.value.map(({ code, name }) => ({ code, name }))])
const showCartEntry = true
const hasMore = computed(() => page.value < totalPages.value)

async function loadPage({ reset = false } = {}) {
  if (!reset && (loading.value || loadingMore.value || !hasMore.value)) return
  const requestId = ++requestSequence
  const targetPage = reset ? 1 : page.value + 1
  if (reset) {
    loading.value = true
    loadingMore.value = false
  } else {
    loadingMore.value = true
  }
  try {
    const params = { page: targetPage, pageSize }
    if (categoryCode.value) params.categoryCode = categoryCode.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const result = await api.get(`/products${queryString(params)}`)
    if (requestId !== requestSequence) return
    const nextItems = result.data?.list || []
    if (reset) {
      list.value = nextItems
    } else {
      const existingIds = new Set(list.value.map((item) => item.id))
      list.value = [
        ...list.value,
        ...nextItems.filter((item) => !existingIds.has(item.id))
      ]
    }
    page.value = targetPage
    totalPages.value = result.data?.totalPages || 0
  } catch (error) {
    if (requestId === requestSequence) {
      ui.toast(error.message, 'error')
    }
  } finally {
    if (requestId === requestSequence) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function loadCategories() {
  try {
    const result = await api.get('/categories?productType=goods')
    categoryTree.value = result.data || []
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

function search() {
  requestSequence += 1
  list.value = []
  page.value = 0
  totalPages.value = 0
  loadPage({ reset: true })
}

async function openCart() {
  if (!await requireUser({ action: '查看购物车' })) return
  router.push('/cart')
}

async function addToCart(product) {
  if (!await requireUser({ action: '加入购物车' })) return
  try {
    await cartStore.addItem(product.id, 1)
    ui.toast('已加入购物车', 'success')
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

watch(categoryCode, search)
watch(() => route.query.categoryCode, (value) => {
  categoryCode.value = String(value || '')
})
watch(loadSentinel, (element, previous) => {
  if (previous) loadObserver?.unobserve(previous)
  if (element) loadObserver?.observe(element)
}, { flush: 'post' })
onMounted(async () => {
  loadObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) loadPage()
  }, { rootMargin: '320px 0px' })
  await Promise.all([loadCategories(), loadPage({ reset: true })])
  if (isUser.value) {
    await cartStore.refresh()
  } else {
    cartStore.reset()
  }
})
onBeforeUnmount(() => {
  loadObserver?.disconnect()
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <h1>宠物商城</h1>
      <div class="mall-heading-tools">
        <form class="mall-search" @submit.prevent="search">
          <Search :size="18" />
          <input v-model="keyword" placeholder="搜索商品或店铺" aria-label="搜索商品或店铺" />
          <button class="btn btn-primary btn-sm" type="submit">搜索</button>
        </form>
        <button
          v-if="showCartEntry"
          class="mall-cart-entry"
          type="button"
          aria-label="打开购物车"
          @click="openCart"
        >
          <ShoppingCart :size="20" />
          <span>购物车</span>
          <b v-if="cartStore.count">{{ cartStore.count > 99 ? '99+' : cartStore.count }}</b>
        </button>
      </div>
    </header>

    <div class="page-layout-split">
      <CatalogCategoryRail
        title="商品分类"
        :categories="categories"
        :active-code="categoryCode"
        @select="categoryCode = $event"
      />

      <div class="mall-main">
        <LoadingPanel v-if="loading" label="正在寻找合适的商品" />
        <EmptyState v-else-if="!list.length" title="没有找到相关商品" description="换一个关键词或分类再试试。" />
        <template v-else>
          <div v-if="list.length" class="mall-product-grid">
            <MallProductCard
              v-for="product in list"
              :key="product.id"
              :product="product"
              :show-add="showCartEntry"
              @add="addToCart"
            />
          </div>
          <div v-if="hasMore" ref="loadSentinel" class="infinite-status">
            <span v-if="loadingMore" class="spinner" />
            <span>{{ loadingMore ? '正在加载更多' : '继续下滑加载更多' }}</span>
          </div>
          <p v-else class="infinite-status">已显示全部商品</p>
        </template>
      </div>
    </div>
    <BackToTopButton />
  </div>
</template>

<style scoped>
.mall-heading-tools { flex: 1 1 420px; min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: nowrap; }
.mall-search { flex: 1 1 auto; min-width: 0; min-height: 48px; display: flex; align-items: center; gap: 9px; background: var(--color-white); border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 6px 5px 16px; }

.mall-search svg { color: var(--color-text-muted); flex-shrink: 0; }
.mall-search input { flex: 1; border: 0; outline: 0; background: transparent; min-width: 0; }
.mall-cart-entry { position: relative; min-height: 48px; padding: 8px 16px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); color: var(--color-text); display: inline-flex; align-items: center; gap: 7px; flex: 0 0 auto; font-size: var(--font-size-sm); font-weight: var(--font-weight-black); box-shadow: var(--box-shadow-sm); }
.mall-cart-entry:hover { border-color: var(--color-primary); background: var(--color-primary-soft); }
.mall-cart-entry b { min-width: 24px; height: 24px; padding: 0 6px; border-radius: 999px; display: inline-grid; place-items: center; background: var(--color-accent); color: var(--color-white); font-size: var(--font-size-sm); }
.mall-main { min-width: 0; }
.mall-product-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
@media (min-width: 1380px) {
  .mall-product-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 1040px) {
  .mall-product-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 680px) {
  .mall-heading-tools { align-self: stretch; gap: 8px; }
  .mall-search { padding-left: 12px; }
  .mall-cart-entry { padding-inline: 12px; justify-content: center; }
  .mall-product-grid { grid-template-columns: 1fr; }
}
</style>
