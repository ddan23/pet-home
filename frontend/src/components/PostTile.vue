<script setup>
import { computed } from 'vue'
import { Heart, MessageCircle } from 'lucide-vue-next'
import { assetUrl } from '../services/api'
import { useAuthStore } from '../stores/auth'
import { relativeTime } from '../utils/format'

const auth = useAuthStore()

const props = defineProps({
  post: { type: Object, required: true },
  liked: Boolean,
  showImage: {
    type: Boolean,
    default: true
  },
  authorClickable: {
    type: Boolean,
    default: true
  },
  showStatus: {
    type: Boolean,
    default: false
  },
  interactiveLike: {
    type: Boolean,
    default: true
  }
})
defineEmits(['like', 'author'])

const statusMeta = computed(() => {
  const labels = {
    published: { label: '已发布', tone: 'sage' },
    pending: { label: '待审核', tone: 'butter' },
    rejected: { label: '审核未通过', tone: 'danger' },
    offline: { label: '已下架', tone: 'butter' }
  }
  return labels[props.post.displayStatus] || null
})
</script>

<template>
  <article class="app-card">
    <RouterLink :to="{ name: 'post-detail', params: { id: post.id } }" class="post-main">
      <div v-if="showImage && post.image" class="post-cover">
        <img :src="assetUrl(post.image)" :alt="post.title || '社区图片'" loading="lazy" decoding="async" />
      </div>
      <div class="post-content">
        <div class="post-top">
          <span class="tag">{{ post.category }}</span>
          <span v-if="showStatus && statusMeta" class="tag" :class="statusMeta.tone">
            {{ statusMeta.label }}
          </span>
          <span>{{ relativeTime(post.createdAt) }}</span>
        </div>
        <h3>{{ post.title || '分享一件小事' }}</h3>
        <p>{{ post.content }}</p>
        <div class="post-author">
          <span
            class="author-hit"
            :class="{ clickable: authorClickable && post.userId !== auth.user?.id }"
            :role="authorClickable && post.userId !== auth.user?.id ? 'button' : null"
            :tabindex="authorClickable && post.userId !== auth.user?.id ? 0 : null"
            :aria-label="authorClickable && post.userId !== auth.user?.id ? `查看 ${post.nickname || '社区用户'} 的主页` : null"
            @click.prevent.stop="authorClickable && post.userId !== auth.user?.id && $emit('author', post)"
            @keydown.enter.prevent.stop="authorClickable && post.userId !== auth.user?.id && $emit('author', post)"
          >
            <img v-if="post.avatar" :src="assetUrl(post.avatar)" alt="" />
            <span v-else class="author-fallback">{{ post.nickname?.slice(0, 1) || '宠' }}</span>
          </span>
          <b class="author-hit" :class="{ clickable: authorClickable && post.userId !== auth.user?.id }" @click.prevent.stop="authorClickable && post.userId !== auth.user?.id && $emit('author', post)">{{ post.nickname || '社区用户' }}</b>
        </div>
      </div>
    </RouterLink>
    <div class="post-actions">
      <button
        v-if="interactiveLike"
        type="button"
        :class="{ active: liked }"
        @click.stop="$emit('like', post)"
      >
        <Heart :size="16" :fill="liked ? 'currentColor' : 'none'" />{{ post.likes || 0 }}
      </button>
      <span v-else class="readonly-like">
        <Heart :size="16" />{{ post.likes || 0 }}
      </span>
      <RouterLink :to="{ name: 'post-detail', params: { id: post.id } }">
        <MessageCircle :size="16" />{{ post.comments || 0 }}
      </RouterLink>
    </div>
  </article>
</template>

<style scoped>
.app-card { display: flex; flex-direction: column; }
.post-main { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.post-cover { flex: 0 0 190px; height: 190px; overflow: hidden; border-radius: var(--radius-md); background: var(--color-surface-warm); }
.post-cover img { width: 100%; height: 100%; object-fit: cover; transition: transform .35s ease; }
.app-card:hover .post-cover img { transform: scale(1.035); }
.post-content { flex: 1; min-height: 0; padding: 14px 0 12px; display: flex; flex-direction: column; }
.post-top { display: flex; justify-content: space-between; align-items: center; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.post-content h3 { margin: 8px 0 6px; font-size: var(--font-size-xl); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.post-content > p { margin: 0; color: var(--color-text-secondary); font-size: var(--font-size-sm); line-height: 1.65; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; height: calc(1.65em * 3); }
.post-author { display: flex; align-items: center; gap: 7px; margin-top: auto; padding-top: 12px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.post-author img, .post-author .author-fallback { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.post-author .author-hit { display: inline-flex; align-items: center; transition: color 0.16s ease; }
.post-author .author-hit.clickable { cursor: pointer; }
.post-author b.author-hit.clickable:hover, .post-author .author-hit.clickable:hover { color: var(--color-primary); }
.post-author .author-hit.clickable:hover .author-fallback { box-shadow: 0 0 0 2px var(--color-primary-soft); }
.post-actions { border-top: 1px solid var(--color-border); padding: 10px 0 0; display: flex; gap: 18px; }
.post-actions button, .post-actions a, .readonly-like { border: 0; background: transparent; padding: 5px 0; display: inline-flex; align-items: center; gap: 5px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.post-actions button.active { color: var(--color-primary-strong); }
</style>
