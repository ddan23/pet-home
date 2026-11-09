<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Check,
  ChevronDown,
  Search,
  Store,
  UsersRound,
  X
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const views = ['merchants', 'mall', 'service', 'pending']
const view = ref(views.includes(String(route.query.tab)) ? String(route.query.tab) : 'merchants')
const merchants = ref([])
const shops = ref([])
const loading = ref(true)
const keyword = ref('')
const status = ref('')
const page = ref(1)
const pageSize = 6
const stats = ref({ merchants: 0, mall: 0, service: 0, pending: 0 })
const shopDetail = ref(null)
const merchantDetail = ref(null)
const merchantShops = ref(null)
const merchantShopsLoading = ref(false)
const brokenImages = reactive(new Set())

function imageBroken(key) {
  brokenImages.add(key)
}

const statusLabels = {
  open: '营业中',
  closed: '已关闭',
  rejected: '审核未通过',
  pending_audit: '待审核'
}

const statusOptions = computed(() => {
  if (view.value === 'merchants') {
    return [
      { value: '', label: '全部状态' },
      { value: 'normal', label: '正常' },
      { value: 'disabled', label: '禁用' }
    ]
  }
  if (view.value === 'mall' || view.value === 'service') {
    return [
      { value: '', label: '全部状态' },
      { value: 'open', label: '营业中' },
      { value: 'rejected', label: '审核未通过' },
      { value: 'closed', label: '已关闭' }
    ]
  }
  return []
})

const searchPlaceholder = computed(() => {
  if (view.value === 'merchants') return '搜索商家昵称或用户名'
  if (view.value === 'pending') return '搜索待审核店铺'
  return '搜索店铺或商家'
})

const merchantList = computed(() =>
  merchants.value.filter((item) => !status.value || item.status === status.value)
)

const shopList = computed(() => {
  if (view.value === 'pending') {
    return shops.value.filter((shop) => shop.status === 'pending_audit')
  }
  const shopType = view.value === 'mall' ? 'mall' : 'service'
  return shops.value.filter(
    (shop) =>
      shop.type === shopType &&
      shop.status !== 'pending_audit' &&
      (!status.value || shop.status === status.value)
  )
})

const currentTotal = computed(() =>
  view.value === 'merchants' ? merchantList.value.length : shopList.value.length
)

const totalPages = computed(() => Math.max(1, Math.ceil(currentTotal.value / pageSize)))

const currentMerchants = computed(() => {
  const start = (page.value - 1) * pageSize
  return merchantList.value.slice(start, start + pageSize)
})

const currentShops = computed(() => {
  const start = (page.value - 1) * pageSize
  return shopList.value.slice(start, start + pageSize)
})

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '未填写'
}

async function fetchAllBusinessUsers() {
  const list = []
  let currentPage = 1
  let pageCount = 1
  do {
    const params = { role: 'business', page: currentPage, pageSize: 100 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const result = await api.get(`/admin/users${queryString(params)}`)
    list.push(...(result.data?.list || []))
    pageCount = result.data?.totalPages || 1
    currentPage += 1
  } while (currentPage <= pageCount)
  merchants.value = list
}

async function fetchAllShops() {
  const list = []
  let currentPage = 1
  let pageCount = 1
  do {
    const params = { page: currentPage, pageSize: 100 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const result = await api.get(`/admin/shops${queryString(params)}`)
    list.push(...(result.data?.list || []))
    pageCount = result.data?.totalPages || 1
    currentPage += 1
  } while (currentPage <= pageCount)
  shops.value = list
}

async function loadStats() {
  try {
    const [merchantRes, mallRes, serviceRes, pendingRes] = await Promise.all([
      api.get('/admin/users?role=business&page=1&pageSize=1'),
      api.get('/admin/shops?type=mall&page=1&pageSize=1'),
      api.get('/admin/shops?type=service&page=1&pageSize=1'),
      api.get('/admin/shops?status=pending_audit&page=1&pageSize=1')
    ])
    stats.value = {
      merchants: merchantRes.data?.total || 0,
      mall: mallRes.data?.total || 0,
      service: serviceRes.data?.total || 0,
      pending: pendingRes.data?.total || 0
    }
  } catch (error) {
    // 统计读取失败时保留上一次结果，避免切换分类后数字被清零
  }
}

async function loadCurrent() {
  loading.value = true
  try {
    if (view.value === 'merchants') await fetchAllBusinessUsers()
    else await fetchAllShops()
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function switchView(next) {
  if (view.value === next) return
  view.value = next
  page.value = 1
  status.value = ''
  keyword.value = ''
  router.replace({ query: { tab: next } })
  loadCurrent()
}

function search() {
  page.value = 1
  loadCurrent()
}

function changeStatus(value) {
  status.value = value
  page.value = 1
}

async function viewMerchant(user) {
  try {
    merchantDetail.value = (await api.get(`/admin/users/${user.id}`)).data
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function viewMerchantShops(user) {
  merchantShops.value = { merchant: user, shops: [] }
  merchantShopsLoading.value = true
  try {
    const result = await api.get(`/admin/shops?merchantId=${user.id}&page=1&pageSize=100`)
    merchantShops.value = { merchant: user, shops: result.data?.list || [] }
  } catch (error) {
    ui.toast(error.message, 'error')
    merchantShops.value = null
  } finally {
    merchantShopsLoading.value = false
  }
}

async function openShopFromMerchant(shop) {
  merchantShops.value = null
  await detail(shop)
}

async function toggleUser(user) {
  const next = user.status === 'normal' ? 'disabled' : 'normal'
  const confirmed = await ui.confirm({
    message: `确定${next === 'disabled' ? '禁用' : '恢复'}商家「${user.username}」吗？`,
    danger: next === 'disabled'
  })
  if (!confirmed) return
  try {
    await api.put(`/admin/users/${user.id}/status`, { status: next })
    user.status = next
    if (merchantDetail.value?.id === user.id) merchantDetail.value.status = next
    ui.toast('商家状态已更新', 'success')
    await loadStats()
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function detail(shop) {
  try {
    shopDetail.value = (await api.get(`/admin/shops/${shop.id}`)).data
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function audit(shop, decision) {
  const passed = decision === 'open'
  const confirmed = await ui.confirm({
    title: passed ? '通过审核' : '拒绝审核',
    confirmText: passed ? '确认通过' : '确认拒绝',
    message: passed
      ? `确认通过「${shop.name}」的审核？通过后店铺开始营业，商家账号恢复为正常。`
      : `确认拒绝「${shop.name}」的入驻申请？拒绝后店铺进入“审核未通过”状态，商家可修改资料后重新提交审核。`,
    danger: !passed
  })
  if (!confirmed) return
  try {
    await api.put(`/admin/audits/${shop.id}`, { status: decision })
    ui.toast(
      passed ? `「${shop.name}」已通过审核，店铺开始营业` : `「${shop.name}」审核未通过，可修改后重新提交`,
      passed ? 'success' : 'error'
    )
    await Promise.all([loadStats(), loadCurrent()])
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function toggleShop(shop) {
  const next = shop.status === 'open' ? 'closed' : 'open'
  const confirmed = await ui.confirm({
    message: next === 'closed' ? `确定关闭店铺「${shop.name}」吗？` : `确定恢复「${shop.name}」营业吗？`,
    danger: next === 'closed'
  })
  if (!confirmed) return
  try {
    await api.put(`/admin/shops/${shop.id}/status`, { status: next })
    shop.status = next
    if (shopDetail.value?.id === shop.id) shopDetail.value.status = next
    ui.toast('店铺状态已更新', 'success')
    await loadStats()
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

watch(totalPages, (total) => {
  if (page.value > total) page.value = total
})

watch(
  () => route.query.tab,
  (tab) => {
    const next = views.includes(String(tab)) ? String(tab) : 'merchants'
    if (next === view.value) return
    view.value = next
    page.value = 1
    status.value = ''
    keyword.value = ''
    loadCurrent()
  }
)

onMounted(async () => {
  await loadStats()
  await loadCurrent()
})
</script>

<template>
  <div class="admin-page merchant-page">
    <header class="admin-page-head">
      <div>
        <h1>商家管理</h1>
      </div>
    </header>

    <div class="admin-metric-row merchant-metrics">
      <article>
        <span>商家</span>
        <b>{{ stats.merchants.toLocaleString('zh-CN') }}</b>
      </article>
      <article>
        <span>商城店铺</span>
        <b>{{ stats.mall.toLocaleString('zh-CN') }}</b>
      </article>
      <article>
        <span>服务店铺</span>
        <b>{{ stats.service.toLocaleString('zh-CN') }}</b>
      </article>
      <article>
        <span>待审核</span>
        <b>{{ stats.pending.toLocaleString('zh-CN') }}</b>
      </article>
    </div>

    <section class="admin-table-shell merchant-shell">
      <form class="merchant-filter-bar" @submit.prevent="search">
        <div class="merchant-category-tabs" role="tablist" aria-label="商家管理分类">
          <button type="button" :class="{ active: view === 'merchants' }" @click="switchView('merchants')">
            商家 <b>{{ stats.merchants.toLocaleString('zh-CN') }}</b>
          </button>
          <button type="button" :class="{ active: view === 'mall' }" @click="switchView('mall')">
            商城店铺 <b>{{ stats.mall.toLocaleString('zh-CN') }}</b>
          </button>
          <button type="button" :class="{ active: view === 'service' }" @click="switchView('service')">
            服务店铺 <b>{{ stats.service.toLocaleString('zh-CN') }}</b>
          </button>
          <button type="button" :class="{ active: view === 'pending' }" @click="switchView('pending')">
            待审核 <b>{{ stats.pending.toLocaleString('zh-CN') }}</b>
          </button>
        </div>
        <label v-if="statusOptions.length" class="merchant-select">
          <select :value="status" aria-label="状态筛选" @change="changeStatus($event.target.value)">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <ChevronDown :size="16" />
        </label>
        <div class="merchant-search-group">
          <label class="admin-search merchant-search">
            <Search :size="17" />
            <input v-model="keyword" :aria-label="searchPlaceholder" :placeholder="searchPlaceholder">
          </label>
          <button class="btn btn-primary" type="submit">查询</button>
        </div>
      </form>

      <LoadingPanel v-if="loading" label="正在读取商家数据" />

      <template v-else-if="view === 'merchants'">
        <div v-if="currentMerchants.length" class="merchant-grid">
          <article v-for="user in currentMerchants" :key="user.id" class="merchant-card">
            <div class="merchant-main">
              <span class="merchant-logo">
                <img
                  v-if="user.avatar && !brokenImages.has(`user-${user.id}`)"
                  :src="assetUrl(user.avatar)"
                  alt=""
                  @error="imageBroken(`user-${user.id}`)"
                >
                <UsersRound v-else :size="22" />
              </span>
              <div>
                <span class="admin-status" :class="user.status === 'normal' ? 'sage' : 'danger'">
                  {{ user.status === 'normal' ? '正常' : '禁用' }}
                </span>
                <h2>{{ user.nickname || user.username }}</h2>
                <p>@{{ user.username }} · {{ user.shopCount || 0 }} 家店铺</p>
              </div>
            </div>
            <p class="merchant-description">
              {{ user.city || '城市未填' }} · 注册于 {{ formatDate(user.createdAt) }}
            </p>
            <div class="merchant-meta">
              <span>{{ user.phone || '手机号未填' }}</span>
              <span>{{ user.email || '邮箱未填' }}</span>
            </div>
            <footer>
              <button class="admin-row-btn" type="button" @click="viewMerchant(user)">基本信息</button>
              <button class="admin-row-btn" type="button" @click="viewMerchantShops(user)">查看店铺</button>
              <button
                class="admin-row-btn"
                :class="{ danger: user.status === 'normal' }"
                type="button"
                @click="toggleUser(user)"
              >
                {{ user.status === 'normal' ? '禁用' : '恢复' }}
              </button>
            </footer>
          </article>
        </div>
        <EmptyState v-else title="没有匹配商家" />
      </template>

      <template v-else>
        <div v-if="currentShops.length" class="merchant-grid">
          <article v-for="shop in currentShops" :key="shop.id" class="merchant-card">
            <div class="merchant-main">
              <span class="merchant-logo">
                <img
                  v-if="shop.logo && !brokenImages.has(`shop-${shop.id}`)"
                  :src="assetUrl(shop.logo)"
                  alt=""
                  @error="imageBroken(`shop-${shop.id}`)"
                >
                <Store v-else :size="22" />
              </span>
              <div>
                <span
                  class="admin-status"
                  :class="shop.status === 'pending_audit' ? 'warn' : shop.status === 'rejected' ? 'danger' : shop.status === 'open' ? 'sage' : 'neutral'"
                >
                  {{ statusLabels[shop.status] || shop.status }}
                </span>
                <h2>{{ shop.name }}</h2>
                <p>{{ shop.merchantName || '商家' }} · {{ shop.type === 'mall' ? '旗舰店' : '服务门店' }}</p>
              </div>
            </div>
            <p class="merchant-description">{{ shop.description || '暂无店铺介绍' }}</p>
            <div class="merchant-meta">
              <span>{{ shop.contactPhone || '电话未填' }}</span>
              <span>{{ shop.address || '地址未填' }}</span>
            </div>
            <footer>
              <button class="admin-row-btn" type="button" @click="detail(shop)">详情</button>
              <template v-if="view === 'pending'">
                <button class="admin-row-btn" type="button" @click="audit(shop, 'rejected')"><X :size="14" />拒绝</button>
                <button class="btn btn-primary btn-sm audit-pass" type="button" @click="audit(shop, 'open')">
                  <Check :size="14" />通过
                </button>
              </template>
              <button
                v-else-if="['open', 'closed'].includes(shop.status)"
                class="admin-row-btn"
                :class="{ danger: shop.status === 'open' }"
                type="button"
                @click="toggleShop(shop)"
              >
                {{ shop.status === 'open' ? '关闭店铺' : '恢复营业' }}
              </button>
            </footer>
          </article>
        </div>
        <EmptyState v-else :title="view === 'pending' ? '没有待审核店铺' : '没有匹配店铺'" />
      </template>

      <div v-if="!loading && currentTotal" class="admin-table-footer">
        <span>共 {{ currentTotal.toLocaleString('zh-CN') }} {{ view === 'merchants' ? '位商家' : '家店铺' }}</span>
        <PaginationBar :page="page" :total-pages="totalPages" @change="page = $event" />
      </div>
    </section>

    <ModalDialog :open="!!merchantDetail" title="商家信息" width="660px" @close="merchantDetail = null">
      <div v-if="merchantDetail" class="merchant-detail">
        <div class="merchant-detail-head">
          <span class="merchant-logo large">
            <img
              v-if="merchantDetail.avatar && !brokenImages.has(`user-${merchantDetail.id}`)"
              :src="assetUrl(merchantDetail.avatar)"
              alt=""
              @error="imageBroken(`user-${merchantDetail.id}`)"
            >
            <UsersRound v-else :size="26" />
          </span>
          <div>
            <h3>{{ merchantDetail.nickname || merchantDetail.username }}</h3>
            <p>@{{ merchantDetail.username }}</p>
          </div>
          <span class="admin-status" :class="merchantDetail.status === 'normal' ? 'sage' : 'danger'">
            {{ merchantDetail.status === 'normal' ? '正常' : '禁用' }}
          </span>
        </div>
        <div class="detail-grid">
          <div><span>用户名</span><b>{{ merchantDetail.username }}</b></div>
          <div><span>昵称</span><b>{{ merchantDetail.nickname || '--' }}</b></div>
          <div><span>手机号</span><b>{{ merchantDetail.phone || '--' }}</b></div>
          <div><span>邮箱</span><b>{{ merchantDetail.email || '--' }}</b></div>
          <div><span>城市</span><b>{{ merchantDetail.city || '--' }}</b></div>
          <div><span>注册时间</span><b>{{ formatDate(merchantDetail.createdAt) }}</b></div>
        </div>
      </div>
    </ModalDialog>

    <ModalDialog
      :open="!!merchantShops"
      :title="merchantShops ? `${merchantShops.merchant.nickname || merchantShops.merchant.username} 的店铺` : '店铺'"
      width="660px"
      @close="merchantShops = null"
    >
      <div v-if="merchantShops" class="merchant-shop-list">
        <LoadingPanel v-if="merchantShopsLoading" label="正在读取店铺" />
        <template v-else-if="merchantShops.shops.length">
          <article v-for="shop in merchantShops.shops" :key="shop.id">
            <span class="merchant-logo small">
              <img
                v-if="shop.logo && !brokenImages.has(`shop-${shop.id}`)"
                :src="assetUrl(shop.logo)"
                alt=""
                @error="imageBroken(`shop-${shop.id}`)"
              >
              <Store v-else :size="16" />
            </span>
            <div>
              <b>{{ shop.name }}</b>
              <small>{{ shop.type === 'mall' ? '旗舰店' : '服务门店' }}</small>
            </div>
            <span class="admin-status" :class="shop.status === 'pending_audit' ? 'warn' : shop.status === 'open' ? 'sage' : 'neutral'">
              {{ statusLabels[shop.status] || shop.status }}
            </span>
            <button class="admin-row-btn" type="button" @click="openShopFromMerchant(shop)">详情</button>
          </article>
        </template>
        <EmptyState v-else title="该商家还没有店铺" />
      </div>
    </ModalDialog>

    <ModalDialog :open="!!shopDetail" title="店铺详情" width="660px" @close="shopDetail = null">
      <div v-if="shopDetail" class="merchant-detail">
        <div class="merchant-detail-head">
          <span class="merchant-logo large">
            <img
              v-if="shopDetail.logo && !brokenImages.has(`shop-${shopDetail.id}`)"
              :src="assetUrl(shopDetail.logo)"
              alt=""
              @error="imageBroken(`shop-${shopDetail.id}`)"
            >
            <Store v-else :size="26" />
          </span>
          <div>
            <h3>{{ shopDetail.name }}</h3>
            <p>{{ shopDetail.merchantName || '商家' }} · {{ shopDetail.type === 'mall' ? '旗舰店' : '服务门店' }}</p>
          </div>
          <span
            class="admin-status"
            :class="shopDetail.status === 'open' ? 'sage' : shopDetail.status === 'pending_audit' ? 'warn' : shopDetail.status === 'rejected' ? 'danger' : 'neutral'"
          >
            {{ statusLabels[shopDetail.status] || shopDetail.status }}
          </span>
        </div>
        <div class="detail-grid">
          <div><span>店铺名称</span><b>{{ shopDetail.name }}</b></div>
          <div><span>类型</span><b>{{ shopDetail.type === 'mall' ? '旗舰店' : '服务门店' }}</b></div>
          <div><span>商家</span><b>{{ shopDetail.merchantName || '--' }}</b></div>
          <div><span>电话</span><b>{{ shopDetail.contactPhone || '--' }}</b></div>
          <div><span>营业时间</span><b>{{ shopDetail.businessHours || '--' }}</b></div>
          <div><span>评分</span><b>{{ shopDetail.rating ?? '--' }}</b></div>
          <div class="full"><span>地址</span><b>{{ shopDetail.address || '--' }}</b></div>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.merchant-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.merchant-shell {
  display: block;
}

.merchant-filter-bar {
  padding: 14px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--color-border);
}

.merchant-search-group {
  flex: 0 0 auto;
  min-width: 0;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.merchant-select + .merchant-search-group {
  margin-left: 0;
}

.merchant-search {
  width: 232px;
  min-height: 40px;
  padding: 0 12px;
}

.merchant-category-tabs {
  flex: 0 0 auto;
  margin-right: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-raised);
}

.merchant-category-tabs button {
  min-height: 34px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 0;
  border-radius: 9px;
  color: var(--color-text-secondary);
  background: transparent;
  font-family: inherit;
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  cursor: pointer;
  transition: var(--transition-base);
}

.merchant-category-tabs b {
  font-size: 0.72rem;
  font-weight: var(--font-weight-extra-bold);
  color: var(--color-primary-strong);
  font-variant-numeric: tabular-nums;
}

.merchant-category-tabs button.active b {
  color: var(--color-white);
  opacity: 0.85;
}

.merchant-category-tabs button:hover {
  color: var(--color-text);
  background: var(--color-surface-warm);
}

.merchant-category-tabs button.active {
  color: var(--color-white);
  background: var(--color-text);
}

.merchant-select {
  position: relative;
  flex: 0 0 auto;
  margin-left: auto;
  display: inline-flex;
  align-items: center;
}

.merchant-select select {
  width: 100%;
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
  font-weight: 500;
  line-height: 44px;
  cursor: pointer;
  transition: var(--transition-base);
}

.merchant-select select:hover {
  border-color: var(--color-primary);
}

.merchant-select select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: var(--shadow-focus);
}

.merchant-select svg {
  position: absolute;
  right: 15px;
  color: var(--color-text-secondary);
  pointer-events: none;
}

.merchant-grid {
  padding: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.merchant-card {
  min-width: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-ivory);
}

.merchant-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.merchant-main > div {
  min-width: 0;
  flex: 1;
}

.merchant-logo {
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  overflow: hidden;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  color: var(--color-primary-strong);
  background: var(--color-surface-raised);
}

.merchant-logo.large {
  width: 62px;
  height: 62px;
  border-radius: 16px;
}

.merchant-logo.small {
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

.merchant-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.merchant-main h2 {
  margin: 7px 0 2px;
  font-size: 1.22rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.merchant-main p,
.merchant-description {
  color: var(--color-text-muted);
  font-size: 0.82rem;
}

.merchant-main p {
  margin: 0;
}

.merchant-description {
  margin: 13px 0;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.merchant-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: var(--color-text-muted);
  font-size: 0.79rem;
}

.merchant-card footer {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 7px;
  border-top: 1px solid var(--color-border-soft);
}

.merchant-card footer .admin-row-btn {
  min-height: 32px;
  padding: 0 14px;
  font-size: 0.8rem;
}

.merchant-card footer .audit-pass {
  min-height: 32px;
  padding: 0 16px;
}

.merchant-detail {
  display: grid;
  gap: 14px;
}

.merchant-detail-head {
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-warm);
}

.merchant-detail-head > div {
  min-width: 0;
  flex: 1;
}

.merchant-detail-head h3,
.merchant-detail-head p {
  margin: 0;
}

.merchant-detail-head p {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.detail-grid div {
  padding: 10px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-sm);
  background: var(--color-surface-ivory);
}

.detail-grid .full {
  grid-column: 1 / -1;
}

.detail-grid span,
.detail-grid b {
  display: block;
}

.detail-grid span {
  color: var(--color-text-muted);
  font-size: 0.76rem;
}

.merchant-shop-list {
  display: grid;
  gap: 8px;
}

.merchant-shop-list article {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-ivory);
}

.merchant-shop-list article > div {
  min-width: 0;
  flex: 1;
}

.merchant-shop-list b,
.merchant-shop-list small {
  display: block;
}

.merchant-shop-list small {
  color: var(--color-text-muted);
  font-size: 0.76rem;
}

.merchant-shell .pagination {
  margin: 0;
}

@media (max-width: 1024px) {
  .merchant-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .merchant-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .merchant-metrics {
    grid-template-columns: 1fr;
  }

  .merchant-metrics article {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .merchant-metrics article:last-child {
    border-bottom: 0;
  }

  .merchant-filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .merchant-search-group {
    width: 100%;
    margin-left: 0;
  }

  .merchant-search {
    flex: 1;
    width: auto;
    min-width: 0;
  }

  .merchant-category-tabs {
    width: 100%;
    margin-right: 0;
    overflow-x: auto;
  }

  .merchant-select {
    width: 100%;
  }

  .merchant-select select {
    min-width: 0;
  }

  .merchant-card footer {
    flex-wrap: wrap;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid .full {
    grid-column: auto;
  }
}
</style>
