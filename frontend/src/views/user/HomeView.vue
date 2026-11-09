<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight, BellRing, Bot, FileHeart, HeartPulse, MessageCircle, ShoppingBag, Stethoscope
} from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { formatDate, formatPrice } from '../../utils/format'
import HomeHeroShowcase from '../../components/HomeHeroShowcase.vue'
import ProductTile from '../../components/ProductTile.vue'
import LoadingPanel from '../../components/LoadingPanel.vue'

const posts = ref([])
const products = ref([])
const services = ref([])
const loading = ref(true)

const quickLinks = [
  {
    title: '社区',
    description: '分享养宠日常，也找到答案',
    icon: MessageCircle,
    tone: 'gold',
    path: '/community'
  },
  {
    title: '商城',
    description: '主粮零食到出行好物',
    icon: ShoppingBag,
    tone: 'coral',
    path: '/mall'
  },
  {
    title: '服务',
    description: '洗护寄养与体检预约',
    icon: Stethoscope,
    tone: 'sage',
    path: '/services'
  },
  {
    title: '养护',
    description: '档案、提醒与 AI 问答',
    icon: HeartPulse,
    tone: 'butter',
    path: '/health'
  }
]

const healthFeatures = [
  { title: '养护档案', description: '体重、疫苗、驱虫与病史一处留存', icon: FileHeart },
  { title: '到期提醒', description: '疫苗与驱虫到期前主动提醒', icon: BellRing },
  { title: 'AI 顾问', description: '只回答猫狗养护问题的助手', icon: Bot }
]

const communityPosts = computed(() => posts.value.slice(0, 3))
const featuredServices = computed(() => services.value.slice(0, 3))
const featuredProducts = computed(() => products.value.slice(0, 2))

function authorInitial(post) {
  return String(post.nickname || '社区用户').slice(0, 1)
}

async function requestOrDefault(path, fallback) {
  try {
    return await api.get(path)
  } catch {
    return fallback
  }
}

async function loadPublicContent() {
  const [postResult, productResult, serviceResult] = await Promise.all([
    requestOrDefault('/posts?page=1&pageSize=3', null),
    requestOrDefault('/products?page=1&pageSize=2', null),
    requestOrDefault('/services?page=1&pageSize=3', null)
  ])
  posts.value = postResult?.data?.list || []
  products.value = productResult?.data?.list || []
  services.value = serviceResult?.data?.list || []
}

onMounted(async () => {
  try {
    await loadPublicContent()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-layout home-page">
    <HomeHeroShowcase />

    <nav class="home-quick-links" aria-label="主要功能">
      <RouterLink
        v-for="link in quickLinks"
        :key="link.title"
        :to="link.path"
        class="home-quick-link"
      >
        <span class="home-quick-icon" :class="link.tone">
          <component :is="link.icon" :size="20" />
        </span>
        <span class="home-quick-copy">
          <b>{{ link.title }}</b>
          <small>{{ link.description }}</small>
        </span>
        <ArrowRight class="home-quick-arrow" :size="16" />
      </RouterLink>
    </nav>

    <LoadingPanel v-if="loading" label="正在整理公开内容" />

    <template v-else>
      <section class="home-section" aria-labelledby="home-community-title">
        <header class="home-section-head">
          <div>
            <span class="home-eyebrow"><MessageCircle :size="13" />社区</span>
            <h2 id="home-community-title">大家正在聊</h2>
          </div>
          <RouterLink class="home-more" to="/community">查看全部<ArrowRight :size="14" /></RouterLink>
        </header>

        <div v-if="communityPosts.length" class="home-post-grid">
          <RouterLink
            v-for="post in communityPosts"
            :key="post.id"
            class="home-post"
            :to="{ name: 'post-detail', params: { id: post.id } }"
          >
            <div class="home-post-media">
              <img
                v-if="post.image"
                :src="assetUrl(post.image)"
                :alt="post.title || '社区图片'"
                loading="lazy"
                decoding="async"
              />
              <span v-else class="home-post-placeholder">
                <span class="home-post-placeholder-mark"><MessageCircle :size="24" /></span>
                <small>{{ post.category || '社区' }}</small>
              </span>
              <span v-if="post.category" class="home-post-tag">{{ post.category }}</span>
            </div>
            <div class="home-post-body">
              <h3>{{ post.title || '分享一件小事' }}</h3>
              <p>{{ post.content }}</p>
              <div class="home-post-foot">
                <span class="home-post-author">
                  <i>{{ authorInitial(post) }}</i>{{ post.nickname || '社区用户' }}
                </span>
                <span class="home-post-stats">
                  <span>♡ {{ post.likes || 0 }}</span>
                  <span>评论 {{ post.comments || 0 }}</span>
                </span>
              </div>
            </div>
          </RouterLink>
        </div>
        <p v-else class="home-empty-note">还没有公开帖子</p>
      </section>

      <section class="home-section" aria-labelledby="home-market-title">
        <header class="home-section-head">
          <div>
            <span class="home-eyebrow"><ShoppingBag :size="13" />商城与服务</span>
            <h2 id="home-market-title">今天可以带回家的</h2>
          </div>
        </header>

        <div class="home-market-grid">
          <article class="home-panel">
            <div class="home-panel-head">
              <div>
                <h3>商城</h3>
              </div>
              <RouterLink class="home-more" to="/mall">全部商品<ArrowRight :size="14" /></RouterLink>
            </div>
            <div v-if="featuredProducts.length" class="home-goods-grid">
              <ProductTile v-for="product in featuredProducts" :key="product.id" :product="product" />
            </div>
            <p v-else class="home-empty-note">暂无商品</p>
          </article>

          <article class="home-panel">
            <div class="home-panel-head">
              <div>
                <h3>服务</h3>
              </div>
              <RouterLink class="home-more" to="/services">全部服务<ArrowRight :size="14" /></RouterLink>
            </div>
            <div v-if="featuredServices.length" class="home-service-list">
              <RouterLink
                v-for="service in featuredServices"
                :key="service.id"
                class="home-service-item"
                :to="{ name: 'service-detail', params: { id: service.id } }"
              >
                <span class="home-service-image">
                  <img v-if="service.imageUrl" :src="assetUrl(service.imageUrl)" :alt="service.name" loading="lazy" />
                  <Stethoscope v-else :size="20" />
                </span>
                <span class="home-service-copy">
                  <small>{{ service.categoryName || service.category }}</small>
                  <b>{{ service.name }}</b>
                  <span>{{ service.duration || '预约确认' }}</span>
                </span>
                <strong>¥{{ formatPrice(service.price) }}</strong>
              </RouterLink>
            </div>
            <p v-else class="home-empty-note">暂无服务</p>
          </article>
        </div>
      </section>

      <section class="home-health" aria-labelledby="home-health-title">
        <div class="home-health-copy">
          <span class="home-eyebrow"><HeartPulse :size="13" />养护</span>
          <h2 id="home-health-title">记录每次变化，照护更有依据</h2>
          <p>体重、疫苗、驱虫与病史集中管理，到期自动提醒；不确定的时候，还有一个只答猫狗问题的顾问。</p>
          <div class="home-health-actions">
            <RouterLink class="btn btn-primary" to="/health">进入养护中心</RouterLink>
          </div>
        </div>
        <ul class="home-health-features">
          <li v-for="feature in healthFeatures" :key="feature.title">
            <span class="home-health-icon"><component :is="feature.icon" :size="17" /></span>
            <div>
              <b>{{ feature.title }}</b>
              <small>{{ feature.description }}</small>
            </div>
          </li>
        </ul>
      </section>

    </template>
  </div>
</template>

<style scoped>
.home-page { min-width: 0; }

/* 快捷入口 */
.home-quick-links {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.home-quick-link {
  min-height: 84px;
  padding: 16px 18px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 13px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.home-quick-link:hover {
  transform: translateY(-3px);
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-md);
}

.home-quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.home-quick-icon.coral { background: var(--color-accent-soft); color: var(--color-accent-strong); }
.home-quick-icon.sage { background: var(--color-success-soft); color: var(--color-success); }
.home-quick-icon.butter { background: var(--color-surface-warm); color: var(--color-warning); }

.home-quick-copy { min-width: 0; }
.home-quick-copy b { display: block; font-size: 0.98rem; }
.home-quick-copy small { display: block; margin-top: 3px; color: var(--color-text-muted); font-size: 0.78rem; line-height: 1.5; }
.home-quick-arrow { color: var(--color-primary-strong); transition: transform .18s ease; }
.home-quick-link:hover .home-quick-arrow { transform: translateX(3px); }

/* 区块标题 */
.home-section { margin-top: 46px; }

.home-section-head {
  margin-bottom: 18px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.home-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-primary-strong);
  font-size: 0.74rem;
  font-weight: var(--font-weight-black);
  letter-spacing: .14em;
}

.home-section-head h2 {
  margin: 7px 0 0;
  font-family: var(--font-family-display);
  font-size: clamp(1.5rem, 2.4vw, 2rem);
  letter-spacing: -.01em;
}

.home-section-head p {
  margin: 7px 0 0;
  color: var(--color-text-muted);
  font-size: 0.86rem;
}

.home-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary-strong);
  font-size: 0.82rem;
  font-weight: var(--font-weight-extra-bold);
  white-space: nowrap;
}

.home-more:hover { text-decoration: underline; }

.home-empty-note {
  padding: 32px 10px;
  color: var(--color-text-muted);
  text-align: center;
  font-size: var(--font-size-sm);
}

/* 社区卡片 */
.home-post-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.home-post {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 20px;
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  transition: transform .18s ease, box-shadow .18s ease;
}

.home-post:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); }

.home-post-media {
  position: relative;
  height: 172px;
  overflow: hidden;
  background: var(--color-surface-warm);
}

.home-post-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform .4s ease;
}

.home-post:hover .home-post-media img { transform: scale(1.05); }

.home-post-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  background:
    radial-gradient(circle at 78% 18%, rgba(255, 255, 255, .9), transparent 46%),
    linear-gradient(135deg, #fdf1cf, #fbe6b6);
  color: var(--color-primary-strong);
}

.home-post-placeholder-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, .78);
  box-shadow: 0 6px 16px rgba(146, 110, 30, .12);
}

.home-post-placeholder small {
  color: var(--color-primary-strong);
  font-size: 0.72rem;
  font-weight: var(--font-weight-extra-bold);
  letter-spacing: .1em;
}

.home-post-tag {
  position: absolute;
  left: 12px;
  top: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(28, 20, 10, .52);
  color: #fff;
  font-size: 0.72rem;
  font-weight: var(--font-weight-bold);
  backdrop-filter: blur(6px);
}

.home-post-body {
  padding: 15px 17px 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.home-post-body h3 {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.home-post-body p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.home-post-foot {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--color-text-muted);
  font-size: 0.76rem;
}

.home-post-author { display: inline-flex; align-items: center; gap: 6px; min-width: 0; }

.home-post-author i {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
  font-size: 0.7rem;
  font-style: normal;
  font-weight: var(--font-weight-black);
}

.home-post-stats { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }

/* 商城与服务 */
.home-market-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(340px, .85fr);
  gap: 16px;
  align-items: stretch;
}

.home-panel {
  padding: 20px 22px 22px;
  display: flex;
  flex-direction: column;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 7px 26px 26px 7px;
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
}

.home-panel-head {
  margin-bottom: 15px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.home-panel-head h3 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: var(--font-size-2xl);
}

.home-panel-head small {
  display: block;
  margin-top: 3px;
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.home-goods-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.home-goods-grid :deep(.post-cover) { height: 162px; }
.home-goods-grid :deep(.product-image) { height: 164px; }
.home-goods-grid :deep(.product-body h3) { font-size: var(--font-size-lg); }

.home-service-list { flex: 1; display: grid; gap: 10px; grid-auto-rows: minmax(0, 1fr); }

.home-service-item {
  min-height: 88px;
  padding: 9px;
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-surface);
  transition: var(--transition-base);
}

.home-service-item:hover {
  background: var(--color-surface-warm);
  border-color: var(--color-border-strong);
}

.home-service-image {
  width: 62px;
  height: 62px;
  overflow: hidden;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--color-surface-accent);
  color: var(--color-success);
}

.home-service-image img { width: 100%; height: 100%; object-fit: cover; }

.home-service-copy small,
.home-service-copy b,
.home-service-copy > span { display: block; }

.home-service-copy small { color: var(--color-text-muted); font-size: 0.75rem; }
.home-service-copy b { margin: 3px 0; font-size: 0.9rem; }
.home-service-copy > span { color: var(--color-text-muted); font-size: 0.76rem; }

.home-service-item > strong {
  color: var(--color-primary-strong);
  font-family: var(--font-family-display);
  font-size: var(--font-size-lg);
}

/* 养护 */
.home-health {
  margin-top: 46px;
  padding: 28px 30px;
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(0, .98fr);
  gap: 30px;
  align-items: center;
  border: 1px solid var(--color-border);
  border-radius: 10px 32px 32px 10px;
  background:
    radial-gradient(circle at 92% 8%, #fff0bd, transparent 42%),
    linear-gradient(120deg, #fffdf6, #fdf3dd);
}

.home-health-copy h2 {
  margin: 8px 0 0;
  font-family: var(--font-family-display);
  font-size: clamp(1.4rem, 2.2vw, 1.9rem);
  line-height: 1.35;
}

.home-health-copy p {
  margin: 10px 0 0;
  max-width: 36em;
  color: var(--color-text-secondary);
  font-size: 0.86rem;
  line-height: 1.85;
}

.home-health-actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.home-health-features {
  margin: 0;
  padding: 0;
  display: grid;
  gap: 9px;
  list-style: none;
}

.home-health-features li {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, .72);
}

.home-health-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.home-health-features b { display: block; font-size: 0.86rem; }
.home-health-features small { display: block; margin-top: 2px; color: var(--color-text-muted); font-size: 0.76rem; }

@media (max-width: 1080px) {
  .home-quick-links { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .home-market-grid { grid-template-columns: 1fr; }
  .home-health { grid-template-columns: 1fr; gap: 20px; }
}

@media (max-width: 840px) {
  .home-post-grid {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    margin: 0 -24px;
    padding: 0 24px 6px;
    scrollbar-width: none;
  }
  .home-post-grid::-webkit-scrollbar { display: none; }
  .home-post { flex: 0 0 min(290px, 82vw); }
}

@media (max-width: 680px) {
  .home-section { margin-top: 34px; }
  .home-section-head { align-items: flex-start; flex-direction: column; gap: 10px; }
  .home-post-grid { margin: 0 -16px; padding: 0 16px 6px; }
  .home-quick-links { gap: 10px; }
  .home-quick-link { min-height: 76px; padding: 13px 14px; gap: 10px; }
  .home-quick-icon { width: 38px; height: 38px; border-radius: 12px; }
  .home-panel { padding: 17px 16px 18px; }
  .home-goods-grid { grid-template-columns: 1fr; }
  .home-health { padding: 22px 18px; }
  .home-health-actions { align-items: stretch; flex-direction: column; gap: 12px; }
}
</style>
