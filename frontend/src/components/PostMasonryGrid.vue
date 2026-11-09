<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PostTile from './PostTile.vue'

const props = defineProps({
  posts: {
    type: Array,
    default: () => []
  },
  likedIds: {
    type: Object,
    default: () => new Set()
  },
  displayMode: {
    type: String,
    default: 'community',
    validator: (value) => ['community', 'profile'].includes(value)
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

const gridElement = ref(null)
const columnCount = ref(4)
let resizeObserver = null

const columns = computed(() => distributePosts(
  props.posts,
  columnCount.value,
  props.displayMode
))

function postCardMinHeight(post) {
  if (props.displayMode === 'profile') return '300px'
  return post.image ? '430px' : '300px'
}

function estimatePostHeight(post, displayMode) {
  if (displayMode === 'profile') {
    return 300
  }
  return post.image ? 430 : 300
}

function distributePosts(items, count, displayMode) {
  const result = Array.from({ length: count }, () => [])
  const heights = Array.from({ length: count }, () => 0)
  items.forEach((post, index) => {
    const target = heights.indexOf(Math.min(...heights))
    result[target].push({ post, index })
    heights[target] += estimatePostHeight(post, displayMode) + 16
  })
  return result
}

function syncColumns(width) {
  let nextCount = 1
  if (width >= 1050) {
    nextCount = 4
  } else if (width >= 640) {
    nextCount = 2
  }
  if (columnCount.value !== nextCount) {
    columnCount.value = nextCount
  }
}

onMounted(() => {
  syncColumns(gridElement.value?.clientWidth || 0)
  resizeObserver = new ResizeObserver((entries) => {
    syncColumns(entries[0]?.contentRect.width || 0)
  })
  resizeObserver.observe(gridElement.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})
</script>

<template>
  <div
    ref="gridElement"
    class="post-masonry"
    :style="{ '--masonry-columns': columnCount }"
  >
    <div
      v-for="(column, columnIndex) in columns"
      :key="columnIndex"
      class="post-masonry-column"
    >
      <div
        v-for="{ post, index } in column"
        :key="post.id"
        class="post-masonry-item"
        :style="{ '--post-card-min-height': postCardMinHeight(post) }"
      >
        <PostTile
          :post="post"
          :liked="likedIds.has(post.id)"
          :show-image="displayMode !== 'profile'"
          @like="$emit('like', post)"
          @author="$emit('author', post)"
          :author-clickable="authorClickable"
          :show-status="showStatus"
          :interactive-like="interactiveLike"
        />
        <slot name="overlay" :post="post" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-masonry {
  width: 100%;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(var(--masonry-columns), minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.post-masonry-column {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 16px;
}

.post-masonry-item {
  position: relative;
  min-width: 0;
}

.post-masonry-item :deep(.app-card) {
  min-height: var(--post-card-min-height);
}
</style>
