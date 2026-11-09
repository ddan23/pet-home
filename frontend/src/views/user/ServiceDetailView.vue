<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CalendarDays, Clock3, MapPin, MessageCircle, Star } from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { formatPrice } from '../../utils/format'
import { lifeStageLabel, parseHealthTags, targetPetLabel } from '../../utils/productPresentation'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import ShopCatalogDialog from '../../components/ShopCatalogDialog.vue'
import PageBackButton from '../../components/PageBackButton.vue'
import ServiceBookingPanel from '../../components/appointment/ServiceBookingPanel.vue'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const { requireUser } = useUserAccess()
const service = ref(null)
const loading = ref(true)
const bookingOpen = ref(false)
const bookingBusy = ref(false)
const healthTagList = computed(() => parseHealthTags(service.value?.healthTags))

async function load() {
  loading.value = true
  try {
    const result = await api.get(`/services/${route.params.id}`)
    service.value = result.data
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function openBooking() {
  if (!await requireUser({ action: '预约服务' })) return
  bookingOpen.value = true
}

const shopCatalogOpen = ref(false)

async function contactShop() {
  if (!await requireUser({ action: '咨询商家' })) return
  if (!service.value?.shopOwnerId) {
    ui.toast('暂时无法联系商家', 'warning')
    return
  }
  router.push({ path: `/messages/${service.value.shopOwnerId}`, query: { category: 'service', type: 'consult', serviceId: service.value.id } })
}

onMounted(load)
</script>

<template>
  <div class="page-layout">
    <div class="detail-back">
      <PageBackButton fallback-path="/services" aria-label="返回服务" />
    </div>
    <LoadingPanel v-if="loading" label="正在打开服务" />
    <EmptyState v-else-if="!service" title="服务不存在" description="它可能已经下架。" />
    <template v-else>
      <section class="service-detail">
        <div class="service-visual">
          <img v-if="service.imageUrl" :src="assetUrl(service.imageUrl)" :alt="service.name" />
          <div v-else class="service-visual-fallback"><CalendarDays :size="48" /></div>
          <span class="service-duration"><Clock3 :size="15" />{{ service.duration || '时长以商家确认为准' }}</span>
        </div>
        <div class="service-info">
          <p class="eyebrow">{{ service.categoryName || service.category }}</p>
          <h1>{{ service.name }}</h1>
          <div class="shop-line">
            <button
              v-if="service.shopId"
              class="shop-link"
              type="button"
              title="查看店铺全部服务"
              @click="shopCatalogOpen = true"
            >
              <img v-if="service.shopLogo" class="shop-logo" :src="assetUrl(service.shopLogo)" alt="" />
              <span v-else class="shop-logo shop-logo-fallback">{{ (service.shopName || '店').slice(0, 1) }}</span>
              <span class="shop-name">{{ service.shopName }}</span>
              <ArrowRight :size="13" class="shop-arrow" />
            </button>
            <span v-else>{{ service.shopName }}</span>
            <span v-if="service.shopRating"><Star :size="13" />{{ service.shopRating }}</span>
          </div>
          <p class="service-description">{{ service.description || '服务详情暂未补充。' }}</p>
          <div class="care-summary" aria-label="服务养护属性">
            <span>{{ targetPetLabel(service.targetPet) }}</span>
            <span>{{ lifeStageLabel(service.lifeStage) }}</span>
            <span v-for="tag in healthTagList" :key="tag">{{ tag }}</span>
          </div>
          <div class="service-price">¥{{ formatPrice(service.price) }}<span> / 次</span></div>
          <div class="service-actions">
            <button class="btn btn-primary" type="button" :disabled="service.status !== 'on'" @click="openBooking">
              <CalendarDays :size="17" />选择预约时段
            </button>
            <button class="btn btn-ghost" type="button" @click="contactShop"><MessageCircle :size="17" />咨询商家</button>
          </div>
          <div class="service-location">
            <MapPin :size="17" />
            <div><b>服务门店</b><span>{{ service.shopAddress || '地址请联系商家确认' }}</span><small>{{ service.shopBusinessHours || '' }}</small></div>
          </div>
        </div>
      </section>
      <ModalDialog
        :open="bookingOpen"
        :title="`预约 · ${service.name}`"
        width="880px"
        body-class="booking-modal-body"
        panel-class="booking-modal"
        :close-disabled="bookingBusy"
        @close="bookingOpen = false"
      >
        <ServiceBookingPanel
          :service="service"
          @busy-change="bookingBusy = $event"
        />
      </ModalDialog>
    </template>

    <ShopCatalogDialog
      :open="shopCatalogOpen"
      :shop-id="service?.shopId"
      :shop-name="service?.shopName || '本店'"
      product-type="service"
      @close="shopCatalogOpen = false"
    />
  </div>
</template>

<style scoped>
.detail-back { margin-bottom: 20px; }
.service-detail { display: grid; grid-template-columns: minmax(0, .9fr) minmax(420px, 1.1fr); gap: 40px; align-items: start; }
.service-visual { position: relative; min-height: 520px; overflow: hidden; border-radius: 36px 36px 120px 36px; background: linear-gradient(135deg, var(--color-surface-accent), var(--color-surface-warm)); }
.service-visual img { width: 100%; height: 100%; min-height: 520px; object-fit: cover; }
.service-visual-fallback { min-height: 520px; display: grid; place-items: center; color: var(--color-success); }
.service-duration { position: absolute; left: 18px; bottom: 18px; display: flex; align-items: center; gap: 6px; padding: 8px 12px; border-radius: 999px; background: var(--color-surface-white-glass); backdrop-filter: blur(10px); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.service-info h1 { font-size: var(--font-size-detail-title); margin-bottom: 12px; }
.shop-line { display: flex; gap: 11px; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.shop-line span { display: flex; align-items: center; gap: 4px; }
.shop-link {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 2px 6px 2px 3px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary-strong);
  font: inherit;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease;
}
.shop-link:hover { border-color: var(--color-primary-soft); background: var(--color-primary-soft); }
.shop-logo { width: 22px; height: 22px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border-soft); }
.shop-logo-fallback { display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: 0.72rem; font-weight: var(--font-weight-bold); }
.shop-name { border-bottom: 1px dashed var(--color-primary); }
.shop-link:hover { color: var(--color-primary); }
.shop-link svg { color: var(--color-text-muted); transition: transform 0.16s ease, color 0.16s ease; }
.shop-link:hover svg { color: var(--color-primary); transform: translateX(2px); }
.service-description { margin: 25px 0; color: var(--color-text-secondary); line-height: 1.8; }
.care-summary { display: flex; flex-wrap: wrap; gap: 6px; margin: -9px 0 20px; }
.care-summary span { padding: 4px 9px; border: 1px solid var(--color-border-primary-subtle); border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.service-price { font-family: var(--font-family-display); font-size: var(--font-size-5xl); color: var(--color-primary-strong); padding: 18px 0; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.service-price span { color: var(--color-text-muted); font-family: var(--font-family-body); font-size: var(--font-size-sm); }
.service-actions { display: flex; gap: 9px; margin: 24px 0; }
.service-location { display: flex; gap: 12px; align-items: flex-start; padding: 16px; border-radius: 15px; background: var(--color-surface-warm); }
.service-location svg { color: var(--color-primary-strong); flex-shrink: 0; }
.service-location b, .service-location span, .service-location small { display: block; }
.service-location b { font-size: var(--font-size-sm); }
.service-location span { color: var(--color-text-secondary); font-size: var(--font-size-sm); margin-top: 3px; }
.service-location small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
:global(.booking-modal) {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}
:global(.booking-modal .modal-head) {
  padding: 14px 22px;
}
:global(.booking-modal .modal-head h2) {
  font-size: var(--font-size-xl);
}
:global(.booking-modal-body) {
  padding: 0;
  background:
    radial-gradient(circle at 92% 4%, var(--color-butter-wash), transparent 22rem),
    var(--color-surface);
}
@media (max-width: 820px) {
  .service-detail { grid-template-columns: 1fr; }
  .service-visual, .service-visual img, .service-visual-fallback { min-height: 340px; }
}
</style>
