<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronDown, ImagePlus, Package, PenLine, Plus, RefreshCw, Search, Store, Trash2, TriangleAlert
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { formatDateTime, formatPrice } from '../../utils/format'
import {
  orderItemSummary, orderStatus, productStatus, shippingAddressSummary
} from '../../utils/merchantStatus'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import PaginationBar from '../../components/PaginationBar.vue'

// 低库存口径：库存 <= 10 件（含 0 库存；0 库存商品会自动下架）
const LOW_STOCK_THRESHOLD = 10
const ORDER_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'pending_payment', label: '待支付' },
  { value: 'paid', label: '待发货' },
  { value: 'shipped', label: '已发货' },
  { value: 'completed', label: '已完成' },
  { value: 'cancelled', label: '已取消' }
]

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const flagship = ref(null)
const categoryTree = ref([])
const activeView = ref(resolveView(route.query.tab))
const products = ref([])
const productTotal = ref(0)
const productPage = ref(1)
const productTotalPages = ref(1)
const productKeyword = ref('')
const productState = ref('')
const lowStockProducts = ref([])
const lowStockTotal = ref(0)
const lowStockPage = ref(1)
const lowStockTotalPages = ref(1)
const orders = ref([])
const orderTotal = ref(0)
const allOrderTotal = ref(0)
const orderPage = ref(1)
const orderTotalPages = ref(1)
const orderState = ref(String(route.query.status || ''))
const orderKeyword = ref('')
const pendingShipmentTotal = ref(0)
const revenue = ref(0)
const transactionCount = ref(0)
const loading = ref(true)
const savingProduct = ref(false)
const imageUploading = ref(false)
const busyProductId = ref(null)
const busyOrderId = ref(null)
const productOpen = ref(false)
const editingId = ref(null)
const imageInput = ref(null)
const form = ref(createEmptyForm())
const productGuard = useDialogDraftGuard({
  getSnapshot: () => form.value,
  getBusy: () => savingProduct.value || imageUploading.value,
  close: closeProduct
})

const displayedProducts = computed(() => (
  activeView.value === 'low-stock' ? lowStockProducts.value : products.value
))
const displayedProductPage = computed(() => (
  activeView.value === 'low-stock' ? lowStockPage.value : productPage.value
))
const displayedProductPages = computed(() => (
  activeView.value === 'low-stock' ? lowStockTotalPages.value : productTotalPages.value
))
const categoryParents = computed(() => categoryTree.value
  .map((parent) => ({
    ...parent,
    children: (parent.children || []).filter((child) => child.level === 2)
  }))
  .filter((parent) => parent.children.length))

function resolveView(value) {
  return ['orders', 'low-stock'].includes(String(value)) ? String(value) : 'products'
}

function createEmptyForm() {
  return {
    name: '',
    categoryId: null,
    description: '',
    price: '',
    stock: 0,
    imageUrl: '',
    targetPet: '',
    lifeStage: '',
    healthTags: ''
  }
}

async function loadOrganization() {
  const result = await api.get('/merchant/organization')
  flagship.value = result.data?.flagship?.status === 'open'
    ? result.data.flagship : null
}

async function loadCategories() {
  const result = await api.get('/categories?productType=goods&includeEmpty=true')
  categoryTree.value = result.data || []
}

async function loadProducts() {
  if (!flagship.value) return
  const params = {
    page: String(productPage.value),
    pageSize: '20'
  }
  if (productKeyword.value.trim()) params.keyword = productKeyword.value.trim()
  if (productState.value) params.status = productState.value
  const result = await api.get(`/merchant/products${queryString(params)}`)
  products.value = result.data?.list || []
  productTotal.value = Number(result.data?.total || 0)
  productTotalPages.value = Number(result.data?.totalPages || 1)
}

async function loadOrders() {
  if (!flagship.value) return
  const params = {
    page: String(orderPage.value),
    pageSize: '20'
  }
  if (orderState.value) params.status = orderState.value
  if (orderKeyword.value.trim()) params.keyword = orderKeyword.value.trim()
  const result = await api.get(`/merchant/orders${queryString(params)}`)
  orders.value = result.data?.list || []
  orderTotal.value = Number(result.data?.total || 0)
  orderTotalPages.value = Number(result.data?.totalPages || 1)
}

async function loadLowStock() {
  if (!flagship.value) return
  const params = {
    page: String(lowStockPage.value),
    pageSize: '20',
    stockMax: String(LOW_STOCK_THRESHOLD)
  }
  const result = await api.get(`/merchant/products${queryString(params)}`)
  lowStockProducts.value = result.data?.list || []
  lowStockTotal.value = Number(result.data?.total || 0)
  lowStockTotalPages.value = Number(result.data?.totalPages || 1)
}

async function loadSummary() {
  if (!flagship.value) {
    pendingShipmentTotal.value = 0
    revenue.value = 0
    transactionCount.value = 0
    return
  }
  const [pendingResult, totalResult, revenueResult] = await Promise.all([
    api.get('/merchant/orders?page=1&pageSize=1&status=paid'),
    api.get('/merchant/orders?page=1&pageSize=1'),
    api.get('/merchant/revenue/summary?type=mall')
  ])
  pendingShipmentTotal.value = Number(pendingResult.data?.total || 0)
  allOrderTotal.value = Number(totalResult.data?.total || 0)
  revenue.value = Number(revenueResult.data?.revenue || 0)
  transactionCount.value = Number(revenueResult.data?.transactionCount || 0)
}

async function loadData() {
  if (!flagship.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    await Promise.all([loadProducts(), loadOrders(), loadLowStock(), loadSummary()])
  } catch (error) {
    ui.toast(error.message || '读取商城数据失败', 'error')
  } finally {
    loading.value = false
  }
}

function switchView(nextView) {
  activeView.value = nextView
  router.replace({
    path: '/merchant/mall',
    query: nextView === 'orders' && orderState.value
      ? { tab: 'orders', status: orderState.value } : { tab: nextView }
  })
}

function searchProducts() {
  productPage.value = 1
  loadProducts().catch((error) => ui.toast(error.message, 'error'))
}

function changeProductPage(nextPage) {
  if (activeView.value === 'low-stock') {
    lowStockPage.value = nextPage
    loadLowStock().catch((error) => ui.toast(error.message, 'error'))
    return
  }
  productPage.value = nextPage
  loadProducts().catch((error) => ui.toast(error.message, 'error'))
}

function changeOrderState(nextState) {
  orderState.value = nextState
  orderPage.value = 1
  activeView.value = 'orders'
  router.replace({
    path: '/merchant/mall',
    query: nextState ? { tab: 'orders', status: nextState } : { tab: 'orders' }
  })
  loadOrders().catch((error) => ui.toast(error.message, 'error'))
}

function changeOrderPage(nextPage) {
  orderPage.value = nextPage
  loadOrders().catch((error) => ui.toast(error.message, 'error'))
}

function searchOrders() {
  orderPage.value = 1
  loadOrders().catch((error) => ui.toast(error.message, 'error'))
}

function syncViewFromRoute(tab, status) {
  const nextView = resolveView(tab)
  const nextStatus = nextView === 'orders' ? String(status || '') : ''
  if (activeView.value === nextView && orderState.value === nextStatus) return
  activeView.value = nextView
  orderState.value = nextStatus
  orderPage.value = 1
  if (flagship.value) loadOrders().catch((error) => ui.toast(error.message, 'error'))
}

watch(
  () => [route.query.tab, route.query.status],
  ([tab, status]) => syncViewFromRoute(tab, status)
)

function openProduct(product = null) {
  editingId.value = product?.id || null
  form.value = product ? {
    id: product.id,
    name: product.name || '',
    categoryId: product.categoryId || null,
    description: product.description || '',
    price: product.price,
    stock: product.stock || 0,
    imageUrl: product.imageUrl || '',
    targetPet: product.targetPet || '',
    lifeStage: product.lifeStage || '',
    healthTags: product.healthTags || ''
  } : createEmptyForm()
  productOpen.value = true
  productGuard.markClean()
}

function closeProduct() {
  productOpen.value = false
}

async function uploadImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  imageUploading.value = true
  try {
    const result = await api.upload('/product/image', file)
    form.value.imageUrl = result.data
    ui.toast('商品图片已上传', 'success')
  } catch (error) {
    ui.toast(error.message || '图片上传失败', 'error')
  } finally {
    imageUploading.value = false
    if (imageInput.value) imageInput.value.value = ''
  }
}

function validateProduct() {
  if (!form.value.name.trim()) {
    ui.toast('请填写商品名称', 'warning')
    return false
  }
  if (!form.value.categoryId) {
    ui.toast('请选择商品分类', 'warning')
    return false
  }
  if (!Number.isFinite(Number(form.value.price)) || Number(form.value.price) <= 0) {
    ui.toast('商品价格必须大于 0', 'warning')
    return false
  }
  const stockValue = String(form.value.stock ?? '').trim()
  if (!stockValue || !Number.isInteger(Number(stockValue)) || Number(stockValue) < 0) {
    ui.toast('库存必须是大于或等于 0 的整数', 'warning')
    return false
  }
  if (!form.value.description?.trim()) {
    ui.toast('请填写商品描述', 'warning')
    return false
  }
  if (!form.value.targetPet) {
    ui.toast('请选择适用宠物', 'warning')
    return false
  }
  if (!form.value.lifeStage) {
    ui.toast('请选择适用阶段', 'warning')
    return false
  }
  return true
}

async function saveProduct() {
  if (savingProduct.value || !validateProduct()) return
  savingProduct.value = true
  try {
    const request = editingId.value ? api.put : api.post
    const result = await request('/merchant/products', {
      ...form.value,
      name: form.value.name.trim(),
      description: form.value.description.trim(),
      price: Number(form.value.price),
      stock: Number(form.value.stock),
      healthTags: form.value.healthTags.trim()
    })
    ui.toast(result.msg || (editingId.value ? '商品已更新' : '商品已发布'), 'success')
    closeProduct()
    await loadData()
  } catch (error) {
    ui.toast(error.message || '保存商品失败', 'error')
  } finally {
    savingProduct.value = false
  }
}

async function toggleProduct(product) {
  if (busyProductId.value) return
  const closing = product.status === 'on'
  const confirmed = await ui.confirm({
    title: closing ? '下架商品' : '上架商品',
    message: closing
      ? `确认下架「${product.name}」吗？下架后用户无法购买该商品，购物车中的该商品将显示为不可售。`
      : `确认上架「${product.name}」吗？上架后商品将重新公开展示。`,
    confirmText: closing ? '确认下架' : '确认上架',
    danger: closing
  })
  if (!confirmed) return
  busyProductId.value = product.id
  try {
    await api.put(`/merchant/products/${product.id}/toggle`)
    ui.toast(product.status === 'on' ? '商品已下架' : '商品已上架', 'success')
    await Promise.all([loadProducts(), loadLowStock()])
  } catch (error) {
    ui.toast(error.message || '更新商品状态失败', 'error')
  } finally {
    busyProductId.value = null
  }
}

async function deleteProduct(product) {
  if (busyProductId.value) return
  const confirmed = await ui.confirm({
    message: `确定删除「${product.name}」吗？删除后无法恢复。`,
    danger: true
  })
  if (!confirmed) return
  busyProductId.value = product.id
  try {
    await api.delete(`/merchant/products/${product.id}`)
    ui.toast('商品已删除', 'success')
    if (activeView.value === 'low-stock') {
      if (lowStockProducts.value.length === 1 && lowStockPage.value > 1) lowStockPage.value -= 1
    } else if (products.value.length === 1 && productPage.value > 1) {
      productPage.value -= 1
    }
    await loadData()
  } catch (error) {
    ui.toast(error.message || '删除商品失败', 'error')
  } finally {
    busyProductId.value = null
  }
}

async function shipOrder(order) {
  if (busyOrderId.value) return
  const confirmed = await ui.confirm({
    message: `订单 ${order.orderNo} 将标记为已发货。\n收货人：${shippingAddressSummary(order.shippingAddress)}`,
    danger: false
  })
  if (!confirmed) return
  busyOrderId.value = order.id
  try {
    await api.put(`/merchant/orders/${order.id}/ship`)
    ui.toast('订单已标记为发货', 'success')
    await loadData()
  } catch (error) {
    ui.toast(error.message || '发货操作失败', 'error')
  } finally {
    busyOrderId.value = null
  }
}

onMounted(async () => {
  try {
    await loadOrganization()
    await loadCategories().catch((error) => {
      ui.toast(error.message || '读取商品分类失败', 'warning')
    })
    await loadData()
    if (route.query.create === '1' && flagship.value) {
      openProduct()
      await router.replace({ path: '/merchant/mall', query: { tab: 'products' } })
    }
  } catch (error) {
    ui.toast(error.message || '读取商城页面失败', 'error')
    loading.value = false
  }
})
</script>

<template>
  <div class="console-page mall-manage-page">
    <header class="console-head">
      <div>
        <h1>商城管理</h1>
      </div>
      <div class="console-head-actions">
        <span v-if="flagship" class="console-shop-chip is-static">
          <Store :size="14" />{{ flagship.name }}
        </span>
        <button
          v-if="flagship"
          class="btn btn-primary"
          type="button"
          @click="openProduct()"
        >
          <Plus :size="16" />新增商品
        </button>
      </div>
    </header>

    <LoadingPanel v-if="loading" label="正在读取商城数据" />
    <template v-else>
      <EmptyState
        v-if="!flagship"
        title="旗舰店暂不可经营"
        description="请先在店铺管理中创建商城店铺，审核通过并营业后才能管理商品。"
      >
        <button class="btn btn-primary" type="button" @click="router.push('/merchant/shops')">
          前往店铺管理
        </button>
      </EmptyState>

      <template v-else>
        <section class="mall-metrics" aria-label="商城经营摘要">
          <article>
            <span class="metric-icon"><Package :size="20" /></span>
            <div><small>商品总数</small><b>{{ productTotal }}</b></div>
          </article>
          <article>
            <span class="metric-icon warning"><RefreshCw :size="20" /></span>
            <div><small>待发货</small><b>{{ pendingShipmentTotal }}</b></div>
          </article>
          <article>
            <span class="metric-icon danger"><TriangleAlert :size="20" /></span>
            <div><small>低库存商品</small><b>{{ lowStockTotal }}</b></div>
          </article>
          <article>
            <span class="metric-icon"><strong>¥</strong></span>
            <div>
              <small>已完成营业额</small>
              <b>¥{{ formatPrice(revenue) }}</b>
              <span class="metric-sub">交易数 {{ transactionCount }} 单</span>
            </div>
          </article>
        </section>

        <div class="list-toolbar">
          <div class="console-tabs" role="tablist" aria-label="商城管理栏目">
            <button
              type="button"
              role="tab"
              :aria-selected="activeView === 'products'"
              :class="{ active: activeView === 'products' }"
              @click="switchView('products')"
            >
              商品管理 <span>{{ productTotal }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeView === 'orders'"
              :class="{ active: activeView === 'orders' }"
              @click="switchView('orders')"
            >
              订单管理 <span>{{ allOrderTotal }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="activeView === 'low-stock'"
              :class="{ active: activeView === 'low-stock' }"
              @click="switchView('low-stock')"
            >
              低库存 <span>{{ lowStockTotal }}</span>
            </button>
          </div>
          <div v-if="activeView === 'products'" class="toolbar-filters">
            <label class="console-select">
              <select v-model="productState" aria-label="商品状态" @change="searchProducts">
                <option value="">全部状态</option>
                <option value="on">在售</option>
                <option value="off">已下架</option>
              </select>
              <ChevronDown :size="16" />
            </label>
            <label class="search-field">
              <Search :size="17" />
              <input
                v-model="productKeyword"
                type="search"
                placeholder="搜索商品或健康标签"
                @keyup.enter="searchProducts"
              />
            </label>
            <button class="btn btn-primary console-search-btn" type="button" @click="searchProducts">查询</button>
          </div>
          <div v-else-if="activeView === 'orders'" class="toolbar-filters">
            <label class="console-select">
              <select v-model="orderState" aria-label="订单状态" @change="changeOrderState(orderState)">
                <option v-for="filter in ORDER_FILTERS" :key="filter.value" :value="filter.value">
                  {{ filter.label }}
                </option>
              </select>
              <ChevronDown :size="16" />
            </label>
            <label class="search-field">
              <Search :size="17" />
              <input
                v-model="orderKeyword"
                type="search"
                placeholder="搜索订单号或商品名称"
                @keyup.enter="searchOrders"
              />
            </label>
            <button class="btn btn-primary console-search-btn" type="button" @click="searchOrders">查询</button>
          </div>
        </div>

        <template v-if="activeView !== 'orders'">
          <div v-if="displayedProducts.length" class="table-panel">
            <table>
              <thead>
                <tr>
                  <th>商品</th><th>分类</th><th>价格</th><th>库存</th><th>状态</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="product in displayedProducts" :key="product.id">
                  <td>
                    <div class="table-product">
                      <img v-if="product.imageUrl" :src="assetUrl(product.imageUrl)" :alt="product.name" />
                      <span v-else><Package :size="19" /></span>
                      <div>
                        <b>{{ product.name }}</b>
                        <small>{{ product.targetPet === 'CAT' ? '猫咪' : product.targetPet === 'DOG' ? '狗狗' : '猫狗通用' }}</small>
                      </div>
                    </div>
                  </td>
                  <td>{{ product.categoryName || product.category || '未分类' }}</td>
                  <td>¥{{ formatPrice(product.price) }}</td>
                  <td>
                    <span
                      :class="{
                        'stock-warning': Number(product.stock || 0) <= LOW_STOCK_THRESHOLD,
                        'stock-empty': Number(product.stock || 0) === 0
                      }"
                    >
                      {{ Number(product.stock || 0) === 0 ? '已缺货' : `${product.stock} 件` }}
                    </span>
                  </td>
                  <td>
                    <span class="status-label" :class="productStatus(product.status).tone">
                      {{ productStatus(product.status).label }}
                    </span>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button type="button" @click="openProduct(product)">
                        <PenLine :size="15" />编辑
                      </button>
                      <button
                        type="button"
                        :disabled="busyProductId === product.id"
                        @click="toggleProduct(product)"
                      >
                        {{ busyProductId === product.id
                          ? '处理中'
                          : product.status === 'on' ? '下架' : '上架' }}
                      </button>
                      <button
                        class="danger"
                        type="button"
                        :disabled="busyProductId === product.id"
                        @click="deleteProduct(product)"
                      >
                        <Trash2 :size="15" />删除
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <EmptyState
            v-else
            :title="activeView === 'low-stock' ? '库存状态正常' : '没有符合条件的商品'"
            :description="activeView === 'low-stock'
              ? `当前店铺没有库存不高于 ${LOW_STOCK_THRESHOLD} 件的商品。`
              : '调整搜索条件，或发布一个新商品。'"
          />
          <PaginationBar
            v-if="displayedProductPages > 1"
            :page="displayedProductPage"
            :total-pages="displayedProductPages"
            @change="changeProductPage"
          />
        </template>

        <template v-else>
          <div v-if="orders.length" class="order-list">
            <article v-for="order in orders" :key="order.id">
              <header>
                <div>
                  <b>{{ order.orderNo }}</b>
                  <span>{{ formatDateTime(order.createdAt) }}</span>
                </div>
                <span class="status-label" :class="orderStatus(order.status).tone">
                  {{ orderStatus(order.status).label }}
                </span>
              </header>
              <div class="order-content">
                <div class="order-product-icon"><Package :size="20" /></div>
                <div class="order-main">
                  <b>{{ orderItemSummary(order) }}</b>
                  <p>收货信息：{{ shippingAddressSummary(order.shippingAddress) }}</p>
                  <span>买家：{{ order.userName || '顾客' }}</span>
                </div>
                <strong>¥{{ formatPrice(order.totalAmount) }}</strong>
              </div>
              <footer>
                <span>店铺：{{ flagship?.name }}</span>
                <button
                  v-if="order.status === 'paid'"
                  class="btn btn-primary btn-sm"
                  type="button"
                  :disabled="busyOrderId === order.id"
                  @click="shipOrder(order)"
                >
                  {{ busyOrderId === order.id ? '正在提交' : '确认发货' }}
                </button>
              </footer>
            </article>
          </div>
          <EmptyState v-else title="当前筛选下没有订单" description="新的商品订单会自动进入这里。" />
          <PaginationBar
            v-if="orderTotalPages > 1"
            :page="orderPage"
            :total-pages="orderTotalPages"
            @change="changeOrderPage"
          />
        </template>
      </template>
    </template>

    <ModalDialog
      :open="productOpen"
      :title="editingId ? '编辑商品' : '新增商品'"
      width="620px"
      :close-disabled="savingProduct || imageUploading"
      @close="productGuard.requestClose"
    >
      <div class="form-row">
        <label class="field">
          <span>商品名称</span>
          <input v-model="form.name" class="input" maxlength="100" />
        </label>
        <label class="field">
          <span>商品分类</span>
          <select v-model.number="form.categoryId" class="select">
            <option :value="null" disabled>请选择二级分类</option>
            <optgroup v-for="parent in categoryParents" :key="parent.id" :label="parent.name">
              <option v-for="child in parent.children" :key="child.id" :value="child.id">
                {{ child.name }}
              </option>
            </optgroup>
          </select>
        </label>
      </div>
      <div class="form-row">
        <label class="field">
          <span>商品价格</span>
          <input v-model="form.price" class="input" type="number" min="0.01" step="0.01" />
        </label>
        <label class="field">
          <span>库存数量</span>
          <input v-model="form.stock" class="input" type="number" min="0" step="1" />
        </label>
      </div>
      <div class="form-row">
        <label class="field">
          <span>适用宠物</span>
          <select v-model="form.targetPet" class="select">
            <option value="" disabled>请选择适用宠物</option>
            <option value="CAT">猫咪适用</option>
            <option value="DOG">狗狗适用</option>
            <option value="ALL">猫狗通用</option>
          </select>
        </label>
        <label class="field">
          <span>适用阶段</span>
          <select v-model="form.lifeStage" class="select">
            <option value="" disabled>请选择适用阶段</option>
            <option value="YOUNG">幼年阶段</option>
            <option value="ADULT">成年阶段</option>
            <option value="SENIOR">老年阶段</option>
            <option value="ALL">全阶段</option>
          </select>
        </label>
      </div>
      <label class="field">
        <span>健康标签</span>
        <input
          v-model="form.healthTags"
          class="input"
          maxlength="500"
          placeholder="自由填写，例如：美毛,低敏,肠胃呵护"
        />
      </label>
      <label class="field">
        <span>商品描述</span>
        <textarea v-model="form.description" class="textarea" maxlength="5000" />
      </label>
      <div class="field">
        <span>商品图片</span>
        <input ref="imageInput" type="file" accept="image/*" hidden @change="uploadImage" />
        <button class="image-upload" type="button" :disabled="imageUploading" @click="imageInput?.click()">
          <img v-if="form.imageUrl" :src="assetUrl(form.imageUrl)" alt="商品图片预览" />
          <span v-else><ImagePlus :size="20" />上传商品图片</span>
        </button>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="savingProduct || imageUploading" @click="productGuard.requestClose">
          取消
        </button>
        <button class="btn btn-primary" type="button" :disabled="savingProduct || imageUploading" @click="saveProduct">
          {{ savingProduct ? '正在保存' : editingId ? '保存修改' : '发布商品' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.mall-manage-page { display: grid; gap: var(--console-gap); }
.flagship-context { margin: 5px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.mall-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.mall-metrics article { min-height: 78px; padding: 14px 16px; border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-surface); display: flex; align-items: center; gap: 12px; box-shadow: var(--box-shadow-sm); }
.metric-icon { width: 38px; height: 38px; flex: 0 0 auto; border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary-strong); display: grid; place-items: center; }
.metric-icon.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.metric-icon.danger { background: var(--color-danger-soft); color: var(--color-danger); }
.mall-metrics small, .mall-metrics b { display: block; }
.mall-metrics small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.mall-metrics b { font-size: var(--font-size-2xl); }
.mall-metrics .metric-sub { display: block; margin-top: 2px; color: var(--color-text-muted); font-size: 0.75rem; }
.list-toolbar { display: flex; align-items: center; gap: 12px; }
.list-toolbar .console-tabs {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-raised);
}
.list-toolbar .console-tabs button {
  min-height: 34px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  transition: var(--transition-base);
}
.list-toolbar .console-tabs button:hover { background: var(--color-surface-warm); color: var(--color-primary-strong); }
.list-toolbar .console-tabs button span {
  min-width: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--color-primary-strong);
  font-size: 0.72rem;
  font-weight: var(--font-weight-extra-bold);
  font-variant-numeric: tabular-nums;
}
.list-toolbar .console-tabs button.active { background: var(--color-text); color: var(--color-white); }
.list-toolbar .console-tabs button.active span { background: transparent; color: var(--color-white); opacity: 0.85; }

.toolbar-filters { flex: 1; min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: 10px; }

.console-select { position: relative; flex: 0 0 auto; display: inline-flex; align-items: center; }
.console-select select {
  min-width: 148px;
  height: 44px;
  padding: 0 42px 0 18px;
  appearance: none;
  -webkit-appearance: none;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-raised);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  cursor: pointer;
  transition: var(--transition-base);
}
.console-select select:hover { border-color: var(--color-border-strong); }
.console-select svg { position: absolute; right: 15px; color: var(--color-text-secondary); pointer-events: none; }

.search-field {
  width: 232px;
  flex: 0 0 232px;
  min-height: 40px;
  max-width: none;
  min-width: 0;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-raised);
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
}
.search-field svg { flex: 0 0 auto; }
.search-field input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--color-text); font: inherit; }
.console-search-btn { flex: 0 0 auto; }
.table-panel { overflow: auto; border: 1px solid var(--color-border); border-radius: 8px 22px 22px 8px; background: var(--color-surface); }
table { width: 100%; border-collapse: collapse; min-width: 780px; }
th, td { padding: 13px 15px; border-bottom: 1px solid var(--color-border-soft); text-align: left; font-size: var(--font-size-sm); }
th { background: var(--color-surface-warm); color: var(--color-text-muted); }
tbody tr:last-child td { border-bottom: 0; }
.table-product { display: flex; align-items: center; gap: 10px; }
.table-product img, .table-product > span { width: 44px; height: 44px; flex: 0 0 auto; border-radius: 12px; object-fit: cover; display: grid; place-items: center; background: var(--color-surface-accent); color: var(--color-primary-strong); }
.table-product b, .table-product small { display: block; }
.table-product b { max-width: 220px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.table-product small { margin-top: 2px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.stock-warning { color: var(--color-danger); font-weight: var(--font-weight-bold); }
.stock-empty { color: var(--color-danger); font-weight: var(--font-weight-black); }
.status-label { width: fit-content; padding: 4px 9px; border-radius: 999px; background: var(--color-surface-warm); color: var(--color-text-secondary); display: inline-block; font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); white-space: nowrap; }
.status-label.success { background: var(--color-success-soft); color: var(--color-success); }
.status-label.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-label.info { background: var(--color-info-soft); color: var(--color-info); }
.status-label.danger { background: var(--color-danger-soft); color: var(--color-danger); }
.row-actions { display: flex; gap: 5px; flex-wrap: wrap; }
.row-actions button { min-height: 34px; padding: 5px 8px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text-secondary); display: inline-flex; align-items: center; gap: 3px; font-size: var(--font-size-sm); }
.row-actions button:hover { border-color: var(--color-primary); color: var(--color-primary-strong); }
.row-actions button.danger { color: var(--color-danger); }
.order-list { display: grid; gap: 12px; }
.order-list article { padding: 18px 20px; border: 1px solid var(--color-border); border-radius: 8px 22px 22px 8px; background: var(--color-surface); box-shadow: var(--box-shadow-sm); }
.order-list header, .order-list footer { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.order-list header > div b, .order-list header > div span { display: block; }
.order-list header > div span { margin-top: 3px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.order-content { margin: 14px 0; padding: 13px 0; border-top: 1px dashed var(--color-border); border-bottom: 1px dashed var(--color-border); display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; }
.order-product-icon { width: 42px; height: 42px; border-radius: 12px; background: var(--color-primary-soft); color: var(--color-primary-strong); display: grid; place-items: center; }
.order-main b { display: block; }
.order-main p { margin: 4px 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.order-main span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.order-content > strong { color: var(--color-primary-strong); font-size: var(--font-size-xl); }
.order-list footer { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.image-upload { width: 100%; min-height: 110px; border: 1.5px dashed var(--color-border-strong); border-radius: 14px; background: var(--color-surface-warm); color: var(--color-text-muted); }
.image-upload img { width: 100%; max-height: 180px; object-fit: contain; }
.image-upload span { display: flex; justify-content: center; align-items: center; gap: 7px; }
@media (max-width: 1080px) {
  .mall-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .list-toolbar { flex-wrap: wrap; }
  .toolbar-filters { flex: 1 1 100%; justify-content: flex-start; }
  .search-field { flex: 1 1 180px; width: auto; }
}
@media (max-width: 720px) {
  .order-list header, .order-list footer { align-items: flex-start; flex-direction: column; }
  .mall-metrics { grid-template-columns: 1fr; }
  .list-toolbar { align-items: stretch; flex-direction: column; }
  .toolbar-filters { width: 100%; flex-wrap: wrap; }
  .console-select { flex: 1 1 100%; }
  .console-select select { width: 100%; }
  .search-field { flex: 1 1 100%; }
  .order-content { grid-template-columns: auto minmax(0, 1fr); }
  .order-content > strong { grid-column: 2; }
}
</style>
