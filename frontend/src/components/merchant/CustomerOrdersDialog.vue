<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays, Package, UserRound } from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { formatDateTime, formatPrice } from '../../utils/format'
import { appointmentStatus, orderItemSummary, orderServiceSummary, orderStatus } from '../../utils/merchantStatus'
import ModalDialog from '../ModalDialog.vue'
import LoadingPanel from '../LoadingPanel.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  customer: { type: Object, default: null }
})
const emit = defineEmits(['close'])

const loading = ref(false)
const records = ref([])
const errorText = ref('')

// 会话类型决定这里只看一种业务：商城客服看商品订单，服务咨询看服务预约
const isService = computed(() => props.customer?.category === 'service')
const customerName = computed(() => props.customer?.otherUserName || props.customer?.name || '客户')
const customerAvatar = computed(() => props.customer?.otherUserAvatar || props.customer?.avatar || '')
const businessLabel = computed(() => (isService.value ? '服务预约' : '商城订单'))
const totalAmount = computed(() => (
  records.value.reduce((sum, item) => sum + Number(item.totalAmount || 0), 0)
))
const pendingCount = computed(() => records.value.filter(
  (item) => item.status === (isService.value ? 'confirmed' : 'paid')
).length)

async function load() {
  records.value = []
  errorText.value = ''
  const customerId = props.customer?.otherUserId || props.customer?.userId
  if (!customerId) return
  loading.value = true
  try {
    const path = isService.value
      ? `/merchant/appointments?page=1&pageSize=20&userId=${customerId}`
      : `/merchant/orders?page=1&pageSize=20&userId=${customerId}`
    const result = await api.get(path)
    const list = result.data?.list || []
    // 后端已按 userId 过滤时结果不变；旧实例忽略参数时在此兜底
    records.value = list.filter((item) => (
      item.userId == null || Number(item.userId) === Number(customerId)
    ))
  } catch (error) {
    errorText.value = error.message || `${businessLabel.value}暂不可用`
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.customer?.otherUserId, props.customer?.category], ([open]) => {
  if (open) load()
}, { immediate: false })

function close() {
  emit('close')
}

function recordTime(record) {
  if (isService.value) {
    const slot = record.appointmentSlots?.[0]
    if (slot?.slotDate) {
      return `${String(slot.slotDate).slice(5).replace('-', '/')} ${String(slot.startTime || '').slice(0, 5)}`
    }
    return record.appointmentTime ? formatDateTime(record.appointmentTime).slice(0, 16) : '—'
  }
  return record.createdAt ? formatDateTime(record.createdAt).slice(0, 16) : '—'
}

function recordTitle(record) {
  return isService.value ? orderServiceSummary(record) : orderItemSummary(record)
}

function recordStatus(record) {
  return isService.value ? appointmentStatus(record.status) : orderStatus(record.status)
}
</script>

<template>
  <ModalDialog
    :open="open"
    :title="`${customerName} · ${businessLabel}`"
    width="820px"
    body-class="customer-order-body"
    @close="close"
  >
    <div class="customer-summary">
      <span class="customer-avatar">
        <img v-if="customerAvatar" :src="assetUrl(customerAvatar)" alt="" />
        <UserRound v-else :size="20" />
      </span>
      <div class="customer-copy">
        <b>{{ customerName }}</b>
        <small>{{ isService ? '服务咨询客户' : '商城客户' }}</small>
      </div>
      <div class="summary-stats">
        <span><small>{{ businessLabel }}</small><b>{{ records.length }}</b></span>
        <span><small>累计金额</small><b>¥{{ formatPrice(totalAmount) }}</b></span>
        <span v-if="pendingCount">
          <small>{{ isService ? '待服务' : '待发货' }}</small><b>{{ pendingCount }}</b>
        </span>
      </div>
    </div>

    <LoadingPanel v-if="loading" :label="`正在读取${businessLabel}`" />
    <template v-else>
      <div v-if="records.length" class="order-table">
        <div class="order-row head">
          <span>{{ isService ? '预约号' : '订单号' }}</span>
          <span>{{ isService ? '服务' : '商品' }}</span>
          <span>金额</span>
          <span>状态</span>
          <span>{{ isService ? '预约时间' : '下单时间' }}</span>
        </div>
        <div v-for="record in records" :key="record.id" class="order-row">
          <span class="mono">{{ record.orderNo }}</span>
          <span class="order-copy">
            <b>{{ recordTitle(record) }}</b>
            <small>{{ record.shopName || (isService ? '服务店铺' : '商城店铺') }}</small>
          </span>
          <span class="price">¥{{ formatPrice(record.totalAmount) }}</span>
          <span>
            <em class="status-label" :class="recordStatus(record).tone">{{ recordStatus(record).label }}</em>
          </span>
          <span class="time">{{ recordTime(record) }}</span>
        </div>
      </div>
      <p v-else-if="errorText" class="empty-line">{{ errorText }}</p>
      <p v-else class="empty-line">这位客户还没有{{ businessLabel }}。</p>
    </template>

  </ModalDialog>
</template>

<style scoped>
.customer-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-md);
  background: var(--color-surface-warm);
  margin-bottom: 14px;
}
.customer-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--color-surface);
  color: var(--color-primary-strong);
}
.customer-avatar img { width: 100%; height: 100%; object-fit: cover; }
.customer-copy { min-width: 0; }
.customer-copy b { display: block; font-size: var(--font-size-base); }
.customer-copy small { display: block; margin-top: 2px; color: var(--color-text-muted); font-size: 0.75rem; }
.summary-stats { margin-left: auto; display: flex; gap: 18px; }
.summary-stats span { text-align: right; }
.summary-stats small { display: block; color: var(--color-text-muted); font-size: 0.72rem; }
.summary-stats b { display: block; margin-top: 1px; font-family: var(--font-family-display); font-size: var(--font-size-lg); }

.order-table {
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-console-panel);
  overflow: hidden;
}
.order-row {
  display: grid;
  grid-template-columns: 156px minmax(0, 1fr) 92px 82px 104px;
  gap: 10px;
  align-items: center;
  padding: 9px 13px;
  border-bottom: 1px solid var(--color-border-soft);
  font-size: 0.75rem;
}
.order-row:last-child { border-bottom: 0; }
.order-row.head {
  color: var(--color-text-muted);
  font-weight: var(--font-weight-bold);
  background: var(--color-surface-ivory);
  font-size: 0.7rem;
}
.order-row .mono { font-variant-numeric: tabular-nums; color: var(--color-text-secondary); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.order-copy { min-width: 0; }
.order-copy b, .order-copy small { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.order-copy b { font-size: 0.8rem; }
.order-copy small { margin-top: 2px; color: var(--color-text-muted); font-size: 0.7rem; }
.order-row .price { color: var(--color-primary-strong); font-weight: var(--font-weight-bold); }
.order-row .time { color: var(--color-text-muted); }
.empty-line { margin: 0; padding: 26px 13px; color: var(--color-text-muted); text-align: center; font-size: var(--font-size-sm); }

.status-label {
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--color-surface-warm);
  color: var(--color-text-secondary);
  display: inline-block;
  font-style: normal;
  font-size: 0.7rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
}
.status-label.success { background: var(--color-success-soft); color: var(--color-success); }
.status-label.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-label.info { background: var(--color-info-soft); color: var(--color-info); }
.status-label.danger { background: var(--color-danger-soft); color: var(--color-danger); }

@media (max-width: 760px) {  .customer-summary { align-items: flex-start; flex-wrap: wrap; }
  .summary-stats { width: 100%; margin-left: 0; justify-content: space-between; }
  .order-row { grid-template-columns: 110px minmax(0, 1fr) 80px; }
  .order-row.head span:nth-child(4),
  .order-row.head span:nth-child(5),
  .order-row > span:nth-child(4),
  .order-row > span:nth-child(5) { display: none; }
}
</style>
