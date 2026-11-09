<script setup>
import { assetUrl } from '../services/api'
import { formatPrice } from '../utils/format'
import { lifeStageLabel, parseHealthTags, targetPetLabel } from '../utils/productPresentation'

defineProps({
  service: {
    type: Object,
    required: true
  }
})

</script>

<template>
  <RouterLink
    class="app-card"
    :to="{ name: 'service-detail', params: { id: service.id } }"
  >
    <span class="service-card-image">
      <img
        v-if="service.imageUrl"
        :src="assetUrl(service.imageUrl)"
        :alt="service.name"
        loading="lazy"
        decoding="async"
      />
      <b v-else>{{ service.name?.slice(0, 1) || '服' }}</b>
    </span>
    <span class="service-card-copy">
      <small>{{ service.categoryName || service.category }}</small>
      <strong>{{ service.name }}</strong>
      <span>{{ service.shopName }}<template v-if="service.shopRating"> · ★ {{ service.shopRating }}</template></span>
      <span class="service-card-care">
        <span>{{ targetPetLabel(service.targetPet) }}</span>
        <span>{{ lifeStageLabel(service.lifeStage) }}</span>
        <span v-for="tag in parseHealthTags(service.healthTags).slice(0, 3)" :key="tag">{{ tag }}</span>
      </span>
      <span class="service-card-bottom">
        <b>¥{{ formatPrice(service.price) }}</b>
        <em>{{ service.duration || '预约确认' }} · 预约</em>
      </span>
    </span>
  </RouterLink>
</template>

<style scoped>
.app-card { min-height: 174px; display: grid; grid-template-columns: 112px minmax(0, 1fr); gap: 14px; }
.service-card-image { min-height: 150px; overflow: hidden; border-radius: var(--radius-md); display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.service-card-image img { width: 100%; height: 100%; object-fit: cover; }
.service-card-image b { font-family: var(--font-family-display); font-size: var(--font-size-5xl); }
.service-card-copy { min-width: 0; padding: 6px 0 4px; display: flex; flex-direction: column; }
.service-card-copy small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.service-card-copy > strong { margin: 6px 0 4px; font-family: var(--font-family-display); font-size: var(--font-size-xl); line-height: 1.3; }
.service-card-copy > span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.service-card-care { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 9px; }
.service-card-care span { padding: 3px 7px; border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.service-card-bottom { margin-top: auto; padding-top: 11px; border-top: 1px solid var(--color-border); display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.service-card-bottom b { color: var(--color-primary-strong); font-family: var(--font-family-display); font-size: var(--font-size-xl); }
.service-card-bottom em { padding: 5px 8px; border-radius: 999px; background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-style: normal; font-weight: var(--font-weight-black); white-space: nowrap; }
@media (max-width: 560px) {
  .app-card { grid-template-columns: 100px minmax(0, 1fr); }
}
</style>
