<script setup>
import { ArrowLeft } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

defineOptions({
  name: 'PageBackButton'
})

const props = defineProps({
  fallbackPath: {
    type: String,
    required: true
  },
  ariaLabel: {
    type: String,
    required: true
  }
})

const router = useRouter()

function isSafeBackTarget(target) {
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//')) {
    return false
  }
  const path = target.split(/[?#]/, 1)[0]
  const loginLocation = router.resolve({ name: 'login' })
  return path !== loginLocation.path && path !== loginLocation.href
}

function handleBack() {
  const historyBack = window.history.state?.back
  if (isSafeBackTarget(historyBack)) {
    router.back()
    return
  }
  router.replace(props.fallbackPath)
}
</script>

<template>
  <button
    class="page-back-button"
    type="button"
    :aria-label="ariaLabel"
    @click="handleBack"
  >
    <ArrowLeft :size="16" aria-hidden="true" />
    返回
  </button>
</template>

<style scoped>
.page-back-button {
  min-height: 36px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extra-bold);
}

.page-back-button:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.page-back-button:focus-visible {
  outline: 3px solid var(--color-border-primary-halo);
  outline-offset: 2px;
}
</style>
