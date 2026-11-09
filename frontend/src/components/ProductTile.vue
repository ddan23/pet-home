<script setup>
import { ArrowUpRight, Star } from 'lucide-vue-next'
import { assetUrl } from '../services/api'
import { formatPrice } from '../utils/format'
import { lifeStageLabel, parseHealthTags, targetPetLabel } from '../utils/productPresentation'

defineProps({ product: { type: Object, required: true } })
</script>

<template>
  <RouterLink
    class="app-card"
    :to="{ name: product.productType === 'service' ? 'service-detail' : 'product-detail', params: { id: product.id } }"
  >
    <div class="product-image">
      <img v-if="product.imageUrl" :src="assetUrl(product.imageUrl)" :alt="product.name" loading="lazy" decoding="async" />
      <span v-else>{{ product.name?.slice(0, 1) || 'P' }}</span>
      <span class="product-go"><ArrowUpRight :size="17" /></span>
    </div>
    <div class="product-body">
      <div class="product-meta">
        <span>{{ product.categoryName || product.category }}</span>
        <span v-if="product.shopRating"><Star :size="12" />{{ product.shopRating }}</span>
      </div>
      <h3>{{ product.name }}</h3>
      <p>{{ product.description || product.shopName }}</p>
      <div class="product-care">
        <span>{{ targetPetLabel(product.targetPet) }}</span>
        <span>{{ lifeStageLabel(product.lifeStage) }}</span>
        <span v-for="tag in parseHealthTags(product.healthTags).slice(0, 3)" :key="tag">{{ tag }}</span>
      </div>
      <div class="product-bottom">
        <strong>¥{{ formatPrice(product.price) }}</strong>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.product-image { position: relative; height: 190px; border-radius: var(--radius-md); background: linear-gradient(135deg, var(--color-primary-soft), var(--color-surface-accent)); display: grid; place-items: center; overflow: hidden; }
.product-image > img { width: 100%; height: 100%; object-fit: cover; transition: transform .35s ease; }
.app-card:hover .product-image > img { transform: scale(1.035); }
.product-image > span:first-child:not(.product-go) { font-family: var(--font-family-display); font-size: var(--font-size-7xl); color: var(--color-primary); }
.product-go { position: absolute; right: 12px; top: 12px; width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--color-surface-white-glass); color: var(--color-text); backdrop-filter: blur(8px); }
.product-body { padding: 17px 0 0; }
.product-meta { display: flex; justify-content: space-between; gap: 8px; color: var(--color-text-muted); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.product-meta span { display: inline-flex; align-items: center; gap: 4px; }
.product-body h3 { margin: 8px 0 5px; font-size: var(--font-size-xl); }
.product-body p { min-height: 42px; margin: 0 0 15px; color: var(--color-text-secondary); font-size: var(--font-size-sm); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.product-care { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 12px; }
.product-care span { padding: 3px 7px; border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.product-bottom { display: flex; align-items: baseline; justify-content: space-between; border-top: 1px solid var(--color-border); padding-top: 12px; }
.product-bottom strong { color: var(--color-primary-strong); font-family: var(--font-family-display); font-size: var(--font-size-2xl); }
.product-bottom span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
</style>
