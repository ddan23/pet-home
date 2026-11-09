<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  BellRing,
  ChevronDown,
  LoaderCircle,
  Search,
  Send,
  Store,
  Trash2,
  UserRound,
  UsersRound,
  X
} from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { relativeTime } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import PaginationBar from '../../components/PaginationBar.vue'

const ui = useUiStore()
const messages = ref([])
const loading = ref(true)
const page = ref(1)
const totalPages = ref(1)
const total = ref(0)
const composeOpen = ref(false)
const sending = ref(false)
const recipientRole = ref('user')
const recipientKeyword = ref('')
const recipientResults = ref([])
const recipientLoading = ref(false)
const selectedRecipient = ref(null)
const form = ref(emptyForm())
const composeGuard = useDialogDraftGuard({
  getSnapshot: () => ({
    form: form.value,
    receiverId: selectedRecipient.value?.id || null,
    recipientKeyword: recipientKeyword.value
  }),
  getBusy: () => sending.value,
  close: closeCompose
})
let searchTimer = null
let recipientRequestId = 0

const audienceOptions = [
  { value: 'user', title: '全部普通用户', icon: UsersRound },
  { value: 'business', title: '全部商家', icon: Store },
  { value: 'all', title: '全部普通用户和商家', icon: BellRing },
  { value: '', title: '指定账号', icon: UserRound }
]

function emptyForm() {
  return {
    title: '',
    content: '',
    type: 'system',
    targetRole: 'user',
    receiverId: null
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: 20 }
    const result = await api.get(`/admin/messages${queryString(params)}`)
    messages.value = result.data?.list || []
    totalPages.value = result.data?.totalPages || 1
    total.value = Number(result.data?.total || 0)
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function openCompose() {
  form.value = emptyForm()
  selectedRecipient.value = null
  recipientKeyword.value = ''
  recipientResults.value = []
  recipientRole.value = 'user'
  composeOpen.value = true
  composeGuard.markClean()
}

function closeCompose() {
  composeOpen.value = false
  form.value = emptyForm()
  selectedRecipient.value = null
  recipientKeyword.value = ''
  recipientResults.value = []
}

function chooseAudience(value) {
  form.value.targetRole = value
  form.value.receiverId = null
  selectedRecipient.value = null
  if (!value) searchRecipients()
}

function chooseRecipientRole(role) {
  recipientRole.value = role
  selectedRecipient.value = null
  form.value.receiverId = null
  searchRecipients()
}

function scheduleRecipientSearch() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(searchRecipients, 250)
}

async function searchRecipients() {
  const requestId = ++recipientRequestId
  recipientLoading.value = true
  try {
    const params = {
      role: recipientRole.value,
      status: 'normal',
      page: '1',
      pageSize: '8'
    }
    if (recipientKeyword.value.trim()) {
      params.keyword = recipientKeyword.value.trim()
    }
    const result = await api.get(`/admin/users${queryString(params)}`)
    if (requestId !== recipientRequestId) return
    recipientResults.value = result.data?.list || []
  } catch (error) {
    if (requestId !== recipientRequestId) return
    recipientResults.value = []
    ui.toast(error.message, 'error')
  } finally {
    if (requestId === recipientRequestId) recipientLoading.value = false
  }
}

function selectRecipient(user) {
  selectedRecipient.value = user
  form.value.receiverId = user.id
  recipientKeyword.value = user.nickname || user.username
  recipientResults.value = []
}

async function sendMessage() {
  if (sending.value) return
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ui.toast('请填写通知标题和内容', 'warning')
    return
  }
  if (!form.value.targetRole && !selectedRecipient.value) {
    ui.toast('请选择一位接收人', 'warning')
    return
  }
  const audience = selectedRecipient.value
    ? `指定账号「${selectedRecipient.value.nickname || selectedRecipient.value.username}」`
    : audienceOptions.find((option) => option.value === form.value.targetRole)?.title || '选定用户'
  const confirmed = await ui.confirm({
    title: '确认发布通知',
    message: `确认发布通知「${form.value.title.trim()}」给${audience}吗？发布后将立即发送，系统无法整体撤回。`,
    confirmText: '确认发布',
    danger: true
  })
  if (!confirmed) return
  sending.value = true
  try {
    await api.post('/admin/messages', {
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      type: form.value.type,
      targetRole: form.value.targetRole || null,
      receiverId: form.value.receiverId || null
    })
    ui.toast('通知已发布', 'success')
    closeCompose()
    page.value = 1
    await load()
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    sending.value = false
  }
}

async function remove(message) {
  const confirmed = await ui.confirm({
    message: '确定删除这条通知记录吗？',
    danger: true,
    confirmText: '删除记录'
  })
  if (!confirmed) return
  try {
    await api.delete(`/admin/messages/${message.id}`)
    messages.value = messages.value.filter((item) => item.id !== message.id)
    total.value = Math.max(0, total.value - 1)
    ui.toast('通知记录已删除', 'success')
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

onMounted(load)
onBeforeUnmount(() => window.clearTimeout(searchTimer))
</script>

<template>
  <div class="console-page message-page">
    <header class="admin-heading">
      <div>
        <h1>通知发布</h1>
      </div>
      <button class="btn btn-primary" type="button" @click="openCompose">
        <BellRing :size="16" />发布通知
      </button>
    </header>

    <section class="message-panel">
      <div class="panel-title-row">
        <h2>发布记录</h2>
        <span>共 {{ total }} 条</span>
      </div>
      <LoadingPanel v-if="loading" label="正在读取发布记录" />
      <EmptyState
        v-else-if="!messages.length"
        title="暂无发布记录"
      />
      <template v-else>
        <div class="message-list">
          <article v-for="message in messages" :key="message.id">
            <span class="msg-icon"><BellRing :size="18" /></span>
            <div class="message-copy">
              <div class="message-labels">
                <b>{{ message.title || '系统通知' }}</b>
                <span class="tag">{{ message.category }} / {{ message.type }}</span>
              </div>
              <p>{{ message.content }}</p>
              <small>
                接收人 {{ message.receiverId }} · {{ relativeTime(message.createdAt) }}
              </small>
            </div>
            <button
              class="delete-button"
              type="button"
              aria-label="删除通知记录"
              @click="remove(message)"
            >
              <Trash2 :size="16" />
            </button>
          </article>
        </div>
        <PaginationBar :page="page" :total-pages="totalPages" @change="page = $event; load()" />
      </template>
    </section>

    <ModalDialog
      :open="composeOpen"
      title="发布通知"
      width="680px"
      :close-disabled="sending"
      @close="composeGuard.requestClose"
    >
      <div class="compose-layout">
        <div class="compose-form">
          <label class="field">
            <span>标题</span>
            <input
              v-model="form.title"
              class="input"
              maxlength="200"
              placeholder="例如：秋冬季宠物健康照护提醒"
            />
          </label>
          <label class="field">
            <span>内容</span>
            <textarea
              v-model="form.content"
              class="textarea notice-content"
              maxlength="3000"
              placeholder="写清楚需要用户关注或完成的事项"
            />
            <small class="field-help">{{ form.content.length }} / 3000 字</small>
          </label>

          <fieldset class="audience-field">
            <legend>接收角色</legend>
            <div class="audience-grid">
              <button
                v-for="option in audienceOptions"
                :key="option.title"
                type="button"
                :class="{ selected: form.targetRole === option.value }"
                @click="chooseAudience(option.value)"
              >
                <span><component :is="option.icon" :size="17" /></span>
                <div><b>{{ option.title }}</b></div>
                <i></i>
              </button>
            </div>
          </fieldset>

          <div v-if="!form.targetRole" class="recipient-picker">
            <div class="role-switch" role="tablist" aria-label="接收账号类型">
              <button
                type="button"
                role="tab"
                :aria-selected="recipientRole === 'user'"
                :class="{ active: recipientRole === 'user' }"
                @click="chooseRecipientRole('user')"
              >
                普通用户
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="recipientRole === 'business'"
                :class="{ active: recipientRole === 'business' }"
                @click="chooseRecipientRole('business')"
              >
                商家账号
              </button>
            </div>
            <div v-if="selectedRecipient" class="selected-recipient">
              <span class="recipient-avatar">
                <component :is="selectedRecipient.role === 'business' ? Store : UserRound" :size="17" />
              </span>
              <div>
                <b>{{ selectedRecipient.nickname || selectedRecipient.username }}</b>
                <small>@{{ selectedRecipient.username }} · ID {{ selectedRecipient.id }}</small>
              </div>
              <button type="button" aria-label="取消选择接收人" @click="chooseAudience('')">
                <X :size="15" />
              </button>
            </div>
            <template v-else>
              <label class="recipient-search">
                <Search :size="16" />
                <input
                  v-model="recipientKeyword"
                  aria-label="搜索接收人"
                  placeholder="输入昵称或用户名搜索"
                  @input="scheduleRecipientSearch"
                  @focus="searchRecipients"
                />
                <LoaderCircle v-if="recipientLoading" class="spin" :size="16" />
              </label>
              <div v-if="recipientResults.length" class="recipient-results">
                <button
                  v-for="user in recipientResults"
                  :key="user.id"
                  type="button"
                  @click="selectRecipient(user)"
                >
                  <span class="recipient-avatar"><UserRound :size="16" /></span>
                  <div><b>{{ user.nickname || user.username }}</b><small>@{{ user.username }} · ID {{ user.id }}</small></div>
                  <ChevronDown :size="14" />
                </button>
              </div>
              <p v-else-if="!recipientLoading" class="recipient-empty">没有可用的匹配账号</p>
            </template>
          </div>
        </div>

      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="sending" @click="composeGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="sending" @click="sendMessage">
          <Send :size="16" />{{ sending ? '发布中…' : '发布' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.message-page { display: grid; align-content: start; gap: 18px; }
.admin-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
.admin-heading h1 { margin: 0; font-size: var(--font-size-5xl); }
.admin-heading p { margin: 7px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.message-summary {
  min-height: 90px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  background:
    radial-gradient(circle at 92% 20%, var(--color-primary-glow), transparent 16rem),
    var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}
.summary-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}
.message-summary > div { display: grid; min-width: 86px; }
.message-summary small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.message-summary strong { font-family: var(--font-family-display); font-size: var(--font-size-3xl); line-height: 1.15; }
.message-summary p { margin: 0 0 0 9px; color: var(--color-text-secondary); font-size: var(--font-size-sm); }

.message-panel {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}
.panel-title-row {
  min-height: 58px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border);
}
.panel-title-row h2 { margin: 0; font-size: var(--font-size-xl); }
.panel-title-row span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.message-list { display: grid; }
.message-list article {
  padding: 15px 17px;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-bottom: 1px solid var(--color-border-soft);
}
.message-list article:last-child { border-bottom: 0; }
.message-list article:hover { background: var(--color-surface-ivory); }
.msg-icon {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}
.message-copy { flex: 1; min-width: 0; }
.message-labels { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.message-labels b { font-size: var(--font-size-base); }
.message-copy p {
  margin: 5px 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.65;
  white-space: pre-wrap;
}
.message-copy small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.delete-button {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 9px;
  color: var(--color-text-muted);
  background: transparent;
}
.delete-button:hover { color: var(--color-danger); border-color: var(--color-border-danger-soft); background: var(--color-danger-soft); }

.compose-layout { display: block; }
.compose-form { min-width: 0; }
.notice-content { min-height: 130px; }
.field-help { align-self: end; color: var(--color-text-muted); font-size: .75rem; }
.audience-field { min-width: 0; margin: 2px 0 17px; padding: 0; border: 0; }
.audience-field legend { margin-bottom: 8px; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.audience-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.audience-grid button {
  min-width: 0;
  min-height: 62px;
  padding: 9px 10px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  color: var(--color-text);
  background: white;
  text-align: left;
}
.audience-grid button:hover { border-color: var(--color-border-strong); background: var(--color-surface-warm); }
.audience-grid button.selected { border-color: var(--color-primary); background: var(--color-primary-soft); box-shadow: var(--shadow-focus); }
.audience-grid button > span {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: var(--color-primary-strong);
  background: var(--color-surface-warm);
}
.audience-grid button.selected > span { background: white; }
.audience-grid button > div { min-width: 0; flex: 1; }
.audience-grid b, .audience-grid small { display: block; }
.audience-grid b { font-size: var(--font-size-sm); }
.audience-grid small { color: var(--color-text-muted); font-size: .75rem; }
.audience-grid i {
  width: 14px;
  height: 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: 50%;
}
.audience-grid button.selected i { border: 4px solid var(--color-primary); background: white; }

.recipient-picker {
  margin-top: -3px;
  padding: 12px;
  border: 1px solid var(--color-border-primary-soft);
  border-radius: 12px;
  background: var(--color-surface-warm);
}
.role-switch { display: flex; gap: 5px; margin-bottom: 9px; }
.role-switch button {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  background: white;
  font-size: var(--font-size-sm);
}
.role-switch button.active { color: var(--color-text); border-color: var(--color-primary); background: var(--color-primary-soft); font-weight: var(--font-weight-bold); }
.recipient-search {
  min-height: 40px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: white;
}
.recipient-search svg { color: var(--color-text-muted); flex: 0 0 auto; }
.recipient-search input { min-width: 0; flex: 1; border: 0; background: transparent; }
.recipient-results {
  max-height: 205px;
  overflow-y: auto;
  margin-top: 7px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: white;
}
.recipient-results button {
  width: 100%;
  min-height: 47px;
  padding: 7px 9px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-bottom: 1px solid var(--color-border-soft);
  color: var(--color-text);
  background: white;
  text-align: left;
}
.recipient-results button:last-child { border-bottom: 0; }
.recipient-results button:hover { background: var(--color-surface-warm); }
.recipient-results button > div { flex: 1; min-width: 0; }
.recipient-results b, .recipient-results small { display: block; }
.recipient-results b { font-size: var(--font-size-sm); }
.recipient-results small { color: var(--color-text-muted); font-size: .75rem; }
.recipient-results > button > svg { transform: rotate(-90deg); color: var(--color-text-muted); }
.recipient-avatar {
  width: 29px;
  height: 29px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}
.selected-recipient {
  min-height: 48px;
  padding: 7px 9px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: white;
}
.selected-recipient > div { flex: 1; }
.selected-recipient b, .selected-recipient small { display: block; }
.selected-recipient b { font-size: var(--font-size-sm); }
.selected-recipient small { color: var(--color-text-muted); font-size: .75rem; }
.selected-recipient > button { width: 30px; height: 30px; display: grid; place-items: center; border: 0; border-radius: 8px; color: var(--color-text-muted); background: transparent; }
.selected-recipient > button:hover { color: var(--color-danger); background: var(--color-danger-soft); }
.recipient-empty { margin: 10px 3px 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.spin { animation: spin .8s linear infinite; }

@media (max-width: 800px) {
  .admin-heading { align-items: flex-start; flex-direction: column; }
  .admin-heading h1 { font-size: var(--font-size-4xl); }
  .message-summary { align-items: flex-start; flex-wrap: wrap; }
  .message-summary p { flex-basis: 100%; margin: 0; }
}
@media (max-width: 560px) {
  .audience-grid { grid-template-columns: 1fr; }
}
</style>
