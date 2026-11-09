<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Heart,
  MessageCircle,
  Send,
  Trash2,
  UserPlus,
  UserCheck
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { parseJson, relativeTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import LoadingPanel from '../../components/LoadingPanel.vue'
import UserProfileDrawer from '../../components/UserProfileDrawer.vue'
import EmptyState from '../../components/EmptyState.vue'
import PageBackButton from '../../components/PageBackButton.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { isUser, requireUser } = useUserAccess()
const post = ref(null)
const comments = ref([])
const loading = ref(true)
const commentText = ref('')
const sending = ref(false)
const liked = ref(false)
const following = ref(false)
const commentMaxLength = 500
const COMMENT_PAGE_SIZE = 20
const commentPage = ref(1)
const commentTotalPages = ref(1)
const commentTotal = ref(0)
const commentLoadingMore = ref(false)
let commentRequestSequence = 0

const isOwner = computed(() => isUser.value && auth.user?.id === post.value?.userId)
const profileTarget = ref(null)

// 只有点击“他人”头像/名字才打开对方主页；点自己的不处理
function openProfile(target) {
  if (!target?.userId) return
  if (Number(target.userId) === Number(auth.user?.id)) return
  profileTarget.value = target
}

function handleFollowChange(change) {
  if (Number(change?.userId) === Number(post.value?.userId)) {
    following.value = Boolean(change.following)
  }
}


async function load() {
  loading.value = true
  try {
    const postResult = await api.get(`/posts/${route.params.id}`)
    post.value = postResult.data
    await loadComments(true)
    if (isUser.value) {
      const [likesResult, followResult] = await Promise.allSettled([
        api.get('/user/likedIds'),
        api.get(`/user/follow/${post.value.userId}`)
      ])
      liked.value = likesResult.status === 'fulfilled'
        && parseJson(likesResult.value.data?.likedIds, []).includes(post.value.id)
      following.value = followResult.status === 'fulfilled'
        && Boolean(followResult.value.data?.following)
    }
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loadComments(reset = false) {
  const requestSequence = ++commentRequestSequence
  const targetPage = reset ? 1 : commentPage.value + 1
  try {
    const result = await api.get(`/posts/${route.params.id}/comments${queryString({
      page: targetPage,
      pageSize: COMMENT_PAGE_SIZE
    })}`)
    if (requestSequence !== commentRequestSequence) return
    const list = result.data?.list || []
    if (reset) {
      comments.value = list
    } else {
      const existingIds = new Set(comments.value.map((comment) => Number(comment.id)))
      comments.value.push(...list.filter((comment) => !existingIds.has(Number(comment.id))))
    }
    commentPage.value = result.data?.page || targetPage
    commentTotalPages.value = result.data?.totalPages || 1
    commentTotal.value = result.data?.total || comments.value.length
    if (post.value) post.value.comments = commentTotal.value
  } catch (error) {
    if (requestSequence === commentRequestSequence) {
      ui.toast(error.message || '读取评论失败', 'error')
    }
  }
}

async function loadMoreComments() {
  if (commentLoadingMore.value || commentPage.value >= commentTotalPages.value) return
  commentLoadingMore.value = true
  try {
    await loadComments()
  } finally {
    commentLoadingMore.value = false
  }
}

function onCommentsScroll(event) {
  const element = event.target
  if (element.scrollTop + element.clientHeight >= element.scrollHeight - 60) {
    loadMoreComments()
  }
}

async function toggleLike() {
  if (!await requireUser({ action: '点赞帖子' })) return
  try {
    const result = await api.post(`/posts/${post.value.id}/like`)
    liked.value = result.data.liked
    post.value.likes = result.data.likes
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function toggleFollow() {
  if (!await requireUser({ action: '关注用户' })) return
  try {
    const result = await api.post(`/user/follow/${post.value.userId}`)
    following.value = result.data.following
    ui.toast(result.msg, 'success')
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function addComment() {
  if (!await requireUser({ action: '发表评论' })) return
  if (!commentText.value.trim() || sending.value) return
  sending.value = true
  try {
    const result = await api.post(`/posts/${post.value.id}/comments`, {
      content: commentText.value.trim()
    })
    ui.toast(result.msg || '评论成功', 'success')
    commentText.value = ''
    await loadComments(true)
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    sending.value = false
  }
}

async function deleteComment(comment) {
  const confirmed = await ui.confirm({ message: '确定删除这条评论吗？', danger: true })
  if (!confirmed) return
  try {
    await api.delete(`/posts/${post.value.id}/comments/${comment.id}`)
    ui.toast('评论已删除', 'success')
    if (comments.value.length === 1 && commentPage.value > 1) {
      commentPage.value -= 1
    }
    await loadComments(true)
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function deletePost() {
  const confirmed = await ui.confirm({ message: '删除后帖子将不再公开展示，确定继续吗？', danger: true })
  if (!confirmed) return
  try {
    await api.delete(`/posts/${post.value.id}`)
    ui.toast('帖子已删除', 'success')
    router.replace('/community')
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

onMounted(load)
watch(isUser, (hasUserAccess) => {
  if (hasUserAccess && post.value) {
    load()
    return
  }
  liked.value = false
  following.value = false
})
</script>

<template>
  <div class="page-layout">
    <div class="detail-back">
      <PageBackButton fallback-path="/community" aria-label="返回社区" />
    </div>
    <LoadingPanel v-if="loading" label="正在打开帖子" />
    <EmptyState v-else-if="!post" title="帖子不存在" description="它可能已经被删除。" />
    <template v-else>
      <article class="post-article panel">
        <header class="article-head">
          <div class="author-line">
            <span
              class="author-avatar"
              :class="{ clickable: post.userId !== auth.user?.id }"
              :role="post.userId !== auth.user?.id ? 'button' : null"
              :tabindex="post.userId !== auth.user?.id ? 0 : null"
              :aria-label="post.userId !== auth.user?.id ? `查看 ${post.nickname || '社区用户'} 的主页` : null"
              @click="openProfile({ userId: post.userId, nickname: post.nickname || '社区用户', avatar: post.avatar || '' })"
              @keydown.enter="openProfile({ userId: post.userId, nickname: post.nickname || '社区用户', avatar: post.avatar || '' })"
            >
              <img v-if="post.avatar" :src="assetUrl(post.avatar)" alt="" />
              <span v-else class="author-fallback">{{ post.nickname?.slice(0, 1) || '宠' }}</span>
            </span>
            <div class="author-copy">
              <b
                :class="{ clickable: post.userId !== auth.user?.id }"
                @click="openProfile({ userId: post.userId, nickname: post.nickname || '社区用户', avatar: post.avatar || '' })"
              >{{ post.nickname || '社区用户' }}</b>
            </div>
            <button v-if="!isOwner" class="follow-btn" type="button" :class="{ active: following }" @click="toggleFollow">
              <UserCheck v-if="following" :size="15" /><UserPlus v-else :size="15" />
              {{ following ? '已关注' : '关注' }}
            </button>
            <button
              v-if="!isOwner && isUser"
              class="btn btn-ghost btn-sm"
              type="button"
              @click="router.push({ path: `/messages/${post.userId}`, query: { category: 'community', type: 'private_letter', postId: post.id } })"
            >
              <MessageCircle :size="15" />私信
            </button>
            <button v-if="isOwner" class="btn btn-danger btn-sm" type="button" @click="deletePost"><Trash2 :size="15" />删除</button>
          </div>
          <h1>{{ post.title || '分享一件小事' }}</h1>
        </header>
        <img v-if="post.image" class="article-image" :src="assetUrl(post.image)" :alt="post.title || '帖子图片'" />
        <div class="article-body">{{ post.content }}</div>
        <div class="article-taxonomy">
          <span class="tag">{{ post.category }}</span>
          <span>{{ relativeTime(post.createdAt) }}</span>
        </div>
        <footer class="article-actions">
          <button type="button" :class="{ liked }" @click="toggleLike">
            <Heart :size="18" :fill="liked ? 'currentColor' : 'none'" />{{ liked ? '已点赞' : '点赞' }} {{ post.likes || 0 }}
          </button>
          <span><MessageCircle :size="18" />{{ post.comments || 0 }} 条评论</span>
        </footer>
      </article>

      <section id="comments" class="comments panel">
        <div class="panel-title"><h2>评论</h2><span>{{ commentTotal }} 条</span></div>
        <div class="comment-compose">
          <div class="comment-compose-field">
            <textarea
              v-model="commentText"
              class="textarea"
              placeholder="写下你的回应…"
              :maxlength="commentMaxLength"
              aria-describedby="comment-length"
            />
            <small id="comment-length" class="comment-length">{{ commentText.length }}/{{ commentMaxLength }}</small>
          </div>
          <button class="btn btn-primary" type="button" :disabled="sending || !commentText.trim()" @click="addComment">
            <Send :size="16" />{{ sending ? '发送中' : '发送评论' }}
          </button>
        </div>
        <div v-if="comments.length" class="comment-list-scroll" @scroll.passive="onCommentsScroll">
          <div class="comment-list">
            <article v-for="comment in comments" :key="comment.id" class="comment-item">
              <span
                class="comment-avatar-hit"
                :class="{ clickable: comment.senderId !== auth.user?.id }"
                :role="comment.senderId !== auth.user?.id ? 'button' : null"
                :tabindex="comment.senderId !== auth.user?.id ? 0 : null"
                :aria-label="comment.senderId !== auth.user?.id ? `查看 ${comment.senderName || '社区用户'} 的主页` : null"
                @click="openProfile({ userId: comment.senderId, nickname: comment.senderName || '社区用户', avatar: comment.senderAvatar || '' })"
              >
                <img v-if="comment.senderAvatar" :src="assetUrl(comment.senderAvatar)" alt="" />
                <span v-else class="comment-avatar">{{ comment.senderName?.slice(0, 1) || '宠' }}</span>
              </span>
              <div class="comment-main">
                <div class="comment-meta">
                  <b
                    :class="{ clickable: comment.senderId !== auth.user?.id }"
                    @click="openProfile({ userId: comment.senderId, nickname: comment.senderName || '社区用户', avatar: comment.senderAvatar || '' })"
                  >{{ comment.senderName || '社区用户' }}</b>
                  <span>{{ relativeTime(comment.createdAt) }}</span>
                </div>
                <p>{{ comment.content }}</p>
              </div>
              <div class="comment-actions">
                <button
                  v-if="auth.user?.id === comment.senderId"
                  class="comment-delete"
                  type="button"
                  aria-label="删除评论"
                  @click="deleteComment(comment)"
                >
                  <Trash2 :size="15" />
                </button>
              </div>
            </article>
          </div>
          <p v-if="commentLoadingMore" class="comment-load-status">正在加载更多…</p>
          <p v-else-if="commentPage >= commentTotalPages" class="comment-load-status">没有更多评论了</p>
        </div>
        <EmptyState v-else title="还没有评论" description="成为第一个回应的人。" />
      </section>

      <UserProfileDrawer
        :open="Boolean(profileTarget)"
        :user-id="profileTarget?.userId"
        :nickname="profileTarget?.nickname || 'TA'"
        :avatar="profileTarget?.avatar || ''"
        @close="profileTarget = null"
        @follow-change="handleFollowChange"
      />
    </template>
  </div>
</template>

<style scoped>
.page-layout { max-width: 820px; }
.detail-back { margin-bottom: 12px; }
.post-article { overflow: hidden; }
.article-head { padding: 22px clamp(20px, 3vw, 30px) 20px; }
.tag-row { display: flex; align-items: center; gap: 10px; color: var(--color-text-muted); font-size: 0.76rem; }
.article-head h1 {
  font-size: clamp(1.4rem, 2.2vw, 1.7rem);
  line-height: 1.4;
  margin: 14px 0 2px;
  letter-spacing: -0.01em;
}
.author-copy { display: grid; gap: 3px; min-width: 0; flex: 1; }
.author-avatar.clickable, .author-copy b.clickable { cursor: pointer; }
.author-copy b.clickable { transition: color 0.16s ease; }
.author-copy b.clickable:hover { color: var(--color-primary); }
.author-avatar.clickable:hover .author-fallback { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.comment-avatar-hit.clickable { cursor: pointer; }
.comment-avatar-hit.clickable:hover img, .comment-avatar-hit.clickable:hover .comment-avatar { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.comment-meta b.clickable { cursor: pointer; transition: color 0.16s ease; }
.comment-meta b.clickable:hover { color: var(--color-primary); }
.author-copy b { font-size: var(--font-size-base); }
.article-taxonomy {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 0 clamp(20px, 3vw, 30px) 14px;
  color: var(--color-text-muted);
  font-size: 0.74rem;
}
.author-line { display: flex; align-items: center; gap: 11px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.author-line img, .author-line .author-avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-weight: var(--font-weight-bold); }
.author-line b, .author-line small { display: block; }
.author-line b { color: var(--color-text); font-size: var(--font-size-sm); }
.follow-btn { margin-left: auto; border: 1px solid var(--color-border); background: var(--color-white); border-radius: 999px; padding: 6px 11px; display: flex; gap: 5px; align-items: center; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.follow-btn.active { background: var(--color-surface-accent); color: var(--color-success); }
.article-image { width: 100%; max-height: 400px; object-fit: cover; background: var(--color-surface-warm); }
.article-body {
  padding: 20px clamp(20px, 3vw, 30px) 24px;
  white-space: pre-wrap;
  font-size: 0.92rem;
  line-height: 1.85;
  color: var(--color-text-secondary);
}
.article-actions { display: flex; justify-content: center; gap: 24px; padding: 12px; border-top: 1px solid var(--color-border); }
.article-actions button, .article-actions span { border: 0; background: transparent; display: flex; align-items: center; gap: 6px; color: var(--color-text-muted); }
.article-actions button.liked { color: var(--color-primary-strong); }
.comments { margin-top: 22px; }
.comment-compose { padding: 16px 20px; display: flex; gap: 10px; align-items: flex-end; border-bottom: 1px solid var(--color-border); }
.comment-compose-field { flex: 1; min-width: 0; display: grid; gap: 5px; }
.comment-compose .textarea { min-height: 74px; width: 100%; }
.comment-length { justify-self: end; color: var(--color-text-muted); font-size: 0.7rem; font-variant-numeric: tabular-nums; }
.comment-list-scroll { max-height: min(55dvh, 560px); overflow-y: auto; overscroll-behavior: contain; }
.comment-list { padding: 4px 20px 18px; }
.comment-load-status { margin: 0; padding: 10px 20px 16px; color: var(--color-text-muted); text-align: center; font-size: var(--font-size-sm); }
.comment-item { display: flex; gap: 11px; padding: 14px 0; border-bottom: 1px dashed var(--color-border); }
.comment-item:last-child { border-bottom: 0; }
.comment-item img, .comment-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; display: grid; place-items: center; background: var(--color-surface-accent); color: var(--color-success); flex-shrink: 0; }
.comment-main { flex: 1; min-width: 0; }
.comment-meta { display: flex; align-items: center; gap: 10px; font-size: var(--font-size-sm); }
.comment-meta span { color: var(--color-text-muted); }
.comment-main p { margin: 5px 0 0; color: var(--color-text-secondary); white-space: pre-wrap; }
.comment-actions { display: flex; align-items: center; }
.comment-delete { border: 0; background: transparent; color: var(--color-text-muted); padding: 5px; }
@media (max-width: 620px) {
  .comment-compose { flex-direction: column; align-items: stretch; }
  .author-copy { display: grid; gap: 3px; min-width: 0; flex: 1; }
.author-avatar.clickable, .author-copy b.clickable { cursor: pointer; }
.author-copy b.clickable { transition: color 0.16s ease; }
.author-copy b.clickable:hover { color: var(--color-primary); }
.author-avatar.clickable:hover .author-fallback { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.comment-avatar-hit.clickable { cursor: pointer; }
.comment-avatar-hit.clickable:hover img, .comment-avatar-hit.clickable:hover .comment-avatar { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.comment-meta b.clickable { cursor: pointer; transition: color 0.16s ease; }
.comment-meta b.clickable:hover { color: var(--color-primary); }
.author-copy b { font-size: var(--font-size-base); }
.article-taxonomy {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin: 0 clamp(20px, 3vw, 30px) 14px;
  color: var(--color-text-muted);
  font-size: 0.74rem;
}
.author-line { flex-wrap: wrap; }
}
</style>
