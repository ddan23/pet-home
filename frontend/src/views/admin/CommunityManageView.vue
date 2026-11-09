<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Check,
  ChevronDown,
  Eye,
  Heart,
  MessageCircle,
  RefreshCw,
  Search,
  Trash2,
  TrendingUp,
  X
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { relativeTime } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const ui = useUiStore()
const route = useRoute()
const router = useRouter()
const posts = ref([])
const stats = ref({})
const loading = ref(true)
const refreshing = ref(false)
const keyword = ref('')
const status = ref('')
const category = ref('')
const page = ref(1)
const totalPages = ref(1)
const selected = ref(null)
const moderationLog = ref(null)
const postComments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const detailLoading = ref(false)
const selectedComment = ref(null)
const commentModerationLog = ref(null)
const pendingComments = ref([])
const pendingLoading = ref(false)
const pendingPage = ref(1)
const pendingTotalPages = ref(1)
const pendingTotal = ref(0)
const commentStatus = ref('pending')
const postActionBusyId = ref(null)
const commentActionBusyId = ref(null)
const queueNames = ['posts', 'pendingPosts', 'comments']
const activeQueue = ref(queueNames.includes(route.query.tab) ? route.query.tab : 'posts')
const categories = ['晒宠', '求助', '领养', '好物', '日常']
const displayStatusLabels = {
  published: '已发布',
  pending: '待审核',
  rejected: '审核未通过',
  offline: '已下架'
}
const commentStatusLabels = { normal: '已通过', pending: '待审核', blocked: '已拒绝' }
const activePostCount = computed(() => (
  Number(stats.value.normal || 0) + Number(stats.value.blocked || 0)
))

function displayStatus(post) {
  if (post?.displayStatus) return post.displayStatus
  return post?.status === 'normal' ? 'published' : post?.status === 'blocked' ? 'offline' : 'pending'
}

function statusTone(post) {
  const current = displayStatus(post)
  return {
    butter: current === 'pending' || current === 'offline',
    danger: current === 'rejected',
    sage: current === 'published'
  }
}

async function loadStats() {
  try {
    stats.value = (await api.get('/admin/posts/stats')).data || {}
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function loadQueueBadges() {
  try {
    const commentsRes = await api.get('/admin/comments?status=pending&page=1&pageSize=1')
    pendingTotal.value = commentsRes.data?.total || 0
  } catch (error) {
    pendingTotal.value = 0
  }
}

async function loadPosts() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: 20 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (category.value) params.category = category.value
    if (activeQueue.value === 'pendingPosts') {
      params.status = 'pending'
    } else if (status.value) {
      params.status = status.value
    } else {
      // 帖子：只显示已发布与下架内容，待审核内容统一在「待审核帖子」处理
      params.status = 'normal,blocked'
    }
    const result = await api.get(`/admin/posts${queryString(params)}`)
    posts.value = result.data?.list || []
    totalPages.value = result.data?.totalPages || 1
    const keepSelected = selected.value && posts.value.some((item) => item.id === selected.value.id)
    if (!keepSelected) {
      if (posts.value.length) await selectPost(posts.value[0])
      else clearDetail()
    }
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function clearDetail() {
  selected.value = null
  moderationLog.value = null
  postComments.value = []
  commentTotal.value = 0
  commentPage.value = 1
}

async function selectPost(post) {
  selected.value = post
  moderationLog.value = null
  postComments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  detailLoading.value = true
  try {
    const [detail, moderation, comments] = await Promise.all([
      api.get(`/admin/posts/${post.id}`),
      api.get(`/admin/posts/${post.id}/moderation`).catch(() => ({ data: null })),
      api.get(`/admin/posts/${post.id}/comments?page=1&pageSize=20`).catch(() => ({ data: null }))
    ])
    if (selected.value?.id !== post.id) return
    selected.value = detail.data
    moderationLog.value = moderation.data || null
    postComments.value = comments.data?.list || []
    commentTotal.value = comments.data?.total || 0
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    detailLoading.value = false
  }
}

async function loadMoreComments() {
  if (!selected.value) return
  const nextPage = commentPage.value + 1
  try {
    const result = await api.get(`/admin/posts/${selected.value.id}/comments?page=${nextPage}&pageSize=20`)
    postComments.value.push(...(result.data?.list || []))
    commentPage.value = nextPage
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function loadPendingComments() {
  pendingLoading.value = true
  try {
    const result = await api.get(
      `/admin/comments?status=${commentStatus.value}&page=${pendingPage.value}&pageSize=20`
    )
    pendingComments.value = result.data?.list || []
    pendingTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    pendingLoading.value = false
  }
}

function changeCommentStatus() {
  pendingPage.value = 1
  loadPendingComments()
}

function search() {
  page.value = 1
  loadPosts()
}

function resetPostFilters() {
  keyword.value = ''
  category.value = ''
  status.value = ''
  page.value = 1
}

function switchQueue(next) {
  if (!queueNames.includes(next) || next === activeQueue.value) return
  activeQueue.value = next
  router.replace({ query: { ...route.query, tab: next } })
  if (next === 'posts' || next === 'pendingPosts') {
    resetPostFilters()
    clearDetail()
    loadPosts()
  }
}

async function refreshAll() {
  refreshing.value = true
  try {
    await Promise.all([
      loadPosts(),
      loadStats(),
      loadQueueBadges(),
      loadPendingComments()
    ])
  } finally {
    refreshing.value = false
  }
}

async function updateStatus(post, nextStatus) {
  if (postActionBusyId.value) return
  const previousStatus = post.status
  const previousDisplayStatus = displayStatus(post)
  const blocking = nextStatus === 'blocked'
  const title = blocking
    ? previousStatus === 'pending' ? '拒绝帖子' : '下架帖子'
    : previousDisplayStatus === 'pending'
      ? '通过帖子审核'
      : previousDisplayStatus === 'rejected' ? '复核通过帖子' : '恢复帖子'
  const message = blocking
    ? previousStatus === 'pending'
      ? `确认拒绝帖子「${post.title || '无标题帖子'}」吗？拒绝后管理端将显示“审核未通过”。`
      : `确认下架帖子「${post.title || '无标题帖子'}」吗？下架后其他用户将无法查看、点赞或评论。`
    : previousDisplayStatus === 'pending'
      ? `确认通过帖子「${post.title || '无标题帖子'}」的审核吗？通过后帖子将公开。`
      : previousDisplayStatus === 'rejected'
        ? `确认复核通过帖子「${post.title || '无标题帖子'}」吗？通过后帖子将恢复公开。`
        : `确认恢复上架帖子「${post.title || '无标题帖子'}」吗？`
  const confirmed = await ui.confirm({
    title,
    message,
    confirmText: blocking ? `确认${previousStatus === 'pending' ? '拒绝' : '下架'}` : '确认通过',
    danger: blocking
  })
  if (!confirmed) return
  postActionBusyId.value = post.id
  try {
    await api.put(`/admin/posts/${post.id}/status`, { status: nextStatus })
    post.status = nextStatus
    if (selected.value?.id === post.id) selected.value.status = nextStatus
    post.displayStatus = nextStatus === 'normal'
      ? 'published'
      : previousStatus === 'pending' ? 'rejected' : 'offline'
    const message = nextStatus === 'blocked'
      ? previousStatus === 'pending' ? '帖子审核未通过' : '帖子已下架'
      : previousDisplayStatus === 'pending'
        ? '帖子已通过'
        : previousDisplayStatus === 'rejected' ? '帖子已复核通过' : '帖子已恢复上架'
    ui.toast(message, 'success')
    await Promise.all([loadStats(), loadPosts()])
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    postActionBusyId.value = null
  }
}

async function deletePost(post) {
  const confirmed = await ui.confirm({ message: '删除后帖子将不再公开展示，确定继续吗？', danger: true })
  if (!confirmed) return
  try {
    await api.delete(`/admin/posts/${post.id}`)
    if (selected.value?.id === post.id) clearDetail()
    if (page.value > 1 && posts.value.length === 1) page.value -= 1
    ui.toast('帖子已删除', 'success')
    await Promise.all([loadStats(), loadPosts()])
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function reviewComment(comment, status) {
  if (commentActionBusyId.value) return
  const summary = String(comment.content || '')
  const preview = `${summary.slice(0, 30)}${summary.length > 30 ? '…' : ''}`
  const confirmed = await ui.confirm({
    title: status === 'normal' ? '通过评论' : '拒绝评论',
    message: `确认${status === 'normal' ? '通过' : '拒绝'}「${comment.senderName || '社区用户'}」在《${comment.postTitle || '无标题帖子'}》下的评论吗？评论内容：“${preview}”`,
    confirmText: status === 'normal' ? '确认通过' : '确认拒绝',
    danger: status !== 'normal'
  })
  if (!confirmed) return
  commentActionBusyId.value = comment.id
  try {
    await api.put(`/admin/comments/${comment.id}/status`, { status })
    if (pendingPage.value > 1 && pendingComments.value.length === 1) pendingPage.value -= 1
    ui.toast(status === 'normal' ? '评论已通过' : '评论已拒绝', 'success')
    await Promise.all([loadStats(), loadQueueBadges(), loadPendingComments()])
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    commentActionBusyId.value = null
  }
}

async function viewCommentEvidence(comment) {
  selectedComment.value = comment
  commentModerationLog.value = null
  try {
    const result = await api.get(`/admin/comments/${comment.id}/moderation`)
    commentModerationLog.value = result.data
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

onMounted(async () => {
  await Promise.all([
    loadPosts(),
    loadStats(),
    loadQueueBadges(),
    loadPendingComments()
  ])
})

watch(() => route.query.tab, (next) => {
  if (!queueNames.includes(next) || next === activeQueue.value) return
  activeQueue.value = next
  if (next === 'posts' || next === 'pendingPosts') {
    resetPostFilters()
    clearDetail()
    loadPosts()
  }
})
</script>

<template>
  <div class="console-page">
    <header class="admin-page-head">
      <div><h1>社区管理</h1></div>
      <button class="admin-icon-btn" type="button" :disabled="refreshing" aria-label="刷新社区审核数据" @click="refreshAll">
        <RefreshCw :size="17" :class="{ spinning: refreshing }" />
      </button>
    </header>
    <div class="metric-grid">
      <article><TrendingUp :size="19" /><span>今日新增</span><b>{{ stats.today || 0 }}</b></article>
      <article><span>全部帖子</span><b>{{ stats.total || 0 }}</b></article>
      <article class="warn"><span>待审核</span><b>{{ stats.pending || 0 }}</b></article>
      <article class="danger"><span>审核未通过 / 已下架</span><b>{{ stats.blocked || 0 }}</b></article>
    </div>
    <div class="community-toolbar">
      <nav class="queue-tabs" aria-label="社区审核分类">
        <button type="button" :class="{ active: activeQueue === 'posts' }" @click="switchQueue('posts')">
          帖子 <b>{{ activePostCount }}</b>
        </button>
        <button type="button" :class="{ active: activeQueue === 'pendingPosts' }" @click="switchQueue('pendingPosts')">
          待审核帖子 <b>{{ stats.pending || 0 }}</b>
        </button>
        <button type="button" :class="{ active: activeQueue === 'comments' }" @click="switchQueue('comments')">
          待审核评论 <b>{{ pendingTotal }}</b>
        </button>
      </nav>
      <form
        v-if="activeQueue === 'posts' || activeQueue === 'pendingPosts'"
        class="post-filter-bar"
        @submit.prevent="search"
      >
        <label class="post-select">
          <select v-model="category" aria-label="帖子分类" @change="search">
            <option value="">全部分类</option>
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
          <ChevronDown :size="16" />
        </label>
        <label v-if="activeQueue === 'posts'" class="post-select">
          <select v-model="status" aria-label="帖子状态" @change="search">
            <option value="">全部状态</option>
            <option value="published">已发布</option>
            <option value="rejected">审核未通过</option>
            <option value="offline">已下架</option>
          </select>
          <ChevronDown :size="16" />
        </label>
        <label class="admin-search post-search">
          <Search :size="17" />
          <input v-model="keyword" aria-label="搜索标题或正文" placeholder="搜索标题或正文">
        </label>
        <button class="btn btn-primary post-search-btn" type="submit">查询</button>
      </form>
      <label v-if="activeQueue === 'comments'" class="post-select toolbar-tail">
        <select v-model="commentStatus" aria-label="评论状态" @change="changeCommentStatus">
          <option value="pending">待审核</option>
          <option value="blocked">已拒绝</option>
        </select>
        <ChevronDown :size="16" />
      </label>
    </div>

    <section v-if="activeQueue === 'posts' || activeQueue === 'pendingPosts'" class="queue-section">
      <LoadingPanel v-if="loading" label="正在读取帖子" />
      <div v-else class="post-workspace">
        <div class="post-list">
          <article
            v-for="post in posts"
            :key="post.id"
            :class="{ active: selected?.id === post.id }"
            @click="selectPost(post)"
          >
            <div class="post-list-head">
              <span
                class="tag"
                :class="statusTone(post)"
              >
                {{ displayStatusLabels[displayStatus(post)] || displayStatus(post) }}
              </span>
              <span class="tag">{{ post.category }}</span>
              <small>{{ relativeTime(post.createdAt) }}</small>
            </div>
            <b>{{ post.title || '无标题帖子' }}</b>
            <p>{{ post.content }}</p>
            <div class="post-list-meta">
              <span><Heart :size="14" />{{ post.likes || 0 }}</span>
              <span><MessageCircle :size="14" />{{ post.comments || 0 }}</span>
            </div>
            <div class="post-list-actions" @click.stop>
              <button
                v-if="post.status !== 'normal'"
                type="button"
                :disabled="postActionBusyId === post.id"
                @click="updateStatus(post, 'normal')"
              >
                <Check :size="14" />{{
                  displayStatus(post) === 'rejected'
                    ? '复核通过'
                    : displayStatus(post) === 'offline' ? '恢复上架' : '通过'
                }}
              </button>
              <button
                v-if="post.status !== 'blocked'"
                class="danger"
                type="button"
                :disabled="postActionBusyId === post.id"
                @click="updateStatus(post, 'blocked')"
              >
                <X :size="14" />{{ displayStatus(post) === 'pending' ? '拒绝' : '下架' }}
              </button>
              <button
                class="danger"
                type="button"
                :disabled="postActionBusyId === post.id"
                @click="deletePost(post)"
              >
                <Trash2 :size="14" />删除
              </button>
            </div>
          </article>
          <EmptyState v-if="!posts.length" :title="activeQueue === 'pendingPosts' ? '没有待审核帖子' : '没有匹配帖子'" />
        </div>

        <aside class="post-detail">
          <template v-if="selected">
            <header class="detail-author">
              <img v-if="selected.avatar" :src="assetUrl(selected.avatar)" alt="">
              <span v-else class="detail-avatar-mark">{{ (selected.nickname || '用').slice(0, 1) }}</span>
              <div>
                <b>{{ selected.nickname || '社区用户' }}</b>
                <small>{{ relativeTime(selected.createdAt) }}</small>
              </div>
              <span
                class="tag"
                :class="statusTone(selected)"
              >
                {{ displayStatusLabels[displayStatus(selected)] || displayStatus(selected) }}
              </span>
            </header>

            <h2 class="detail-title">{{ selected.title || '无标题帖子' }}</h2>
            <p class="detail-content">{{ selected.content }}</p>
            <img v-if="selected.image" class="detail-image" :src="assetUrl(selected.image)" :alt="selected.title || '帖子图片'">

            <div class="detail-stats">
              <span><Heart :size="15" />{{ selected.likes || 0 }} 点赞</span>
              <span><MessageCircle :size="15" />{{ selected.comments || 0 }} 评论</span>
            </div>

            <section v-if="moderationLog" class="moderation-evidence">
              <div>
                <span
                  class="tag"
                  :class="{ danger: moderationLog.aiDecision === 'BLOCK', butter: moderationLog.aiDecision === 'REVIEW', sage: moderationLog.aiDecision === 'ALLOW' }"
                >
                  多模态 AI：{{ moderationLog.aiDecision }}
                </span>
                <span class="tag">{{ moderationLog.riskLevel }} 风险</span>
                <span v-if="moderationLog.confidence != null" class="tag">
                  置信度 {{ Number(moderationLog.confidence).toFixed(2) }}
                </span>
              </div>
              <p>{{ moderationLog.reason }}</p>
              <code v-if="moderationLog.ruleHits">规则命中：{{ moderationLog.ruleHits }}</code>
              <small>模型 {{ moderationLog.modelName }} · 提示词 {{ moderationLog.promptVersion }}</small>
            </section>

            <section class="comment-block">
              <h3>评论（{{ commentTotal }}）</h3>
              <p v-if="detailLoading" class="text-muted">正在读取评论…</p>
              <template v-else-if="postComments.length">
                <div class="comment-list">
                  <article v-for="comment in postComments" :key="comment.id">
                    <img v-if="comment.senderAvatar" :src="assetUrl(comment.senderAvatar)" alt="">
                    <span v-else class="comment-avatar-mark">{{ (comment.senderName || '用').slice(0, 1) }}</span>
                    <div class="comment-body">
                      <div class="comment-head">
                        <b>{{ comment.senderName || '社区用户' }}</b>
                        <small>{{ relativeTime(comment.createdAt) }}</small>
                        <span v-if="comment.status !== 'normal'" class="tag" :class="{ danger: comment.status === 'blocked' }">
                          {{ commentStatusLabels[comment.status] }}
                        </span>
                      </div>
                      <p>{{ comment.content }}</p>
                      <small v-if="comment.likeCount" class="comment-like">赞 {{ comment.likeCount }}</small>
                    </div>
                  </article>
                </div>
                <button
                  v-if="postComments.length < commentTotal"
                  class="load-more"
                  type="button"
                  @click="loadMoreComments"
                >
                  加载更多评论（{{ postComments.length }}/{{ commentTotal }}）
                </button>
              </template>
              <p v-else class="text-muted">这条帖子还没有评论。</p>
            </section>
          </template>
          <EmptyState v-else title="从左侧选择一条帖子" description="点击左侧帖子即可在这里查看正文、点赞评论和具体评论。" />
        </aside>
      </div>

      <PaginationBar
        v-if="!loading && posts.length"
        :page="page"
        :total-pages="totalPages"
        @change="page = $event; loadPosts()"
      />
    </section>

    <section v-if="activeQueue === 'comments'" class="pending-comments">
      <LoadingPanel v-if="pendingLoading" label="正在读取待审核评论" />
      <EmptyState v-else-if="!pendingComments.length" title="没有待审核评论" description="自动审核发现疑似内容时，评论会进入这里。" />
      <template v-else>
        <div class="pending-list">
          <article v-for="comment in pendingComments" :key="comment.id">
            <div>
              <b>{{ comment.senderName || '社区用户' }}</b>
              <small>评论《{{ comment.postTitle || '无标题帖子' }}》 · {{ relativeTime(comment.createdAt) }}</small>
              <span class="tag" :class="{ butter: comment.status === 'pending', danger: comment.status === 'blocked' }">
                {{ commentStatusLabels[comment.status] }}
              </span>
            </div>
            <p>{{ comment.content }}</p>
            <div class="pending-actions">
              <button type="button" @click="viewCommentEvidence(comment)"><Eye :size="14" />依据</button>
              <template v-if="comment.status === 'pending'">
                <button
                  type="button"
                  :disabled="commentActionBusyId === comment.id"
                  @click="reviewComment(comment, 'normal')"
                ><Check :size="14" />通过</button>
                <button
                  type="button"
                  class="danger"
                  :disabled="commentActionBusyId === comment.id"
                  @click="reviewComment(comment, 'blocked')"
                ><X :size="14" />拒绝</button>
              </template>
            </div>
          </article>
        </div>
        <PaginationBar :page="pendingPage" :total-pages="pendingTotalPages" @change="pendingPage = $event; loadPendingComments()" />
      </template>
    </section>

    <ModalDialog
      :open="!!selectedComment"
      title="评论审核依据"
      width="620px"
      @close="selectedComment = null"
    >
      <div v-if="selectedComment" class="preview">
        <div>
          <span class="tag">{{ commentStatusLabels[selectedComment.status] }}</span>
          <span class="tag">{{ selectedComment.senderName || '社区用户' }}</span>
        </div>
        <p>{{ selectedComment.content }}</p>
        <section v-if="commentModerationLog" class="moderation-evidence">
          <div>
            <span class="tag" :class="{ danger: commentModerationLog.aiDecision === 'BLOCK', butter: commentModerationLog.aiDecision === 'REVIEW', sage: commentModerationLog.aiDecision === 'ALLOW' }">
              审核结论：{{ commentModerationLog.aiDecision }}
            </span>
            <span class="tag">{{ commentModerationLog.riskLevel }} 风险</span>
            <span v-if="commentModerationLog.confidence != null" class="tag">
              置信度 {{ Number(commentModerationLog.confidence).toFixed(2) }}
            </span>
          </div>
          <p>{{ commentModerationLog.reason }}</p>
          <code v-if="commentModerationLog.ruleHits">规则命中：{{ commentModerationLog.ruleHits }}</code>
          <code v-if="commentModerationLog.evidenceJson">证据：{{ commentModerationLog.evidenceJson }}</code>
          <small>模型 {{ commentModerationLog.modelName }} · 触发 {{ commentModerationLog.triggerSource }}</small>
        </section>
        <p v-else class="text-muted">暂无异步审核结果，可能仍在处理中。</p>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.console-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; margin-bottom: 20px; }
.console-heading h1 { margin: 0; font-size: var(--font-size-5xl); }
.console-heading p:last-child { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.community-toolbar {
  margin-bottom: 14px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.queue-tabs {
  flex: 0 0 auto;
  margin-right: 18px;
  padding: 5px;
  display: flex;
  gap: 4px;
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}
.queue-tabs button {
  min-height: 38px;
  padding: 6px 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 0;
  border-radius: 11px;
  color: var(--color-text-secondary);
  background: transparent;
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  cursor: pointer;
  transition: var(--transition-base);
}
.queue-tabs b {
  font-size: 0.72rem;
  font-weight: var(--font-weight-extra-bold);
  color: var(--color-primary-strong);
  font-variant-numeric: tabular-nums;
}
.queue-tabs button.active b { color: var(--color-text); }
.queue-tabs button:hover { color: var(--color-primary-strong); background: var(--color-surface-warm); }
.queue-tabs button.active { color: var(--color-text); background: var(--color-primary); }
.queue-section { display: grid; gap: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.metric-grid article { padding: 16px; border: 1px solid var(--color-border); border-radius: 16px; background: var(--color-white); display: flex; justify-content: space-between; align-items: center; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.metric-grid b { color: var(--color-text); font-size: var(--font-size-2xl); }
.metric-grid article.warn b { color: var(--color-warning); }
.metric-grid article.danger b { color: var(--color-danger); }

.post-filter-bar {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.post-search {
  width: 232px;
  min-height: 40px;
  padding: 0 12px;
}
.post-search input { font-size: 0.92rem; }
.post-select { position: relative; display: inline-flex; align-items: center; }
.toolbar-tail { margin-left: auto; }
.post-select select {
  height: 44px;
  min-width: 118px;
  padding: 0 36px 0 15px;
  appearance: none;
  -webkit-appearance: none;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-white);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 500;
  line-height: 44px;
  cursor: pointer;
  transition: var(--transition-base);
}
.post-select select:hover,
.post-select select:focus { border-color: var(--color-primary); outline: none; }
.post-select svg { position: absolute; right: 13px; color: var(--color-text-secondary); pointer-events: none; }
.post-search-btn { min-height: 43px; padding: 0 14px; }

.post-workspace {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}
.post-list { display: grid; gap: 10px; }
.post-list > article {
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-white);
  cursor: pointer;
  transition: var(--transition-base);
}
.post-list > article:hover { border-color: var(--color-primary); }
.post-list > article.active {
  border-color: var(--color-primary);
  background: var(--color-surface-warm);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}
.post-list-head { display: flex; align-items: center; gap: 6px; }
.post-list-head small { margin-left: auto; color: var(--color-text-muted); font-size: 0.75rem; }
.post-list > article > b { display: block; margin: 8px 0 4px; font-size: 0.95rem; }
.post-list > article > p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.84rem;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.post-list-meta { margin-top: 8px; display: flex; gap: 14px; color: var(--color-text-muted); font-size: 0.78rem; }
.post-list-meta span { display: inline-flex; align-items: center; gap: 4px; }
.post-list-actions {
  margin-top: 10px;
  padding-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  border-top: 1px dashed var(--color-border);
}
.post-list-actions button {
  min-height: 30px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-white);
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  font-weight: var(--font-weight-bold);
  cursor: pointer;
  transition: var(--transition-base);
}
.post-list-actions button:hover { border-color: var(--color-primary); color: var(--color-primary-strong); background: var(--color-surface-warm); }
.post-list-actions button.danger { color: var(--color-danger); }
.post-list-actions button.danger:hover { border-color: var(--color-danger); background: var(--color-danger-soft); }

.post-detail {
  position: sticky;
  top: 16px;
  height: calc(100vh - 272px);
  min-height: 460px;
  overflow-y: auto;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-white);
  box-shadow: var(--box-shadow-sm);
}
.detail-author { display: flex; align-items: center; gap: 10px; }
.detail-author img,
.detail-avatar-mark {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-weight: var(--font-weight-bold);
}
.detail-author > div { flex: 1; min-width: 0; }
.detail-author b, .detail-author small { display: block; }
.detail-author small { color: var(--color-text-muted); font-size: 0.76rem; }
.detail-title { margin: 12px 0 6px; font-size: var(--font-size-xl); }
.detail-content { margin: 0; white-space: pre-wrap; line-height: 1.85; color: var(--color-text-secondary); }
.detail-image { width: 100%; max-height: 320px; object-fit: contain; margin-top: 12px; border-radius: 12px; background: var(--color-surface-warm); }
.detail-stats { display: flex; gap: 18px; margin: 14px 0 0; color: var(--color-text-muted); font-size: 0.82rem; }
.detail-stats span { display: inline-flex; align-items: center; gap: 5px; }

.comment-block { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--color-border); }
.comment-block h3 { margin: 0 0 10px; font-size: 1rem; }
.comment-list { display: grid; gap: 12px; }
.comment-list article { display: flex; gap: 9px; }
.comment-list img,
.comment-avatar-mark {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 0.78rem;
  font-weight: var(--font-weight-bold);
}
.comment-body { min-width: 0; flex: 1; }
.comment-head { display: flex; align-items: center; gap: 6px; }
.comment-head b { font-size: 0.85rem; }
.comment-head small { color: var(--color-text-muted); font-size: 0.74rem; }
.comment-body p { margin: 3px 0 0; color: var(--color-text-secondary); font-size: 0.85rem; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.comment-like { color: var(--color-text-muted); font-size: 0.74rem; }
.load-more {
  margin-top: 12px;
  width: 100%;
  padding: 8px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
}
.load-more:hover { border-color: var(--color-primary); color: var(--color-primary-strong); }

.preview > div { display: flex; gap: 5px; }
.preview p { white-space: pre-wrap; line-height: 1.8; color: var(--color-text-secondary); }
.preview small { color: var(--color-text-muted); }
.moderation-evidence { margin: 14px 0 0; padding: 13px; border-radius: 12px; background: var(--color-surface-warm); border: 1px solid var(--color-border); }
.moderation-evidence > div { display: flex; flex-wrap: wrap; gap: 5px; }
.moderation-evidence p { margin: 9px 0; font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.moderation-evidence code { display: block; margin-top: 5px; color: var(--color-text-muted); font-size: var(--font-size-sm); white-space: pre-wrap; word-break: break-all; }
.pending-comments { margin-top: 0; }
.pending-heading { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 10px; }
.pending-heading h2 { margin: 6px 0 0; font-size: var(--font-size-xl); }
.pending-list { display: grid; gap: 8px; }
.pending-list article { display: grid; grid-template-columns: 180px 1fr auto; gap: 14px; align-items: center; padding: 13px 15px; border: 1px solid var(--color-border); border-radius: 13px; background: var(--color-white); }
.pending-list b, .pending-list small { display: block; }
.pending-list b { font-size: var(--font-size-sm); }
.pending-list small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.pending-list p { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); line-height: 1.6; }
.pending-actions { display: flex; gap: 5px; }
.pending-actions button { border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-white); padding: 5px 8px; display: flex; align-items: center; gap: 3px; font-size: var(--font-size-sm); cursor: pointer; }
.pending-actions button.danger { color: var(--color-danger); }
.spinning { animation: spin .8s linear infinite; }

@media (max-width: 1100px) {
  .post-workspace { grid-template-columns: 1fr; }
  .post-detail {
    position: static;
    height: auto;
    min-height: 0;
    overflow: visible;
  }
}
@media (max-width: 900px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .pending-list article { grid-template-columns: 1fr; }
}
@media (max-width: 650px) {
  .post-filter-bar { justify-content: stretch; }
  .post-search { width: 100%; }
  .post-select { flex: 1; }
  .post-select select { min-width: 0; width: 100%; }
  .queue-tabs button { flex: 0 0 auto; }
  .admin-page-head { flex-direction: row; align-items: center; }
}
</style>
