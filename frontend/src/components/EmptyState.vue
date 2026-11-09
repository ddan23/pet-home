<script setup>
import { PawPrint } from 'lucide-vue-next'

defineProps({
  title: { type: String, default: '这里还没有内容' },
  description: { type: String, default: '' }
})
</script>

<template>
  <div class="empty-state">
    <span class="empty-mark"><PawPrint :size="30" /></span>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
