<script setup>
defineProps({ label: { type: String, default: '正在加载' } })
</script>

<template>
  <div class="loading-panel" aria-live="polite">
    <span class="spinner" />
    <span>{{ label }}</span>
  </div>
</template>
