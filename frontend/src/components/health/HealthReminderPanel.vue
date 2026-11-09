<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { BellRing, CalendarClock, Check, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { api } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import EmptyState from '../EmptyState.vue'
import LoadingPanel from '../LoadingPanel.vue'
import ModalDialog from '../ModalDialog.vue'

const props = defineProps({
  pets: {
    type: Array,
    required: true
  },
  selectedPetId: {
    type: [Number, String],
    default: null
  },
  refreshToken: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['select-pet'])
const ui = useUiStore()
const reminders = ref([])
const loading = ref(false)
const editorOpen = ref(false)
const saving = ref(false)
const busyId = ref(null)
const form = ref(emptyForm())
const formGuard = useDialogDraftGuard({
  getSnapshot: () => form.value,
  getBusy: () => saving.value,
  close: closeEditor
})

const reminderGroups = computed(() => {
  const groups = [
    { key: 'upcoming', title: '即将到期', items: [] },
    { key: 'later', title: '稍后', items: [] },
    { key: 'standing', title: '持续提醒', items: [] }
  ]
  for (const reminder of reminders.value) {
    if (reminder.status !== 'active') continue
    if (!isDatedReminder(reminder)) {
      groups[2].items.push(reminder)
      continue
    }
    const days = daysUntil(reminder)
    if (days < 0) continue
    groups[days <= 7 ? 0 : 1].items.push(reminder)
  }
  groups[0].items.sort((left, right) => daysUntil(left) - daysUntil(right))
  groups[1].items.sort((left, right) => daysUntil(left) - daysUntil(right))
  return groups.filter((group) => group.items.length)
})

function emptyForm() {
  return {
    id: null,
    petId: '',
    title: '',
    deadlineType: 'dated',
    remindAt: '',
    note: ''
  }
}

function toLocalDateTimeInput(date) {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function defaultRemindAt() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  date.setHours(9, 0, 0, 0)
  return toLocalDateTimeInput(date)
}

async function load() {
  loading.value = true
  try {
    const result = await api.get('/ai/health/reminders')
    reminders.value = result.data || []
  } catch (error) {
    ui.toast(error.message || '读取健康提醒失败', 'error')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = {
    ...emptyForm(),
    petId: props.selectedPetId || props.pets[0]?.id || '',
    remindAt: defaultRemindAt()
  }
  editorOpen.value = true
  formGuard.markClean()
}

function openEdit(reminder) {
  if (!reminder.reminderId || reminder.status !== 'active') return
  form.value = {
    id: reminder.reminderId,
    petId: reminder.petId,
    title: reminder.title,
    deadlineType: reminder.remindAt ? 'dated' : 'standing',
    remindAt: String(reminder.remindAt || '').slice(0, 16),
    note: reminder.detail === '自定义健康提醒' ? '' : reminder.detail
  }
  editorOpen.value = true
  formGuard.markClean()
}

function closeEditor() {
  editorOpen.value = false
  form.value = emptyForm()
}

async function saveReminder() {
  const title = form.value.title.trim()
  const needsRemindAt = form.value.deadlineType === 'dated'
  if (!form.value.petId) {
    ui.toast('请选择宠物', 'warning')
    return
  }
  if (!title) {
    ui.toast('请填写提醒标题', 'warning')
    return
  }
  if (needsRemindAt && !form.value.remindAt) {
    ui.toast('请选择提醒时间', 'warning')
    return
  }
  if (needsRemindAt && new Date(form.value.remindAt).getTime() <= Date.now()) {
    ui.toast('提醒时间必须晚于当前时间', 'warning')
    return
  }
  if (saving.value) return
  saving.value = true
  const payload = {
    petId: Number(form.value.petId),
    title,
    remindAt: needsRemindAt
      ? (form.value.remindAt.length === 16
          ? `${form.value.remindAt}:00` : form.value.remindAt)
      : null,
    note: form.value.note.trim()
  }
  try {
    if (form.value.id) {
      await api.put(`/ai/health/reminders/${form.value.id}`, payload)
    } else {
      await api.post('/ai/health/reminders', payload)
    }
    ui.toast(form.value.id ? '提醒已更新' : '提醒已添加', 'success')
    closeEditor()
    await load()
  } catch (error) {
    ui.toast(error.message || '保存提醒失败', 'error')
  } finally {
    saving.value = false
  }
}

async function completeReminder(reminder) {
  if (!reminder.reminderId || busyId.value) return
  const confirmed = await ui.confirm({
    title: '完成提醒',
    message: `确认完成提醒「${reminder.title}」吗？`,
    confirmText: '确认完成'
  })
  if (!confirmed) return
  busyId.value = reminder.reminderId
  try {
    await api.put(`/ai/health/reminders/${reminder.reminderId}/complete`)
    ui.toast('提醒已完成', 'success')
    await load()
  } catch (error) {
    ui.toast(error.message || '完成提醒失败', 'error')
  } finally {
    busyId.value = null
  }
}

async function deleteReminder(reminder) {
  if (!reminder.reminderId || busyId.value) return
  const confirmed = await ui.confirm({
    title: '删除提醒',
    message: `确定删除提醒「${reminder.title}」吗？删除后无法恢复。`,
    confirmText: '删除提醒',
    danger: true
  })
  if (!confirmed) return
  busyId.value = reminder.reminderId
  try {
    await api.delete(`/ai/health/reminders/${reminder.reminderId}`)
    ui.toast('提醒已删除', 'success')
    await load()
  } catch (error) {
    ui.toast(error.message || '删除提醒失败', 'error')
  } finally {
    busyId.value = null
  }
}

function selectPet(petId) {
  if (petId && Number(petId) > 0) {
    emit('select-pet', Number(petId))
  }
}

function setDeadlineType(deadlineType) {
  form.value.deadlineType = deadlineType
  if (deadlineType === 'dated' && !form.value.remindAt) {
    form.value.remindAt = defaultRemindAt()
  }
}

function daysUntil(reminder) {
  const parts = String(reminder.dueDate || '').split('-').map(Number)
  if (parts.length !== 3 || parts.some((part) => !Number.isFinite(part))) {
    return Number.NaN
  }
  const dueTime = Date.UTC(parts[0], parts[1] - 1, parts[2])
  const today = new Date()
  const todayTime = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.round((dueTime - todayTime) / 86_400_000)
}

function isDatedReminder(reminder) {
  return reminder.type !== 'WEIGHT' && Number.isFinite(daysUntil(reminder))
}

function countdownLabel(reminder) {
  if (!isDatedReminder(reminder)) return '无固定期限'
  const days = daysUntil(reminder)
  return days === 0 ? '今天到期' : `剩余 ${days} 天`
}

function reminderMeta(reminder) {
  if (reminder.type === 'WEIGHT') {
    return reminder.dueDate ? `记录 ${reminder.dueDate}` : '体重趋势提示'
  }
  return isDatedReminder(reminder) ? `计划 ${reminder.dueDate}` : '常驻提醒'
}

function typeLabel(reminder) {
  if (reminder.type === 'CUSTOM') return '自定义提醒'
  if (reminder.type === 'VACCINE') return '疫苗计划'
  if (reminder.type === 'DEWORMING') return '驱虫计划'
  if (reminder.type === 'WEIGHT') return '体重提示'
  return '健康提醒'
}

function levelLabel(reminder) {
  if (reminder.level === 'CONSULT_VET') return '建议咨询兽医'
  if (reminder.level === 'WEIGHT_ALERT') return '建议关注'
  return ''
}

function levelTone(reminder) {
  if (reminder.level === 'CONSULT_VET') return 'danger'
  if (reminder.level === 'WEIGHT_ALERT') return 'warning'
  return 'normal'
}

function reminderTone(reminder) {
  return String(reminder.type || 'custom').toLowerCase()
}

watch(() => props.refreshToken, load)
onMounted(load)
</script>

<template>
  <section id="health-reminders" class="panel reminder-panel">
    <header class="reminder-head">
      <div>
        <h2><BellRing :size="18" />健康提醒</h2>
      </div>
      <button class="btn btn-primary btn-sm" type="button" @click="openCreate">
        <Plus :size="15" />添加提醒
      </button>
    </header>

    <LoadingPanel v-if="loading" label="正在读取健康提醒" />
    <EmptyState
      v-else-if="!reminderGroups.length"
      title="暂无待处理提醒"
      description="可以添加有日期或无固定期限的提醒。"
    />
    <div v-else class="reminder-groups">
      <section
        v-for="group in reminderGroups"
        :key="group.key"
        class="reminder-group"
        :aria-labelledby="`reminder-group-${group.key}`"
      >
        <h3 :id="`reminder-group-${group.key}`">{{ group.title }}</h3>
        <div class="reminder-list">
          <article
            v-for="reminder in group.items"
            :key="reminder.reminderId || `${reminder.type}-${reminder.petId}-${reminder.dueDate}`"
            class="reminder-card"
          >
            <span class="reminder-icon" :class="`tone-${reminderTone(reminder)}`">
              <CalendarClock :size="18" />
            </span>
            <button class="reminder-main" type="button" @click="selectPet(reminder.petId)">
              <span class="reminder-title">
                <b>{{ reminder.title }}</b>
                <span class="reminder-type">{{ typeLabel(reminder) }}</span>
                <em v-if="levelLabel(reminder)" :class="levelTone(reminder)">
                  {{ levelLabel(reminder) }}
                </em>
              </span>
              <small>{{ reminder.petName }} · {{ reminderMeta(reminder) }}</small>
              <p>{{ reminder.detail }}</p>
            </button>
            <div class="reminder-side">
              <span
                class="reminder-countdown"
                :class="{ soon: isDatedReminder(reminder) && daysUntil(reminder) <= 7 }"
              >{{ countdownLabel(reminder) }}</span>
              <div v-if="reminder.reminderId" class="reminder-actions">
                <button
                  v-if="reminder.status === 'active'"
                  type="button"
                  :disabled="busyId === reminder.reminderId"
                  aria-label="编辑提醒"
                  @click="openEdit(reminder)"
                ><Pencil :size="14" /></button>
                <button
                  v-if="reminder.status === 'active' || reminder.status === 'notified'"
                  type="button"
                  :disabled="busyId === reminder.reminderId"
                  aria-label="完成提醒"
                  @click="completeReminder(reminder)"
                ><Check :size="14" /></button>
                <button
                  type="button"
                  :disabled="busyId === reminder.reminderId"
                  aria-label="删除提醒"
                  @click="deleteReminder(reminder)"
                ><Trash2 :size="14" /></button>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>

    <ModalDialog
      :open="editorOpen"
      :title="form.id ? '编辑健康提醒' : '添加健康提醒'"
      width="520px"
      :close-disabled="saving"
      @close="formGuard.requestClose"
    >
      <div class="reminder-form">
        <label class="field">
          <span>宠物</span>
          <select v-model="form.petId" class="select">
            <option value="" disabled>请选择宠物</option>
            <option v-for="pet in pets" :key="pet.id" :value="pet.id">{{ pet.name }}</option>
          </select>
        </label>
        <label class="field">
          <span>提醒标题</span>
          <input v-model="form.title" class="input" maxlength="100" placeholder="例如：预约年度体检" />
        </label>
        <div class="field">
          <span>提醒方式</span>
          <div class="deadline-switch" role="group" aria-label="提醒方式">
            <button
              type="button"
              :class="{ active: form.deadlineType === 'dated' }"
              :aria-pressed="form.deadlineType === 'dated'"
              @click="setDeadlineType('dated')"
            >有日期</button>
            <button
              type="button"
              :class="{ active: form.deadlineType === 'standing' }"
              :aria-pressed="form.deadlineType === 'standing'"
              @click="setDeadlineType('standing')"
            >无固定期限</button>
          </div>
        </div>
        <label v-if="form.deadlineType === 'dated'" class="field">
          <span>提醒时间</span>
          <input v-model="form.remindAt" class="input" type="datetime-local" />
        </label>
        <label class="field">
          <span>提醒说明</span>
          <textarea v-model="form.note" class="textarea" maxlength="500" rows="3" />
        </label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="saving" @click="formGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="saveReminder">
          {{ saving ? '正在保存' : '保存提醒' }}
        </button>
      </template>
    </ModalDialog>
  </section>
</template>

<style scoped>
.reminder-panel { display: grid; gap: 18px; margin-top: 20px; padding: 20px 22px; }
.reminder-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.reminder-head h2 { margin: 0; display: flex; align-items: center; gap: 8px; font-size: var(--font-size-xl); }
.reminder-groups { display: grid; gap: 20px; }
.reminder-group { display: grid; gap: 9px; }
.reminder-group h3 { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.reminder-list { display: grid; gap: 9px; }
.reminder-card { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: start; gap: 13px; padding: 15px 16px; border: 1px solid var(--color-border-soft); border-radius: var(--radius-md); background: var(--color-surface-raised); transition: border-color .18s ease, box-shadow .18s ease; }
.reminder-card:hover { border-color: var(--color-border-primary-soft); box-shadow: var(--box-shadow-sm); }
.reminder-icon { width: 38px; height: 38px; border-radius: 12px; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.reminder-icon.tone-deworming { background: var(--color-success-soft); color: var(--color-success); }
.reminder-icon.tone-weight { background: var(--color-warning-soft); color: var(--color-warning); }
.reminder-icon.tone-custom { background: var(--color-surface-warm); color: var(--color-text-secondary); }
.reminder-main { min-width: 0; padding: 1px 0 0; border: 0; background: transparent; text-align: left; color: var(--color-text); }
.reminder-title { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.reminder-title b { font-size: var(--font-size-base); }
.reminder-type, .reminder-title em { padding: 2px 8px; border-radius: 999px; background: var(--color-surface-warm); color: var(--color-text-secondary); font-size: var(--font-size-sm); font-style: normal; font-weight: var(--font-weight-medium); }
.reminder-title em.danger { background: var(--color-danger-soft); color: var(--color-danger); }
.reminder-title em.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.reminder-main small { display: block; margin-top: 6px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.reminder-main p { margin: 6px 0 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); line-height: 1.6; white-space: pre-wrap; }
.reminder-side { display: grid; justify-items: end; gap: 10px; min-width: 112px; }
.reminder-countdown { display: inline-flex; align-items: center; min-height: 28px; padding: 3px 10px; border-radius: 999px; background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); white-space: nowrap; }
.reminder-countdown.soon { background: var(--color-warning-soft); color: var(--color-warning); }
.reminder-actions { display: flex; gap: 5px; }
.reminder-actions button { width: 31px; height: 31px; border: 1px solid var(--color-border); border-radius: 50%; background: var(--color-white); color: var(--color-text-secondary); display: grid; place-items: center; }
.reminder-actions button:hover { border-color: var(--color-primary); color: var(--color-primary-strong); }
.reminder-actions button:disabled { cursor: not-allowed; opacity: .55; }
.reminder-form { display: grid; gap: 14px; }
.deadline-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.deadline-switch button { min-height: 40px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text-secondary); }
.deadline-switch button.active { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary-strong); font-weight: var(--font-weight-bold); }
@media (max-width: 640px) {
  .reminder-panel { padding: 17px 15px; }
  .reminder-head { align-items: flex-start; flex-direction: column; }
  .reminder-head .btn { width: 100%; justify-content: center; }
  .reminder-card { grid-template-columns: auto minmax(0, 1fr); }
  .reminder-side { grid-column: 2; width: 100%; min-width: 0; display: flex; align-items: center; justify-content: space-between; }
}
</style>
