<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Heart, MessageCircle, UserCheck, UserPlus } from 'lucide-vue-next'
import { api, assetUrl } from '../services/api'
import { relativeTime } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { useUserAccess } from '../composables/useUserAccess'
import { useUiStore } from '../stores/ui'
import AppDrawer from './AppDrawer.vue'
import LoadingPanel from './LoadingPanel.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  userId: { type: [Number, String], default: null },
  nickname: { type: String, default: 'TA' },
  avatar: { type: String, default: '' }
})
const emit = defineEmits(['close', 'follow-change'])

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { requireUser } = useUserAccess()

const posts = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const following = ref(false)
const followBusy = ref(false)
const myAvatar = ref('')
const postPage = ref(0)
const postTotalPages = ref(0)
const postTotal = ref(0)
const postPageSize = 20
let profileRequestId = 0
const isSelf = computed(() => Boolean(props.userId) && Number(auth.user?.id) === Number(props.userId))
const hasMorePosts = computed(() => postPage.value < postTotalPages.value)

async function loadProfile() {
  if (!props.userId) return
  const requestId = ++profileRequestId
  loading.value = true
  loadingMore.value = false
  posts.value = []
  postPage.value = 0
  postTotalPages.value = 0
  postTotal.value = 0
  myAvatar.value = props.avatar || ''
  try {
    const postResult = await api.get(
      `/posts?userId=${props.userId}&page=1&pageSize=${postPageSize}`
    )
    if (requestId !== profileRequestId) return
    posts.value = postResult.data?.list || []
    postPage.value = postResult.data?.page || 1
    postTotalPages.value = postResult.data?.totalPages || 0
    postTotal.value = postResult.data?.total ?? posts.value.length
    myAvatar.value = posts.value[0]?.avatar || props.avatar || ''
  } catch (error) {
    if (requestId === profileRequestId) {
      posts.value = []
      postTotal.value = 0
    }
  } finally {
    if (requestId === profileRequestId) loading.value = false
  }
  if (auth.isAuthenticated && !isSelf.value) {
    try {
      const followResult = await api.get(`/user/follow/${props.userId}`)
      if (requestId === profileRequestId) {
        following.value = Boolean(followResult.data?.following)
      }
    } catch (error) {
      if (requestId === profileRequestId) following.value = false
    }
  }
}

async function loadMorePosts() {
  if (!props.userId || loading.value || loadingMore.value || !hasMorePosts.value) return
  const requestId = profileRequestId
  const nextPage = postPage.value + 1
  loadingMore.value = true
  try {
    const result = await api.get(
      `/posts?userId=${props.userId}&page=${nextPage}&pageSize=${postPageSize}`
    )
    if (requestId !== profileRequestId) return
    const existingIds = new Set(posts.value.map((post) => post.id))
    posts.value.push(...(result.data?.list || []).filter((post) => !existingIds.has(post.id)))
    postPage.value = result.data?.page || nextPage
    postTotalPages.value = result.data?.totalPages || postTotalPages.value
    postTotal.value = result.data?.total ?? postTotal.value
  } catch (error) {
    ui.toast(error.message || '读取更多帖子失败', 'error')
  } finally {
    if (requestId === profileRequestId) loadingMore.value = false
  }
}

async function toggleFollow() {
  if (!await requireUser({ action: '关注用户' })) return
  followBusy.value = true
  try {
    const result = await api.post(`/user/follow/${props.userId}`)
    following.value = result.data.following
    emit('follow-change', {
      userId: props.userId,
      following: following.value
    })
    ui.toast(result.msg || (following.value ? '已关注' : '已取消关注'), 'success')
  } catch (error) {
    ui.toast(error.message || '关注失败', 'error')
  } finally {
    followBusy.value = false
  }
}

async function openChat() {
  if (!await requireUser({ action: '发私信' })) return
  emit('close')
  router.push({ path: `/messages/${props.userId}`, query: { category: 'community', type: 'private_letter' } })
}

function openPost(post) {
  emit('close')
  router.push(`/community/${post.id}`)
}

watch(() => [props.open, props.userId], ([open]) => {
  if (open) {
    following.value = false
    loadProfile()
  }
})
</script>

<template>
  <AppDrawer :open="open" :title="`${nickname} 的主页`" width="420px" @close="emit('close')">
    <div class="profile-head">
      <span class="profile-avatar">
        <img v-if="myAvatar" :src="assetUrl(myAvatar)" alt="" />
        <span v-else>{{ nickname.slice(0, 1) || '宠' }}</span>
      </span>
      <div class="profile-copy">
        <b>{{ nickname }}</b>
        <small>共 {{ postTotal }} 篇帖子</small>
      </div>
    </div>
    <div v-if="!isSelf" class="profile-actions">
      <button class="btn btn-ghost btn-sm" type="button" :disabled="followBusy" @click="toggleFollow">
        <UserCheck v-if="following" :size="15" /><UserPlus v-else :size="15" />
        {{ following ? '已关注' : '关注' }}
      </button>
      <button class="btn btn-primary btn-sm" type="button" @click="openChat">
        <MessageCircle :size="15" />发消息
      </button>
    </div>

    <h3 class="profile-section">TA 的帖子</h3>
    <LoadingPanel v-if="loading" label="正在加载" />
    <template v-else>
      <button
        v-for="post in posts"
        :key="post.id"
        class="post-row"
        type="button"
        @click="openPost(post)"
      >
        <img v-if="post.image" :src="assetUrl(post.image)" alt="" />
        <span v-else class="post-fallback">{{ (post.title || '帖')[0] }}</span>
        <span class="post-copy">
          <b>{{ post.title || '分享一件小事' }}</b>
          <small>{{ post.category || '日常' }} · {{ relativeTime(post.createdAt) }}</small>
        </span>
        <span class="post-stats">
          <span><Heart :size="13" />{{ post.likes || 0 }}</span>
          <span><MessageCircle :size="13" />{{ post.comments || 0 }}</span>
        </span>
      </button>
      <p v-if="!posts.length" class="profile-empty">TA 还没有发布过帖子。</p>
      <button
        v-if="hasMorePosts"
        class="btn btn-ghost profile-load-more"
        type="button"
        :disabled="loadingMore"
        @click="loadMorePosts"
      >
        {{ loadingMore ? '正在加载…' : '加载更多帖子' }}
      </button>
    </template>
  </AppDrawer>
</template>

<style scoped>
.profile-head { display: flex; align-items: center; gap: 12px; }
.profile-avatar { width: 58px; height: 58px; flex: 0 0 auto; border-radius: 50%; overflow: hidden; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-weight: var(--font-weight-extra-bold); font-size: 1.15rem; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-copy { min-width: 0; display: grid; gap: 3px; }
.profile-copy b { font-size: var(--font-size-base); }
.profile-copy small { color: var(--color-text-muted); font-size: 0.72rem; }
.profile-actions { margin-top: 12px; display: flex; gap: 8px; }
.profile-section { margin: 18px 0 10px; font-size: 0.8rem; color: var(--color-text-secondary); letter-spacing: 0.04em; }
.post-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-surface);
  text-align: left;
  cursor: pointer;
  transition: var(--transition-base);
}
.post-row:hover { border-color: var(--color-primary); background: var(--color-surface-warm); }
.post-row img, .post-fallback { width: 48px; height: 48px; flex: 0 0 auto; border-radius: 10px; object-fit: cover; }
.post-fallback { display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-weight: var(--font-weight-bold); }
.post-copy { flex: 1; min-width: 0; display: grid; gap: 2px; }
.post-copy b { font-size: var(--font-size-sm); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.post-copy small { color: var(--color-text-muted); font-size: 0.7rem; }
.post-stats { flex: 0 0 auto; display: grid; gap: 3px; justify-items: end; color: var(--color-text-muted); font-size: 0.66rem; }
.post-stats span { display: inline-flex; align-items: center; gap: 3px; }
.profile-empty { margin: 0; padding: 22px 0; text-align: center; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.profile-load-more { width: 100%; margin-top: 4px; }
</style>
