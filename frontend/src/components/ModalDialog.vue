<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({
  open: Boolean,
  title: { type: String, default: '' },
  width: { type: String, default: '560px' },
  bodyClass: { type: String, default: '' },
  panelClass: { type: String, default: '' },
  closeOnEscape: { type: Boolean, default: true },
  closeOnBackdrop: { type: Boolean, default: true },
  closeDisabled: { type: Boolean, default: false }
})
const emit = defineEmits(['close'])

const dialogRef = ref(null)
const titleId = `modal-title-${useId()}`
let previouslyFocusedElement = null
let previousBodyOverflow = ''

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

function close() {
  if (props.closeDisabled) return
  emit('close')
}

function restorePageInteraction() {
  if (typeof document === 'undefined') return
  document.body.style.overflow = previousBodyOverflow
  previouslyFocusedElement?.focus?.()
  previouslyFocusedElement = null
}

function focusDialog() {
  nextTick(() => dialogRef.value?.focus())
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    if (props.closeOnEscape) close()
    return
  }
  if (event.key !== 'Tab' || !dialogRef.value) return
  const focusableElements = [...dialogRef.value.querySelectorAll(focusableSelector)]
    .filter((element) => element.offsetParent !== null)
  if (focusableElements.length === 0) {
    event.preventDefault()
    dialogRef.value.focus()
    return
  }
  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]
  const focusOutsideDialog = !dialogRef.value.contains(document.activeElement)
  if (event.shiftKey && (document.activeElement === firstElement || focusOutsideDialog)) {
    event.preventDefault()
    lastElement.focus()
  } else if (!event.shiftKey && document.activeElement === lastElement) {
    event.preventDefault()
    firstElement.focus()
  }
}

watch(() => props.open, (open) => {
  if (typeof document === 'undefined') return
  if (open) {
    previouslyFocusedElement = document.activeElement
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    focusDialog()
  } else {
    restorePageInteraction()
  }
}, { immediate: true, flush: 'post' })

onBeforeUnmount(restorePageInteraction)
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="dialog-backdrop" @click.self="closeOnBackdrop && close()">
        <section
          ref="dialogRef"
          class="modal-card"
          :class="panelClass"
          :style="{ maxWidth: width }"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          tabindex="-1"
          @keydown="handleKeydown"
        >
          <header class="modal-head">
            <h2 :id="titleId">{{ title }}</h2>
            <button
              class="icon-btn"
              type="button"
              aria-label="关闭弹窗"
              :disabled="closeDisabled"
              @click="close"
            >
              <X :size="19" />
            </button>
          </header>
          <div class="modal-body" :class="bodyClass"><slot /></div>
          <footer v-if="$slots.footer" class="modal-foot"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
