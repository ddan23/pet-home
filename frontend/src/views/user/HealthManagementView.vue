<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  BookOpenCheck, Bug, CalendarClock, ChartNoAxesCombined, Check, ClipboardList,
  Download, HeartPulse, Link2, PencilLine, Plus, Printer, RotateCcw, Send, ShieldAlert,
  ShieldCheck, ShoppingBag, Sparkles, Syringe, Trash2, Weight, X
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { streamAi } from '../../services/aiStream'
import { formatPrice, parseJson } from '../../utils/format'
import { formatAllergyLabel, mergeAllergies } from '../../utils/allergies'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import ModalDialog from '../../components/ModalDialog.vue'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import HealthReminderPanel from '../../components/health/HealthReminderPanel.vue'

const AI_MODES = Object.freeze({
  QA: 'QA',
  REPORT: 'REPORT',
  RECOMMENDATION: 'RECOMMENDATION'
})
const ui = useUiStore()
const route = useRoute()
const { isUser, requireUser } = useUserAccess()
const pets = ref([])
const selectedIndex = ref(0)
const loading = ref(true)
const editOpen = ref(false)
const editSaving = ref(false)
const petFormMode = ref('edit')
const editPet = ref(null)
const planOpen = ref(false)
const planSaving = ref(false)
const planEditIndex = ref(-1)
const planForm = ref(defaultPlanForm('vaccine'))
const completeOpen = ref(false)
const completionSaving = ref(false)
const selectedPlan = ref(null)
const completionForm = ref(defaultCompletionForm())
const aiOpen = ref(false)
const aiMessages = ref([])
const aiInput = ref('')
const aiLoading = ref(false)
const aiHistoryLoading = ref(false)
const aiMode = ref(AI_MODES.QA)
const reportDownloadingId = ref(null)
const aiConversationIds = ref({
  [AI_MODES.QA]: '',
  [AI_MODES.REPORT]: '',
  [AI_MODES.RECOMMENDATION]: ''
})
const reminderRefreshToken = ref(0)
const chatBox = ref(null)
const allergyInput = ref('')
let aiAbortController = null
let aiHistorySequence = 0
const weightForm = ref({
  weight: '',
  date: todayText(),
  note: ''
})
const editGuard = useDialogDraftGuard({
  getSnapshot: () => ({ mode: petFormMode.value, pet: editPet.value }),
  getBusy: () => editSaving.value,
  close: closeEdit
})
const planGuard = useDialogDraftGuard({
  getSnapshot: () => ({ index: planEditIndex.value, form: planForm.value }),
  getBusy: () => planSaving.value,
  close: closePlan
})
const completionGuard = useDialogDraftGuard({
  getSnapshot: () => ({
    planDate: selectedPlan.value?.plannedOn || '',
    form: completionForm.value
  }),
  getBusy: () => completionSaving.value,
  close: closeCompletion
})

const currentPet = computed(() => pets.value[selectedIndex.value] || null)
const petFormTitle = computed(() => (
  petFormMode.value === 'add' ? '添加宠物' : '编辑宠物档案'
))
const petFormSaveLabel = computed(() => (
  petFormMode.value === 'add' ? '保存宠物' : '保存档案'
))
const printDate = computed(() => new Date().toLocaleDateString('zh-CN'))
const nextVaccinePlan = computed(() => findNextPlan('vaccine'))
const nextDewormingPlan = computed(() => findNextPlan('deworming'))
const completedVaccines = computed(() => sortByDateDesc(currentPet.value?.vaccines, 'date'))
const completedDewormings = computed(() => sortByDateDesc(currentPet.value?.dewormings, 'date'))
const weightTableRows = computed(() => {
  const records = [...(currentPet.value?.weightRecords || [])]
    .map((record, originalIndex) => ({ ...record, originalIndex }))
    .filter((record) => record.date && Number(record.weight) > 0)
    .sort((left, right) => left.date.localeCompare(right.date))
  return records
    .map((record, index) => ({
      ...record,
      change: index > 0 ? Number(record.weight) - Number(records[index - 1].weight) : null
    }))
    .reverse()
})
const latestWeight = computed(() => weightTableRows.value[0] || null)
const daysSinceWeight = computed(() => daysSince(latestWeight.value?.date))
const weightChart = computed(() => buildWeightChart(
  weightTableRows.value.slice(0, 12).reverse()))
const quickPrompts = computed(() => {
  if (aiMode.value === AI_MODES.RECOMMENDATION) {
    return [
      '根据它的年龄推荐合适的猫粮',
      '找适合它的美毛或低敏商品',
      '查找可预约的洗护或体检服务'
    ]
  }
  if (aiMode.value === AI_MODES.QA) {
    return [
      '分析最近体重变化',
      '整理带宠物就医时需要说明的信息',
      '疫苗和驱虫安排有哪些注意事项？'
    ]
  }
  return []
})
const aiWelcome = computed(() => {
  if (aiMode.value === AI_MODES.REPORT) {
    return {
      title: '一键生成专属健康报告',
      description: 'AI 将读取当前宠物年龄、体重、疫苗、驱虫、过敏史和照护计划，生成结构化健康分析。'
    }
  }
  if (aiMode.value === AI_MODES.RECOMMENDATION) {
    return {
      title: '按真实档案寻找商品与服务',
      description: '描述宠物需求，我会结合物种、年龄阶段、健康标签和平台在售内容给出推荐。'
    }
  }
  return {
    title: '您好，我是您的专属养宠顾问。',
    description: '您有任何关于疾病预防、科学选粮、行为训练的问题，随时可以问我。'
  }
})
const aiInputPlaceholder = computed(() => (
  aiMode.value === AI_MODES.RECOMMENDATION
    ? '说说想找的商品、服务或养护需求...'
    : '问问关于健康、选粮、训练的问题...'
))

function defaultPlanForm(careType) {
  return {
    careType,
    title: careType === 'vaccine' ? '猫三联加强针' : '体内外驱虫',
    plannedOn: '',
    note: ''
  }
}

function defaultCompletionForm() {
  return {
    title: '',
    completedOn: todayText(),
    providerName: '',
    batchNo: '',
    note: '',
    nextPlanDate: ''
  }
}

function todayText() {
  return new Date().toISOString().slice(0, 10)
}

function normalizePets(items) {
  const normalized = Array.isArray(items) ? items : []
  normalized.forEach((pet) => {
    pet.allergies ||= []
    pet.weightRecords ||= []
    pet.vaccines ||= []
    pet.dewormings ||= []
    pet.carePlans ||= []
  })
  return normalized
}

function applyPets(items) {
  pets.value = normalizePets(items)
  if (!pets.value.length) {
    selectedIndex.value = 0
    return
  }
  const saved = Number(localStorage.getItem('petHomeSelectedPet') || 0)
  selectedIndex.value = saved >= 0 && saved < pets.value.length ? saved : 0
}

async function load(showLoading = true) {
  if (showLoading) {
    loading.value = true
  }
  try {
    const petsResult = await api.get('/user/pets')
    applyPets(parseJson(petsResult.data?.pets, []))
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    if (showLoading) {
      loading.value = false
    }
  }
}

async function savePets(message = '养护档案已保存') {
  try {
    const result = await api.put('/user/pets', {
      pets: JSON.stringify(pets.value)
    })
    ui.toast(result.msg || message, 'success')
    await load(false)
  } catch (error) {
    ui.toast(error.message, 'error')
    throw error
  }
}

function selectPet(index) {
  selectedIndex.value = Number(index)
  localStorage.setItem('petHomeSelectedPet', String(selectedIndex.value))
}

function selectPetById(petId) {
  const index = pets.value.findIndex((pet) => Number(pet.id) === Number(petId))
  if (index >= 0) {
    selectPet(index)
  }
}

function selectPetFromReminder(petId) {
  selectPetById(petId)
  nextTick(() => {
    document.querySelector('.next-care')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    })
  })
}

function scrollToRemindersIfRequested() {
  if (route.query.section !== 'reminders') return
  nextTick(() => {
    document.getElementById('health-reminders')?.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    })
  })
}

function openEdit() {
  if (!currentPet.value) return
  petFormMode.value = 'edit'
  editPet.value = JSON.parse(JSON.stringify(currentPet.value))
  editPet.value.vaccines ||= []
  editPet.value.dewormings ||= []
  editPet.value.allergies ||= []
  allergyInput.value = ''
  editOpen.value = true
  editGuard.markClean()
}

function openAddPet() {
  petFormMode.value = 'add'
  editPet.value = {
    name: '',
    species: '猫',
    breed: '',
    color: '',
    gender: '母',
    birthDate: '',
    weight: '',
    neutered: false,
    medicalHistory: '',
    allergies: [],
    vaccines: [],
    dewormings: [],
    weightRecords: [],
    carePlans: []
  }
  allergyInput.value = ''
  editOpen.value = true
  editGuard.markClean()
}

function closeEdit() {
  editOpen.value = false
  editPet.value = null
  allergyInput.value = ''
}

function addVaccineRecord() {
  editPet.value.vaccines.push({
    name: '',
    date: '',
    batchNo: '',
    vetName: ''
  })
}

function addDewormingRecord() {
  editPet.value.dewormings.push({
    productName: '',
    date: '',
    note: ''
  })
}

async function saveEdit() {
  if (!editPet.value?.name?.trim()) {
    ui.toast('请填写宠物名字', 'warning')
    return
  }
  editPet.value.weight = editPet.value.weight === ''
    ? latestWeightValue(editPet.value) : Number(editPet.value.weight)
  editPet.value.vaccines = editPet.value.vaccines.map(({ nextDate, ...record }) => record)
  editPet.value.dewormings = editPet.value.dewormings.map(
    ({ nextDate, ...record }) => record)
  const previousPets = pets.value
  const isAdding = petFormMode.value === 'add'
  if (editSaving.value) return
  editSaving.value = true
  try {
    const nextPets = isAdding
      ? [...pets.value, editPet.value]
      : pets.value.map((pet, index) => (
        index === selectedIndex.value ? editPet.value : pet
      ))
    pets.value = nextPets
    await savePets(isAdding ? '宠物已添加' : '宠物档案已更新')
    reminderRefreshToken.value += 1
    if (isAdding) {
      selectPet(pets.value.length - 1)
    }
    closeEdit()
  } catch {
    pets.value = previousPets
  } finally {
    editSaving.value = false
  }
}

function latestWeightValue(pet) {
  const latest = sortByDateDesc(pet?.weightRecords, 'date')[0]
  return latest ? Number(latest.weight) : null
}

function sortByDateDesc(items, dateField) {
  return [...(items || [])]
    .filter((item) => item && item[dateField])
    .sort((left, right) => String(right[dateField]).localeCompare(String(left[dateField])))
}

async function addWeight() {
  const weight = Number(weightForm.value.weight)
  const recordDate = weightForm.value.date
  if (!weight || weight <= 0 || !recordDate) {
    ui.toast('请输入有效体重和日期', 'warning')
    return
  }
  if (recordDate > todayText()) {
    ui.toast('体重记录日期不能晚于今天', 'warning')
    return
  }
  currentPet.value.weightRecords.push({
    weight,
    date: recordDate,
    note: weightForm.value.note.trim()
  })
  currentPet.value.weight = latestWeightValue(currentPet.value)
  const defaultDate = todayText()
  weightForm.value = { weight: '', date: defaultDate, note: '' }
  try {
    await savePets('体重记录已添加')
    reminderRefreshToken.value += 1
  } catch (error) {
    await load(false)
  }
}

async function deleteWeight(originalIndex) {
  const confirmed = await ui.confirm({
    message: '确定删除这条体重记录吗？',
    danger: true
  })
  if (!confirmed) return
  currentPet.value.weightRecords.splice(originalIndex, 1)
  currentPet.value.weight = latestWeightValue(currentPet.value)
  try {
    await savePets('体重记录已删除')
    reminderRefreshToken.value += 1
  } catch (error) {
    await load(false)
  }
}

function addAllergies() {
  if (!editPet.value) return
  if (!allergyInput.value.trim()) {
    ui.toast('请输入过敏原名称', 'warning')
    return
  }
  const result = mergeAllergies(editPet.value.allergies, allergyInput.value)
  editPet.value.allergies = result.allergies
  allergyInput.value = ''
  if (result.addedCount > 0) {
    ui.toast(`已添加 ${result.addedCount} 个过敏原`, 'success')
  }
  if (result.duplicateCount > 0) {
    ui.toast('重复的过敏原已自动忽略', 'warning')
  }
  if (result.tooLongCount > 0) {
    ui.toast('单个过敏原名称不能超过 50 个字符', 'warning')
  }
  if (result.limitCount > 0) {
    ui.toast('每只宠物最多记录 20 个过敏原', 'warning')
  }
}

function removeAllergy(code) {
  if (!editPet.value?.allergies) return
  editPet.value.allergies = editPet.value.allergies.filter((item) => item !== code)
}

function findNextPlan(careType) {
  return [...(currentPet.value?.carePlans || [])]
    .map((plan, originalIndex) => ({
      ...plan,
      originalIndex,
      days: daysUntil(plan.plannedOn)
    }))
    .filter((plan) => plan.careType === careType)
    .sort((left, right) => String(left.plannedOn).localeCompare(String(right.plannedOn)))[0]
    || null
}

function daysUntil(dateText) {
  if (!dateText) return null
  const dueTime = new Date(`${dateText}T00:00:00`).getTime()
  const todayTime = new Date(`${todayText()}T00:00:00`).getTime()
  return Math.ceil((dueTime - todayTime) / 86400000)
}

function daysSince(dateText) {
  if (!dateText) return null
  return Math.max(0, Math.floor(
    (new Date(`${todayText()}T00:00:00`).getTime()
      - new Date(`${dateText}T00:00:00`).getTime()) / 86400000))
}

function planCountdown(plan) {
  if (!plan || plan.days === null) return '日期待确认'
  if (plan.days < 0) return `已逾期 ${Math.abs(plan.days)} 天`
  if (plan.days === 0) return '今天到期'
  return `还有 ${plan.days} 天`
}

function planCardClass(plan) {
  if (!plan || plan.days === null) return ''
  if (plan.days < 0) return 'overdue'
  if (plan.days <= 30) return 'due-soon'
  return ''
}

function openPlanEditor(careType, plan = null) {
  planEditIndex.value = plan ? plan.originalIndex : -1
  planForm.value = plan
    ? {
      careType: plan.careType,
      title: plan.title || '',
      plannedOn: plan.plannedOn || '',
      note: plan.note || ''
    }
    : defaultPlanForm(careType)
  planOpen.value = true
  planGuard.markClean()
}

function closePlan() {
  planOpen.value = false
  planEditIndex.value = -1
  planForm.value = defaultPlanForm('vaccine')
}

async function deleteEditingPlan() {
  const plan = currentPet.value?.carePlans?.[planEditIndex.value]
  if (!plan) return
  if (await removePlan({ ...plan, originalIndex: planEditIndex.value })) {
    closePlan()
  }
}

async function savePlan() {
  if (planSaving.value) return
  const title = planForm.value.title.trim()
  if (!title || !planForm.value.plannedOn) {
    ui.toast('请填写照护项目和计划日期', 'warning')
    return
  }
  if (planForm.value.plannedOn < todayText()) {
    ui.toast('新的照护计划日期不能早于今天', 'warning')
    return
  }
  currentPet.value.carePlans ||= []
  const plan = {
    careType: planForm.value.careType,
    title,
    plannedOn: planForm.value.plannedOn,
    note: planForm.value.note.trim()
  }
  if (planEditIndex.value >= 0) {
    currentPet.value.carePlans[planEditIndex.value] = plan
  } else {
    currentPet.value.carePlans = currentPet.value.carePlans
      .filter((item) => item.careType !== plan.careType)
    currentPet.value.carePlans.push(plan)
  }
  planSaving.value = true
  try {
    await savePets('下一次照护计划已保存')
    reminderRefreshToken.value += 1
    closePlan()
  } catch (error) {
    await load(false)
  } finally {
    planSaving.value = false
  }
}

async function removePlan(plan) {
  const confirmed = await ui.confirm({
    message: '确定删除这个待完成计划吗？',
    danger: true
  })
  if (!confirmed) return false
  currentPet.value.carePlans.splice(plan.originalIndex, 1)
  try {
    await savePets('照护计划已删除')
    reminderRefreshToken.value += 1
    return true
  } catch (error) {
    await load(false)
    return false
  }
}

function openCompletion(plan) {
  selectedPlan.value = plan
  completionForm.value = {
    title: plan.title || '',
    completedOn: todayText(),
    providerName: '',
    batchNo: '',
    note: plan.note || '',
    nextPlanDate: ''
  }
  completeOpen.value = true
  completionGuard.markClean()
}

function closeCompletion() {
  completeOpen.value = false
  selectedPlan.value = null
  completionForm.value = defaultCompletionForm()
}

async function confirmCompletion() {
  if (!selectedPlan.value || completionSaving.value) return
  if (!completionForm.value.title.trim() || !completionForm.value.completedOn) {
    ui.toast('请填写完成项目和实际完成日期', 'warning')
    return
  }
  if (completionForm.value.completedOn > todayText()) {
    ui.toast('实际完成日期不能晚于今天', 'warning')
    return
  }
  if (completionForm.value.nextPlanDate
      && completionForm.value.nextPlanDate <= completionForm.value.completedOn) {
    ui.toast('下一次计划日期必须晚于实际完成日期', 'warning')
    return
  }

  completionSaving.value = true
  const plan = selectedPlan.value
  const title = completionForm.value.title.trim()
  if (plan.careType === 'vaccine') {
    currentPet.value.vaccines.push({
      name: title,
      date: completionForm.value.completedOn,
      batchNo: completionForm.value.batchNo.trim(),
      vetName: completionForm.value.providerName.trim()
    })
  } else {
    currentPet.value.dewormings.push({
      productName: title,
      date: completionForm.value.completedOn,
      note: completionForm.value.note.trim()
    })
  }
  currentPet.value.carePlans.splice(plan.originalIndex, 1)
  if (completionForm.value.nextPlanDate) {
    currentPet.value.carePlans.push({
      careType: plan.careType,
      title,
      plannedOn: completionForm.value.nextPlanDate,
      note: ''
    })
  }
  try {
    await savePets('已完成记录已写入宠物档案')
    reminderRefreshToken.value += 1
    closeCompletion()
  } catch (error) {
    await load(false)
  } finally {
    completionSaving.value = false
  }
}

function buildWeightChart(records) {
  if (!records.length) {
    return {
      linePoints: '',
      areaPoints: '',
      points: [],
      labels: [],
      ticks: []
    }
  }
  const width = 760
  const height = 169
  const left = 48
  const right = 20
  const top = 28
  const bottom = 30
  const values = records.map((record) => Number(record.weight))
  const rawMin = Math.min(...values)
  const rawMax = Math.max(...values)
  const padding = Math.max((rawMax - rawMin) * .18, .2)
  const minimum = rawMin - padding
  const maximum = rawMax + padding
  const stepX = records.length > 1
    ? (width - left - right) / (records.length - 1) : 0
  const points = records.map((record, index) => {
    const x = records.length > 1 ? left + stepX * index : width / 2
    const ratio = (Number(record.weight) - minimum) / (maximum - minimum || 1)
    const y = top + (height - top - bottom) * (1 - ratio)
    return {
      x,
      y,
      weight: Number(record.weight),
      date: record.date
    }
  })
  const linePoints = points.map((point) => `${point.x},${point.y}`).join(' ')
  const baseline = height - bottom
  const areaPoints = `${points[0].x},${baseline} ${linePoints} `
    + `${points[points.length - 1].x},${baseline}`
  const labelStep = Math.max(1, Math.ceil(points.length / 4))
  const labels = points
    .map((point, index) => ({ ...point, index }))
    .filter((point, index) => (
      index % labelStep === 0 || index === points.length - 1
    ))
    .map((point) => ({
      x: point.x,
      y: height - 8,
      text: point.date.slice(5)
    }))
  const ticks = [maximum, (maximum + minimum) / 2, minimum].map((value, index) => ({
    y: top + (height - top - bottom) * (index / 2),
    text: value.toFixed(1)
  }))
  return { linePoints, areaPoints, points, labels, ticks }
}

function formatWeightChange(change) {
  if (change === null || change === undefined) return '首次记录'
  if (Math.abs(change) < .05) return '持平'
  return `${change > 0 ? '+' : ''}${change.toFixed(1)} kg`
}

function formatAge(birthDate) {
  if (!birthDate) return '--'
  const birthday = new Date(`${birthDate}T00:00:00`)
  if (Number.isNaN(birthday.getTime()) || birthday > new Date()) return '--'
  const today = new Date()
  let months = (today.getFullYear() - birthday.getFullYear()) * 12
    + today.getMonth() - birthday.getMonth()
  if (today.getDate() < birthday.getDate()) months--
  if (months < 0) return '--'
  const years = Math.floor(months / 12)
  const remainingMonths = months % 12
  return years > 0
    ? `${years}岁${remainingMonths ? `${remainingMonths}个月` : ''}`
    : `${Math.max(1, remainingMonths)}个月`
}

async function openAssistant() {
  if (!await requireUser({ action: 'AI 养宠顾问' })) return
  if (!currentPet.value) {
    ui.toast('请先添加宠物档案', 'warning')
    return
  }
  aiOpen.value = true
  await loadAiHistory()
}

async function sendAi(text = '') {
  if (!await requireUser({ action: 'AI 养宠顾问' })) return
  if (aiMode.value === AI_MODES.REPORT) {
    await generateHealthReport()
    return
  }
  const message = String(text || '').trim()
  if (!message || aiLoading.value) return
  if (message.length > 1000) {
    ui.toast('问题不能超过 1000 个字符', 'warning')
    return
  }
  aiInput.value = ''
  const path = aiMode.value === AI_MODES.RECOMMENDATION
    ? '/ai/health/recommend/stream'
    : '/ai/health/chat/stream'
  await runAiStream(path, message, message)
}

async function generateHealthReport() {
  if (!await requireUser({ action: '生成专属健康报告' })) return
  if (!currentPet.value || aiLoading.value) return
  await runAiStream('/ai/health/report/stream', '', '生成专属健康报告')
}

async function downloadHealthReport(message) {
  if (!message?.id || reportDownloadingId.value) return
  reportDownloadingId.value = message.id
  try {
    const result = await api.download(
      `/ai/health/reports/${encodeURIComponent(message.id)}/pdf`
    )
    const objectUrl = URL.createObjectURL(result.blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = result.filename || `health-report-${message.id}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  } catch (error) {
    ui.toast(error.message || '健康报告下载失败', 'error')
  } finally {
    reportDownloadingId.value = null
  }
}

async function runAiStream(path, message, userContent) {
  const streamMode = aiMode.value
  const assistantMessage = reactive({
    role: 'assistant',
    messageType: streamMode === AI_MODES.REPORT ? 'HEALTH_REPORT' : '',
    content: '',
    citations: [],
    recommendations: [],
    emergency: false,
    disclaimer: '',
    streaming: true
  })
  if (userContent) {
    aiMessages.value.push({
      role: 'user',
      content: userContent
    })
  }
  aiMessages.value.push(assistantMessage)
  aiLoading.value = true
  aiAbortController = new AbortController()
  let streamCompleted = false
  scrollChat()
  try {
    await streamAi(path, {
      conversationId: aiConversationIds.value[streamMode] || null,
      petId: currentPet.value.id,
      ...(message ? { message } : {})
    }, {
      signal: aiAbortController.signal,
      onEvent: (event) => {
        if (event.event === 'done') {
          streamCompleted = true
        }
        applyAiStreamEvent(assistantMessage, event, streamMode)
      }
    })
    if (!streamCompleted) {
      throw new Error('AI 流式响应意外中断，请重试')
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      if (!assistantMessage.content) {
        assistantMessage.content = '已停止生成。'
      }
    } else {
      assistantMessage.content ||= error.message || 'AI 养宠顾问暂时不可用。'
      ui.toast(error.message || 'AI 生成失败', 'error')
    }
  } finally {
    assistantMessage.streaming = false
    aiLoading.value = false
    aiAbortController = null
    scrollChat()
  }
}

function applyAiStreamEvent(message, event, streamMode) {
  const payload = event.data || {}
  if (event.event === 'start') {
    if (payload.conversationId) {
      aiConversationIds.value[streamMode] = String(payload.conversationId)
    }
    return
  }
  if (event.event === 'delta') {
    message.content += payload.text || payload.content || ''
    scrollChat()
    return
  }
  if (event.event === 'citations') {
    message.citations = payload.citations || []
    return
  }
  if (event.event === 'recommendations') {
    message.recommendations = payload.recommendations || []
    return
  }
  if (event.event === 'replace') {
    message.content = payload.text || ''
    if (payload.category) message.category = payload.category
    scrollChat()
    return
  }
  if (event.event === 'done') {
    message.streaming = false
    message.messageType = payload.messageType || message.messageType
    message.emergency = Boolean(payload.emergency)
    message.disclaimer = payload.disclaimer || ''
    if (payload.messageId) {
      message.id = payload.messageId
    }
    if (payload.conversationId) {
      aiConversationIds.value[streamMode] = String(payload.conversationId)
    }
  }
}

function stopAiGeneration() {
  aiAbortController?.abort()
}

async function setAiMode(mode) {
  if (aiLoading.value) {
    stopAiGeneration()
  }
  aiMode.value = mode
  aiMessages.value = []
  await loadAiHistory()
}

async function loadAiHistory() {
  if (!isUser.value || !currentPet.value) {
    aiMessages.value = []
    return
  }
  const sequence = ++aiHistorySequence
  const mode = aiMode.value
  const petId = currentPet.value.id
  aiHistoryLoading.value = true
  try {
    const result = await api.get(
      `/ai/health/conversations${queryString({
        petId,
        mode
      })}`
    )
    if (sequence !== aiHistorySequence) return
    const conversation = normalizeConversation(result.data)
    if (!conversation?.id) {
      aiConversationIds.value[mode] = ''
      aiMessages.value = []
      return
    }
    aiConversationIds.value[mode] = String(conversation.id)
    const messageResult = await api.get(
      `/ai/health/conversations/${conversation.id}/messages`
    )
    if (sequence !== aiHistorySequence) return
    aiMessages.value = normalizeHistoryMessages(messageResult.data)
    scrollChat()
  } catch (error) {
    if (sequence === aiHistorySequence) {
      aiMessages.value = []
      ui.toast(error.message || '读取 AI 对话失败', 'error')
    }
  } finally {
    if (sequence === aiHistorySequence) {
      aiHistoryLoading.value = false
    }
  }
}

function normalizeConversation(data) {
  if (!data) return null
  if (Array.isArray(data)) return data[0] || null
  if (Array.isArray(data.list)) return data.list[0] || null
  if (data.conversation) return data.conversation
  return data
}

function normalizeHistoryMessages(data) {
  const list = Array.isArray(data)
    ? data
    : data?.list || data?.messages || []
  return list.map((item) => {
    const structuredData = parseStructuredData(item.structuredData)
    return {
      id: item.id,
      role: item.role === 'assistant' ? 'assistant' : 'user',
      messageType: item.messageType,
      content: item.content || '',
      citations: parseStructuredData(item.citations) || [],
      recommendations: structuredData?.recommendations || [],
      emergency: Boolean(structuredData?.emergency),
      disclaimer: structuredData?.disclaimer || '',
      streaming: false
    }
  })
}

function parseStructuredData(value) {
  if (!value) return null
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

async function clearAiConversation() {
  const confirmed = await ui.confirm({
    title: '清空当前对话',
    message: '当前 AI 对话及历史消息将被删除，且无法恢复。',
    confirmText: '清空对话',
    danger: true
  })
  if (!confirmed) return
  stopAiGeneration()
  const mode = aiMode.value
  const conversationId = aiConversationIds.value[mode]
  if (conversationId) {
    try {
      await api.delete(`/ai/health/conversations/${conversationId}`)
    } catch (error) {
      ui.toast(error.message || '清空对话失败', 'error')
      return
    }
  }
  aiConversationIds.value[mode] = ''
  if (mode === aiMode.value) {
    aiMessages.value = []
  }
  ui.toast('当前 AI 对话已清空', 'success')
}

function closeAiDrawer() {
  stopAiGeneration()
  aiOpen.value = false
}

function scrollChat() {
  nextTick(() => {
    if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
  })
}

function targetPetLabel(value) {
  const labels = { CAT: '猫咪适用', DOG: '狗狗适用', ALL: '猫狗通用' }
  return labels[value] || '适用宠物待确认'
}

function printProfile() {
  if (!currentPet.value) return
  window.print()
}

function resetPrivateState() {
  stopAiGeneration()
  pets.value = []
  selectedIndex.value = 0
  editOpen.value = false
  planOpen.value = false
  completeOpen.value = false
  aiOpen.value = false
  aiMessages.value = []
  aiConversationIds.value = {
    [AI_MODES.QA]: '',
    [AI_MODES.REPORT]: '',
    [AI_MODES.RECOMMENDATION]: ''
  }
  loading.value = false
}

onMounted(async () => {
  if (isUser.value) {
    await load()
    selectPetById(route.query.petId)
    scrollToRemindersIfRequested()
  } else {
    loading.value = false
  }
})

watch(isUser, async (hasUserAccess) => {
  if (hasUserAccess) {
    await load()
    selectPetById(route.query.petId)
    scrollToRemindersIfRequested()
  } else {
    resetPrivateState()
  }
})

watch(() => route.query.petId, (petId) => {
  if (petId) selectPetById(petId)
})

watch(() => route.query.section, scrollToRemindersIfRequested)

watch(() => currentPet.value?.id, async (petId, previousPetId) => {
  if (!petId || petId === previousPetId) return
  stopAiGeneration()
  aiConversationIds.value = {
    [AI_MODES.QA]: '',
    [AI_MODES.REPORT]: '',
    [AI_MODES.RECOMMENDATION]: ''
  }
  aiMessages.value = []
  if (aiOpen.value) {
    await loadAiHistory()
  }
})

onBeforeUnmount(() => {
  stopAiGeneration()
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading health-heading">
      <div>
        <h1>宠物养护</h1>
      </div>
      <div class="health-heading-actions">
        <button class="btn ai-entry-button" type="button" @click="openAssistant()">
          <Sparkles :size="16" />
          AI 养宠顾问
        </button>
      </div>
    </header>

    <LoadingPanel v-if="isUser && loading" label="正在读取宠物档案" />
    <EmptyState v-else-if="!pets.length" title="还没有宠物档案">
      <button class="btn btn-primary" type="button" @click="openAddPet">
        <Plus :size="16" />添加宠物
      </button>
    </EmptyState>
    <template v-else>
      <nav
        class="pet-switcher"
        :style="{ '--pet-count': pets.length }"
        aria-label="选择宠物"
      >
        <button
          v-for="(pet, index) in pets"
          :key="pet.id || `${pet.name}-${index}`"
          class="pet-switch-card"
          :class="{ active: selectedIndex === index }"
          type="button"
          :aria-pressed="selectedIndex === index"
          @click="selectPet(index)"
        >
          <span class="pet-switch-avatar">
            <img v-if="pet.photo" :src="assetUrl(pet.photo)" :alt="`${pet.name}的头像`" />
            <HeartPulse v-else :size="18" />
          </span>
          <span>
            <b>{{ pet.name || '未命名' }}</b>
            <small>{{ pet.breed || '品种未填写' }} · {{ formatAge(pet.birthDate) }}</small>
          </span>
          <Check v-if="selectedIndex === index" :size="16" />
        </button>
        <button class="add-pet-link" type="button" @click="openAddPet">
          <Plus :size="16" />添加宠物
        </button>
      </nav>

      <div class="health-workspace">
        <div class="health-main">
          <section class="panel pet-archive">
            <header class="archive-head">
              <div class="archive-portrait">
                <img
                  v-if="currentPet.photo"
                  :src="assetUrl(currentPet.photo)"
                  :alt="`${currentPet.name}的照片`"
                />
                <HeartPulse v-else :size="30" />
              </div>
              <div class="archive-identity">
                <div>
                  <h2>{{ currentPet.name || '未命名' }}</h2>
                  <span class="tag sage">当前宠物档案</span>
                </div>
                <p>
                  {{ currentPet.species || currentPet.type || '宠物' }}
                  · {{ currentPet.gender || '性别未填写' }}
                  · {{ currentPet.neutered ? '已绝育' : '未绝育' }}
                  · {{ currentPet.birthDate || '出生日期未填写' }}
                </p>
                <div class="identity-tags">
                  <span>{{ formatAge(currentPet.birthDate) }}</span>
                  <span>{{ latestWeight ? `${latestWeight.weight} kg` : '体重未记录' }}</span>
                  <span>{{ currentPet.allergies?.length || 0 }} 项过敏原</span>
                </div>
              </div>
              <div class="archive-actions">
                <button class="btn btn-dark btn-sm" type="button" @click="openEdit">
                  <PencilLine :size="15" />编辑档案
                </button>
                <button class="btn btn-ghost btn-sm" type="button" @click="printProfile">
                  <Printer :size="15" />打印
                </button>
              </div>
            </header>

            <div class="archive-grid">
              <section class="archive-column">
                <h3><ClipboardList :size="15" />基本信息</h3>
                <dl>
                  <div><dt>品种</dt><dd>{{ currentPet.breed || '--' }}</dd></div>
                  <div><dt>毛色</dt><dd>{{ currentPet.color || '--' }}</dd></div>
                  <div><dt>当前体重</dt><dd>{{ latestWeight ? `${latestWeight.weight} kg` : '--' }}</dd></div>
                  <div><dt>体重记录</dt><dd>{{ currentPet.weightRecords?.length || 0 }} 条</dd></div>
                </dl>
              </section>

              <section class="archive-column">
                <h3><ShieldAlert :size="15" />过敏与病史</h3>
                <div v-if="currentPet.allergies?.length" class="allergy-tags">
                  <span v-for="allergy in currentPet.allergies" :key="allergy">
                    {{ formatAllergyLabel(allergy) }}
                  </span>
                </div>
                <p v-else class="muted-copy">暂未记录过敏原</p>
                <p class="medical-note">{{ currentPet.medicalHistory || '暂无病史记录' }}</p>
              </section>

              <section class="archive-column archive-history">
                <h3><BookOpenCheck :size="15" />已完成疫苗与驱虫</h3>
                <div v-if="completedVaccines.length || completedDewormings.length">
                  <div
                    v-for="(vaccine, index) in completedVaccines"
                    :key="`vaccine-${index}`"
                    class="completed-record"
                  >
                    <span><Syringe :size="14" /></span>
                    <div>
                      <b>{{ vaccine.name || '疫苗记录' }}</b>
                      <small>{{ vaccine.date || '--' }} {{ vaccine.vetName ? `· ${vaccine.vetName}` : '' }}</small>
                    </div>
                    <em>已完成</em>
                  </div>
                  <div
                    v-for="(record, index) in completedDewormings"
                    :key="`deworming-${index}`"
                    class="completed-record"
                  >
                    <span><Bug :size="14" /></span>
                    <div>
                      <b>{{ record.productName || '驱虫记录' }}</b>
                      <small>{{ record.date || '--' }}</small>
                    </div>
                    <em>已完成</em>
                  </div>
                </div>
                <p v-else class="muted-copy">暂无已完成的疫苗或驱虫记录</p>
              </section>
            </div>
          </section>

          <section class="panel next-care">
            <header class="section-head">
              <div>
                <h2><CalendarClock :size="18" />下一次照护</h2>
              </div>
            </header>
            <div class="next-care-grid">
              <article class="care-plan" :class="planCardClass(nextVaccinePlan)">
                <span class="care-plan-icon"><Syringe :size="20" /></span>
                <div>
                  <span>下一次疫苗</span>
                  <template v-if="nextVaccinePlan">
                    <b>{{ nextVaccinePlan.title }}</b>
                    <p>{{ nextVaccinePlan.plannedOn }} · {{ planCountdown(nextVaccinePlan) }}</p>
                  </template>
                  <template v-else>
                    <b>尚未设置计划</b>
                    <p>添加下一次接种时间</p>
                  </template>
                </div>
                <div class="care-plan-actions">
                  <template v-if="nextVaccinePlan">
                    <button class="btn btn-ghost btn-sm" type="button" @click="openPlanEditor('vaccine', nextVaccinePlan)">调整</button>
                    <button class="btn btn-primary btn-sm" type="button" @click="openCompletion(nextVaccinePlan)">完成并记入档案</button>
                  </template>
                  <button v-else class="btn btn-ghost btn-sm" type="button" @click="openPlanEditor('vaccine')">
                    <Plus :size="14" />添加计划
                  </button>
                </div>
              </article>

              <article class="care-plan" :class="planCardClass(nextDewormingPlan)">
                <span class="care-plan-icon deworming"><Bug :size="20" /></span>
                <div>
                  <span>下一次驱虫</span>
                  <template v-if="nextDewormingPlan">
                    <b>{{ nextDewormingPlan.title }}</b>
                    <p>{{ nextDewormingPlan.plannedOn }} · {{ planCountdown(nextDewormingPlan) }}</p>
                  </template>
                  <template v-else>
                    <b>尚未设置计划</b>
                    <p>添加下一次驱虫时间</p>
                  </template>
                </div>
                <div class="care-plan-actions">
                  <template v-if="nextDewormingPlan">
                    <button class="btn btn-ghost btn-sm" type="button" @click="openPlanEditor('deworming', nextDewormingPlan)">调整</button>
                    <button class="btn btn-primary btn-sm" type="button" @click="openCompletion(nextDewormingPlan)">完成并记入档案</button>
                  </template>
                  <button v-else class="btn btn-ghost btn-sm" type="button" @click="openPlanEditor('deworming')">
                    <Plus :size="14" />添加计划
                  </button>
                </div>
              </article>
            </div>
          </section>

          <section class="panel weight-panel">
            <header class="section-head weight-head">
              <div>
                <h2><ChartNoAxesCombined :size="18" />体重趋势与记录</h2>
              </div>
              <span>{{ currentPet.weightRecords?.length || 0 }} 条记录</span>
            </header>

            <div v-if="weightChart.points.length" class="weight-chart">
              <svg viewBox="0 0 760 169" role="img" aria-label="体重变化趋势图">
                <line
                  v-for="tick in weightChart.ticks"
                  :key="`tick-${tick.y}`"
                  x1="48"
                  :y1="tick.y"
                  x2="740"
                  :y2="tick.y"
                  stroke="var(--color-border-soft)"
                />
                <polygon :points="weightChart.areaPoints" fill="var(--color-chart-fill)" opacity=".55" />
                <polyline
                  :points="weightChart.linePoints"
                  fill="none"
                  stroke="var(--color-primary)"
                  stroke-width="4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <g v-for="point in weightChart.points" :key="point.date">
                  <circle
                    :cx="point.x"
                    :cy="point.y"
                    r="5"
                    fill="var(--color-surface-paper)"
                    stroke="var(--color-primary)"
                    stroke-width="3"
                  >
                    <title>{{ point.date }}：{{ point.weight }} kg</title>
                  </circle>
                </g>
                <text
                  v-for="label in weightChart.labels"
                  :key="`label-${label.text}-${label.x}`"
                  :x="label.x"
                  :y="label.y"
                  text-anchor="middle"
                  fill="var(--color-text-muted)"
                  font-size="9"
                >{{ label.text }}</text>
                <text
                  v-for="tick in weightChart.ticks"
                  :key="`value-${tick.y}`"
                  x="18"
                  :y="tick.y + 3"
                  fill="var(--color-text-muted)"
                  font-size="9"
                >{{ tick.text }}</text>
              </svg>
              <span v-if="latestWeight" class="weight-freshness">
                距上次称重 {{ daysSinceWeight }} 天
              </span>
            </div>

            <form class="weight-form" @submit.prevent="addWeight">
              <input v-model="weightForm.weight" class="input" type="number" step="0.1" min="0.1" placeholder="体重 kg" />
              <input v-model="weightForm.date" class="input" type="date" :max="todayText()" />
              <input v-model="weightForm.note" class="input" maxlength="255" placeholder="备注（选填）" />
              <button class="btn btn-primary" type="submit"><Plus :size="15" />记录体重</button>
            </form>

            <div v-if="weightTableRows.length" class="weight-table-wrap">
              <table class="weight-table">
                <thead>
                  <tr><th>记录日期</th><th>体重</th><th>较上次</th><th>备注</th><th>操作</th></tr>
                </thead>
                <tbody>
                  <tr v-for="record in weightTableRows" :key="`${record.date}-${record.originalIndex}`">
                    <td>{{ record.date }}</td>
                    <td><b>{{ Number(record.weight).toFixed(1) }} kg</b></td>
                    <td>{{ formatWeightChange(record.change) }}</td>
                    <td>{{ record.note || '--' }}</td>
                    <td>
                      <button type="button" class="delete-button" aria-label="删除体重记录" @click="deleteWeight(record.originalIndex)">
                        <Trash2 :size="15" />
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <EmptyState v-else title="还没有体重记录" />
          </section>
        </div>

      </div>

      <HealthReminderPanel
        :pets="pets"
        :selected-pet-id="currentPet?.id"
        :refresh-token="reminderRefreshToken"
        @select-pet="selectPetFromReminder"
      />
    </template>

    <ModalDialog
      :open="editOpen"
      :title="petFormTitle"
      width="720px"
      :close-disabled="editSaving"
      @close="editGuard.requestClose"
    >
      <div v-if="editPet" class="edit-grid">
        <label class="field"><span>名字</span><input v-model="editPet.name" class="input" maxlength="50" /></label>
        <label class="field"><span>物种</span><select v-model="editPet.species" class="select"><option>猫</option><option>狗</option></select></label>
        <label class="field"><span>品种</span><input v-model="editPet.breed" class="input" maxlength="50" /></label>
        <label class="field"><span>毛色</span><input v-model="editPet.color" class="input" maxlength="50" /></label>
        <label class="field"><span>性别</span><select v-model="editPet.gender" class="select"><option>公</option><option>母</option></select></label>
        <label class="field"><span>出生日期</span><input v-model="editPet.birthDate" class="input" type="date" :max="todayText()" /></label>
        <label class="field"><span>当前体重 kg</span><input v-model="editPet.weight" class="input" type="number" step="0.1" min="0.1" /></label>
        <label class="field checkbox-field"><input v-model="editPet.neutered" type="checkbox" /><span>已绝育</span></label>
        <label class="field full"><span>病史</span><textarea v-model="editPet.medicalHistory" class="textarea" maxlength="1000" /></label>
        <div class="field full">
          <span>过敏原</span>
          <div v-if="editPet.allergies?.length" class="allergy-tags editable">
            <span v-for="allergy in editPet.allergies" :key="allergy">
              {{ formatAllergyLabel(allergy) }}
              <button type="button" :aria-label="`删除过敏原 ${formatAllergyLabel(allergy)}`" @click="removeAllergy(allergy)">
                <X :size="13" />
              </button>
            </span>
          </div>
          <div class="allergy-input-row">
            <input
              v-model="allergyInput"
              class="input"
              type="text"
              maxlength="200"
              placeholder="输入过敏原，例如：鸡肉、花粉、尘螨"
              @keydown.enter.prevent="addAllergies"
            />
            <button class="btn btn-ghost btn-sm" type="button" @click="addAllergies">
              <Plus :size="15" />添加
            </button>
          </div>
          <small>可输入多个名称，用逗号或顿号分隔。</small>
        </div>
        <div class="field full">
          <span>已完成疫苗</span>
          <div v-for="(vaccine, index) in editPet.vaccines" :key="`vaccine-${index}`" class="record-editor">
            <input v-model="vaccine.name" class="input" maxlength="100" placeholder="疫苗名称" />
            <input v-model="vaccine.date" class="input" type="date" :max="todayText()" />
            <input v-model="vaccine.vetName" class="input" maxlength="100" placeholder="接种机构或兽医" />
            <button class="icon-btn" type="button" aria-label="删除疫苗记录" @click="editPet.vaccines.splice(index, 1)">
              <X :size="16" />
            </button>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" @click="addVaccineRecord">
            <Plus :size="15" />添加已完成疫苗
          </button>
        </div>
        <div class="field full">
          <span>已完成驱虫</span>
          <div v-for="(record, index) in editPet.dewormings" :key="`deworming-${index}`" class="record-editor">
            <input v-model="record.productName" class="input" maxlength="100" placeholder="驱虫产品" />
            <input v-model="record.date" class="input" type="date" :max="todayText()" />
            <input v-model="record.note" class="input" maxlength="255" placeholder="备注" />
            <button class="icon-btn" type="button" aria-label="删除驱虫记录" @click="editPet.dewormings.splice(index, 1)">
              <X :size="16" />
            </button>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" @click="addDewormingRecord">
            <Plus :size="15" />添加已完成驱虫
          </button>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="editGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="editSaving" @click="saveEdit">{{ petFormSaveLabel }}</button>
      </template>
    </ModalDialog>

    <ModalDialog
      :open="planOpen"
      title="设置下一次照护"
      width="540px"
      :close-disabled="planSaving"
      @close="planGuard.requestClose"
    >
      <div class="plan-form">
        <label class="field">
          <span>照护类型</span>
          <select v-model="planForm.careType" class="select">
            <option value="vaccine">疫苗</option>
            <option value="deworming">驱虫</option>
          </select>
        </label>
        <label class="field">
          <span>项目名称</span>
          <input v-model="planForm.title" class="input" maxlength="100" />
        </label>
        <label class="field">
          <span>计划日期</span>
          <input v-model="planForm.plannedOn" class="input" type="date" :min="todayText()" />
        </label>
        <label class="field">
          <span>备注</span>
          <textarea v-model="planForm.note" class="textarea" maxlength="500" />
        </label>
      </div>
      <template #footer>
        <button
          v-if="planEditIndex >= 0"
          class="btn btn-danger"
          type="button"
          @click="deleteEditingPlan"
        >删除计划</button>
        <button class="btn btn-ghost" type="button" @click="planGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="planSaving" @click="savePlan">保存计划</button>
      </template>
    </ModalDialog>

    <ModalDialog
      :open="completeOpen"
      title="完成照护并写入档案"
      width="560px"
      :close-disabled="completionSaving"
      @close="completionGuard.requestClose"
    >
      <div class="completion-intro">
        <ShieldCheck :size="18" />
        <p>确认后，这条记录会从待完成计划转入宠物档案。</p>
      </div>
      <div class="plan-form">
        <label class="field">
          <span>{{ selectedPlan?.careType === 'vaccine' ? '疫苗名称' : '驱虫产品' }}</span>
          <input v-model="completionForm.title" class="input" maxlength="100" />
        </label>
        <label class="field">
          <span>实际完成日期</span>
          <input v-model="completionForm.completedOn" class="input" type="date" :max="todayText()" />
        </label>
        <label v-if="selectedPlan?.careType === 'vaccine'" class="field">
          <span>接种机构 / 兽医</span>
          <input v-model="completionForm.providerName" class="input" maxlength="100" />
        </label>
        <label v-if="selectedPlan?.careType === 'vaccine'" class="field">
          <span>疫苗批次号</span>
          <input v-model="completionForm.batchNo" class="input" maxlength="100" />
        </label>
        <label class="field">
          <span>备注</span>
          <textarea v-model="completionForm.note" class="textarea" maxlength="500" />
        </label>
        <label class="field">
          <span>可选的再下一次计划</span>
          <input v-model="completionForm.nextPlanDate" class="input" type="date" :min="completionForm.completedOn" />
        </label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="completionGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="completionSaving" @click="confirmCompletion">确认并保存档案</button>
      </template>
    </ModalDialog>

    <section class="print-document" aria-hidden="true">
      <header class="print-document-head">
        <div>
          <span>猫狗宠物之家</span>
          <h1>宠物养护档案</h1>
        </div>
        <div class="print-document-meta">
          <b>{{ currentPet?.name || '未命名' }}</b>
          <span>打印日期：{{ printDate }}</span>
        </div>
      </header>

      <section class="print-section print-identity">
        <div class="print-photo">
          <img v-if="currentPet?.photo" :src="assetUrl(currentPet.photo)" :alt="`${currentPet.name}的照片`" />
          <HeartPulse v-else :size="44" />
        </div>
        <div>
          <h2>{{ currentPet?.name || '未命名' }}</h2>
          <div class="print-facts">
            <span><b>物种</b>{{ currentPet?.species || currentPet?.type || '--' }}</span>
            <span><b>品种</b>{{ currentPet?.breed || '--' }}</span>
            <span><b>性别</b>{{ currentPet?.gender || '--' }}</span>
            <span><b>年龄</b>{{ formatAge(currentPet?.birthDate) }}</span>
            <span><b>出生日期</b>{{ currentPet?.birthDate || '--' }}</span>
            <span><b>当前体重</b>{{ latestWeight ? `${latestWeight.weight} kg` : '--' }}</span>
            <span><b>绝育情况</b>{{ currentPet?.neutered ? '已绝育' : '未绝育' }}</span>
            <span><b>体重记录</b>{{ currentPet?.weightRecords?.length || 0 }} 条</span>
          </div>
        </div>
      </section>

      <section class="print-section">
        <h2>过敏原</h2>
        <div v-if="currentPet?.allergies?.length" class="print-tags">
          <span v-for="allergy in currentPet.allergies" :key="allergy">
            {{ formatAllergyLabel(allergy) }}
          </span>
        </div>
        <p v-else class="print-empty">暂未记录</p>
      </section>

      <section class="print-section">
        <h2>病史记录</h2>
        <p class="print-paragraph">{{ currentPet?.medicalHistory || '暂无记录' }}</p>
      </section>

      <section class="print-section">
        <h2>已完成疫苗</h2>
        <table v-if="completedVaccines.length">
          <thead><tr><th>疫苗名称</th><th>接种日期</th><th>批次号</th><th>接种机构或兽医</th></tr></thead>
          <tbody>
            <tr v-for="(vaccine, index) in completedVaccines" :key="`print-vaccine-${index}`">
              <td>{{ vaccine.name || '--' }}</td>
              <td>{{ vaccine.date || '--' }}</td>
              <td>{{ vaccine.batchNo || '--' }}</td>
              <td>{{ vaccine.vetName || '--' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="print-empty">暂无记录</p>
      </section>

      <section class="print-section">
        <h2>已完成驱虫</h2>
        <table v-if="completedDewormings.length">
          <thead><tr><th>驱虫产品</th><th>完成日期</th><th>备注</th></tr></thead>
          <tbody>
            <tr v-for="(record, index) in completedDewormings" :key="`print-deworming-${index}`">
              <td>{{ record.productName || '--' }}</td>
              <td>{{ record.date || '--' }}</td>
              <td>{{ record.note || '--' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="print-empty">暂无记录</p>
      </section>

      <section class="print-section">
        <h2>体重记录</h2>
        <table v-if="weightTableRows.length">
          <thead><tr><th>日期</th><th>体重</th><th>较上次</th><th>备注</th></tr></thead>
          <tbody>
            <tr v-for="record in weightTableRows" :key="`print-weight-${record.date}-${record.originalIndex}`">
              <td>{{ record.date }}</td>
              <td>{{ Number(record.weight).toFixed(1) }} kg</td>
              <td>{{ formatWeightChange(record.change) }}</td>
              <td>{{ record.note || '--' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="print-empty">暂无记录</p>
      </section>

      <footer class="print-document-foot">
        <span>本档案由用户记录，仅用于宠物养护管理与就医沟通。</span>
        <span>{{ currentPet?.name || '未命名' }} · {{ printDate }}</span>
      </footer>
    </section>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="aiOpen" class="ai-backdrop" @click.self="closeAiDrawer">
          <section class="ai-drawer" role="dialog" aria-modal="true" aria-label="AI 养宠顾问">
            <header class="ai-drawer-head">
              <div class="ai-identity">
                <span><Sparkles :size="18" /></span>
                <div><b>AI 养宠顾问</b><small>{{ currentPet?.name }} · 猫狗养护专用</small></div>
              </div>
              <div class="ai-mode-tabs" role="tablist" aria-label="养宠顾问模式">
                <button type="button" role="tab" :aria-selected="aiMode === AI_MODES.QA" :class="{ active: aiMode === AI_MODES.QA }" @click="setAiMode(AI_MODES.QA)">健康问答</button>
                <button type="button" role="tab" :aria-selected="aiMode === AI_MODES.REPORT" :class="{ active: aiMode === AI_MODES.REPORT }" @click="setAiMode(AI_MODES.REPORT)">健康报告</button>
                <button type="button" role="tab" :aria-selected="aiMode === AI_MODES.RECOMMENDATION" :class="{ active: aiMode === AI_MODES.RECOMMENDATION }" @click="setAiMode(AI_MODES.RECOMMENDATION)">养宠推荐</button>
              </div>
              <div class="ai-head-actions">
                <button type="button" aria-label="清空当前对话" @click="clearAiConversation"><RotateCcw :size="16" /></button>
                <button type="button" aria-label="关闭" @click="closeAiDrawer"><X :size="18" /></button>
              </div>
            </header>
            <div ref="chatBox" class="ai-messages" aria-live="polite">
              <div v-if="aiHistoryLoading" class="ai-history-loading">
                <span class="spinner" aria-hidden="true"></span>
                <span>正在读取历史对话</span>
              </div>
              <div v-else-if="!aiMessages.length" class="ai-welcome">
                <Sparkles :size="30" />
                <h3>{{ aiWelcome.title }}</h3>
                <p>{{ aiWelcome.description }}</p>
                <button
                  v-if="aiMode === AI_MODES.REPORT"
                  class="btn btn-primary ai-report-start"
                  type="button"
                  :disabled="aiLoading"
                  @click="generateHealthReport"
                >
                  <Sparkles :size="16" />{{ aiLoading ? '正在生成报告' : '生成专属健康报告' }}
                </button>
                <div v-else class="ai-suggestions">
                  <button
                    v-for="prompt in quickPrompts"
                    :key="prompt"
                    type="button"
                    @click="sendAi(prompt)"
                  >{{ prompt }}</button>
                </div>
              </div>
              <div v-for="(message, index) in aiMessages" :key="message.id || index" class="ai-message" :class="message.role">
                <div class="ai-bubble">
                  {{ message.content }}
                  <span v-if="message.streaming" class="ai-cursor" aria-hidden="true"></span>
                  <span v-if="message.emergency" class="ai-emergency">可能属于紧急情况，请尽快联系兽医。</span>
                  <small v-else-if="message.role === 'assistant' && message.disclaimer">{{ message.disclaimer }}</small>
                  <small v-else-if="message.role === 'assistant'">AI 建议仅供参考</small>
                  <div v-if="message.citations?.length" class="ai-citations">
                    <a
                      v-for="citation in message.citations"
                      :key="citation.referenceId"
                      :href="citation.sourceUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <Link2 :size="11" />[{{ citation.referenceId }}] {{ citation.publisher }}
                    </a>
                  </div>
                  <button
                    v-if="message.messageType === 'HEALTH_REPORT' && message.id && !message.streaming"
                    class="ai-report-download"
                    type="button"
                    :disabled="reportDownloadingId === message.id"
                    @click="downloadHealthReport(message)"
                  >
                    <Download :size="14" />
                    {{ reportDownloadingId === message.id ? '正在生成 PDF' : '下载 PDF 报告' }}
                  </button>
                  <div v-if="message.recommendations?.length" class="ai-recommendations">
                    <RouterLink
                      v-for="item in message.recommendations"
                      :key="`${item.itemType}-${item.itemId}`"
                      class="ai-recommendation-card"
                      :to="item.actionPath"
                    >
                      <span class="ai-recommendation-image">
                        <img
                          v-if="item.imageUrl"
                          :src="assetUrl(item.imageUrl)"
                          :alt="item.name"
                          loading="lazy"
                          decoding="async"
                        />
                        <ShoppingBag v-else :size="20" aria-hidden="true" />
                      </span>
                      <span class="ai-recommendation-copy">
                        <small>
                          {{ String(item.itemType).toLowerCase() === 'service' ? '养护服务' : '宠物商品' }}
                          · {{ targetPetLabel(item.targetPet) }}
                        </small>
                        <b>{{ item.name }}</b>
                        <em>{{ item.reason }}</em>
                        <span v-if="item.healthTags?.length" class="ai-recommendation-tags">
                          <span v-for="tag in item.healthTags.slice(0, 3)" :key="tag">{{ tag }}</span>
                        </span>
                      </span>
                      <span class="ai-recommendation-action">
                        {{ item.actionLabel }}
                      </span>
                    </RouterLink>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="aiMode === AI_MODES.REPORT" class="ai-report-compose">
              <div>
                <b>基于当前档案生成完整分析</b>
                <span>年龄、体重趋势、疫苗、驱虫、过敏史和照护计划只从服务端读取。</span>
              </div>
              <button
                v-if="aiLoading"
                class="btn btn-ghost"
                type="button"
                @click="stopAiGeneration"
              >
                <X :size="15" />停止生成
              </button>
              <button
                v-else
                class="btn btn-primary"
                type="button"
                @click="generateHealthReport"
              >
                <Sparkles :size="15" />{{ aiMessages.length ? '重新生成报告' : '生成健康报告' }}
              </button>
              <p>健康报告用于养护参考，不能替代执业兽医诊断。</p>
            </div>
            <form v-else class="ai-compose" @submit.prevent="sendAi(aiInput)">
              <textarea
                v-model="aiInput"
                maxlength="1000"
                rows="2"
                :placeholder="aiInputPlaceholder"
                :disabled="aiLoading"
              />
              <button
                v-if="aiLoading"
                class="btn btn-ghost"
                type="button"
                aria-label="停止生成"
                @click="stopAiGeneration"
              >
                <X :size="16" />
              </button>
              <button
                v-else
                class="btn btn-primary"
                type="submit"
                :disabled="!aiInput.trim()"
                aria-label="发送"
              >
                <Send :size="16" />
              </button>
              <p>仅处理猫狗养护与宠物服务，AI 不能替代兽医诊断。</p>
            </form>
          </section>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.health-heading-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.health-heading-actions .btn { gap: 7px; }
.ai-entry-button { border-color: var(--color-ai-entry-from); color: var(--color-surface); background: linear-gradient(135deg, var(--color-ai-entry-from), var(--color-ai-entry-to)); box-shadow: 0 8px 20px var(--color-shadow-ai); }
.ai-entry-button:hover { background: linear-gradient(135deg, var(--color-ai-entry-hover-from), var(--color-ai-entry-hover-to)); transform: translateY(-1px); }
.ai-recommendations { display: grid; gap: 8px; margin-top: 12px; }
.ai-recommendation-card { min-height: 96px; padding: 10px; display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; gap: 10px; align-items: center; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-raised); color: var(--color-text); text-decoration: none; transition: border-color var(--transition-base), background var(--transition-base); }
.ai-recommendation-card:hover { border-color: var(--color-primary); background: var(--color-surface-warm); }
.ai-recommendation-image { width: 64px; height: 64px; overflow: hidden; display: grid; place-items: center; border-radius: var(--radius-sm); background: var(--color-primary-soft); color: var(--color-primary-strong); }
.ai-recommendation-image img { width: 100%; height: 100%; object-fit: cover; }
.ai-recommendation-copy { min-width: 0; display: grid; gap: 3px; }
.ai-recommendation-copy small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.ai-recommendation-copy b { font-size: var(--font-size-base); }
.ai-recommendation-copy em { color: var(--color-text-secondary); font-size: var(--font-size-sm); font-style: normal; }
.ai-recommendation-tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 3px; }
.ai-recommendation-tags > span { padding: 3px 7px; border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); }
.ai-recommendation-action { min-height: 44px; padding: 0 10px; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--radius-pill); background: var(--color-primary); color: var(--color-white); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); white-space: nowrap; }
@media (max-width: 560px) {
  .ai-recommendation-card { grid-template-columns: 52px minmax(0, 1fr); }
  .ai-recommendation-image { width: 52px; height: 52px; }
  .ai-recommendation-action { grid-column: 1 / -1; width: 100%; }
  .ai-report-compose { grid-template-columns: 1fr; }
  .ai-report-compose .btn { width: 100%; }
}
.pet-switcher { margin-bottom: 15px; padding: 8px; display: grid; grid-template-columns: repeat(var(--pet-count), minmax(0, 1fr)) 130px; gap: 7px; border: 1px solid var(--color-border); border-radius: 19px; background: var(--color-surface-veil); }
.pet-switch-card { min-height: 56px; padding: 8px 10px; border: 1px solid transparent; border-radius: 13px; background: transparent; color: var(--color-text); display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; gap: 9px; align-items: center; text-align: left; }
.pet-switch-card.active { border-color: var(--color-border-primary-strong); background: linear-gradient(120deg, var(--color-warning-soft), var(--color-surface-ivory)); box-shadow: inset 4px 0 0 var(--color-primary); }
.pet-switch-avatar { width: 38px; height: 38px; overflow: hidden; border-radius: 12px; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.pet-switch-avatar img { width: 100%; height: 100%; object-fit: cover; }
.pet-switch-card b, .pet-switch-card small { display: block; }
.pet-switch-card b { font-size: var(--font-size-sm); }
.pet-switch-card small { margin-top: 2px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.add-pet-link { border: 1px dashed var(--color-border-strong); border-radius: 13px; color: var(--color-primary-strong); background: var(--color-surface-warm); display: flex; align-items: center; justify-content: center; gap: 6px; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.health-workspace { display: block; }
.health-main { min-width: 0; display: grid; gap: 16px; }
.pet-archive { overflow: hidden; border-radius: 8px 30px 30px 8px; }
.archive-head { padding: 18px 21px 16px; display: grid; grid-template-columns: 76px minmax(0, 1fr) auto; gap: 15px; align-items: center; border-bottom: 1px solid var(--color-border); background: radial-gradient(circle at 88% 18%, var(--color-primary-glow-soft), transparent 14rem), linear-gradient(120deg, var(--color-surface-warm-strong), var(--color-surface) 65%); }
.archive-portrait { width: 76px; height: 84px; overflow: hidden; border-radius: 25px 25px 10px 25px; display: grid; place-items: center; background: linear-gradient(145deg, var(--color-butter), var(--color-surface-portrait)); color: var(--color-primary-strong); box-shadow: inset 0 0 0 5px var(--color-surface-veil-faint); }
.archive-portrait img { width: 100%; height: 100%; object-fit: cover; }
.archive-identity > div:first-child { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.archive-identity h2 { margin: 0; font-size: var(--font-size-4xl); }
.archive-identity > p { margin: 4px 0 8px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.identity-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.identity-tags span { padding: 4px 8px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface-veil-soft); color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.archive-actions { display: flex; gap: 7px; flex-wrap: wrap; justify-content: flex-end; }
.archive-grid { padding: 17px 20px 19px; display: grid; grid-template-columns: .82fr 1fr 1.25fr; gap: 18px; }
.archive-column + .archive-column { padding-left: 18px; border-left: 1px solid var(--color-border); }
.archive-column h3 { margin: 0 0 10px; display: flex; align-items: center; gap: 6px; color: var(--color-text); font-family: var(--font-family-body); font-size: var(--font-size-sm); }
.archive-column dl { margin: 0; display: grid; gap: 7px; }
.archive-column dl div { display: flex; justify-content: space-between; gap: 12px; font-size: var(--font-size-sm); }
.archive-column dt { color: var(--color-text-muted); }
.archive-column dd { margin: 0; text-align: right; font-weight: var(--font-weight-bold); }
.allergy-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.allergy-tags span { padding: 4px 8px; border: 1px solid var(--color-border-danger-soft); border-radius: 999px; background: var(--color-danger-soft); color: var(--color-danger); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.allergy-tags.editable span { display: inline-flex; align-items: center; gap: 3px; padding-right: 4px; }
.allergy-tags button { width: 22px; height: 22px; padding: 0; border: 0; border-radius: 50%; background: var(--color-surface-neutral-wash); color: inherit; display: grid; place-items: center; }
.medical-note { margin: 9px 0 0; color: var(--color-text-secondary); white-space: pre-wrap; font-size: var(--font-size-sm); line-height: 1.7; }
.muted-copy { margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.archive-history { max-height: 176px; overflow-y: auto; padding-right: 3px !important; }
.completed-record { padding: 8px 9px; display: grid; grid-template-columns: 26px minmax(0, 1fr) auto; gap: 7px; align-items: center; border-radius: 10px; background: var(--color-surface-warm); }
.completed-record + .completed-record { margin-top: 6px; }
.completed-record > span { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; background: var(--color-surface-accent); color: var(--color-success); }
.completed-record b, .completed-record small { display: block; }
.completed-record b { font-size: var(--font-size-sm); }
.completed-record small { margin-top: 2px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.completed-record em { color: var(--color-success); font-size: var(--font-size-sm); font-style: normal; font-weight: var(--font-weight-extra-bold); }
.next-care { padding-bottom: 15px; overflow: hidden; border-color: var(--color-border-care-strong); background: linear-gradient(135deg, var(--color-surface-care), var(--color-surface-ivory) 62%); }
.section-head { padding: 15px 18px 12px; display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.section-head h2 { margin: 0; display: flex; align-items: center; gap: 7px; font-size: var(--font-size-lg); }
.section-head > span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.next-care-grid { padding: 0 15px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.care-plan { padding: 11px; border: 1px solid var(--color-border-care-soft); border-radius: 12px; background: var(--color-surface-veil); display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 9px; }
.care-plan.overdue { border-color: var(--color-border-danger-soft); background: var(--color-surface-danger-faint); }
.care-plan.due-soon { border-color: var(--color-border-warning); }
.care-plan-icon { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.care-plan-icon.deworming { color: var(--color-success); background: var(--color-surface-accent); }
.care-plan > div { min-width: 0; }
.care-plan > div > span, .care-plan b { display: block; }
.care-plan > div > span { color: var(--color-text-muted); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.care-plan b { margin-top: 2px; font-size: var(--font-size-sm); }
.care-plan p { margin: 3px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.care-plan-actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 6px; margin-top: 2px; }
.care-plan-actions .btn { min-height: 31px; padding: 5px 10px; font-size: var(--font-size-sm); }
.weight-head { padding-bottom: 8px; }
.weight-chart { position: relative; padding: 8px 18px 0; }
.weight-chart svg { width: 100%; height: 180px; overflow: visible; }
.weight-freshness { position: absolute; top: 9px; left: 20px; padding: 6px 10px; border: 1px solid var(--color-border-warning); border-radius: 999px; color: var(--color-warning); background: var(--color-surface-warning-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.weight-form { margin: 0 18px 12px; padding: 10px 0; display: grid; grid-template-columns: .72fr 1fr 1.2fr auto; gap: 7px; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.weight-form .input { min-height: 38px; padding: 7px 10px; }
.weight-form .btn { min-height: 38px; padding: 6px 12px; white-space: nowrap; }
.weight-table-wrap { overflow-x: auto; }
.weight-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: var(--font-size-sm); }
.weight-table th, .weight-table td { padding: 10px 13px; border-top: 1px solid var(--color-border-soft); text-align: left; }
.weight-table th { color: var(--color-text-muted); background: var(--color-surface-warm); font-size: var(--font-size-sm); }
.weight-table td { color: var(--color-text-secondary); }
.weight-table td b { color: var(--color-text); }
.delete-button { width: 28px; height: 28px; padding: 0; border: 0; border-radius: 50%; display: grid; place-items: center; color: var(--color-text-muted); background: var(--color-surface-warm); }
.edit-grid, .plan-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.edit-grid .full, .plan-form .field:last-child { grid-column: 1 / -1; }
.checkbox-field { display: flex; flex-direction: row; align-items: center; gap: 8px; padding-top: 25px; }
.allergy-input-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.field > small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.record-editor { margin-bottom: 7px; display: grid; grid-template-columns: 1.2fr 1fr 1.2fr auto; gap: 7px; }
.completion-intro { margin-bottom: 8px; padding: 10px 12px; border-radius: 11px; display: flex; gap: 8px; color: var(--color-success); background: var(--color-surface-accent); font-size: var(--font-size-sm); }
.completion-intro p { margin: 0; }
.print-document { display: none; }
.ai-backdrop { position: fixed; inset: 0; z-index: 100; padding: 18px; display: flex; justify-content: flex-end; background: var(--color-overlay); backdrop-filter: blur(4px); }
.ai-drawer { width: min(520px, 100%); height: 100%; max-height: 800px; margin: auto 0; overflow: hidden; border-radius: 28px; background: var(--color-surface-drawer); box-shadow: var(--shadow-md); display: flex; flex-direction: column; }
.ai-drawer-head { padding: 14px 16px; display: flex; align-items: center; gap: 10px; border-bottom: 1px solid var(--color-border); background: linear-gradient(135deg, var(--color-warning-soft), var(--color-surface)); }
.ai-identity { min-width: 0; display: flex; align-items: center; gap: 8px; }
.ai-identity > span { width: 36px; height: 36px; flex: 0 0 auto; border-radius: 50%; display: grid; place-items: center; background: var(--color-primary); color: var(--color-text); }
.ai-identity b, .ai-identity small { display: block; }
.ai-identity small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.ai-mode-tabs { display: flex; gap: 3px; margin-left: auto; padding: 3px; border-radius: 999px; background: var(--color-surface-veil-soft); }
.ai-mode-tabs button { border: 0; border-radius: 999px; padding: 6px 8px; background: transparent; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.ai-mode-tabs button.active { background: var(--color-surface); color: var(--color-primary-strong); box-shadow: var(--box-shadow-sm); }
.ai-head-actions { display: flex; gap: 3px; }
.ai-head-actions button { width: 32px; height: 32px; padding: 0; border: 0; border-radius: 50%; display: grid; place-items: center; background: transparent; color: var(--color-text-secondary); }
.ai-messages { flex: 1; overflow-y: auto; padding: 18px; }
.ai-history-loading { min-height: 180px; display: flex; align-items: center; justify-content: center; gap: 9px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.ai-welcome { padding: 16px 4px; text-align: center; color: var(--color-text-secondary); }
.ai-welcome > svg { color: var(--color-primary-strong); }
.ai-welcome h3 { margin: 10px 0 3px; font-size: var(--font-size-2xl); }
.ai-welcome p { margin: 0; font-size: var(--font-size-sm); }
.ai-report-start { margin-top: 18px; }
.ai-suggestions { display: grid; gap: 7px; margin-top: 16px; }
.ai-suggestions button { padding: 9px 10px; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-surface); text-align: left; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.ai-message { display: flex; margin-bottom: 11px; }
.ai-message.user { justify-content: flex-end; }
.ai-bubble { max-width: 84%; padding: 10px 13px; border: 1px solid var(--color-border); border-radius: 16px 16px 16px 4px; background: var(--color-surface); white-space: pre-wrap; font-size: var(--font-size-sm); line-height: 1.7; }
.ai-message.user .ai-bubble { border-color: var(--color-primary); border-radius: 16px 16px 4px 16px; background: var(--color-primary); color: var(--color-text); }
.ai-cursor { display: inline-block; width: 2px; height: 1em; margin-left: 2px; vertical-align: text-bottom; background: var(--color-primary-strong); animation: ai-cursor-blink .8s step-end infinite; }
@keyframes ai-cursor-blink { 50% { opacity: 0; } }
.ai-bubble small { display: block; margin-top: 6px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.ai-emergency { display: block; margin-top: 7px; color: var(--color-danger); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.typing { display: flex; gap: 4px; }
.typing i { width: 5px; height: 5px; border-radius: 50%; background: var(--color-primary); animation: typing-blink 1s infinite ease-in-out; }
.typing i:nth-child(2) { animation-delay: .15s; }
.typing i:nth-child(3) { animation-delay: .3s; }
@keyframes typing-blink { 0%, 80%, 100% { opacity: .3; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
.ai-citations { display: grid; gap: 5px; margin-top: 8px; padding-top: 7px; border-top: 1px dashed var(--color-border); }
.ai-citations a { display: flex; align-items: center; gap: 4px; color: var(--color-primary-strong); font-size: var(--font-size-sm); text-decoration: none; }
.ai-report-download {
  margin-top: 10px;
  min-height: 34px;
  padding: 6px 11px;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-primary-strong);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}
.ai-report-download:disabled { cursor: wait; opacity: .65; }
.ai-compose { padding: 11px 13px; border-top: 1px solid var(--color-border); background: var(--color-surface); display: grid; grid-template-columns: 1fr auto; gap: 7px; }
.ai-compose textarea { min-height: 62px; resize: none; border: 1px solid var(--color-border); border-radius: 11px; padding: 8px 10px; outline: none; font: inherit; font-size: var(--font-size-sm); }
.ai-compose button { width: 40px; height: 40px; align-self: end; padding: 0; border-radius: 50%; }
.ai-compose p { grid-column: 1 / -1; margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; }
.ai-report-compose { padding: 13px 15px; border-top: 1px solid var(--color-border); background: var(--color-surface); display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; align-items: center; }
.ai-report-compose b, .ai-report-compose span { display: block; }
.ai-report-compose b { font-size: var(--font-size-sm); }
.ai-report-compose span { margin-top: 3px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.ai-report-compose p { grid-column: 1 / -1; margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
@media (max-width: 1080px) {
  .archive-grid { grid-template-columns: .8fr 1fr; }
  .archive-history { grid-column: 1 / -1; max-height: none; padding: 12px 0 0 !important; border-top: 1px solid var(--color-border); border-left: 0 !important; }
}
@media (max-width: 820px) {
  .pet-switcher { grid-template-columns: 1fr; }
  .health-heading-actions { width: 100%; }
}
@media (max-width: 620px) {
  .archive-head { grid-template-columns: 64px minmax(0, 1fr); }
  .archive-portrait { width: 64px; height: 72px; }
  .archive-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .archive-grid, .next-care-grid, .edit-grid, .plan-form { grid-template-columns: 1fr; }
  .archive-column + .archive-column { padding: 12px 0 0; border-top: 1px solid var(--color-border); border-left: 0; }
  .edit-grid .full, .plan-form .field:last-child { grid-column: auto; }
  .weight-form { grid-template-columns: 1fr 1fr; }
  .weight-form .input:nth-child(3), .weight-form .btn { grid-column: 1 / -1; }
  .record-editor { grid-template-columns: 1fr 1fr; }
  .ai-backdrop { padding: 0; }
  .ai-drawer { height: 100%; max-height: none; border-radius: 0; }
  .ai-mode-tabs { order: 3; width: 100%; margin-left: 0; }
  .ai-drawer-head { flex-wrap: wrap; }
  .ai-recommendation-card { grid-template-columns: 56px minmax(0, 1fr); }
  .ai-recommendation-image { width: 56px; height: 56px; }
  .ai-recommendation-action { grid-column: 1 / -1; justify-self: start; }
}
@media print {
  :global(body) { background: var(--color-white); }
  :global(.site-header), :global(.site-footer), :global(.dialog-backdrop), :global(.ai-backdrop) { display: none !important; }
  :global(.site-main) { min-height: 0; padding: 0; background: var(--color-white) !important; }
  .health-heading, .pet-switcher, .health-workspace { display: none !important; }
  .print-document { display: block; color: var(--color-text); font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
  .print-document-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; padding-bottom: 14px; border-bottom: 2px solid var(--color-border-print-strong); }
  .print-document-head > div:first-child > span { color: var(--color-warning); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); letter-spacing: .08em; }
  .print-document-head h1 { margin: 4px 0 0; font-family: "SimSun", "Songti SC", serif; font-size: var(--font-size-3xl); }
  .print-document-meta { display: grid; gap: 3px; text-align: right; font-size: var(--font-size-sm); color: var(--color-text-secondary); }
  .print-document-meta b { color: var(--color-text); font-size: var(--font-size-base); }
  .print-section { break-inside: avoid; padding: 14px 0; border-bottom: 1px solid var(--color-border-print); }
  .print-section h2 { margin: 0 0 10px; font-family: "SimSun", "Songti SC", serif; font-size: var(--font-size-lg); }
  .print-identity { display: grid; grid-template-columns: 86px 1fr; gap: 18px; align-items: start; }
  .print-photo { width: 86px; height: 86px; display: grid; place-items: center; overflow: hidden; border: 1px solid var(--color-border-print-photo); border-radius: 16px; background: var(--color-canvas-console); color: var(--color-primary-strong); }
  .print-photo img { width: 100%; height: 100%; object-fit: cover; }
  .print-identity h2 { margin: 0 0 10px; font-family: "SimSun", "Songti SC", serif; font-size: var(--font-size-xl); }
  .print-facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px 14px; }
  .print-facts span { display: grid; gap: 2px; font-size: var(--font-size-sm); }
  .print-facts b { color: var(--color-text-secondary); font-size: var(--font-size-sm); }
  .print-tags { display: flex; flex-wrap: wrap; gap: 6px; }
  .print-tags span { padding: 3px 9px; border: 1px solid var(--color-border-print-tag); border-radius: 999px; background: var(--color-surface-ivory); font-size: var(--font-size-sm); }
  .print-paragraph { margin: 0; white-space: pre-wrap; line-height: 1.7; font-size: var(--font-size-sm); }
  .print-empty { margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
  .print-document table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: var(--font-size-sm); }
  .print-document th, .print-document td { padding: 6px 7px; border: 1px solid var(--color-border-print); text-align: left; vertical-align: top; overflow-wrap: anywhere; }
  .print-document th { background: var(--color-surface-table-head); color: var(--color-text-secondary); font-weight: var(--font-weight-extra-bold); }
  .print-document-foot { display: flex; justify-content: space-between; gap: 20px; padding-top: 14px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
  @page { size: A4; margin: 14mm; }
}
</style>
