<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUp } from 'lucide-vue-next'

const visible = ref(false)

function updateVisibility() {
  visible.value = window.scrollY > 480
}

function backToTop() {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({
    top: 0,
    behavior: reduceMotion ? 'auto' : 'smooth'
  })
}

onMounted(() => {
  updateVisibility()
  window.addEventListener('scroll', updateVisibility, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateVisibility)
})
</script>

<template>
  <Transition name="back-top">
    <button
      v-if="visible"
      class="back-to-top"
      type="button"
      aria-label="回到顶部"
      @click="backToTop"
    >
      <ArrowUp :size="20" />
    </button>
  </Transition>
</template>

<style scoped>
.back-to-top { position: fixed; right: 24px; bottom: 28px; z-index: 35; width: 48px; height: 48px; border: 1px solid var(--color-border-strong); border-radius: 50%; background: var(--color-text); color: var(--color-butter); display: grid; place-items: center; box-shadow: var(--shadow-md); }
.back-to-top:hover { background: var(--color-primary-strong); }
.back-top-enter-active, .back-top-leave-active { transition: opacity .18s ease, transform .18s ease; }
.back-top-enter-from, .back-top-leave-to { opacity: 0; transform: translateY(8px); }
@media (max-width: 680px) { .back-to-top { right: 14px; bottom: 18px; width: 44px; height: 44px; } }
</style>
