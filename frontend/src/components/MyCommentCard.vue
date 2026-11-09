<script setup>
import { computed } from 'vue'
import { ExternalLink, Trash2 } from 'lucide-vue-next'
import { formatDateTime } from '../utils/format'

const props = defineProps({
  comment: {
    type: Object,
    required: true
  }
})

defineEmits(['delete'])

const statusMeta = computed(() => {
  const statusMap = {
    normal: { label: '已发布', className: 'sage' },
    pending: { label: '审核中', className: 'butter' },
    blocked: { label: '已拒绝', className: 'danger' }
  }
  return statusMap[props.comment.status] || {
    label: '未知状态',
    className: ''
  }
})
</script>

<template>
  <article class="app-card my-comment-card">
    <header class="comment-card-head">
      <span class="tag" :class="statusMeta.className">
        {{ statusMeta.label }}
      </span>
      <time :datetime="comment.createdAt">
        {{ formatDateTime(comment.createdAt) }}
      </time>
    </header>

    <p class="comment-context">
      我在帖子
      <RouterLink
        class="comment-post-link"
        :to="{ name: 'post-detail', params: { id: comment.postId } }"
      >
        《{{ comment.postTitle || '无标题帖子' }}》
      </RouterLink>
      中评论：
    </p>

    <blockquote class="comment-content">
      {{ comment.content }}
    </blockquote>

    <footer class="comment-card-foot">
      <span class="comment-author">
        原帖作者：{{ comment.postAuthorName || '社区用户' }}
      </span>
      <div class="comment-card-actions">
        <button
          class="btn btn-ghost btn-sm comment-delete-button"
          type="button"
          @click="$emit('delete', comment)"
        >
          <Trash2 :size="14" />
          删除评论
        </button>
        <RouterLink
          class="btn btn-primary btn-sm"
          :to="{
            name: 'post-detail',
            params: { id: comment.postId },
            hash: '#comments'
          }"
        >
          查看原帖
          <ExternalLink :size="14" />
        </RouterLink>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.my-comment-card {
  display: grid;
  gap: 14px;
}
.comment-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.comment-card-head time {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.comment-context {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}
.comment-post-link {
  color: var(--color-primary-strong);
  font-weight: var(--font-weight-extra-bold);
}
.comment-post-link:hover {
  text-decoration: underline;
}
.comment-content {
  margin: 0;
  padding: 14px 16px;
  border-left: 4px solid var(--color-primary);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--color-surface-warm);
  color: var(--color-text);
  font-size: var(--font-size-base);
  line-height: 1.8;
}
.comment-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.comment-author {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}
.comment-card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.comment-delete-button {
  color: var(--color-danger);
}
@media (max-width: 680px) {
  .comment-card-foot {
    align-items: stretch;
    flex-direction: column;
  }
  .comment-card-actions {
    justify-content: stretch;
  }
  .comment-card-actions > * {
    flex: 1;
  }
}
</style>
