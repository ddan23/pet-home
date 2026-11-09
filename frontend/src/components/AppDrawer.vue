<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '400px' }
})
const emit = defineEmits(['close'])
const drawerRef = ref(null)
const titleId = `drawer-title-${useId()}`
let previousBodyOverflow = ''
let previouslyFocusedElement = null

function handleKey(event) {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !drawerRef.value) return
  const focusableElements = [...drawerRef.value.querySelectorAll(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), '
      + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )].filter((element) => element.offsetParent !== null)
  if (!focusableElements.length) {
    event.preventDefault()
    drawerRef.value.focus()
    return
  }
  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]
  if (event.shiftKey && document.activeElement === firstElement) {
    event.preventDefault()
    lastElement.focus()
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault()
    firstElement.focus()
  }
}

watch(() => props.open, (open) => {
  if (open) {
    previouslyFocusedElement = document.activeElement
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', handleKey)
    nextTick(() => drawerRef.value?.focus())
  } else {
    document.body.style.overflow = previousBodyOverflow
    window.removeEventListener('keydown', handleKey)
    previouslyFocusedElement?.focus?.()
    previouslyFocusedElement = null
  }
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousBodyOverflow
  window.removeEventListener('keydown', handleKey)
  previouslyFocusedElement?.focus?.()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div
        v-if="open"
        ref="drawerRef"
        class="drawer-shell"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
      >
        <div class="drawer-mask" @click="emit('close')" />
        <aside class="drawer-panel" :style="{ maxWidth: width }">
          <header class="drawer-head">
            <b :id="titleId">{{ title }}</b>
            <button class="drawer-close" type="button" aria-label="关闭" @click="emit('close')">
              <X :size="17" />
            </button>
          </header>
          <div class="drawer-body">
            <slot />
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-shell { position: fixed; inset: 0; z-index: var(--z-modal, 1200); }
.drawer-mask { position: absolute; inset: 0; background: rgba(58, 42, 16, 0.32); backdrop-filter: blur(1px); }
.drawer-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: -18px 0 48px rgba(58, 42, 16, 0.14);
}
.drawer-head {
  flex: 0 0 auto;
  min-height: 56px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--color-border-soft);
  background: var(--color-surface-warm);
}
.drawer-head b { font-size: var(--font-size-base); }
.drawer-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--color-text-secondary);
  transition: var(--transition-base);
}
.drawer-close:hover { background: var(--color-danger-soft); color: var(--color-danger); }
.drawer-body { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; }

.drawer-enter-active, .drawer-leave-active { transition: opacity 0.22s ease; }
.drawer-enter-active .drawer-panel, .drawer-leave-active .drawer-panel { transition: transform 0.26s cubic-bezier(0.22, 1, 0.36, 1); }
.drawer-enter-from, .drawer-leave-to { opacity: 0; }
.drawer-enter-from .drawer-panel, .drawer-leave-to .drawer-panel { transform: translateX(100%); }
</style>
