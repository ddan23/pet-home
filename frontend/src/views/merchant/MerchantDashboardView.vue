<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, CalendarDays, MapPin, Package, Store, TriangleAlert
} from 'lucide-vue-next'
import { api } from '../../services/api'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import { formatPrice } from '../../utils/format'
import { shopStatus } from '../../utils/merchantStatus'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const loading = ref(true)
const overview = ref(createEmptyOverview())
const shopPickerOpen = ref(false)

const summary = computed(() => overview.value.summary)
const shops = computed(() => overview.value.shops || [])
const serviceOutlets = computed(() => shops.value.filter((shop) => shop.type === 'service'))
const openServiceOutlets = computed(() => serviceOutlets.value.filter((shop) => shop.status === 'open'))
const todoTotal = computed(() => (
  Number(summary.value.pendingShipments || 0) + Number(summary.value.pendingAppointments || 0)
))
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

function createEmptyOverview() {
  return {
    summary: {
      totalRevenue: 0,
      totalShopCount: 0,
      activeShopCount: 0,
      productCount: 0,
      serviceCount: 0,
      pendingShipments: 0,
      pendingAppointments: 0,
      lowStockCount: 0
    },
    shops: [],
    lowStockProducts: [],
    recentOrders: [],
    todayAppointments: []
  }
}

function shopTypeLabel(shop) {
  return shop.type === 'mall' ? '商城店铺' : '服务店铺'
}

function shopItemLabel(shop) {
  if (shop.status !== 'open') return '—'
  return shop.type === 'mall'
    ? `${shop.itemCount || 0} 件商品`
    : `${shop.itemCount || 0} 个服务`
}

function shopTodoLabel(shop) {
  if (shop.status !== 'open') return '—'
  return shop.type === 'mall'
    ? `待发货 ${shop.pendingCount || 0}`
    : `待服务 ${shop.pendingCount || 0}`
}

function shopActionLabel(shop) {
  if (shop.status !== 'open') return '查看店铺资料'
  return shop.type === 'mall' ? '进入商城管理' : '进入服务管理'
}

async function loadDashboard() {
  loading.value = true
  try {
    const result = await api.get('/merchant/dashboard')
    overview.value = result.data || createEmptyOverview()
  } catch (error) {
    ui.toast(error.message || '读取经营数据失败', 'error')
  } finally {
    loading.value = false
  }
}

function goTo(path) {
  router.push(path)
}

function enterShop(shop) {
  if (shop.status !== 'open') {
    goTo('/merchant/shops')
    return
  }
  if (shop.type === 'mall') {
    goTo('/merchant/mall')
    return
  }
  window.localStorage.setItem('merchant.serviceOutletId', String(shop.id))
  goTo(`/merchant/services?outletId=${shop.id}&tab=services`)
}

function enterServiceOutlet(shop) {
  shopPickerOpen.value = false
  window.localStorage.setItem('merchant.serviceOutletId', String(shop.id))
  goTo(`/merchant/services?outletId=${shop.id}&tab=appointments`)
}

function goServiceAppointments() {
  if (!openServiceOutlets.value.length) {
    goTo('/merchant/shops')
    return
  }
  if (openServiceOutlets.value.length === 1) {
    enterServiceOutlet(openServiceOutlets.value[0])
    return
  }
  shopPickerOpen.value = true
}

onMounted(loadDashboard)
</script>

<template>
  <div class="console-page dashboard-page">
    <header class="console-head">
      <div>
        <h1>经营总览</h1>
      </div>
      <div class="console-head-actions">
        <span class="date-chip">{{ todayLabel }}</span>
      </div>
    </header>

    <LoadingPanel v-if="loading" label="正在汇总全部店铺经营数据" />
    <template v-else>
      <EmptyState
        v-if="!shops.length"
        title="还没有经营店铺"
        description="先创建商城店铺或服务店铺，经营数据会自动汇总到这里。"
      >
        <button class="btn btn-primary" type="button" @click="goTo('/merchant/shops')">
          前往店铺管理
        </button>
      </EmptyState>

      <template v-else>
        <section class="dashboard-hero">
          <div class="dashboard-welcome">
            <p class="hero-greeting">{{ greeting }}，{{ auth.user?.nickname || auth.user?.username || '商家' }}</p>
            <p class="hero-todo">
              今天有 {{ todoTotal }} 项履约待办：待发货 {{ summary.pendingShipments }} 单，
              待服务 {{ summary.pendingAppointments }} 单。
            </p>
          </div>
          <div class="hero-stats">
            <div class="hero-stat">
              <small>累计营业额</small>
              <strong>¥{{ formatPrice(summary.totalRevenue) }}</strong>
            </div>
            <div class="hero-stat">
              <small>店铺</small>
              <strong>{{ summary.totalShopCount }} 家</strong>
            </div>
            <div class="hero-stat">
              <small>今日履约待办</small>
              <strong>{{ todoTotal }} 项</strong>
            </div>
          </div>
        </section>

        <section class="portfolio-section">
          <header class="section-head">
            <div>
              <h2>全部店铺</h2>
            </div>
            <button class="text-link" type="button" @click="goTo('/merchant/shops')">
              管理店铺资料<ArrowRight :size="14" />
            </button>
          </header>

          <div class="shop-portfolio">
            <article
              v-for="shop in shops"
              :key="shop.id"
              class="shop-overview-card"
              :class="{
                'is-mall': shop.type === 'mall',
                'is-inactive': shop.status !== 'open'
              }"
            >
              <header class="shop-head">
                <div class="shop-identity">
                  <span class="shop-avatar"><Store :size="17" /></span>
                  <div>
                    <div class="shop-labels">
                      <span class="status-label" :class="shopStatus(shop.status).tone">
                        {{ shopStatus(shop.status).label }}
                      </span>
                      <span class="shop-type">{{ shopTypeLabel(shop) }}</span>
                    </div>
                    <h3>{{ shop.name }}</h3>
                  </div>
                </div>
              </header>

              <p class="shop-address"><MapPin :size="12" />{{ shop.address || '店铺地址尚未填写' }}</p>

              <div class="shop-facts">
                <div><small>经营项目</small><b>{{ shopItemLabel(shop) }}</b></div>
                <div><small>待办</small><b>{{ shopTodoLabel(shop) }}</b></div>
                <div><small>累计营业额</small><b>{{ shop.status === 'open' ? `¥${formatPrice(shop.revenue)}` : '—' }}</b></div>
                <div><small>累计营业数</small><b>{{ shop.status === 'open' ? `${shop.transactionCount || 0} 单` : '—' }}</b></div>
              </div>

              <footer>
                <button type="button" @click="enterShop(shop)">
                  {{ shopActionLabel(shop) }}<ArrowRight :size="13" />
                </button>
              </footer>
            </article>
          </div>
        </section>

        <section class="todo-row" aria-label="履约待办">
          <button class="todo-card" type="button" @click="goTo('/merchant/mall?tab=orders&status=paid')">
            <span class="todo-icon warning"><Package :size="18" /></span>
            <span><small>待发货订单</small><b>{{ summary.pendingShipments }}</b></span>
            <ArrowRight :size="16" />
          </button>
          <button class="todo-card" type="button" @click="goServiceAppointments">
            <span class="todo-icon"><CalendarDays :size="18" /></span>
            <span><small>待服务预约</small><b>{{ summary.pendingAppointments }}</b></span>
            <ArrowRight :size="16" />
          </button>
          <button class="todo-card" type="button" @click="goTo('/merchant/mall?tab=low-stock')">
            <span class="todo-icon danger"><TriangleAlert :size="18" /></span>
            <span><small>低库存商品</small><b>{{ summary.lowStockCount }}</b></span>
            <ArrowRight :size="16" />
          </button>
        </section>
      </template>
    </template>

    <ModalDialog
      :open="shopPickerOpen"
      title="选择服务店铺"
      width="460px"
      @close="shopPickerOpen = false"
    >
      <div class="picker-list">
        <button
          v-for="shop in openServiceOutlets"
          :key="shop.id"
          type="button"
          @click="enterServiceOutlet(shop)"
        >
          <span class="picker-avatar"><Store :size="16" /></span>
          <span><b>{{ shop.name }}</b><small>{{ shop.address || '地址未填写' }}</small></span>
          <ArrowRight :size="15" />
        </button>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.dashboard-page { display: grid; gap: var(--console-gap); }
.date-chip {
  padding: 6px 11px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.dashboard-hero {
  padding: 24px 26px;
  border: 1px solid var(--color-border-primary-soft);
  border-radius: var(--radius-console-hero);
  background: linear-gradient(120deg, var(--color-text) 0%, var(--color-primary-strong) 100%);
  color: var(--color-white);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 26px;
  position: relative;
  overflow: hidden;
}
.dashboard-hero::before {
  content: "";
  position: absolute;
  left: 0;
  top: 22px;
  bottom: 22px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: var(--color-butter);
}
.dashboard-hero::after {
  content: "";
  position: absolute;
  width: 300px;
  height: 300px;
  right: -120px;
  top: -180px;
  border: 1px solid var(--color-console-divider);
  border-radius: 50%;
}
.dashboard-welcome { max-width: 520px; position: relative; z-index: 1; }
.hero-greeting { margin: 0 0 7px; color: var(--color-butter); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.hero-todo { margin: 0; color: var(--color-console-text); font-size: var(--font-size-sm); line-height: 1.7; max-width: 420px; }

.hero-stats { display: flex; gap: 0; position: relative; z-index: 1; }
.hero-stat { min-width: 148px; padding: 0 18px; }
.hero-stat + .hero-stat { border-left: 1px solid var(--color-console-divider); }
.hero-stat small, .hero-stat strong, .hero-stat span { display: block; }
.hero-stat small { color: var(--color-console-text); font-size: 0.75rem; }
.hero-stat strong { margin: 4px 0 3px; font-family: var(--font-family-display); font-size: var(--font-size-3xl); }
.hero-stat span { color: var(--color-console-text-muted); font-size: 0.75rem; }

.portfolio-section { display: grid; gap: 12px; }
.section-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.section-head h2 { margin: 0; font-size: var(--font-size-2xl); }
.section-head p { margin: 4px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.text-link { border: 0; background: transparent; padding: 0; color: var(--color-primary-strong); display: inline-flex; align-items: center; gap: 4px; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); white-space: nowrap; }

.shop-portfolio { display: grid; grid-template-columns: repeat(auto-fill, minmax(258px, 1fr)); gap: 12px; }
.shop-overview-card {
  min-width: 0;
  padding: 15px 16px;
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-success);
  border-radius: var(--radius-console-card);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.shop-overview-card.is-mall {
  border-left-color: var(--color-accent);
  background: linear-gradient(110deg, var(--color-surface), var(--color-surface-ivory));
}
.shop-overview-card.is-inactive { border-left-color: var(--color-border-strong); }

.shop-identity { display: flex; align-items: center; gap: 9px; min-width: 0; }
.shop-avatar { width: 34px; height: 34px; flex: 0 0 auto; border-radius: 11px; display: grid; place-items: center; background: var(--color-success-soft); color: var(--color-success); }
.shop-overview-card.is-mall .shop-avatar { background: var(--color-accent-soft); color: var(--color-accent-strong); }
.shop-identity > div { min-width: 0; }
.shop-labels { display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.shop-type { color: var(--color-text-muted); font-size: 0.75rem; }
.shop-identity h3 { margin: 4px 0 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: var(--font-size-lg); }

.shop-address { display: flex; align-items: center; gap: 4px; margin: 0; color: var(--color-text-muted); font-size: 0.75rem; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.shop-address svg { flex: 0 0 auto; }

.shop-facts { display: grid; gap: 3px; padding: 8px 0; border-top: 1px dashed var(--color-border); border-bottom: 1px dashed var(--color-border); }
.shop-facts div { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.shop-facts small { color: var(--color-text-muted); font-size: 0.75rem; white-space: nowrap; }
.shop-facts b { font-size: var(--font-size-sm); white-space: nowrap; }

.shop-overview-card footer { margin-top: auto; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.shop-overview-card footer button { border: 0; background: transparent; color: var(--color-primary-strong); display: inline-flex; align-items: center; gap: 2px; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); white-space: nowrap; }

.todo-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }

.todo-card {
  min-height: 96px;
  padding: 18px 22px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  color: var(--color-text);
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 11px;
  text-align: left;
  box-shadow: var(--box-shadow-sm);
  transition: var(--transition-base);
}
.todo-card:hover { border-color: var(--color-border-primary-soft); transform: translateY(-1px); }
.todo-icon { width: 42px; height: 42px; border-radius: 50%; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.todo-icon.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.todo-icon.danger { background: var(--color-danger-soft); color: var(--color-danger); }
.todo-card small, .todo-card b { display: block; }
.todo-card small { color: var(--color-text-muted); font-size: 0.75rem; }
.todo-card b { font-size: var(--font-size-xl); }

.status-label { width: fit-content; padding: 3px 8px; border-radius: 999px; background: var(--color-surface-warm); color: var(--color-text-secondary); font-size: 0.75rem; font-weight: var(--font-weight-bold); white-space: nowrap; }
.status-label.success { background: var(--color-success-soft); color: var(--color-success); }
.status-label.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-label.danger { background: var(--color-danger-soft); color: var(--color-danger); }
.status-label.info { background: var(--color-info-soft); color: var(--color-info); }

.picker-list { display: grid; gap: 8px; }
.picker-list button {
  width: 100%;
  padding: 12px 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  text-align: left;
  color: var(--color-text);
}
.picker-list button:hover { border-color: var(--color-primary); background: var(--color-surface-warm); }
.picker-avatar { width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.picker-list b { display: block; font-size: var(--font-size-sm); }
.picker-list small { display: block; margin-top: 2px; color: var(--color-text-muted); font-size: 0.75rem; }

@media (max-width: 980px) {
  .dashboard-hero { align-items: flex-start; flex-direction: column; }
  .hero-stats { flex-wrap: wrap; gap: 12px 0; }
  .hero-stat { padding-left: 0; }
  .hero-stat + .hero-stat { border-left: 0; padding-left: 0; }
}
@media (max-width: 760px) {
  .todo-row { grid-template-columns: 1fr; }
  .shop-portfolio { grid-template-columns: 1fr; }
  .section-head { align-items: flex-start; flex-direction: column; }
  .hero-stat { min-width: 0; }
}
</style>
