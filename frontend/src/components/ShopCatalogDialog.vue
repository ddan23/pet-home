<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MapPin, MessageCircle, PawPrint, Star } from 'lucide-vue-next'
import { api, assetUrl } from '../services/api'
import ModalDialog from './ModalDialog.vue'
import LoadingPanel from './LoadingPanel.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  shopId: { type: [Number, String], default: null },
  shopName: { type: String, default: '本店' },
  // goods=只展示该店商品，service=只展示该店服务；单店铺单品类，不跨店聚合
  productType: { type: String, default: 'goods' }
})
const emit = defineEmits(['close'])

const router = useRouter()
const items = ref([])
const loading = ref(false)

const isService = computed(() => props.productType === 'service')
const dialogTitle = computed(() => `${props.shopName} · 全部${isService.value ? '服务' : '商品'}`)
const shopMeta = computed(() => {
  const first = items.value[0]
  return {
    rating: first?.shopRating || null,
    address: first?.shopAddress || ''
  }
})

async function loadCatalog() {
  if (!props.shopId) return
  loading.value = true
  try {
    const result = await api.get(
      `/${isService.value ? 'services' : 'products'}?shopId=${props.shopId}&page=1&pageSize=60`)
    items.value = result.data?.list || []
  } catch (error) {
    items.value = []
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.shopId, props.productType], ([open]) => {
  if (open) loadCatalog()
})

function goDetail(item) {
  emit('close')
  router.replace(isService.value ? `/services/${item.id}` : `/mall/${item.id}`)
}
</script>

<template>
  <ModalDialog :open="open" :title="dialogTitle" @close="emit('close')">
    <div class="shop-catalog">
      <div v-if="!loading" class="catalog-head">
        <b>{{ shopName }}</b>
        <span v-if="shopMeta.rating" class="catalog-rating"><Star :size="13" />{{ shopMeta.rating }}</span>
        <span v-if="shopMeta.address" class="catalog-address"><MapPin :size="13" />{{ shopMeta.address }}</span>
      </div>
      <LoadingPanel v-if="loading" label="正在加载" />
      <template v-else>
        <div class="catalog-list">
          <button
            v-for="item in items"
            :key="item.id"
            class="catalog-row"
            type="button"
            @click="goDetail(item)"
          >
            <img v-if="item.imageUrl" :src="assetUrl(item.imageUrl)" alt="" />
            <span v-else class="catalog-fallback">
              <PawPrint v-if="isService" :size="18" />
              <MessageCircle v-else :size="18" />
            </span>
            <span class="catalog-copy">
              <b>{{ item.name }}</b>
              <small>
                {{ isService
                  ? (item.duration ? `时长 ${item.duration}` : (item.categoryName || '宠物服务'))
                  : (item.categoryName || '宠物商品') }}
              </small>
            </span>
            <span class="catalog-price">{{ item.price != null ? `¥${item.price}` : '价格面议' }}</span>
          </button>
          <p v-if="!items.length" class="catalog-empty">
            这家店还没有上架{{ isService ? '服务' : '商品' }}。
          </p>
        </div>
      </template>
    </div>
  </ModalDialog>
</template>

<style scoped>
.catalog-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
  padding: 9px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-surface-warm);
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.catalog-head b { color: var(--color-text); font-size: var(--font-size-sm); }
.catalog-rating, .catalog-address { display: inline-flex; align-items: center; gap: 4px; }
.catalog-rating { color: var(--color-primary-strong); }
.catalog-list { display: grid; gap: 8px; max-height: 56vh; overflow-y: auto; padding-right: 2px; }
.catalog-row {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 8px 10px;
  border: 1px solid var(--color-border-soft);
  border-radius: 12px;
  background: var(--color-surface);
  text-align: left;
  cursor: pointer;
  transition: var(--transition-base);
}
.catalog-row:hover { border-color: var(--color-primary); background: var(--color-surface-warm); }
.catalog-row img, .catalog-fallback { width: 48px; height: 48px; flex: 0 0 auto; border-radius: 10px; object-fit: cover; }
.catalog-fallback { display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.catalog-copy { flex: 1; min-width: 0; display: grid; gap: 2px; }
.catalog-copy b { font-size: var(--font-size-sm); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.catalog-copy small { color: var(--color-text-muted); font-size: 0.7rem; }
.catalog-price { flex: 0 0 auto; color: var(--color-primary-strong); font-weight: var(--font-weight-bold); font-size: var(--font-size-sm); }
.catalog-empty { margin: 0; padding: 26px 0; text-align: center; color: var(--color-text-muted); font-size: var(--font-size-sm); }
</style>
