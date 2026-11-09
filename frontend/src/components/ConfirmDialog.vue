<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { useUiStore } from '../stores/ui'

const ui = useUiStore()
const dialogRef = ref(null)
const cancelRef = ref(null)
const titleId = `confirm-title-${useId()}`
let previousBodyOverflow = ''
let previouslyFocusedElement = null

function closeAsCancel() {
  if (ui.confirmState) ui.settle(false)
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeAsCancel()
    return
  }
  if (event.key !== 'Tab' || !dialogRef.value) return
  const focusableElements = [...dialogRef.value.querySelectorAll(
    'button:not([disabled]), [href], input:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )]
  if (!focusableElements.length) return
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

function restorePageInteraction() {
  document.body.style.overflow = previousBodyOverflow
  previouslyFocusedElement?.focus?.()
  previouslyFocusedElement = null
}

watch(() => ui.confirmState, (state) => {
  if (state) {
    previouslyFocusedElement = document.activeElement
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    nextTick(() => cancelRef.value?.focus())
    return
  }
  restorePageInteraction()
})

onBeforeUnmount(restorePageInteraction)
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="ui.confirmState" class="dialog-backdrop confirm-backdrop" @click.self="ui.settle(false)">
        <div
          ref="dialogRef"
          class="confirm-card"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
          @keydown="handleKeydown"
        >
          <h2 :id="titleId">{{ ui.confirmState.title }}</h2>
          <p>{{ ui.confirmState.message }}</p>
          <div class="dialog-actions">
            <button
              ref="cancelRef"
              class="btn btn-ghost"
              type="button"
              @click="closeAsCancel"
            >
              {{ ui.confirmState.cancelText }}
            </button>
            <button
              class="btn"
              :class="ui.confirmState.danger ? 'btn-danger' : 'btn-primary'"
              type="button"
              @click="ui.settle(true)"
            >
              {{ ui.confirmState.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
