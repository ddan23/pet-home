<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  BellRing,
  CheckCircle2,
  Clock3,
  MessageCircle,
  Store,
  UsersRound,
  Warehouse
} from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { relativeTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const loading = ref(true)
const partialError = ref(false)
const serviceHealthy = ref(true)
const available = reactive({
  users: false,
  businesses: false,
  shops: false,
  pendingShops: false,
  posts: false,
  moderation: false,
  knowledge: false,
  messages: false
})
const metrics = reactive({
  users: 0,
  businessUsers: 0,
  shops: 0,
  pendingShops: 0,
  posts: 0,
  publishedKnowledge: 0,
  notifications: 0
})
const moderation = reactive({
  posts: 0,
  comments: 0,
  total: 0
})
const knowledge = reactive({
  total: 0,
  published: 0,
  draft: 0,
  archived: 0
})
const latestArticle = ref(null)
const shopQueue = ref([])
const commentQueue = ref([])
const recentMessages = ref([])

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const overviewReady = computed(() => available.moderation && available.pendingShops)
const taskCount = computed(() => overviewReady.value ? moderation.total + metrics.pendingShops : 0)

const priorityRows = computed(() => {
  const rows = []
  if (metrics.pendingShops > 0) {
    rows.push({
      key: 'shops',
      count: metrics.pendingShops,
      title: `${metrics.pendingShops} 家店铺等待资质审核`,
      detail: shopQueue.value[0]
        ? `${shopQueue.value[0].merchantName || '商家'} · ${shopQueue.value[0].name}`
        : '进入商家管理处理新提交的门店资料。',
      icon: Store,
      tone: 'coral',
      path: '/admin/merchants?tab=pending',
      action: '去审核'
    })
  }
  if (moderation.comments > 0) {
    rows.push({
      key: 'comments',
      count: moderation.comments,
      title: `${moderation.comments} 条评论等待人工复核`,
      detail: commentQueue.value[0]?.content || '查看自动审核结论后决定通过或拒绝。',
      icon: MessageCircle,
      tone: '',
      path: '/admin/community?tab=comments',
      action: '开始复核'
    })
  }
  if (moderation.posts > 0) {
    rows.push({
      key: 'posts',
      count: moderation.posts,
      title: `${moderation.posts} 条帖子需要人工复核`,
      detail: '结合规则命中与 AI 审核依据进行最终判断。',
      icon: Clock3,
      tone: 'coral',
      path: '/admin/community?tab=posts',
      action: '查看帖子'
    })
  }
  return rows.slice(0, 4)
})

const knowledgeRows = computed(() => {
  const rows = [
    { key: 'published', label: '已发布', value: knowledge.published },
    { key: 'draft', label: '草稿', value: knowledge.draft },
    { key: 'archived', label: '已归档', value: knowledge.archived }
  ]
  const max = Math.max(1, ...rows.map((item) => item.value))
  return rows.map((item) => ({
    ...item,
    width: `${Math.max(item.value ? 8 : 0, (item.value / max) * 100)}%`
  }))
})

const publishedRate = computed(() => {
  if (!knowledge.total) return 0
  return Math.round((knowledge.published / knowledge.total) * 100)
})

function open(path) {
  router.push(path)
}

function number(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function displayMetric(key) {
  const metricKey = {
    businesses: 'businessUsers',
    knowledge: 'publishedKnowledge'
  }[key] || key
  return available[key] ? number(metrics[metricKey]) : '—'
}

function takeResult(results, index, fallback) {
  const result = results[index]
  return result.status === 'fulfilled' ? result.value : fallback
}

async function load() {
  loading.value = true
  partialError.value = false
  Object.keys(available).forEach((key) => {
    available[key] = false
  })
  try {
    const results = await Promise.allSettled([
      api.get('/admin/users?role=user&page=1&pageSize=1'),
      api.get('/admin/users?role=business&page=1&pageSize=1'),
      api.get('/admin/shops?page=1&pageSize=1'),
      api.get('/admin/shops?status=pending_audit&page=1&pageSize=4'),
      api.get('/admin/posts/stats'),
      api.get('/admin/moderation/unread-summary'),
      api.get('/admin/knowledge?status=published&page=1&pageSize=1'),
      api.get('/admin/messages?page=1&pageSize=4'),
      api.get('/admin/comments?status=pending&page=1&pageSize=3'),
      api.get('/admin/knowledge?page=1&pageSize=1'),
      api.get('/admin/knowledge?status=draft&page=1&pageSize=1'),
      api.get('/admin/knowledge?status=archived&page=1&pageSize=1')
    ])

    const failedCount = results.filter((result) => result.status === 'rejected').length
    partialError.value = failedCount > 0
    serviceHealthy.value = failedCount === 0
    available.users = results[0].status === 'fulfilled'
    available.businesses = results[1].status === 'fulfilled'
    available.shops = results[2].status === 'fulfilled'
    available.pendingShops = results[3].status === 'fulfilled'
    available.posts = results[4].status === 'fulfilled'
    available.moderation = results[5].status === 'fulfilled'
    available.knowledge = [6, 9, 10, 11].every((index) => results[index].status === 'fulfilled')
    available.messages = results[7].status === 'fulfilled'

    const users = takeResult(results, 0, { data: {} })
    const businesses = takeResult(results, 1, { data: {} })
    const shops = takeResult(results, 2, { data: {} })
    const pendingShops = takeResult(results, 3, { data: {} })
    const postStats = takeResult(results, 4, { data: {} })
    const moderationSummary = takeResult(results, 5, { data: {} })
    const knowledgePublished = takeResult(results, 6, { data: {} })
    const messages = takeResult(results, 7, { data: {} })
    const comments = takeResult(results, 8, { data: {} })
    const knowledgeAll = takeResult(results, 9, { data: {} })
    const knowledgeDraft = takeResult(results, 10, { data: {} })
    const knowledgeArchived = takeResult(results, 11, { data: {} })

    metrics.users = users.data?.total || 0
    metrics.businessUsers = businesses.data?.total || 0
    metrics.shops = shops.data?.total || 0
    metrics.pendingShops = pendingShops.data?.total || 0
    metrics.posts = postStats.data?.total || 0
    metrics.publishedKnowledge = knowledgePublished.data?.total || 0
    metrics.notifications = messages.data?.total || 0

    Object.assign(moderation, {
      posts: moderationSummary.data?.posts || 0,
      comments: moderationSummary.data?.comments || 0,
      total: moderationSummary.data?.total || 0
    })

    Object.assign(knowledge, {
      total: knowledgeAll.data?.total || 0,
      published: knowledgePublished.data?.total || 0,
      draft: knowledgeDraft.data?.total || 0,
      archived: knowledgeArchived.data?.total || 0
    })
    latestArticle.value = knowledgeAll.data?.list?.[0] || null

    shopQueue.value = pendingShops.data?.list || []
    commentQueue.value = comments.data?.list || []
    recentMessages.value = messages.data?.list || []
    if (failedCount === results.length) {
      ui.toast('运营数据读取失败，请稍后刷新', 'error')
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page overview-page">
    <header class="overview-title">
      <h1>运营总览</h1>
    </header>

    <div v-if="partialError" class="overview-error" role="status">
      部分运营数据暂时不可用，页面中的其余数据仍可正常使用。
    </div>

    <section class="overview-pending">
      <div class="overview-pending-head">
        <h2>待审核内容</h2>
        <span>点击进入对应审核队列</span>
      </div>
      <div class="overview-pending-grid">
        <button class="overview-pending-item" type="button" @click="open('/admin/merchants?tab=pending')">
          <div>
            <span>待审核店铺</span>
            <b>{{ displayMetric('pendingShops') }}</b>
          </div>
          <span class="overview-pending-go">去审核 <ArrowRight :size="14" /></span>
        </button>
        <button class="overview-pending-item" type="button" @click="open('/admin/community?tab=pendingPosts')">
          <div>
            <span>待审核帖子</span>
            <b>{{ available.moderation ? number(moderation.posts) : '—' }}</b>
          </div>
          <span class="overview-pending-go">去审核 <ArrowRight :size="14" /></span>
        </button>
        <button class="overview-pending-item" type="button" @click="open('/admin/community?tab=comments')">
          <div>
            <span>待审核评论</span>
            <b>{{ available.moderation ? number(moderation.comments) : '—' }}</b>
          </div>
          <span class="overview-pending-go">去审核 <ArrowRight :size="14" /></span>
        </button>
      </div>
    </section>

    <div class="overview-stat-grid">
      <button class="overview-stat" type="button" @click="open('/admin/users')">
        <span class="overview-stat-icon"><UsersRound :size="19" /></span>
        <div><small>普通用户</small><b>{{ displayMetric('users') }}</b></div>
      </button>
      <button class="overview-stat" type="button" @click="open('/admin/merchants')">
        <span class="overview-stat-icon coral"><Store :size="19" /></span>
        <div><small>商家</small><b>{{ displayMetric('businesses') }}</b></div>
      </button>
      <button class="overview-stat" type="button" @click="open('/admin/merchants?tab=mall')">
        <span class="overview-stat-icon sage"><Warehouse :size="19" /></span>
        <div><small>店铺</small><b>{{ displayMetric('shops') }}</b></div>
      </button>
      <button class="overview-stat" type="button" @click="open('/admin/community')">
        <span class="overview-stat-icon"><MessageCircle :size="19" /></span>
        <div><small>帖子</small><b>{{ displayMetric('posts') }}</b></div>
      </button>
    </div>

    <div class="overview-content-grid">
      <section class="admin-panel overview-queue">
        <div class="admin-panel-head">
          <div>
            <h2>待办事项</h2>
            <p>按审核风险与处理时效排列。</p>
          </div>
          <button class="admin-panel-link" type="button" @click="open('/admin/community')">
            全部待办 <ArrowRight :size="14" />
          </button>
        </div>

        <div v-if="loading" class="loading-panel">
          <span class="spinner"></span>正在读取待办
        </div>
        <div v-else-if="!overviewReady" class="admin-empty-row">
          <p>待办数据暂时不可用</p>
        </div>
        <div v-else-if="!priorityRows.length" class="overview-queue-list">
          <div class="admin-empty-row">
            <div>
              <CheckCircle2 :size="28" />
              <p>暂无待审核事项</p>
            </div>
          </div>
        </div>
        <div v-else class="overview-queue-list">
          <article v-for="item in priorityRows" :key="item.key" class="overview-queue-row">
            <span class="overview-queue-icon" :class="item.tone">
              <component :is="item.icon" :size="17" />
            </span>
            <div class="overview-queue-copy">
              <b>{{ item.title }}</b>
              <span>{{ item.detail }}</span>
            </div>
            <button class="admin-row-btn" type="button" @click="open(item.path)">
              {{ item.action }}
            </button>
          </article>
        </div>
      </section>

      <div class="overview-side-stack">
        <section class="admin-panel">
          <div class="admin-panel-head">
            <div>
              <h2>养护知识库</h2>
              <p>{{ available.knowledge ? `共 ${number(knowledge.total)} 条知识条目 · 已发布 ${publishedRate}%` : '知识库数据暂时不可用。' }}</p>
            </div>
            <button class="admin-panel-link" type="button" @click="open('/admin/knowledge')">
              管理 <ArrowRight :size="14" />
            </button>
          </div>

          <div class="overview-distribution">
            <div v-for="item in knowledgeRows" :key="item.key" class="overview-dist-row">
              <span>{{ item.label }}</span>
              <div class="overview-dist-track"><i :class="item.key" :style="{ width: available.knowledge ? item.width : '0%' }"></i></div>
              <b>{{ available.knowledge ? item.value : '—' }}</b>
            </div>
          </div>

          <div v-if="latestArticle" class="overview-knowledge-latest">
            <span>最近更新</span>
            <b>{{ latestArticle.title }}</b>
            <small>{{ relativeTime(latestArticle.updatedAt) }}</small>
          </div>
        </section>

        <section class="admin-panel">
          <div class="admin-panel-head">
            <div>
              <h2>最近通知</h2>
              <p>{{ available.messages ? `共 ${number(metrics.notifications)} 条发布记录。` : '发布记录暂时不可用。' }}</p>
            </div>
            <button class="admin-panel-link" type="button" @click="open('/admin/messages')">
              查看 <ArrowRight :size="14" />
            </button>
          </div>
          <div v-if="recentMessages.length" class="overview-notice-list">
            <article v-for="message in recentMessages.slice(0, 3)" :key="message.id" class="overview-notice">
              <span class="overview-notice-icon"><BellRing :size="14" /></span>
              <div>
                <b>{{ message.title || '系统通知' }}</b>
                <small>{{ relativeTime(message.createdAt) }} · {{ message.targetRole || '指定账号' }}</small>
              </div>
            </article>
          </div>
          <div v-else class="admin-empty-row">
            <p>还没有发布记录</p>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overview-title {
  margin-bottom: 18px;
}

.overview-title h1 {
  margin: 0;
  font-size: var(--font-size-5xl);
  line-height: 1.15;
}

.overview-pending {
  margin-bottom: 14px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}

.overview-pending-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.overview-pending-head h2 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.overview-pending-head span {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.overview-pending-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.overview-pending-item {
  padding: 13px 15px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-ivory);
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: var(--transition-base);
}

.overview-pending-item:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-warm);
  transform: translateY(-1px);
}

.overview-pending-item span {
  display: block;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.overview-pending-item b {
  display: block;
  margin-top: 4px;
  font-family: var(--font-family-display);
  font-size: var(--font-size-2xl);
}

.overview-pending-go {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--color-primary-strong);
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
}

.overview-pending-item:hover .overview-pending-go {
  text-decoration: underline;
}

.overview-stat {
  appearance: none;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
  transition: var(--transition-base);
}

.overview-stat:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.spinning {
  animation: spin 0.8s linear infinite;
}

.admin-empty-row svg {
  color: var(--color-success);
  margin: 0 auto 8px;
}

.admin-empty-row p {
  margin: 0;
}

@media (max-width: 900px) {
  .overview-pending-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .overview-stat:hover {
    transform: none;
  }
}
</style>
