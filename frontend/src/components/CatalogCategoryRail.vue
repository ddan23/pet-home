<script setup>
defineProps({
  title: {
    type: String,
    default: '分类'
  },
  categories: {
    type: Array,
    required: true
  },
  activeCode: {
    type: String,
    default: ''
  }
})

defineEmits(['select'])
</script>

<template>
  <aside class="catalog-rail">
    <header>
      <span>{{ title }}</span>
    </header>
    <nav :aria-label="title">
      <button
        v-for="category in categories"
        :key="category.code || 'all'"
        type="button"
        :class="{ active: activeCode === category.code }"
        @click="$emit('select', category.code)"
      >
        <span>{{ category.name }}</span>
      </button>
    </nav>
  </aside>
</template>

<style scoped>
.catalog-rail { position: relative; width: 100%; min-width: 0; align-self: start; overflow: hidden; padding: 15px; border: 1px solid var(--color-border); border-radius: 7px 24px 24px 7px; background: var(--color-surface); box-shadow: var(--box-shadow-sm); }
.catalog-rail > header { padding: 0 7px 12px; border-bottom: 1px solid var(--color-border); color: var(--color-text-muted); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.catalog-rail nav { width: 100%; min-width: 0; display: grid; gap: 4px; padding-top: 8px; }
.catalog-rail button { min-height: 42px; padding: 9px 10px; border: 0; border-radius: 10px; background: transparent; color: var(--color-text-secondary); display: flex; align-items: center; justify-content: space-between; gap: 8px; text-align: left; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); white-space: nowrap; }
.catalog-rail button:hover { background: var(--color-surface-warm); }
.catalog-rail button.active { background: var(--color-primary); color: var(--color-white); }
@media (max-width: 840px) {
  .catalog-rail { padding: 10px; border-radius: 8px 18px 18px 8px; }
  .catalog-rail > header { display: none; }
  .catalog-rail nav { display: flex; gap: 6px; overflow-x: auto; padding: 0; scrollbar-width: none; }
  .catalog-rail nav::-webkit-scrollbar { display: none; }
  .catalog-rail button { flex: 0 0 auto; min-height: 36px; padding: 7px 10px; border: 1px solid var(--color-border); border-radius: 999px; background: transparent; }
}
</style>
