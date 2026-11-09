<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertCircle,
  CalendarDays,
  Check,
  Clock3,
  MapPin,
  PawPrint,
  RefreshCw
} from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { formatPrice, parseJson } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'

const props = defineProps({
  service: {
    type: Object,
    required: true
  }
})

const router = useRouter()
const route = useRoute()
const emit = defineEmits(['busy-change'])
const ui = useUiStore()
const {
  isUser,
  isNonUserAccount,
  requireUser
} = useUserAccess()
const slots = ref([])
const pets = ref([])
const activeDate = ref('')
const selectedPetId = ref('')
const selectedSlotIds = ref([])
const note = ref('')
const slotsLoading = ref(true)
const petsLoading = ref(false)
const submitting = ref(false)
const slotsError = ref('')
const submitError = ref('')

const selectedSlots = computed(() => slots.value.filter((slot) => (
  selectedSlotIds.value.includes(slot.id)
)))
const selectedPet = computed(() => pets.value.find((pet) => pet.id === selectedPetId.value))
const totalAmount = computed(() => (
  Number(props.service.price || 0) * selectedSlotIds.value.length
))
const bookingWindow = computed(() => {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  // 可约窗口 7 天：与后端 BOOKABLE_DAYS 对齐，服务端还会强制收敛，这里只决定请求区间
  const end = new Date(start)
  end.setDate(end.getDate() + 6)
  return { from: toDateKey(start), to: toDateKey(end) }
})
const dateGroups = computed(() => {
  const groups = new Map()
  slots.value.forEach((slot) => {
    if (!groups.has(slot.slotDate)) {
      groups.set(slot.slotDate, [])
    }
    groups.get(slot.slotDate).push(slot)
  })
  return [...groups.entries()].map(([date, items]) => ({
    date,
    slots: items.sort((left, right) => left.startTime.localeCompare(right.startTime))
  }))
})
const activeSlots = computed(() => (
  dateGroups.value.find((group) => group.date === activeDate.value)?.slots || []
))
const selectedCountForDate = computed(() => {
  const counts = new Map()
  selectedSlots.value.forEach((slot) => {
    counts.set(slot.slotDate, (counts.get(slot.slotDate) || 0) + 1)
  })
  return counts
})

async function loadSlots() {
  slotsLoading.value = true
  slotsError.value = ''
  try {
    const result = await api.get(
      `/services/${props.service.id}/booking-slots${queryString(bookingWindow.value)}`
    )
    slots.value = (result.data || []).sort((left, right) => (
      `${left.slotDate} ${left.startTime}`.localeCompare(`${right.slotDate} ${right.startTime}`)
    ))
    const availableIds = new Set(slots.value
      .filter((slot) => slot.status === 'OPEN' && Number(slot.remaining) > 0)
      .map((slot) => slot.id))
    selectedSlotIds.value = selectedSlotIds.value.filter((id) => availableIds.has(id))
    if (!dateGroups.value.some((group) => group.date === activeDate.value)) {
      activeDate.value = dateGroups.value[0]?.date || ''
    }
  } catch (error) {
    slotsError.value = error.message
  } finally {
    slotsLoading.value = false
  }
}

async function loadPets() {
  if (!isUser.value) {
    pets.value = []
    selectedPetId.value = ''
    return
  }
  petsLoading.value = true
  try {
    const result = await api.get('/user/pets')
    pets.value = parseJson(result.data?.pets, [])
    const selectedStillExists = pets.value.some((pet) => pet.id === selectedPetId.value)
    if (!selectedStillExists) {
      selectedPetId.value = pets.value[0]?.id || ''
    }
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    petsLoading.value = false
  }
}

function toggleSlot(slot) {
  if (!isSlotAvailable(slot)) return
  const index = selectedSlotIds.value.indexOf(slot.id)
  if (index >= 0) {
    selectedSlotIds.value.splice(index, 1)
    submitError.value = ''
    return
  }
  if (selectedSlotIds.value.length >= 8) {
    ui.toast('一次最多选择 8 个时段', 'warning')
    return
  }
  selectedSlotIds.value.push(slot.id)
  submitError.value = ''
}

async function submitBooking() {
  if (submitting.value) return
  if (!await requireUser({ action: '预约服务' })) return
  if (!selectedPetId.value) {
    ui.toast('请先选择预约宠物', 'warning')
    return
  }
  if (selectedSlotIds.value.length === 0) {
    ui.toast('请至少选择一个预约时段', 'warning')
    return
  }
  const slotPreview = selectedSlots.value
    .slice(0, 3)
    .map((slot) => formatSelection(slot))
    .join('、')
  const slotSummary = selectedSlots.value.length > 3
    ? `${slotPreview}等 ${selectedSlots.value.length} 个时段`
    : slotPreview
  const confirmed = await ui.confirm({
    title: '确认预约',
    message: `确认预约「${props.service.name}」吗？宠物：${selectedPet.value?.name || '未选择'}；时段：${slotSummary}；到店地址：${props.service.shopAddress || '以商家页面信息为准'}；合计：¥${formatPrice(totalAmount.value)}。提交后将立即占用预约时段。`,
    confirmText: '确认预约'
  })
  if (!confirmed) return
  submitting.value = true
  emit('busy-change', true)
  submitError.value = ''
  try {
    const result = await api.post('/orders/booking', {
      serviceId: props.service.id,
      petId: selectedPetId.value,
      slotIds: [...selectedSlotIds.value],
      note: note.value.trim()
    })
    ui.toast(result.msg || `已预约 ${result.data?.slotCount || selectedSlotIds.value.length} 个时段`, 'success')
    await router.replace({
      name: 'me',
      query: { tab: 'appointments', status: 'confirmed' }
    })
  } catch (error) {
    const isConflict = error.code === 409 || error.status === 409
    if (isConflict) {
      submitError.value = '有部分时段刚刚被预约，已为你刷新可预约时间，请重新选择。'
      await loadSlots()
    } else {
      submitError.value = error.message
    }
    ui.toast(error.message, 'error')
  } finally {
    submitting.value = false
    emit('busy-change', false)
  }
}

function isSlotAvailable(slot) {
  return slot.status === 'OPEN' && Number(slot.remaining) > 0
}

function isSlotSelected(slot) {
  return selectedSlotIds.value.includes(slot.id)
}

function formatTime(time) {
  return String(time || '').slice(0, 5)
}

function toDateKey(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseDateKey(value) {
  const [year, month, day] = String(value).split('-').map(Number)
  return new Date(year, month - 1, day)
}

function formatDateLabel(value) {
  const date = parseDateKey(value)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (date.getTime() === today.getTime()) return '今天'
  if (date.getTime() === tomorrow.getTime()) return '明天'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function formatWeekday(value) {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekdays[parseDateKey(value).getDay()]
}

function formatSelection(slot) {
  return `${formatDateLabel(slot.slotDate)} ${formatTime(slot.startTime)}`
}

watch(isUser, (hasUserAccess) => {
  if (hasUserAccess) {
    loadPets()
  } else {
    pets.value = []
    selectedPetId.value = ''
    selectedSlotIds.value = []
  }
})

onMounted(() => {
  loadSlots()
  loadPets()
})
</script>

<template>
  <section id="booking-panel" class="booking-panel" aria-label="预约服务内容">
    <div class="booking-setup">
      <div class="pet-field">
        <label for="booking-pet">
          <PawPrint :size="16" aria-hidden="true" />
          预约宠物
        </label>
        <select
          v-if="isUser"
          id="booking-pet"
          v-model="selectedPetId"
          class="select"
          :disabled="petsLoading"
        >
          <option value="" disabled>{{ petsLoading ? '正在读取宠物档案' : '请选择宠物' }}</option>
          <option v-for="pet in pets" :key="pet.id" :value="pet.id">
            {{ pet.name }} · {{ pet.species }}{{ pet.breed ? ` · ${pet.breed}` : '' }}
          </option>
        </select>
        <button
          v-else
          class="pet-login"
          type="button"
          @click="requireUser({ action: '预约服务' })"
        >
          {{ isNonUserAccount ? '此功能需普通用户账号' : '登录后选择宠物' }}
        </button>
        <button
          v-if="isUser && !petsLoading && pets.length === 0"
          class="text-link pet-link"
          type="button"
          @click="router.push({
            name: 'me',
            query: { tab: 'pets', return: route.fullPath }
          })"
        >
          还没有宠物档案，去添加
        </button>
        <small v-else-if="selectedPet" class="pet-note">
          本次预约将关联 {{ selectedPet.name }}
        </small>
      </div>

      <label class="date-field" for="booking-date">
        <span class="field-label">
          <CalendarDays :size="16" aria-hidden="true" />
          预约日期
        </span>
        <span class="date-control">
          <CalendarDays :size="16" aria-hidden="true" />
          <select
            id="booking-date"
            v-model="activeDate"
            class="select"
            :disabled="slotsLoading || dateGroups.length === 0"
          >
            <option value="" disabled>{{ slotsLoading ? '正在读取日期' : '请选择日期' }}</option>
            <option v-for="group in dateGroups" :key="group.date" :value="group.date">
              {{ formatDateLabel(group.date) }} {{ formatWeekday(group.date) }}
              {{ selectedCountForDate.get(group.date) ? ` · 已选 ${selectedCountForDate.get(group.date)}` : '' }}
            </option>
          </select>
        </span>
      </label>
    </div>

    <div v-if="slotsLoading" class="slot-loading" aria-live="polite">
      <span class="spinner" aria-hidden="true"></span>
      <span>正在读取可预约时间</span>
    </div>

    <div v-else-if="slotsError" class="slot-message error" role="alert">
      <AlertCircle :size="18" aria-hidden="true" />
      <span>{{ slotsError }}</span>
      <button class="text-link" type="button" @click="loadSlots">
        <RefreshCw :size="15" aria-hidden="true" />重试
      </button>
    </div>

    <div v-else-if="dateGroups.length === 0" class="slot-message">
      <CalendarDays :size="22" aria-hidden="true" />
      <div>
        <b>暂无可预约时段</b>
        <span>商家尚未发布时段，或当前时段已约满。</span>
      </div>
    </div>

    <template v-else>
      <div class="slot-heading">
        <b>{{ formatDateLabel(activeDate) }}可约时段</b>
        <span class="booking-window">未来 30 天</span>
      </div>

      <div class="slot-grid" aria-live="polite">
        <button
          v-for="slot in activeSlots"
          :key="slot.id"
          class="slot-chip"
          :class="{ selected: isSlotSelected(slot), unavailable: !isSlotAvailable(slot) }"
          type="button"
          :disabled="!isSlotAvailable(slot)"
          :aria-pressed="isSlotSelected(slot)"
          :aria-label="`${formatTime(slot.startTime)}至${formatTime(slot.endTime)}，剩余${slot.remaining}个名额`"
          @click="toggleSlot(slot)"
        >
          <Check v-if="isSlotSelected(slot)" :size="15" aria-hidden="true" />
          <span>{{ formatTime(slot.startTime) }}–{{ formatTime(slot.endTime) }}</span>
          <small>{{ slot.remaining > 0 ? `余 ${slot.remaining}` : '已满' }}</small>
        </button>
      </div>
    </template>

    <div v-if="selectedSlots.length" class="selected-slots" aria-live="polite">
      <div class="selected-head">
        <b>已选 {{ selectedSlots.length }} 个时段</b>
        <button class="text-link" type="button" @click="selectedSlotIds = []">清空</button>
      </div>
      <div class="selected-list">
        <button
          v-for="slot in selectedSlots"
          :key="slot.id"
          type="button"
          :aria-label="`取消${formatSelection(slot)}的预约`"
          @click="toggleSlot(slot)"
        >
          {{ formatSelection(slot) }}<span aria-hidden="true">×</span>
        </button>
      </div>
    </div>

    <p class="store-address">
      <MapPin :size="14" aria-hidden="true" />
      <span>到店地址：{{ service.shopAddress || '以商家页面信息为准' }}</span>
    </p>

    <label class="field booking-note">
      <span>预约备注（选填）</span>
      <textarea
        v-model="note"
        class="textarea"
        rows="2"
        maxlength="500"
        placeholder="可填写宠物体型、性格特点或需要商家提前准备的事项"
      />
    </label>

    <p v-if="submitError" class="form-error" role="alert">{{ submitError }}</p>

    <div class="booking-submit">
      <div>
        <span>合计</span>
        <strong>¥{{ formatPrice(totalAmount) }}</strong>
        <small>{{ selectedSlots.length }} 个时段 × ¥{{ formatPrice(service.price) }}</small>
      </div>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="submitting || (isUser && pets.length === 0)"
        @click="submitBooking"
      >
        <Clock3 :size="17" aria-hidden="true" />
        {{
          submitting
            ? '正在提交…'
            : isUser
              ? '确认预约'
              : isNonUserAccount
                ? '须使用普通用户账号'
                : '登录后预约'
        }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.booking-panel {
  padding: 18px 22px 20px;
}

.booking-setup,
.selected-head,
.booking-submit,
.pet-field > label {
  display: flex;
  align-items: center;
}

.booking-setup {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(220px, .85fr);
  gap: 12px;
  align-items: stretch;
}

.selected-head,
.booking-submit {
  justify-content: space-between;
  gap: 16px;
}

.booking-window,
.booking-submit > div > span,
.booking-submit > div > small,
.pet-note,
.slot-message span {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.pet-field {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--color-surface-warm);
}

.pet-field > label {
  gap: 7px;
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extra-bold);
}

.pet-login {
  width: 100%;
  min-height: 38px;
  padding: 7px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface-raised);
  color: var(--color-primary-strong);
  text-align: left;
  font-weight: var(--font-weight-extra-bold);
}

.pet-link {
  display: inline-flex;
  margin-top: 6px;
}

.pet-note {
  display: block;
  margin-top: 5px;
}

.date-field {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-ivory);
}

.field-label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extra-bold);
}

.date-control {
  position: relative;
  display: block;
}

.date-control > svg {
  position: absolute;
  left: 12px;
  top: 50%;
  z-index: 1;
  color: var(--color-primary-strong);
  transform: translateY(-50%);
  pointer-events: none;
}

.date-control .select {
  min-height: 38px;
  padding: 7px 34px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.slot-loading,
.slot-message {
  min-height: 76px;
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  text-align: left;
}

.slot-message.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
}

.slot-message b,
.slot-message span {
  display: block;
}

.slot-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0 7px;
}

.slot-heading b {
  font-size: var(--font-size-base);
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.slot-chip {
  min-height: 48px;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-raised);
  color: var(--color-text-secondary);
  text-align: left;
  display: flex;
  align-items: center;
  gap: 5px;
}

.slot-chip span,
.slot-chip small {
  display: inline;
}

.slot-chip span {
  flex: 1;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extra-bold);
}

.slot-chip small {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.slot-chip.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  box-shadow: inset 0 0 0 1px var(--color-primary);
}

.slot-chip.unavailable {
  opacity: .55;
}

.selected-slots {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  background: var(--color-surface-ivory);
}

.selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.selected-list button {
  min-height: 30px;
  padding: 4px 8px;
  border: 1px solid var(--color-border-primary-soft);
  border-radius: var(--radius-pill);
  background: var(--color-surface-raised);
  color: var(--color-primary-strong);
  font-size: var(--font-size-sm);
}

.selected-list button span {
  margin-left: 6px;
}

.booking-note {
  margin: 10px 0;
}

.store-address {
  margin: 10px 0 0;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.store-address svg {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--color-primary-strong);
}

.booking-note .textarea {
  min-height: 58px;
  height: 58px;
  padding: 8px 11px;
  resize: none;
}

.booking-submit {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.booking-submit strong {
  margin-left: 7px;
  color: var(--color-primary-strong);
  font-family: var(--font-family-display);
  font-size: var(--font-size-xl);
}

.booking-submit small {
  display: block;
}

@media (max-width: 720px) {
  .booking-panel {
    padding: 16px;
  }
}

@media (max-width: 680px) {
  .slot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .booking-setup {
    grid-template-columns: 1fr;
    gap: 9px;
  }
}

@media (max-width: 420px) {
  .slot-grid {
    grid-template-columns: 1fr;
  }

  .booking-submit {
    position: sticky;
    bottom: 0;
    z-index: 2;
    margin: 10px -16px -16px;
    padding: 10px 16px;
    border-radius: 0 0 var(--radius-md) var(--radius-md);
    background: var(--color-surface-glass);
    backdrop-filter: blur(14px);
  }

  .booking-submit .btn {
    flex: 0 0 auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .slot-chip {
    transition: none;
  }
}
</style>
