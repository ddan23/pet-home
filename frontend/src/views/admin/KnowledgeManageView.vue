<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Archive,
  BookOpenCheck,
  CheckCircle2,
  ExternalLink,
  Pencil,
  Plus,
  Search,
  Trash2
} from 'lucide-vue-next'
import { api, queryString } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const ui = useUiStore()
const list = ref([])
const loading = ref(true)
const summaryLoading = ref(true)
const keyword = ref('')
const status = ref('')
const page = ref(1)
const totalPages = ref(1)
const total = ref(0)
const counts = ref({ all: 0, draft: 0, published: 0, archived: 0 })
const summaryAvailable = ref(false)
const editorOpen = ref(false)
const editingId = ref(null)
const saving = ref(false)
const form = ref(emptyForm())
const editorGuard = useDialogDraftGuard({
  getSnapshot: () => ({ editingId: editingId.value, form: form.value }),
  getBusy: () => saving.value,
  close: closeEditor
})

const statusLabels = { draft: '草稿', published: '已发布', archived: '已归档' }
const speciesLabels = { all: '猫狗通用', cat: '猫', dog: '狗' }
const tabs = [
  { value: '', label: '全部状态', countKey: 'all' },
  { value: 'draft', label: '草稿', countKey: 'draft' },
  { value: 'published', label: '已发布', countKey: 'published' },
  { value: 'archived', label: '已归档', countKey: 'archived' }
]
function emptyForm() {
  return {
    title: '',
    species: 'all',
    topic: '',
    summary: '',
    sourceTitle: '',
    sourceUrl: '',
    publisher: '',
    publishedOn: '',
    content: '',
    reviewStatus: 'draft'
  }
}

function resultTotal(result) {
  return result.status === 'fulfilled'
    ? Number(result.value.data?.total || 0)
    : 0
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const results = await Promise.allSettled([
      api.get('/admin/knowledge?page=1&pageSize=1'),
      api.get('/admin/knowledge?status=draft&page=1&pageSize=1'),
      api.get('/admin/knowledge?status=published&page=1&pageSize=1'),
      api.get('/admin/knowledge?status=archived&page=1&pageSize=1')
    ])
    const nextCounts = { ...counts.value }
    if (results[0].status === 'fulfilled') nextCounts.all = resultTotal(results[0])
    if (results[1].status === 'fulfilled') nextCounts.draft = resultTotal(results[1])
    if (results[2].status === 'fulfilled') nextCounts.published = resultTotal(results[2])
    if (results[3].status === 'fulfilled') nextCounts.archived = resultTotal(results[3])
    counts.value = nextCounts
    summaryAvailable.value = results.every((result) => result.status === 'fulfilled')
  } finally {
    summaryLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: 20 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (status.value) params.status = status.value
    const result = await api.get(`/admin/knowledge${queryString(params)}`)
    list.value = result.data?.list || []
    totalPages.value = result.data?.totalPages || 1
    total.value = Number(result.data?.total || 0)
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function changeStatus(nextStatus) {
  status.value = nextStatus
  page.value = 1
  load()
}

function search() {
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  editorOpen.value = true
  editorGuard.markClean()
}

async function openEdit(article) {
  try {
    const result = await api.get(`/admin/knowledge/${article.id}`)
    const detail = result.data
    editingId.value = article.id
    form.value = {
      ...detail.article,
      publishedOn: detail.article.publishedOn || '',
      content: (detail.chunks || []).map((chunk) => chunk.content).join('\n\n'),
      reviewStatus: detail.article.reviewStatus === 'archived'
        ? 'draft'
        : detail.article.reviewStatus
    }
    editorOpen.value = true
    editorGuard.markClean()
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

function closeEditor() {
  editorOpen.value = false
}

async function save() {
  if (!form.value.title.trim() || !form.value.topic.trim()
    || !form.value.sourceTitle.trim() || !form.value.sourceUrl.trim()
    || !form.value.publisher.trim() || !form.value.content.trim()) {
    ui.toast('请完整填写标题、主题、来源和正文', 'warning')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      publishedOn: form.value.publishedOn || null
    }
    const result = editingId.value
      ? await api.put(`/admin/knowledge/${editingId.value}`, payload)
      : await api.post('/admin/knowledge', payload)
    ui.toast(result.msg || '已保存', 'success')
    closeEditor()
    await Promise.all([load(), loadSummary()])
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    saving.value = false
  }
}

async function publish(article) {
  const confirmed = await ui.confirm({
    message: `确认发布《${article.title}》？发布后会进入 AI 养护回答的知识检索范围。`,
    confirmText: '发布文章'
  })
  if (!confirmed) return
  try {
    const result = await api.put(`/admin/knowledge/${article.id}/publish`, {})
    ui.toast(result.msg || '已发布', 'success')
    await Promise.all([load(), loadSummary()])
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function archive(article) {
  const confirmed = await ui.confirm({
    message: `确认归档《${article.title}》？归档后不会再进入 AI 知识检索。`,
    confirmText: '归档文章'
  })
  if (!confirmed) return
  try {
    const result = await api.put(`/admin/knowledge/${article.id}/archive`, {})
    ui.toast(result.msg || '已归档', 'success')
    await Promise.all([load(), loadSummary()])
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function remove(article) {
  const confirmed = await ui.confirm({
    message: `确定永久删除《${article.title}》及其分块吗？此操作不可恢复。`,
    danger: true,
    confirmText: '永久删除'
  })
  if (!confirmed) return
  try {
    await api.delete(`/admin/knowledge/${article.id}`)
    ui.toast('知识文章已删除', 'success')
    await Promise.all([load(), loadSummary()])
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

onMounted(() => {
  load()
  loadSummary()
})
</script>

<template>
  <div class="console-page knowledge-page">
    <header class="admin-heading">
      <div>
        <h1>养护知识库</h1>
        <p>维护猫狗养护 RAG 证据。只有已发布且带权威来源的内容会进入回答。</p>
      </div>
      <button class="btn btn-primary" type="button" @click="openCreate">
        <Plus :size="16" />新增知识
      </button>
    </header>

    <section class="knowledge-panel">
      <div class="knowledge-toolbar">
        <div class="admin-tabs" role="tablist" aria-label="知识状态">
          <button
            v-for="tab in tabs"
            :key="tab.value || 'all'"
            type="button"
            role="tab"
            :aria-selected="status === tab.value"
            :class="{ active: status === tab.value }"
            @click="changeStatus(tab.value)"
          >
            {{ tab.label }}
            <span>{{ summaryLoading || !summaryAvailable ? '—' : counts[tab.countKey] }}</span>
          </button>
        </div>
        <form class="admin-search" role="search" @submit.prevent="search">
          <Search :size="17" />
          <input v-model="keyword" aria-label="搜索知识库" placeholder="搜索标题、主题或机构" />
          <button type="submit">查询</button>
        </form>
      </div>

      <LoadingPanel v-if="loading" label="正在读取知识库" />
      <EmptyState
        v-else-if="!list.length"
        title="暂无知识文章"
      />
      <template v-else>
        <div class="knowledge-list">
          <article v-for="article in list" :key="article.id" class="knowledge-row">
            <span class="knowledge-mark" :class="article.species">
              <BookOpenCheck :size="20" />
            </span>
            <div class="knowledge-copy">
              <div class="knowledge-labels">
                <span class="tag">{{ speciesLabels[article.species] || article.species }}</span>
                <span
                  class="tag"
                  :class="{
                    sage: article.reviewStatus === 'published',
                    butter: article.reviewStatus === 'draft'
                  }"
                >
                  {{ statusLabels[article.reviewStatus] }}
                </span>
              </div>
              <h2>{{ article.title }}</h2>
              <p>{{ article.summary }}</p>
              <small>{{ article.publisher }} · 审核日期 {{ article.reviewedOn || '未发布' }} · {{ article.topic }}</small>
            </div>
            <a
              class="source-link"
              :href="article.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <ExternalLink :size="15" />来源
            </a>
            <div class="knowledge-actions">
              <button type="button" @click="openEdit(article)"><Pencil :size="15" />编辑</button>
              <button
                v-if="article.reviewStatus !== 'published'"
                class="publish"
                type="button"
                @click="publish(article)"
              >
                <CheckCircle2 :size="15" />发布
              </button>
              <button
                v-if="article.reviewStatus === 'published'"
                type="button"
                @click="archive(article)"
              >
                <Archive :size="15" />归档
              </button>
              <button
                class="danger"
                type="button"
                aria-label="删除知识文章"
                @click="remove(article)"
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </article>
        </div>
        <PaginationBar :page="page" :total-pages="totalPages" @change="page = $event; load()" />
      </template>
    </section>

    <ModalDialog
      :open="editorOpen"
      :title="editingId ? '编辑知识文章' : '新增知识文章'"
      width="820px"
      :close-disabled="saving"
      @close="editorGuard.requestClose"
    >
      <div class="knowledge-form">
        <label class="field full">
          <span>标题</span>
          <input v-model="form.title" class="input" maxlength="200" />
        </label>
        <label class="field">
          <span>适用物种</span>
          <select v-model="form.species" class="select">
            <option value="all">猫狗通用</option>
            <option value="cat">猫</option>
            <option value="dog">狗</option>
          </select>
        </label>
        <label class="field">
          <span>主题</span>
          <input v-model="form.topic" class="input" placeholder="如：泌尿系统" />
        </label>
        <label class="field full">
          <span>摘要</span>
          <textarea v-model="form.summary" class="textarea" maxlength="1000" />
        </label>
        <label class="field">
          <span>来源标题</span>
          <input v-model="form.sourceTitle" class="input" />
        </label>
        <label class="field">
          <span>发布机构</span>
          <input v-model="form.publisher" class="input" />
        </label>
        <label class="field full">
          <span>HTTPS 来源链接</span>
          <input v-model="form.sourceUrl" class="input" type="url" />
        </label>
        <label class="field">
          <span>来源发布日期</span>
          <input v-model="form.publishedOn" class="input" type="date" />
        </label>
        <label class="field">
          <span>保存状态</span>
          <select v-model="form.reviewStatus" class="select">
            <option value="draft">草稿</option>
            <option value="published">审核并发布</option>
          </select>
        </label>
        <label class="field full">
          <span>正文（RAG 分块来源）</span>
          <textarea
            v-model="form.content"
            class="textarea knowledge-content"
            maxlength="20000"
          />
        </label>
        <p class="form-note">发布即表示管理员已核对来源。AI 只能引用这些分块，知识不足时会拒答。</p>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="saving" @click="editorGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存知识' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.knowledge-page { display: grid; align-content: start; gap: 18px; }
.admin-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
.admin-heading h1 { margin: 0; font-size: var(--font-size-5xl); }
.admin-heading p { margin: 7px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }

.knowledge-panel {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}
.knowledge-toolbar {
  padding: 13px 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid var(--color-border);
}
.admin-tabs { display: flex; flex-wrap: wrap; gap: 4px; }
.admin-tabs button {
  min-height: 34px;
  padding: 0 11px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--color-text-muted);
  background: transparent;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}
.admin-tabs button span {
  min-width: 20px;
  padding: 1px 5px;
  border-radius: 999px;
  color: var(--color-text-muted);
  background: var(--color-surface-warm);
  font-size: .75rem;
}
.admin-tabs button:hover { background: var(--color-surface-warm); }
.admin-tabs button.active { color: var(--color-text); border-color: var(--color-border); background: var(--color-primary-soft); }
.admin-tabs button.active span { color: var(--color-primary-strong); background: white; }
.admin-search {
  min-width: min(380px, 42vw);
  min-height: 38px;
  padding-left: 11px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: white;
}
.admin-search > svg { color: var(--color-text-muted); flex: 0 0 auto; }
.admin-search input { min-width: 0; flex: 1; border: 0; background: transparent; }
.admin-search button {
  align-self: stretch;
  padding: 0 13px;
  border: 0;
  border-left: 1px solid var(--color-border);
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
  font-weight: var(--font-weight-bold);
}

.knowledge-list { display: grid; }
.knowledge-row {
  min-width: 0;
  padding: 16px 17px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 13px;
  border-bottom: 1px solid var(--color-border-soft);
}
.knowledge-row:last-child { border-bottom: 0; }
.knowledge-row:hover { background: var(--color-surface-ivory); }
.knowledge-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}
.knowledge-mark.cat { color: var(--color-accent-strong); background: var(--color-accent-soft); }
.knowledge-mark.dog { color: var(--color-success); background: var(--color-success-soft); }
.knowledge-copy { min-width: 0; }
.knowledge-labels { display: flex; flex-wrap: wrap; gap: 5px; }
.knowledge-copy h2 { margin: 7px 0 3px; font-family: var(--font-family-body); font-size: var(--font-size-base); }
.knowledge-copy p { margin: 0; overflow: hidden; color: var(--color-text-secondary); font-size: var(--font-size-sm); text-overflow: ellipsis; white-space: nowrap; }
.knowledge-copy small { display: block; margin-top: 4px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.source-link { display: inline-flex; align-items: center; gap: 5px; color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.knowledge-actions { display: flex; gap: 5px; }
.knowledge-actions button {
  min-height: 34px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text-secondary);
  background: white;
  font-size: var(--font-size-sm);
}
.knowledge-actions button:hover { border-color: var(--color-primary); color: var(--color-primary-strong); background: var(--color-surface-warm); }
.knowledge-actions button.publish { color: var(--color-primary-strong); background: var(--color-primary-soft); border-color: var(--color-border-warning); }
.knowledge-actions button.danger { color: var(--color-danger); }
.knowledge-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.knowledge-form .full { grid-column: 1 / -1; }
.knowledge-content { min-height: 220px; }
.form-note { grid-column: 1 / -1; margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }

@media (max-width: 980px) {
  .knowledge-toolbar { align-items: stretch; flex-direction: column; }
  .admin-search { min-width: 0; width: 100%; }
  .knowledge-row { grid-template-columns: auto minmax(0, 1fr); }
  .source-link, .knowledge-actions { grid-column: 2; }
}
@media (max-width: 720px) {
  .admin-heading { align-items: flex-start; flex-direction: column; }
  .admin-heading h1 { font-size: var(--font-size-4xl); }
  .knowledge-row { padding: 14px; }
  .knowledge-actions { flex-wrap: wrap; }
  .knowledge-form { grid-template-columns: 1fr; }
  .knowledge-form .full { grid-column: auto; }
}
</style>
