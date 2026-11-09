<script setup>
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps({
  page: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 }
})
const emit = defineEmits(['change'])

function go(next) {
  if (next >= 1 && next <= props.totalPages && next !== props.page) emit('change', next)
}
</script>

<template>
  <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
    <button type="button" :disabled="page <= 1" @click="go(page - 1)">
      <ChevronLeft :size="17" /> 上一页
    </button>
    <span>{{ page }} / {{ totalPages }}</span>
    <button type="button" :disabled="page >= totalPages" @click="go(page + 1)">
      下一页 <ChevronRight :size="17" />
    </button>
  </nav>
</template>
