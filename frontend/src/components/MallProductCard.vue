<script setup>
import { ShoppingCart } from 'lucide-vue-next'
import { assetUrl } from '../services/api'
import { formatPrice } from '../utils/format'
import { lifeStageLabel, parseHealthTags, targetPetLabel } from '../utils/productPresentation'

defineProps({
  product: {
    type: Object,
    required: true
  },
  showAdd: {
    type: Boolean,
    default: true
  }
})

defineEmits(['add'])
</script>

<template>
  <article class="app-card">
    <RouterLink
      class="mall-product-main"
      :to="{ name: 'product-detail', params: { id: product.id } }"
    >
      <div class="mall-product-image">
        <img
          v-if="product.imageUrl"
          :src="assetUrl(product.imageUrl)"
          :alt="product.name"
          loading="lazy"
          decoding="async"
        />
        <span v-else>{{ product.name?.slice(0, 1) || '宠' }}</span>
      </div>
      <div class="mall-product-copy">
        <div class="mall-product-meta">
          <span>{{ product.categoryName || product.category }}</span>
          <span v-if="product.shopRating">★ {{ product.shopRating }}</span>
        </div>
        <h3>{{ product.name }}</h3>
        <p>{{ product.shopName }}</p>
        <div class="mall-product-care">
          <span>{{ targetPetLabel(product.targetPet) }}</span>
          <span>{{ lifeStageLabel(product.lifeStage) }}</span>
          <span v-for="tag in parseHealthTags(product.healthTags).slice(0, 3)" :key="tag">{{ tag }}</span>
        </div>
        <div class="mall-product-bottom">
          <strong>¥{{ formatPrice(product.price) }}</strong>
        </div>
      </div>
    </RouterLink>
    <button
      v-if="showAdd"
      class="mall-add-button"
      type="button"
      :aria-label="`加入购物车：${product.name}`"
      @click="$emit('add', product)"
    >
      <ShoppingCart :size="16" />
    </button>
  </article>
</template>

<style scoped>
.mall-product-main { display: block; }
.mall-product-image { height: 194px; overflow: hidden; border-radius: var(--radius-md); display: grid; place-items: center; background: linear-gradient(145deg, var(--color-surface-warm), var(--color-surface)); color: var(--color-primary-strong); }
.mall-product-image img { width: 100%; height: 100%; object-fit: cover; transition: transform .3s ease; }
.app-card:hover .mall-product-image img { transform: scale(1.025); }
.mall-product-image > span { font-family: var(--font-family-display); font-size: 46px; }
.mall-product-copy { padding: 15px 0 0; }
.mall-product-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--color-text-muted); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.mall-product-copy h3 { margin: 7px 0 3px; font-size: var(--font-size-lg); }
.mall-product-copy p { margin: 0 0 13px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.mall-product-care { display: flex; flex-wrap: wrap; gap: 5px; margin: 0 0 12px; }
.mall-product-care span { padding: 3px 7px; border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.mall-product-bottom { padding-top: 11px; padding-right: 42px; border-top: 1px solid var(--color-border); display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.mall-product-bottom strong { color: var(--color-primary-strong); font-family: var(--font-family-display); font-size: var(--font-size-xl); }
.mall-product-bottom span { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.mall-add-button { position: absolute; right: var(--layout-card-padding); bottom: var(--layout-card-padding); width: 34px; height: 34px; border: 1px solid var(--color-border); border-radius: 50%; background: var(--color-surface-warm); color: var(--color-text); display: grid; place-items: center; }
.mall-add-button:hover { border-color: var(--color-primary); background: var(--color-primary-soft); }
</style>
