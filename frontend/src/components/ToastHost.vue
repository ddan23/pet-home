<script setup>
import { CheckCircle2, CircleAlert, Info, X } from 'lucide-vue-next'
import { useUiStore } from '../stores/ui'

const ui = useUiStore()
const icons = { success: CheckCircle2, warning: CircleAlert, error: CircleAlert, info: Info }
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" aria-live="polite">
      <TransitionGroup name="toast">
        <button
          v-for="item in ui.toasts"
          :key="item.id"
          class="toast-item"
          :class="`toast-${item.type}`"
          type="button"
          @click="ui.dismiss(item.id)"
        >
          <component :is="icons[item.type] || Info" :size="18" />
          <span>{{ item.message }}</span>
          <X :size="15" />
        </button>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
