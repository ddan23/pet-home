<script setup>
import { computed } from 'vue'
import { CalendarCheck, MapPin, PawPrint, ShoppingBag } from 'lucide-vue-next'
import { formatPrice, parseJson } from '../utils/format'

const props = defineProps({
  order: {
    type: Object,
    required: true
  },
  busy: {
    type: Boolean,
    default: false
  }
})

defineEmits(['action'])

const statusLabels = {
  pending_payment: '待支付',
  paid: '待发货',
  shipped: '待收货',
  completed: '已完成',
  cancelled: '已取消',
  confirmed: '已预约'
}

const statusLabel = computed(() => statusLabels[props.order.status] || props.order.status)
const orderTypeLabel = computed(() => (
  props.order.orderType === 'booking' ? '服务预约' : '商品订单'
))
const orderItems = computed(() => parseJson(props.order.items, []))
const isBooking = computed(() => props.order.orderType === 'booking')
const appointmentSlots = computed(() => props.order.appointmentSlots || [])

function formatSlotTime(value) {
  return String(value || '').slice(0, 5)
}

function formatSlotDate(value) {
  if (!value) return ''
  const [year, month, day] = String(value).split('-')
  return `${month}月${day}日`
}

function formatLegacyAppointment(value) {
  return String(value || '').replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <article class="order-record">
    <header>
      <div class="order-identity">
        <span class="order-icon">
          <CalendarCheck v-if="isBooking" :size="18" />
          <ShoppingBag v-else :size="18" />
        </span>
        <div>
          <b>{{ orderTypeLabel }}</b>
          <small>{{ order.orderNo }}</small>
        </div>
      </div>
      <span class="order-status" :class="{ warning: order.status === 'pending_payment' }">
        {{ statusLabel }}
      </span>
    </header>
    <div class="order-products">
      <span v-for="item in orderItems" :key="`${order.id}-${item.productId || item.name}`">
        {{ item.name }} × {{ item.quantity || 1 }}
      </span>
    </div>
    <div v-if="isBooking" class="appointment-slots">
      <span v-if="order.appointmentPetName" class="appointment-slot">
        <PawPrint :size="14" aria-hidden="true" />
        预约宠物：{{ order.appointmentPetName }}
      </span>
      <span
        v-for="slot in appointmentSlots"
        :key="slot.id"
        class="appointment-slot"
      >
        <CalendarCheck :size="14" aria-hidden="true" />
        {{ formatSlotDate(slot.slotDate) }}
        {{ formatSlotTime(slot.startTime) }}–{{ formatSlotTime(slot.endTime) }}
      </span>
      <span v-if="appointmentSlots.length === 0 && order.appointmentTime" class="appointment-slot">
        <CalendarCheck :size="14" aria-hidden="true" />
        历史预约 {{ formatLegacyAppointment(order.appointmentTime) }}
      </span>
      <span v-if="order.shopAddress" class="appointment-address">
        <MapPin :size="14" aria-hidden="true" />
        到店地址：{{ order.shopAddress }}
      </span>
    </div>
    <footer>
      <div class="order-shop">
        <span>{{ order.shopName }}</span>
        <b>¥{{ formatPrice(order.totalAmount) }}</b>
      </div>
      <div class="order-actions">
        <small
          v-if="!isBooking && order.status === 'pending_payment'"
          class="payment-deadline"
        >
          请在创建后 10 分钟内完成支付
        </small>
        <button
          v-if="order.status === 'pending_payment'"
          class="btn btn-primary btn-sm"
          type="button"
          :disabled="busy"
          @click="$emit('action', order, 'pay')"
        >
          {{ busy ? '处理中…' : '支付' }}
        </button>
        <button
          v-if="(isBooking && order.status === 'confirmed') || (!isBooking && order.status === 'pending_payment')"
          class="btn btn-ghost btn-sm"
          type="button"
          :disabled="busy"
          @click="$emit('action', order, 'cancel')"
        >
          {{ isBooking ? '取消预约' : '取消订单' }}
        </button>
        <button
          v-if="!isBooking && order.status === 'paid'"
          class="btn btn-ghost btn-sm"
          type="button"
          :disabled="busy"
          @click="$emit('action', order, 'remind-shipment')"
        >
          提醒发货
        </button>
        <button
          v-if="!isBooking && order.status === 'shipped'"
          class="btn btn-primary btn-sm"
          type="button"
          :disabled="busy"
          @click="$emit('action', order, 'confirm')"
        >
          {{ busy ? '处理中…' : '确认收货' }}
        </button>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.order-record {
  padding: 19px 21px;
  display: grid;
  gap: 13px;
  border: 1px solid var(--color-border);
  border-radius: 7px 20px 20px 7px;
  background: var(--color-surface);
}

.order-record header,
.order-record footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
}

.order-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.order-identity b,
.order-identity small {
  display: block;
}

.order-identity b {
  font-size: var(--font-size-sm);
}

.order-identity small {
  margin-top: 2px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.order-status {
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--color-success-soft);
  color: var(--color-success);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-black);
}

.order-status.warning {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.order-products {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.order-products span {
  padding: 6px 10px;
  border-radius: 9px;
  background: var(--color-surface-warm);
  font-size: var(--font-size-sm);
}

.appointment-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.appointment-slot {
  min-height: 34px;
  padding: 6px 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--color-border-primary-subtle);
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.appointment-address {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 5px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.appointment-address svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--color-primary-strong);
}

.order-record footer {
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.order-shop {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.order-shop span {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.order-shop b {
  color: var(--color-primary-strong);
  font-family: var(--font-family-display);
  font-size: var(--font-size-xl);
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.payment-deadline {
  color: var(--color-warning);
  font-size: var(--font-size-sm);
}

@media (max-width: 520px) {
  .order-record header,
  .order-record footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .order-actions {
    width: 100%;
  }
}
</style>
