<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import CatalogCategoryRail from '../../components/CatalogCategoryRail.vue'
import ServiceCard from '../../components/ServiceCard.vue'
import BackToTopButton from '../../components/BackToTopButton.vue'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'

const ui = useUiStore()
const route = useRoute()
const categoryTree = ref([])
const categoryCode = ref(String(route.query.categoryCode || ''))
const keyword = ref('')

const page = ref(0)
const pageSize = 9
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
    const result = await api.get(`/services${queryString(params)}`)
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
    const result = await api.get('/categories?productType=service')
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
})
onBeforeUnmount(() => {
  loadObserver?.disconnect()
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <h1>宠物服务</h1>
      <form class="service-search" @submit.prevent="search">
        <Search :size="18" />
        <input v-model="keyword" placeholder="搜索服务或店铺" aria-label="搜索服务或店铺" />
        <button class="btn btn-primary btn-sm" type="submit">搜索</button>
      </form>
    </header>

    <div class="page-layout-split">
      <CatalogCategoryRail
        title="服务分类"
        :categories="categories"
        :active-code="categoryCode"
        @select="categoryCode = $event"
      />

      <div class="service-main">
        <LoadingPanel v-if="loading" label="正在查找附近服务" />
        <EmptyState v-else-if="!list.length" title="暂时没有匹配服务" description="换个分类或关键词再试试。" />
        <template v-else>
          <div v-if="list.length" class="service-card-grid">
            <ServiceCard
              v-for="service in list"
              :key="service.id"
              :service="service"
            />
          </div>
          <div v-if="hasMore" ref="loadSentinel" class="infinite-status">
            <span v-if="loadingMore" class="spinner" />
            <span>{{ loadingMore ? '正在加载更多' : '继续下滑加载更多' }}</span>
          </div>
          <p v-else class="infinite-status">已显示全部服务</p>
        </template>
      </div>
    </div>
    <BackToTopButton />
  </div>
</template>

<style scoped>
.service-search { flex: 0 1 420px; min-width: 0; min-height: 50px; display: flex; align-items: center; gap: 9px; background: var(--color-white); border: 1px solid var(--color-border); border-radius: 999px; padding: 5px 6px 5px 16px; }

.service-search svg { color: var(--color-text-muted); }
.service-search input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; }
.service-main { min-width: 0; }
.service-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
@media (max-width: 1040px) {
  .service-card-grid { grid-template-columns: 1fr; }
}
@media (max-width: 680px) {
  .service-search { align-self: stretch; }
}
</style>
