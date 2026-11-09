<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CalendarClock, CalendarDays, ChevronDown, Eye, ImagePlus, PenLine, Plus, Power, Search,
  Sparkles, Store, Trash2, Wallet
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { formatDateTime, formatPrice } from '../../utils/format'
import { appointmentStatus, orderServiceSummary, productStatus } from '../../utils/merchantStatus'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import MerchantAppointmentSchedulePanel from '../../components/appointment/MerchantAppointmentSchedulePanel.vue'

const APPOINTMENT_STATES = [
  { value: '', label: '全部状态' },
  { value: 'confirmed', label: '待服务' },
  { value: 'completed', label: '已完成' },
  { value: 'cancelled', label: '已取消' }
]

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const organization = ref({ serviceOutlets: [] })
const serviceOutlets = ref([])
const categoryTree = ref([])
const outletId = ref(resolveInitialOutlet(route.query.outletId))
const view = ref(resolveInitialView(route.query.tab))
const services = ref([])
const serviceTotal = ref(0)
const servicePage = ref(1)
const serviceTotalPages = ref(1)
const serviceKeyword = ref('')
const serviceState = ref('')
const appointments = ref([])
const appointmentTotal = ref(0)
const pendingAppointmentTotal = ref(0)
const appointmentState = ref('')
const appointmentKeyword = ref('')
const appointmentQuery = ref('')
const detailOpen = ref(false)
const detailItem = ref(null)
const revenue = ref(0)
const transactionCount = ref(0)
const loading = ref(true)
const savingService = ref(false)
const serviceImageUploading = ref(false)
const busyServiceId = ref(null)
const busyAppointmentId = ref(null)
const modalOpen = ref(false)
const editingId = ref(null)
const imageInput = ref(null)
const form = ref(createEmptyForm())
const serviceGuard = useDialogDraftGuard({
  getSnapshot: () => form.value,
  getBusy: () => savingService.value || serviceImageUploading.value,
  close: closeService
})
const scheduleOpen = ref(false)
const scheduleService = ref(null)

const openOutlets = computed(() => serviceOutlets.value.filter((shop) => shop.status === 'open'))
const currentShop = computed(() => openOutlets.value.find(
  (shop) => String(shop.id) === String(outletId.value)
) || null)
const unavailableOutlets = computed(() => serviceOutlets.value.filter(
  (shop) => shop.status !== 'open'
))
const filteredAppointments = computed(() => {
  const keyword = appointmentQuery.value.trim().toLowerCase()
  const today = localDateText(new Date())
  return appointments.value
    .filter((item) => {
      // 只展示今天及以后的预约，历史预约不再进入列表
      const date = appointmentDate(item)
      if (!date || date < today) return false
      if (appointmentState.value && item.status !== appointmentState.value) return false
      if (!keyword) return true
      return [item.orderNo, item.userName, orderServiceSummary(item), appointmentSlotsLabel(item)]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(keyword)
    })
    // 待服务按预约时间由近到远排在前面，已完成/已取消按最近发生在前
    .sort((left, right) => {
      const leftPending = left.status === 'confirmed' ? 0 : 1
      const rightPending = right.status === 'confirmed' ? 0 : 1
      if (leftPending !== rightPending) return leftPending - rightPending
      return leftPending === 0
        ? appointmentStartValue(left).localeCompare(appointmentStartValue(right))
        : appointmentStartValue(right).localeCompare(appointmentStartValue(left))
    })
})
const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function appointmentDateParts(date) {
  const [year, month, day] = String(date).split('-').map(Number)
  if (!year || !month || !day) return null
  return { month, day, weekday: WEEKDAY_LABELS[new Date(year, month - 1, day).getDay()] }
}

// 按预约日期分组：今天起由近到远，过去日期由近到远排在后面
const appointmentGroups = computed(() => {
  const today = localDateText(new Date())
  const tomorrow = localDateText(new Date(Date.now() + 86400000))
  const yesterday = localDateText(new Date(Date.now() - 86400000))
  const buckets = new Map()
  filteredAppointments.value.forEach((item) => {
    const date = appointmentDate(item) || 'unknown'
    if (!buckets.has(date)) buckets.set(date, [])
    buckets.get(date).push(item)
  })
  const dates = [...buckets.keys()].filter((date) => date !== 'unknown')
  dates.sort((left, right) => {
    const leftUpcoming = left >= today
    const rightUpcoming = right >= today
    if (leftUpcoming !== rightUpcoming) return leftUpcoming ? -1 : 1
    return leftUpcoming ? left.localeCompare(right) : right.localeCompare(left)
  })
  if (buckets.has('unknown')) dates.push('unknown')
  return dates.map((date) => {
    const items = buckets.get(date)
    const pending = items.filter((item) => item.status === 'confirmed').length
    const parts = date === 'unknown' ? null : appointmentDateParts(date)
    const label = date === today
      ? '今天'
      : date === tomorrow
        ? '明天'
        : date === yesterday
          ? '昨天'
          : parts ? `${parts.month}月${parts.day}日` : '日期待确认'
    return {
      date,
      day: parts ? parts.day : '--',
      month: parts ? parts.month : '',
      label,
      summary: parts
        ? `${parts.weekday} · ${items.length} 个预约${pending ? ` · 待服务 ${pending}` : ''}`
        : `${items.length} 个预约`,
      isToday: date === today,
      items
    }
  })
})

const categoryParents = computed(() => categoryTree.value
  .map((parent) => ({
    ...parent,
    children: (parent.children || []).filter((child) => child.level === 2)
  }))
  .filter((parent) => parent.children.length))

function resolveInitialView(value) {
  return ['services', 'appointments'].includes(String(value)) ? String(value) : 'services'
}

function resolveInitialOutlet(value) {
  const fromQuery = value ? String(value) : ''
  if (fromQuery) return fromQuery
  return window.localStorage.getItem('merchant.serviceOutletId') || ''
}

function createEmptyForm() {
  return {
    outletId: null,
    name: '',
    categoryId: null,
    description: '',
    price: '',
    durationMinutes: '',
    imageUrl: '',
    targetPet: 'ALL',
    lifeStage: 'ALL',
    healthTags: ''
  }
}

function localDateText(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function appointmentDate(item) {
  return item.appointmentSlots?.[0]?.slotDate || localDateText(item.appointmentTime)
}

function appointmentDurationLabel(item) {
  const raw = String(item.appointmentDuration || '').trim()
  if (!raw) return ''
  return raw.replace(/^(\d+)\s*个时段$/, '$1 个时段')
}

// 两小时内开始且仍待服务的预约做一次提醒
function appointmentSoon(item) {
  if (item.status !== 'confirmed') return false
  const start = appointmentStart(item)
  if (!start || Number.isNaN(start.getTime())) return false
  const diff = start.getTime() - Date.now()
  return diff > 0 && diff <= 2 * 60 * 60 * 1000
}

function appointmentStart(item) {
  const slot = item.appointmentSlots?.[0]
  if (slot?.slotDate) {
    return new Date(`${slot.slotDate}T${String(slot.startTime || '00:00').slice(0, 8)}`)
  }
  return item.appointmentTime ? new Date(item.appointmentTime) : null
}

function appointmentStartValue(item) {
  const start = appointmentStart(item)
  return start && !Number.isNaN(start.getTime()) ? start.toISOString() : ''
}

function appointmentClock(item) {
  const slot = item.appointmentSlots?.[0]
  if (slot?.startTime) return String(slot.startTime).slice(0, 5)
  if (item.appointmentTime) return formatDateTime(item.appointmentTime).slice(-5)
  return '待定'
}

function appointmentSlotsLabel(item) {
  const slots = item.appointmentSlots || []
  if (!slots.length) {
    return item.appointmentTime ? formatDateTime(item.appointmentTime) : '时段待确认'
  }
  return slots
    .map((slot) => (
      `${String(slot.slotDate || '').slice(5)} ${String(slot.startTime || '').slice(0, 5)}`
      + `–${String(slot.endTime || '').slice(0, 5)}`
    ))
    .join('、')
}

async function loadOrganization() {
  const result = await api.get('/merchant/organization')
  organization.value = result.data || { serviceOutlets: [] }
  serviceOutlets.value = organization.value.serviceOutlets || []
  const availableIds = openOutlets.value.map((shop) => String(shop.id))
  if (!outletId.value || !availableIds.includes(String(outletId.value))) {
    outletId.value = availableIds[0] || ''
  }
  if (outletId.value) {
    window.localStorage.setItem('merchant.serviceOutletId', String(outletId.value))
  }
}

async function loadCategories() {
  const result = await api.get('/categories?productType=service&includeEmpty=true')
  categoryTree.value = result.data || []
}

async function loadServices() {
  if (!outletId.value) return
  const params = {
    page: String(servicePage.value),
    pageSize: '20',
    outletId: outletId.value
  }
  if (serviceKeyword.value.trim()) params.keyword = serviceKeyword.value.trim()
  if (serviceState.value) params.status = serviceState.value
  const result = await api.get(`/merchant/services${queryString(params)}`)
  services.value = result.data?.list || []
  serviceTotal.value = Number(result.data?.total || 0)
  serviceTotalPages.value = Number(result.data?.totalPages || 1)
}

async function loadAppointments() {
  if (!outletId.value) return
  const params = { page: '1', pageSize: '100', outletId: outletId.value }
  const result = await api.get(`/merchant/appointments${queryString(params)}`)
  appointments.value = result.data?.list || []
  appointmentTotal.value = Number(result.data?.total || 0)
}

async function loadSummary() {
  if (!outletId.value) {
    pendingAppointmentTotal.value = 0
    revenue.value = 0
    transactionCount.value = 0
    return
  }
  const [pendingResult, revenueResult] = await Promise.all([
    api.get(`/merchant/appointments?page=1&pageSize=1&status=confirmed&outletId=${outletId.value}`),
    api.get(`/merchant/revenue/summary?type=service&outletId=${outletId.value}`)
  ])
  pendingAppointmentTotal.value = Number(pendingResult.data?.total || 0)
  revenue.value = Number(revenueResult.data?.revenue || 0)
  transactionCount.value = Number(revenueResult.data?.transactionCount || 0)
}

async function loadData() {
  if (!outletId.value) {
    services.value = []
    appointments.value = []
    serviceTotal.value = 0
    appointmentTotal.value = 0
    pendingAppointmentTotal.value = 0
    revenue.value = 0
    loading.value = false
    return
  }
  loading.value = true
  try {
    await Promise.all([
      loadServices(),
      loadAppointments(),
      loadSummary()
    ])
  } catch (error) {
    ui.toast(error.message || '读取服务数据失败', 'error')
  } finally {
    loading.value = false
  }
}

async function changeOutlet() {
  servicePage.value = 1
  if (outletId.value) {
    window.localStorage.setItem('merchant.serviceOutletId', String(outletId.value))
  }
  router.replace({
    path: '/merchant/services',
    query: {
      tab: view.value,
      ...(outletId.value ? { outletId: outletId.value } : {})
    }
  })
  await loadData()
}

function switchView(nextView) {
  if (view.value === nextView) return
  view.value = nextView
  router.replace({
    path: '/merchant/services',
    query: {
      tab: nextView,
      ...(outletId.value ? { outletId: outletId.value } : {})
    }
  })
}

function syncViewFromRoute(tab) {
  const nextView = resolveInitialView(tab)
  if (view.value === nextView) return
  view.value = nextView
}

watch(() => route.query.tab, syncViewFromRoute)
watch(() => route.query.outletId, (value) => {
  const next = resolveInitialOutlet(value)
  if (next === outletId.value) return
  outletId.value = next
  loadData()
})

function searchServices() {
  servicePage.value = 1
  loadServices().catch((error) => ui.toast(error.message, 'error'))
}

// 预约列表为前端筛选，点击「查询」时才把输入框的关键字应用上去
function searchAppointments() {
  appointmentQuery.value = appointmentKeyword.value.trim()
}

function changeServicePage(nextPage) {
  servicePage.value = nextPage
  loadServices().catch((error) => ui.toast(error.message, 'error'))
}

function openService(service = null) {
  editingId.value = service?.id || null
  form.value = service ? {
    id: service.id,
    outletId: service.shopId || Number(outletId.value) || null,
    name: service.name || '',
    categoryId: service.categoryId || null,
    description: service.description || '',
    price: service.price,
    durationMinutes: service.durationMinutes ?? '',
    imageUrl: service.imageUrl || '',
    targetPet: service.targetPet || 'ALL',
    lifeStage: service.lifeStage || 'ALL',
    healthTags: service.healthTags || ''
  } : {
    ...createEmptyForm(),
    outletId: Number(outletId.value) || null
  }
  modalOpen.value = true
  serviceGuard.markClean()
}

function closeService() {
  modalOpen.value = false
}

function openSchedule(service) {
  // 排班入口改为弹窗：面板针对单服务加载规则与时段，不再预载服务列表
  scheduleService.value = service
  scheduleOpen.value = true
}

async function uploadImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  serviceImageUploading.value = true
  try {
    if (!form.value.outletId) {
      ui.toast('请先选择服务店铺', 'warning')
      return
    }
    const result = await api.upload(`/service/image?outletId=${form.value.outletId}`, file)
    form.value.imageUrl = result.data
    ui.toast('服务图片已上传', 'success')
  } catch (error) {
    ui.toast(error.message || '图片上传失败', 'error')
  } finally {
    serviceImageUploading.value = false
    if (imageInput.value) imageInput.value.value = ''
  }
}

function validateService() {
  if (!form.value.outletId) {
    ui.toast('请选择服务店铺', 'warning')
    return false
  }
  if (!form.value.name.trim()) {
    ui.toast('请填写服务名称', 'warning')
    return false
  }
  if (!form.value.categoryId) {
    ui.toast('请选择服务分类', 'warning')
    return false
  }
  if (!Number.isFinite(Number(form.value.price)) || Number(form.value.price) <= 0) {
    ui.toast('服务价格必须大于 0', 'warning')
    return false
  }
  // 结构化时长是排班时段切分的一半依据（另一半是排班模板里的服务间隔）：范围与后端 DTO 校验保持一致
  const durationMinutes = Number(form.value.durationMinutes)
  if (!Number.isInteger(durationMinutes) || durationMinutes < 15 || durationMinutes > 480) {
    ui.toast('服务时长必须是 15~480 的整数分钟', 'warning')
    return false
  }
  return true
}

async function saveService() {
  if (savingService.value || !validateService()) return
  savingService.value = true
  try {
    const request = editingId.value ? api.put : api.post
    const { outletId: selectedOutletId, ...servicePayload } = form.value
    const result = await request(`/merchant/services?outletId=${selectedOutletId}`, {
      ...servicePayload,
      name: form.value.name.trim(),
      price: Number(form.value.price),
      durationMinutes: Number(form.value.durationMinutes),
      healthTags: form.value.healthTags.trim()
    })
    ui.toast(result.msg || (editingId.value ? '服务已更新' : '服务已发布'), 'success')
    closeService()
    await Promise.all([loadServices(), loadSummary()])
  } catch (error) {
    ui.toast(error.message || '保存服务失败', 'error')
  } finally {
    savingService.value = false
  }
}

async function toggleService(service) {
  if (busyServiceId.value) return
  const closing = service.status === 'on'
  const confirmed = await ui.confirm({
    title: closing ? '下架服务' : '上架服务',
    message: closing
      ? `确认下架「${service.name}」吗？下架后用户将无法预约该服务。`
      : `确认上架「${service.name}」吗？上架后服务将重新公开展示。`,
    confirmText: closing ? '确认下架' : '确认上架',
    danger: closing
  })
  if (!confirmed) return
  busyServiceId.value = service.id
  try {
    await api.put(`/merchant/services/${service.id}/toggle?outletId=${service.shopId}`)
    ui.toast(service.status === 'on' ? '服务已下架' : '服务已上架', 'success')
    await loadServices()
  } catch (error) {
    ui.toast(error.message || '更新服务状态失败', 'error')
  } finally {
    busyServiceId.value = null
  }
}

async function deleteService(service) {
  if (busyServiceId.value) return
  const confirmed = await ui.confirm({
    message: `确定删除「${service.name}」吗？删除后无法恢复。`,
    danger: true
  })
  if (!confirmed) return
  busyServiceId.value = service.id
  try {
    await api.delete(`/merchant/services/${service.id}?outletId=${service.shopId}`)
    ui.toast('服务已删除', 'success')
    if (services.value.length === 1 && servicePage.value > 1) servicePage.value -= 1
    await Promise.all([loadServices(), loadSummary()])
  } catch (error) {
    ui.toast(error.message || '删除服务失败', 'error')
  } finally {
    busyServiceId.value = null
  }
}

async function appointmentAction(item, action) {
  if (busyAppointmentId.value) return
  const confirmed = await ui.confirm({
    message: action === 'complete'
      ? `确认「${item.userName || '顾客'}」的「${orderServiceSummary(item)}」预约已经完成？\n预约时段：${appointmentSlotsLabel(item)}`
      : `确认取消「${item.userName || '顾客'}」的「${orderServiceSummary(item)}」预约？\n预约时段：${appointmentSlotsLabel(item)}\n取消后会释放预约时段。`,
    danger: action === 'cancel'
  })
  if (!confirmed) return
  busyAppointmentId.value = item.id
  try {
    await api.put(`/merchant/appointments/${item.id}/${action}?outletId=${item.shopId}`)
    ui.toast(action === 'complete' ? '预约已完成' : '预约已取消', 'success')
    await Promise.all([loadAppointments(), loadSummary()])
  } catch (error) {
    ui.toast(error.message || '更新预约状态失败', 'error')
  } finally {
    busyAppointmentId.value = null
  }
}

function openDetail(item) {
  detailItem.value = item
  detailOpen.value = true
}

function detailAction(action) {
  const item = detailItem.value
  if (!item) return
  detailOpen.value = false
  appointmentAction(item, action)
}

onMounted(async () => {
  try {
    await loadOrganization()
    await loadCategories().catch((error) => {
      ui.toast(error.message || '读取服务分类失败', 'warning')
    })
    await loadData()
    if (outletId.value) {
      await router.replace({
        path: '/merchant/services',
        query: { tab: view.value, outletId: outletId.value }
      })
    }
    if (route.query.create === '1' && outletId.value) {
      openService()
      await router.replace({
        path: '/merchant/services',
        query: { tab: 'services', outletId: outletId.value }
      })
    }
  } catch (error) {
    ui.toast(error.message || '读取服务页面失败', 'error')
    loading.value = false
  }
})

</script>

<template>
  <div class="console-page service-manage-page">
    <header class="console-head">
      <div>
        <h1>服务管理</h1>
      </div>
      <div class="console-head-actions">
        <label v-if="openOutlets.length" class="outlet-select">
          <Store :size="15" class="outlet-select-icon" />
          <select v-model="outletId" aria-label="切换服务店铺" @change="changeOutlet">
            <option v-for="shop in openOutlets" :key="shop.id" :value="String(shop.id)">
              {{ shop.name }}
            </option>
          </select>
          <ChevronDown :size="15" class="outlet-select-arrow" />
        </label>
        <button
          v-if="openOutlets.length"
          class="btn btn-primary"
          type="button"
          @click="openService()"
        >
          <Plus :size="16" />新增服务
        </button>
      </div>
    </header>

    <LoadingPanel v-if="loading" label="正在读取服务数据" />
    <template v-else>
      <EmptyState
        v-if="!openOutlets.length"
        title="当前没有可经营的服务店铺"
        :description="unavailableOutlets.length
          ? '店铺正在审核或已关闭，审核通过并营业后才能管理服务。'
          : '先创建服务店铺，再发布服务项目。'"
      >
        <button class="btn btn-primary" type="button" @click="router.push('/merchant/shops')">
          前往店铺管理
        </button>
      </EmptyState>

      <template v-else>
        <section class="service-metrics" aria-label="服务经营摘要">
          <article>
            <span><Sparkles :size="18" /></span>
            <div><small>服务项目</small><b>{{ serviceTotal }}</b></div>
          </article>
          <article>
            <span><CalendarClock :size="18" /></span>
            <div><small>待服务预约</small><b>{{ pendingAppointmentTotal }}</b></div>
          </article>
          <article>
            <span><Wallet :size="18" /></span>
            <div>
              <small>累计营业额</small>
              <b>¥{{ formatPrice(revenue) }}</b>
              <span class="metric-sub">交易数 {{ transactionCount }} 单</span>
            </div>
          </article>
        </section>

        <div class="list-toolbar">
          <div class="console-tabs" role="tablist" aria-label="服务管理栏目">
            <button
              type="button"
              role="tab"
              :aria-selected="view === 'services'"
              :class="{ active: view === 'services' }"
              @click="switchView('services')"
            >
              服务项目 <span>{{ serviceTotal }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="view === 'appointments'"
              :class="{ active: view === 'appointments' }"
              @click="switchView('appointments')"
            >
              预约安排 <span>{{ pendingAppointmentTotal }}</span>
            </button>
          </div>
          <div v-if="view === 'services'" class="toolbar-filters">
            <label class="console-select">
              <select v-model="serviceState" aria-label="服务状态" @change="searchServices">
                <option value="">全部状态</option>
                <option value="on">在售</option>
                <option value="off">已下架</option>
              </select>
              <ChevronDown :size="16" />
            </label>
            <label class="search-field">
              <Search :size="17" />
              <input
                v-model="serviceKeyword"
                type="search"
                placeholder="搜索服务或健康标签"
                @keyup.enter="searchServices"
              />
            </label>
            <button class="btn btn-primary console-search-btn" type="button" @click="searchServices">查询</button>
          </div>
          <div v-else class="toolbar-filters">
            <label class="console-select">
              <select v-model="appointmentState" aria-label="预约状态">
                <option v-for="state in APPOINTMENT_STATES" :key="state.value" :value="state.value">
                  {{ state.label }}
                </option>
              </select>
              <ChevronDown :size="16" />
            </label>
            <label class="search-field">
              <Search :size="17" />
              <input
                v-model="appointmentKeyword"
                type="search"
                placeholder="搜索顾客或预约号"
                @keyup.enter="searchAppointments"
              />
            </label>
            <button class="btn btn-primary console-search-btn" type="button" @click="searchAppointments">查询</button>
          </div>
        </div>

        <template v-if="view === 'services'">
          <div v-if="services.length" class="service-list">
            <article
              v-for="service in services"
              :key="service.id"
              class="service-row"
              :class="{ 'is-off': service.status !== 'on' }"
            >
              <div class="service-thumb">
                <img v-if="service.imageUrl" :src="assetUrl(service.imageUrl)" :alt="service.name" />
                <Sparkles v-else :size="24" />
              </div>
              <div class="service-main">
                <div class="service-title">
                  <h3>{{ service.name }}</h3>
                  <span class="status-label" :class="productStatus(service.status).tone">
                    {{ productStatus(service.status).label }}
                  </span>
                </div>
                <div class="service-meta">
                  <span class="service-category">{{ service.categoryName || service.category || '未分类' }}</span>
                  <span>{{ service.duration || '时长待确认' }}</span>
                  <span>{{ service.targetPet === 'CAT' ? '猫咪' : service.targetPet === 'DOG' ? '狗狗' : '猫狗通用' }}</span>
                </div>
              </div>
              <div class="service-price">
                <small>单价</small>
                <b>¥{{ formatPrice(service.price) }}</b>
              </div>
              <div class="service-actions">
                <button type="button" class="row-action primary" @click="openService(service)">
                  <PenLine :size="13" />编辑
                </button>
                <button type="button" class="row-action" @click="openSchedule(service)">
                  <CalendarDays :size="13" />排班
                </button>
                <button
                  type="button"
                  class="row-action"
                  :disabled="busyServiceId === service.id"
                  @click="toggleService(service)"
                >
                  <Power :size="13" />{{ service.status === 'on' ? '下架' : '上架' }}
                </button>
                <button
                  type="button"
                  class="row-action danger"
                  :disabled="busyServiceId === service.id"
                  @click="deleteService(service)"
                >
                  <Trash2 :size="13" />删除
                </button>
              </div>
            </article>
          </div>
          <EmptyState
            v-else
            title="没有符合条件的服务"
            description="调整搜索条件，或发布一个新服务。"
          >
            <button class="btn btn-primary" type="button" @click="openService()">新增服务</button>
          </EmptyState>
          <PaginationBar
            v-if="serviceTotalPages > 1"
            :page="servicePage"
            :total-pages="serviceTotalPages"
            @change="changeServicePage"
          />
        </template>

        <template v-else>
          <div v-if="filteredAppointments.length" class="appt-board">
            <section
              v-for="group in appointmentGroups"
              :key="group.date"
              class="appt-group"
              :class="{ 'is-today': group.isToday }"
            >
              <header class="appt-group-head">
                <span class="appt-group-day">
                  <b>{{ group.day }}</b>
                  <small>{{ group.month }}月</small>
                </span>
                <div class="appt-group-copy">
                  <strong>{{ group.label }}</strong>
                  <small>{{ group.summary }}</small>
                </div>
                <span class="appt-group-line"></span>
              </header>

              <div class="appt-list">
                <article
                  v-for="item in group.items"
                  :key="item.id"
                  class="appt-item"
                  :class="{ 'is-muted': item.status !== 'confirmed' }"
                >
                  <div class="appt-time">
                    <b>{{ appointmentClock(item) }}</b>
                    <small>{{ appointmentDurationLabel(item) || '时段待定' }}</small>
                  </div>
                  <span class="appt-dot" aria-hidden="true"></span>
                  <div class="appt-card">
                    <div class="appt-card-main">
                      <div class="appt-card-title">
                        <h3>{{ orderServiceSummary(item) }}</h3>
                        <span class="status-label" :class="appointmentStatus(item.status).tone">
                          {{ appointmentStatus(item.status).label }}
                        </span>
                        <span v-if="appointmentSoon(item)" class="soon-chip">即将开始</span>
                      </div>
                      <div class="appt-card-meta">
                        <span class="appt-avatar">{{ (item.userName || '顾').slice(0, 1) }}</span>
                        <b>{{ item.userName || '顾客' }}</b>
                        <span class="appt-sep">·</span>
                        <span v-if="item.appointmentPetName">宠物：{{ item.appointmentPetName }}</span>
                        <span v-if="item.appointmentPetName" class="appt-sep">·</span>
                        <span>预约号 {{ item.orderNo }}</span>
                      </div>
                    </div>
                    <div class="appt-card-side">
                      <b>¥{{ formatPrice(item.totalAmount) }}</b>
                      <div class="appt-actions">
                        <button class="row-action" type="button" @click="openDetail(item)">
                          <Eye :size="13" />详情
                        </button>
                        <button
                          v-if="item.status === 'confirmed'"
                          class="row-action primary"
                          type="button"
                          :disabled="busyAppointmentId === item.id"
                          @click="appointmentAction(item, 'complete')"
                        >
                          {{ busyAppointmentId === item.id ? '处理中' : '完成服务' }}
                        </button>
                        <button
                          v-if="item.status === 'confirmed'"
                          class="row-action danger"
                          type="button"
                          :disabled="busyAppointmentId === item.id"
                          @click="appointmentAction(item, 'cancel')"
                        >
                          取消预约
                        </button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>
          <EmptyState
            v-else
            :title="appointmentQuery
              ? '没有匹配的预约'
              : appointmentState
                ? `暂无${APPOINTMENT_STATES.find(state => state.value === appointmentState)?.label}预约`
                : '今天起暂无预约'"
            :description="appointmentQuery
              ? '换个顾客姓名、预约号或服务名称试试。'
              : '只显示今天及之后的预约，历史预约不在此列表。'"
          />
        </template>
      </template>
    </template>

    <ModalDialog
      :open="detailOpen"
      title="预约详情"
      width="520px"
      @close="detailOpen = false"
    >
      <div v-if="detailItem" class="appointment-detail">
        <header>
          <span class="detail-avatar">{{ (detailItem.userName || '顾').slice(0, 1) }}</span>
          <div>
            <b>{{ detailItem.userName || '顾客' }}</b>
            <small>{{ detailItem.shopName || currentShop?.name || '服务店铺' }}</small>
          </div>
          <span class="status-label" :class="appointmentStatus(detailItem.status).tone">
            {{ appointmentStatus(detailItem.status).label }}
          </span>
        </header>
        <dl>
          <div><dt>预约号</dt><dd>{{ detailItem.orderNo }}</dd></div>
          <div><dt>预约宠物</dt><dd>{{ detailItem.appointmentPetName || '未记录' }}</dd></div>
          <div><dt>服务项目</dt><dd>{{ orderServiceSummary(detailItem) }}</dd></div>
          <div><dt>预约时段</dt><dd>{{ appointmentSlotsLabel(detailItem) }}</dd></div>
          <div><dt>金额</dt><dd>¥{{ formatPrice(detailItem.totalAmount) }}</dd></div>
          <div><dt>下单时间</dt><dd>{{ formatDateTime(detailItem.createdAt) }}</dd></div>
        </dl>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="detailOpen = false">关闭</button>
        <button
          v-if="detailItem?.status === 'confirmed'"
          class="btn btn-ghost"
          type="button"
          @click="detailAction('cancel')"
        >
          取消预约
        </button>
        <button
          v-if="detailItem?.status === 'confirmed'"
          class="btn btn-primary"
          type="button"
          @click="detailAction('complete')"
        >
          完成服务
        </button>
      </template>
    </ModalDialog>

    <ModalDialog
      :open="modalOpen"
      :title="editingId ? '编辑服务' : '新增服务'"
      width="620px"
      :close-disabled="savingService || serviceImageUploading"
      @close="serviceGuard.requestClose"
    >
      <label class="field">
        <span>服务店铺</span>
        <select v-model.number="form.outletId" class="select" :disabled="true">
          <option :value="form.outletId">{{ currentShop?.name || '当前店铺' }}</option>
        </select>
      </label>
      <div class="form-row">
        <label class="field">
          <span>服务名称</span>
          <input v-model="form.name" class="input" maxlength="100" />
        </label>
        <label class="field">
          <span>服务分类</span>
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
          <span>服务价格</span>
          <input v-model="form.price" class="input" type="number" min="0.01" step="0.01" />
        </label>
        <label class="field">
          <span>服务时长（分钟）</span>
          <input v-model="form.durationMinutes" class="input" type="number" min="15" max="480" step="5" placeholder="15~480" />
        </label>
      </div>
      <div class="form-row">
        <label class="field">
          <span>适用宠物</span>
          <select v-model="form.targetPet" class="select">
            <option value="CAT">猫咪适用</option>
            <option value="DOG">狗狗适用</option>
            <option value="ALL">猫狗通用</option>
          </select>
        </label>
        <label class="field">
          <span>适用阶段</span>
          <select v-model="form.lifeStage" class="select">
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
          placeholder="自由填写，例如：美毛,低敏,皮肤护理"
        />
      </label>
      <label class="field">
        <span>服务说明</span>
        <textarea v-model="form.description" class="textarea" maxlength="2000" />
      </label>
      <div class="field">
        <span>服务图片</span>
        <input ref="imageInput" type="file" accept="image/*" hidden @change="uploadImage" />
        <button class="image-upload" type="button" :disabled="serviceImageUploading" @click="imageInput?.click()">
          <img v-if="form.imageUrl" :src="assetUrl(form.imageUrl)" alt="服务图片预览" />
          <span v-else><ImagePlus :size="20" />上传服务图片</span>
        </button>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="savingService || serviceImageUploading" @click="serviceGuard.requestClose">
          取消
        </button>
        <button class="btn btn-primary" type="button" :disabled="savingService || serviceImageUploading" @click="saveService">
          {{ savingService ? '正在保存' : editingId ? '保存修改' : '发布服务' }}
        </button>
      </template>
    </ModalDialog>

    <ModalDialog
      :open="scheduleOpen"
      title="服务排班"
      width="720px"
      @close="scheduleOpen = false"
    >
      <template #default>
        <MerchantAppointmentSchedulePanel
          v-if="scheduleService"
          :service-id="scheduleService.id"
          :duration-minutes="scheduleService.durationMinutes"
          @saved="loadServices().catch(() => {})"
        />
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
/* 服务店铺下拉选择：白底描边，点击展开切换门店 */
.outlet-select {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 11px;
  border: 1.5px solid var(--color-border);
  border-radius: 11px;
  background: var(--color-surface-raised);
  color: var(--color-text);
  cursor: pointer;
  transition: var(--transition-base);
}

.outlet-select:hover {
  border-color: var(--color-primary);
}

.outlet-select:focus-within {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-focus);
}

.outlet-select select {
  appearance: none;
  border: 0;
  outline: 0;
  padding-right: 2px;
  max-width: 190px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  cursor: pointer;
}

.outlet-select-icon,
.outlet-select-arrow {
  flex: 0 0 auto;
  color: var(--color-text-muted);
}
.service-manage-page { display: grid; gap: var(--console-gap); }

.service-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.service-metrics article {
  min-height: 78px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: var(--box-shadow-sm);
}
.service-metrics article > span {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  display: grid;
  place-items: center;
}
.service-metrics small, .service-metrics b { display: block; }
.service-metrics small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.service-metrics b { font-size: var(--font-size-2xl); }
.service-metrics .metric-sub { display: block; margin-top: 2px; color: var(--color-text-muted); font-size: 0.75rem; }

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

.service-list { display: grid; gap: 12px; }
.service-row {
  position: relative;
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) auto auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px 14px 18px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-success);
  border-radius: var(--radius-console-card);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.service-row:hover {
  transform: translateY(-1px);
  border-color: var(--color-border-strong);
  box-shadow: 0 10px 24px rgba(92, 63, 14, 0.1);
}
.service-row.is-off { border-left-color: var(--color-border-strong); background: var(--color-surface-ivory); }
.service-thumb {
  width: 80px;
  height: 80px;
  border-radius: 14px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, var(--color-surface-accent), var(--color-surface-warm));
  border: 1px solid var(--color-border-primary-subtle);
  color: var(--color-primary-strong);
}
.service-thumb img { width: 100%; height: 100%; object-fit: cover; }
.service-row.is-off .service-thumb { filter: grayscale(0.5); opacity: 0.85; }
.service-main { min-width: 0; display: grid; gap: 6px; }
.service-title { display: flex; align-items: center; gap: 8px; min-width: 0; }
.service-title h3 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.01em;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.service-title .status-label { flex: 0 0 auto; }
.service-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--color-text-muted); font-size: 0.75rem; line-height: 1.5; }
.service-meta > span + span::before { content: "·"; margin-right: 8px; color: var(--color-border-strong); }
.service-meta-sub { color: var(--color-text-secondary-translucent); }
.service-category {
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 0.7rem;
  font-weight: var(--font-weight-bold);
}
.soon-chip {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: 0.68rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.02em;
}
.service-price {
  display: grid;
  justify-items: end;
  gap: 1px;
  padding-left: 16px;
  border-left: 1px dashed var(--color-border-soft);
  white-space: nowrap;
}
.service-price small { color: var(--color-text-muted); font-size: 0.68rem; letter-spacing: 0.08em; }
.service-price b { font-family: var(--font-family-display); font-size: var(--font-size-xl); color: var(--color-primary-strong); }
.service-actions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }
.row-action {
  min-height: 30px;
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.row-action:hover:not(:disabled) { border-color: var(--color-border-strong); background: var(--color-surface-warm); color: var(--color-text); }
.row-action:disabled { opacity: 0.5; cursor: not-allowed; }
.row-action.primary { border-color: var(--color-border-primary-soft); background: var(--color-primary-soft); color: var(--color-primary-strong); }
.row-action.primary:hover:not(:disabled) { border-color: var(--color-primary); background: var(--color-primary); color: var(--color-text); }
.row-action.danger { border-color: transparent; background: transparent; color: var(--color-danger); }
.row-action.danger:hover:not(:disabled) { border-color: var(--color-border-danger-soft); background: var(--color-danger-soft); color: var(--color-danger); }

.status-label {
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--color-surface-warm);
  color: var(--color-text-secondary);
  display: inline-block;
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
}
.status-label.success { background: var(--color-success-soft); color: var(--color-success); }
.status-label.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-label.info { background: var(--color-info-soft); color: var(--color-info); }
.status-label.danger { background: var(--color-danger-soft); color: var(--color-danger); }

.appt-board { display: grid; gap: 24px; }
.appt-group { display: grid; gap: 10px; }
.appt-group-head { display: flex; align-items: center; gap: 12px; }
.appt-group-day {
  width: 54px;
  height: 54px;
  flex: 0 0 auto;
  border: 1px solid var(--color-border);
  border-radius: 15px;
  background: var(--color-surface-warm);
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 1px;
}
.appt-group-day b { font-family: var(--font-family-display); font-size: 1.375rem; line-height: 1; color: var(--color-text); }
.appt-group-day small { color: var(--color-text-muted); font-size: 0.65rem; }
.appt-group.is-today .appt-group-day { border-color: var(--color-text); background: var(--color-text); }
.appt-group.is-today .appt-group-day b { color: var(--color-primary); }
.appt-group.is-today .appt-group-day small { color: var(--color-console-text); }
.appt-group-copy { display: grid; gap: 2px; min-width: 0; }
.appt-group-copy strong { font-size: var(--font-size-sm); letter-spacing: 0.01em; }
.appt-group-copy small { color: var(--color-text-muted); font-size: 0.72rem; }
.appt-group-line { flex: 1; height: 1px; background: linear-gradient(90deg, var(--color-border), transparent); }

.appt-list { display: grid; gap: 8px; }
.appt-item {
  display: grid;
  grid-template-columns: 58px 14px minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
}
.appt-time { padding-top: 15px; display: grid; gap: 2px; justify-items: end; }
.appt-time b { font-family: var(--font-family-display); font-size: var(--font-size-lg); line-height: 1.1; color: var(--color-text); }
.appt-time small { color: var(--color-text-muted); font-size: 0.68rem; }
.appt-dot { position: relative; display: grid; justify-items: center; padding-top: 19px; }
.appt-dot::before { content: ""; position: absolute; top: 0; bottom: 0; width: 1px; background: var(--color-border-soft); }
.appt-dot::after {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 0 4px var(--color-surface);
}
.appt-item.is-muted .appt-dot::after { background: var(--color-border-strong); }

.appt-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 5px 16px 16px 5px;
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.appt-card:hover {
  transform: translateY(-1px);
  border-color: var(--color-border-strong);
  box-shadow: 0 8px 20px rgba(92, 63, 14, 0.09);
}
.appt-item.is-muted .appt-card { background: var(--color-surface-ivory); }
.appt-card-main { min-width: 0; flex: 1; display: grid; gap: 5px; }
.appt-card-title { display: flex; align-items: center; gap: 8px; min-width: 0; }
.appt-card-title h3 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: var(--font-size-lg);
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.appt-item.is-muted .appt-card-title h3 { color: var(--color-text-secondary); }
.appt-card-meta { display: flex; align-items: center; gap: 6px; min-width: 0; color: var(--color-text-muted); font-size: 0.75rem; }
.appt-card-meta b { color: var(--color-text); font-weight: 600; }
.appt-avatar {
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 0.62rem;
  font-weight: var(--font-weight-bold);
}
.appt-sep { color: var(--color-border-strong); }
.appt-card-side { flex: 0 0 auto; display: flex; align-items: center; gap: 12px; }
.appt-card-side > b { font-family: var(--font-family-display); font-size: var(--font-size-lg); color: var(--color-primary-strong); white-space: nowrap; }
.appt-actions { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.appointment-detail { display: grid; gap: 18px; }
.appointment-detail header { display: flex; align-items: center; gap: 10px; }
.appointment-detail header b { display: block; font-size: var(--font-size-base); }
.appointment-detail header small { color: var(--color-text-muted); font-size: 0.75rem; }
.appointment-detail header .status-label { margin-left: auto; }
.detail-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--font-weight-bold);
}
.appointment-detail dl { margin: 0; display: grid; gap: 11px; }
.appointment-detail dl > div { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 10px; align-items: baseline; }
.appointment-detail dt { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.appointment-detail dd { margin: 0; color: var(--color-text); font-size: var(--font-size-sm); line-height: 1.6; word-break: break-word; }

.image-upload { width: 100%; min-height: 110px; border: 1.5px dashed var(--color-border-strong); border-radius: 14px; background: var(--color-surface-warm); color: var(--color-text-muted); }
.image-upload img { width: 100%; max-height: 180px; object-fit: contain; }
.image-upload span { display: flex; justify-content: center; align-items: center; gap: 7px; }

@media (max-width: 980px) {
  .service-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .service-row { grid-template-columns: 72px minmax(0, 1fr) auto auto; gap: 12px; }
  .service-thumb { width: 72px; height: 72px; }
  .list-toolbar { flex-wrap: wrap; }
  .toolbar-filters { flex: 1 1 100%; justify-content: flex-start; }
  .search-field { flex: 1 1 180px; width: auto; }
}
@media (max-width: 760px) {
  .service-metrics { grid-template-columns: 1fr; }
  .service-row { grid-template-columns: 64px minmax(0, 1fr); gap: 8px 12px; }
  .service-thumb { width: 64px; height: 64px; grid-row: 1 / span 3; align-self: start; }
  .service-price, .service-actions { grid-column: 2; }
  .service-price { justify-items: start; padding-left: 0; border-left: 0; }
  .service-price small { display: none; }
  .appt-item { grid-template-columns: 46px 10px minmax(0, 1fr); gap: 8px; }
  .appt-time b { font-size: var(--font-size-base); }
  .appt-card { flex-direction: column; align-items: stretch; gap: 10px; padding: 12px; }
  .appt-card-side { justify-content: space-between; }
  .appt-group-day { width: 46px; height: 46px; border-radius: 13px; }
  .appt-group-day b { font-size: 1.2rem; }
  .service-actions { justify-content: flex-start; }
  .list-toolbar { align-items: stretch; flex-direction: column; }
  .toolbar-filters { width: 100%; flex-wrap: wrap; }
  .console-select { flex: 1 1 100%; }
  .console-select select { width: 100%; }
  .search-field { flex: 1 1 100%; }
}
</style>
