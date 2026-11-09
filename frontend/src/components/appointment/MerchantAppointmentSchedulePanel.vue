<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, ChevronLeft, Clock3 } from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { useUiStore } from '../../stores/ui'

const props = defineProps({
  serviceId: {
    type: Number,
    required: true
  },
  // 服务时长只读展示：时段切分的一半依据，另一半是本模板里的服务间隔
  durationMinutes: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['saved'])

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const DAY_CLOSED = 'CLOSED'
const SLOT_STATUS_LABELS = { OPEN: '可约', FULL: '已满', CLOSED: '已关闭', EXPIRED: '已过期' }

const ui = useUiStore()
const view = ref('template')
const loading = ref(false)
const saving = ref(false)
const togglingDate = ref(null)
const hasTemplate = ref(false)
const template = ref(createEmptyTemplate())
const slots = ref([])
const expandedDate = ref(null)
// 休业取消原因：仅休业动作需要，随站内通知发送给被取消预约的用户
const closeForm = ref({ date: null, reason: '' })

function createEmptyTemplate() {
  return {
    startTime: '09:00',
    endTime: '18:00',
    breakStart: '',
    breakEnd: '',
    intervalMinutes: 0,
    capacityPerSlot: 2
  }
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function dateKey(value) {
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
}

function timeText(value) {
  return value ? String(value).slice(0, 5) : ''
}

function toMinutes(time) {
  const [hours, minutes] = String(time || '').split(':').map(Number)
  return (hours || 0) * 60 + (minutes || 0)
}

function fromMinutes(total) {
  return `${pad(Math.floor(total / 60) % 24)}:${pad(total % 60)}`
}

// 预约情况视图覆盖今天起 7 天，与后端可约窗口一致；休业按钮只对明天及以后开放
const overviewDays = computed(() => {
  const result = []
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  for (let offset = 0; offset < 7; offset++) {
    const date = new Date(now)
    date.setDate(date.getDate() + offset)
    const key = dateKey(date)
    const daySlots = slots.value
        .filter((slot) => String(slot.slotDate) === key)
        .sort((left, right) => String(left.startTime).localeCompare(String(right.startTime)))
    const booked = daySlots.reduce((sum, slot) => sum + Number(slot.bookedCount || 0), 0)
    const closed = daySlots.length > 0 && daySlots.every((slot) => slot.dayStatus === DAY_CLOSED)
    result.push({
      date: key,
      label: `${date.getMonth() + 1}月${date.getDate()}日`,
      weekday: WEEKDAY_LABELS[date.getDay()],
      isPast: offset === 0,
      slots: daySlots,
      booked,
      // 无时段 = 未排班；时段存在且全部休业 = 休业；其余 = 营业中
      status: daySlots.length === 0 ? 'empty' : (closed ? 'closed' : 'open')
    })
  }
  return result
})

// 按服务时长 + 模板间隔 + 午休实时切分出时段预览，商家改表单即可看到将来生成的时段
const previewSlots = computed(() => {
  const duration = Number(props.durationMinutes)
  if (!Number.isFinite(duration) || duration <= 0) return []
  const step = duration + Number(template.value.intervalMinutes || 0)
  const startM = toMinutes(template.value.startTime)
  const endM = toMinutes(template.value.endTime)
  const breakStart = template.value.breakStart ? toMinutes(template.value.breakStart) : null
  const breakEnd = template.value.breakEnd ? toMinutes(template.value.breakEnd) : null
  const ranges = (breakStart !== null && breakEnd !== null)
    ? [[startM, breakStart], [breakEnd, endM]]
    : [[startM, endM]]
  const result = []
  for (const [from, to] of ranges) {
    for (let cursor = from; cursor + duration <= to; cursor += step) {
      result.push(`${fromMinutes(cursor)}–${fromMinutes(cursor + duration)}`)
    }
  }
  return result
})

async function loadAll() {
  loading.value = true
  try {
    const [scheduleResult, slotResult] = await Promise.all([
      api.get(`/merchant/services/${props.serviceId}/appointment-schedule`),
      api.get(`/merchant/services/${props.serviceId}/appointment-slots`)
    ])
    const saved = scheduleResult.data
    hasTemplate.value = Boolean(saved)
    template.value = saved
      ? {
          startTime: timeText(saved.startTime),
          endTime: timeText(saved.endTime),
          breakStart: timeText(saved.breakStart),
          breakEnd: timeText(saved.breakEnd),
          intervalMinutes: saved.intervalMinutes ?? 0,
          capacityPerSlot: saved.capacityPerSlot
        }
      : createEmptyTemplate()
    slots.value = slotResult.data || []
  } catch (error) {
    ui.toast(error.message || '读取排班失败', 'error')
  } finally {
    loading.value = false
  }
}

function validateTemplate() {
  if (toMinutes(template.value.startTime) >= toMinutes(template.value.endTime)) {
    ui.toast('开始时间必须早于结束时间', 'warning')
    return false
  }
  const capacity = Number(template.value.capacityPerSlot)
  if (!Number.isInteger(capacity) || capacity < 1 || capacity > 100) {
    ui.toast('每时段容量必须是 1~100 的整数', 'warning')
    return false
  }
  const interval = Number(template.value.intervalMinutes || 0)
  if (!Number.isInteger(interval) || interval < 0 || interval > 120) {
    ui.toast('服务间隔必须是 0~120 的整数分钟', 'warning')
    return false
  }
  const hasBreak = Boolean(template.value.breakStart && template.value.breakEnd)
  if (template.value.breakStart || template.value.breakEnd) {
    if (!hasBreak) {
      ui.toast('午休开始与结束必须同时填写', 'warning')
      return false
    }
    if (toMinutes(template.value.breakStart) >= toMinutes(template.value.breakEnd)) {
      ui.toast('午休开始时间必须早于结束时间', 'warning')
      return false
    }
    if (toMinutes(template.value.breakStart) < toMinutes(template.value.startTime)
      || toMinutes(template.value.breakEnd) > toMinutes(template.value.endTime)) {
      ui.toast('午休必须完全落在营业时间之内', 'warning')
      return false
    }
  }
  return true
}

async function saveTemplate() {
  if (saving.value || !validateTemplate()) return
  saving.value = true
  try {
    const result = await api.post(
      `/merchant/services/${props.serviceId}/appointment-schedule`,
      {
        startTime: template.value.startTime,
        endTime: template.value.endTime,
        breakStart: template.value.breakStart || null,
        breakEnd: template.value.breakEnd || null,
        intervalMinutes: Number(template.value.intervalMinutes || 0),
        capacityPerSlot: Number(template.value.capacityPerSlot)
      }
    )
    hasTemplate.value = true
    ui.toast(result.msg || '时间段已保存', 'success')
    emit('saved')
    await loadAll()
  } catch (error) {
    ui.toast(error.message || '保存时间段失败', 'error')
  } finally {
    saving.value = false
  }
}

function toggleExpand(date) {
  expandedDate.value = expandedDate.value === date ? null : date
}

// status 列没有过期写入点：展示层按结束时间兜底，避免历史时段标成"可约"误导商家
function slotStatusLabel(slot) {
  const end = new Date(`${slot.slotDate}T${String(slot.endTime || '00:00').slice(0, 8)}`)
  if (!Number.isNaN(end.getTime()) && end.getTime() < Date.now()) return '已过期'
  return SLOT_STATUS_LABELS[slot.status] || slot.status
}

function openCloseForm(day) {
  closeForm.value = { date: day.date, reason: '', booked: day.booked }
}

function cancelCloseForm() {
  closeForm.value = { date: null, reason: '' }
}

async function confirmCloseDay() {
  const day = overviewDays.value.find((item) => item.date === closeForm.value.date)
  if (!day || togglingDate.value) return
  // 有预约必须填写原因：原因会随取消通知逐单发送给预约用户
  if (day.booked > 0 && !closeForm.value.reason.trim()) {
    ui.toast('该天已有预约，请填写取消原因以便通知用户', 'warning')
    return
  }
  togglingDate.value = day.date
  try {
    const result = await api.put(
      `/merchant/services/${props.serviceId}/appointment-slots/day-status`
      + queryString({ date: day.date, status: DAY_CLOSED, reason: closeForm.value.reason.trim() })
    )
    ui.toast(result.msg || '该天已休业', 'success')
    cancelCloseForm()
    await loadAll()
  } catch (error) {
    ui.toast(error.message || '操作失败', 'error')
  } finally {
    togglingDate.value = null
  }
}

async function reopenDay(day) {
  if (togglingDate.value) return
  togglingDate.value = day.date
  try {
    const result = await api.put(
      `/merchant/services/${props.serviceId}/appointment-slots/day-status`
      + queryString({ date: day.date, status: 'OPEN' })
    )
    ui.toast(result.msg || '该天已恢复营业', 'success')
    await loadAll()
  } catch (error) {
    ui.toast(error.message || '操作失败', 'error')
  } finally {
    togglingDate.value = null
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="schedule-panel">
    <!-- 视图 1：每日时间段安排（默认） -->
    <template v-if="view === 'template'">
      <div v-if="loading" class="panel-loading">正在读取排班…</div>
      <template v-else>
        <div class="form-grid">
          <div>
            <p class="field-label">开始时间</p>
            <input v-model="template.startTime" class="form-input" type="time" />
          </div>
          <div>
            <p class="field-label">结束时间</p>
            <input v-model="template.endTime" class="form-input" type="time" />
          </div>
          <div>
            <p class="field-label">午休开始（可空）</p>
            <input v-model="template.breakStart" class="form-input" type="time" />
          </div>
          <div>
            <p class="field-label">午休结束（可空）</p>
            <input v-model="template.breakEnd" class="form-input" type="time" />
          </div>
          <div>
            <p class="field-label">服务间隔（分钟）</p>
            <input v-model="template.intervalMinutes" class="form-input" type="number" min="0" max="120" step="5" />
          </div>
          <div>
            <p class="field-label">每时段容量</p>
            <input v-model="template.capacityPerSlot" class="form-input" type="number" min="1" max="100" />
          </div>
        </div>

        <div v-if="previewSlots.length" class="preview-box">
          <p class="preview-title">时段预览</p>
          <div class="preview-list">
            <span v-for="slot in previewSlots" :key="slot" class="preview-chip">{{ slot }}</span>
          </div>
        </div>

        <div class="panel-footer">
          <button class="btn-primary" type="button" :disabled="saving" @click="saveTemplate">
            {{ saving ? '正在保存' : '保存时间段' }}
          </button>
        </div>
        <button class="btn-secondary btn-wide" type="button" @click="view = 'overview'">
          查看 7 天预约情况 →
        </button>
      </template>
    </template>

    <!-- 视图 2：7 天预约情况（含休业） -->
    <template v-else>
      <div class="overview-head">
        <button class="btn-back" type="button" @click="view = 'template'">
          <ChevronLeft :size="14" />返回时间段安排
        </button>
      </div>
      <div class="day-list">
        <template v-for="day in overviewDays" :key="day.date">
          <div class="day-card" :class="`is-${day.status}`">
            <div class="day-info">
              <p class="day-title">
                <b>{{ day.label }}</b>
                <small>{{ day.weekday }}</small>
                <span
                  v-if="day.status !== 'empty'"
                  class="day-badge"
                  :class="day.status === 'closed' ? 'badge-closed' : 'badge-open'"
                >
                  {{ day.status === 'closed' ? `休业 · 已约 ${day.booked}` : `营业 · 已约 ${day.booked}` }}
                </span>
                <span v-else class="day-badge badge-empty">无排班</span>
              </p>
              <button
                v-if="day.slots.length"
                type="button"
                class="expand-link"
                @click="toggleExpand(day.date)"
              >
                展开时段人数
                <ChevronDown :size="12" :class="{ 'is-open': expandedDate === day.date }" />
              </button>
            </div>
            <button
              v-if="!day.isPast && day.status !== 'empty' && closeForm.date !== day.date"
              type="button"
              class="status-toggle"
              :class="day.status === 'closed' ? 'is-reopen' : 'is-close'"
              :disabled="togglingDate === day.date"
              @click="day.status === 'closed' ? reopenDay(day) : openCloseForm(day)"
            >
              {{ togglingDate === day.date ? '操作中' : (day.status === 'closed' ? '恢复营业' : '休业') }}
            </button>
          </div>
          <!-- 休业原因输入：有预约时必填，随取消通知逐单发送给预约用户 -->
          <div v-if="closeForm.date === day.date" class="close-form">
            <textarea
              v-model="closeForm.reason"
              class="close-reason"
              rows="2"
              maxlength="200"
              :placeholder="day.booked > 0
                ? `该天已有 ${day.booked} 条预约，休业将取消这些预约；请填写取消原因，将随通知发送给预约用户`
                : '可填写休业原因（选填）'"
            />
            <div class="close-actions">
              <button class="btn-ghost" type="button" @click="cancelCloseForm">取消</button>
              <button
                class="btn-danger"
                type="button"
                :disabled="togglingDate === day.date"
                @click="confirmCloseDay"
              >
                {{ togglingDate === day.date ? '正在休业' : '确认休业' }}
              </button>
            </div>
          </div>
          <div v-if="expandedDate === day.date && day.slots.length" class="slot-list">
            <div
              v-for="slot in day.slots"
              :key="slot.id"
              class="slot-row"
            >
              <span class="slot-time"><Clock3 :size="12" />{{ timeText(slot.startTime) }}–{{ timeText(slot.endTime) }}</span>
              <span class="slot-count">已约 {{ slot.bookedCount ?? 0 }}/{{ slot.capacity ?? 0 }}</span>
              <span class="slot-status">{{ slotStatusLabel(slot) }}</span>
            </div>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.schedule-panel { display: flex; flex-direction: column; gap: 12px; }
.panel-loading { padding: 24px 0; text-align: center; color: var(--color-text-muted); font-size: 0.85rem; }

.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.field-label { margin: 0 0 5px; font-size: 0.75rem; color: var(--color-text-muted); }
.form-input { width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 8px; font-size: 0.82rem; background: var(--color-surface); color: var(--color-text); }

.preview-box { border: 1px dashed var(--color-border); border-radius: 10px; padding: 10px 12px; }
.preview-title { margin: 0 0 8px; font-size: 0.8rem; font-weight: 600; }
.preview-list { display: flex; flex-wrap: wrap; gap: 6px; }
.preview-chip { border: 1px solid var(--color-border); border-radius: 999px; padding: 3px 10px; font-size: 0.75rem; background: var(--color-surface-muted, #f7f7f4); }

.panel-footer { display: flex; justify-content: flex-end; margin-top: 4px; }
.btn-primary { border: 0; border-radius: 10px; padding: 9px 22px; background: var(--color-primary-strong, #2f6f4f); color: #fff; font-size: 0.85rem; cursor: pointer; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.btn-secondary { border: 1px solid var(--color-border); border-radius: 10px; padding: 8px 0; background: var(--color-surface); font-size: 0.82rem; cursor: pointer; color: var(--color-text); }
.btn-wide { width: 100%; }

.overview-head { display: flex; flex-direction: column; gap: 6px; }
.btn-back { display: inline-flex; align-items: center; gap: 3px; border: 1px solid var(--color-border); border-radius: 8px; padding: 5px 12px; background: var(--color-surface); font-size: 0.78rem; cursor: pointer; align-self: flex-start; }

.day-list { display: flex; flex-direction: column; gap: 8px; }
.day-card { display: flex; align-items: center; gap: 10px; border: 1px solid var(--color-border); border-radius: 10px; padding: 9px 12px; }
.day-card.is-closed { background: var(--color-surface-muted, #f7f7f4); }
.day-card.is-empty { border-style: dashed; opacity: 0.8; }
.day-info { flex: 1; min-width: 0; }
.day-title { margin: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 0.82rem; }
.day-title small { color: var(--color-text-muted); }
.day-badge { font-size: 0.68rem; padding: 2px 9px; border-radius: 999px; }
.badge-open { color: var(--color-primary-strong, #2f6f4f); background: var(--color-primary-soft, #e8f3ec); }
.badge-closed { color: var(--color-warning-strong, #8a5a00); background: var(--color-warning-soft, #fdf3e0); }
.badge-empty { color: var(--color-text-muted); border: 1px solid var(--color-border); }
.expand-link { border: 0; background: transparent; padding: 0; margin-top: 3px; color: var(--color-primary-strong, #2f6f4f); font-size: 0.72rem; cursor: pointer; display: inline-flex; align-items: center; gap: 2px; }
.expand-link svg { transition: transform 150ms ease; }
.expand-link svg.is-open { transform: rotate(180deg); }
.status-toggle { border-radius: 8px; padding: 6px 12px; font-size: 0.75rem; cursor: pointer; flex: 0 0 auto; }
.status-toggle.is-close { border: 1px solid #e2a1a1; background: #fdeeee; color: #9b3434; }
.status-toggle.is-reopen { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text); }
.status-toggle:disabled { opacity: 0.6; cursor: default; }

.close-form { border: 1px solid #e2a1a1; border-radius: 10px; padding: 10px 12px; background: #fdf6f6; }
.close-reason { width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 8px; font-size: 0.8rem; resize: vertical; background: var(--color-surface); color: var(--color-text); }
.close-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.btn-ghost { border: 1px solid var(--color-border); border-radius: 8px; padding: 5px 14px; background: var(--color-surface); font-size: 0.78rem; cursor: pointer; }
.btn-danger { border: 0; border-radius: 8px; padding: 5px 14px; background: #9b3434; color: #fff; font-size: 0.78rem; cursor: pointer; }
.btn-danger:disabled { opacity: 0.6; cursor: default; }

.slot-list { border: 1px dashed var(--color-border); border-radius: 10px; padding: 8px 12px; margin-top: -2px; background: var(--color-surface-muted, #fafaf7); }
.slot-row { display: flex; align-items: center; gap: 12px; padding: 3px 0; font-size: 0.78rem; }
.slot-time { display: inline-flex; align-items: center; gap: 4px; min-width: 105px; font-weight: 600; }
.slot-count { color: var(--color-text-secondary); }
.slot-status { margin-left: auto; font-size: 0.7rem; padding: 2px 8px; border-radius: 999px; border: 1px solid var(--color-border); color: var(--color-text-muted); }
</style>
