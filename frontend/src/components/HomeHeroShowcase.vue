<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { assetUrl } from '../services/api'

const DURATION = 6500

const slides = [
  {
    id: 'about',
    nav: '关于我们',
    titleLines: ['给猫狗一个暖窝，', '给自己一份省心'],
    description: '社区交流、严选商城、到店服务与养护档案彼此相连，把养宠这件小事，做得更从容一点。',
    image: '/images/webphoto/slide1.jpg',
    imageAlt: '小狗与两只小猫一起待在花园里',
    focus: '62% 40%'
  },
  {
    id: 'community',
    nav: '社区',
    titleLines: ['把养宠的日常，', '留在这里'],
    description: '晒一张今天的照片，写一段踩过的坑；也可以提一个问题，等一个过来人的回答。',
    image: '/images/webphoto/slide2.jpg',
    imageAlt: '猫咪趴在翻开的书本上晒太阳',
    focus: '66% 48%'
  },
  {
    id: 'mall',
    nav: '商城',
    titleLines: ['为每一天，', '挑好每一件'],
    description: '主粮、零食、清洁与出行用品，按宠物年龄与体型筛选，价格与库存一眼看清。',
    image: '/images/webphoto/slide3.jpg',
    imageAlt: '小狗与它的黄色小鸭玩具',
    focus: '50% 45%'
  },
  {
    id: 'service',
    nav: '服务',
    titleLines: ['需要照护时，', '就近找到安心'],
    description: '洗护、寄养、体检与训练，看清服务内容与时长，选好时段再预约。',
    image: '/images/webphoto/slide4.jpg',
    imageAlt: '小猫趴在树干上张望',
    focus: '68% 46%'
  },
  {
    id: 'health',
    nav: '养护',
    titleLines: ['记录每次变化，', '照护更有依据'],
    description: '体重、疫苗、驱虫与病史集中管理，到期自动提醒；有疑问时，问问只答猫狗问题的 AI 顾问。',
    image: '/images/webphoto/slide5.jpg',
    imageAlt: '可爱的小熊与兔子卡通插画',
    focus: '50% 50%'
  }
]

const activeIndex = ref(0)
const paused = ref(false)
let autoplayTimer = null
let pointerStartX = 0

const activeSlide = computed(() => slides[activeIndex.value])

function goTo(index) {
  activeIndex.value = (index + slides.length) % slides.length
  restartAutoplay()
}

function next() {
  goTo(activeIndex.value + 1)
}

function previous() {
  goTo(activeIndex.value - 1)
}

function startAutoplay() {
  stopAutoplay()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  autoplayTimer = window.setInterval(() => {
    activeIndex.value = (activeIndex.value + 1) % slides.length
  }, DURATION)
}

function stopAutoplay() {
  if (autoplayTimer) {
    window.clearInterval(autoplayTimer)
    autoplayTimer = null
  }
}

function restartAutoplay() {
  if (!paused.value) startAutoplay()
}

function pause() {
  paused.value = true
  stopAutoplay()
}

function resume() {
  paused.value = false
  startAutoplay()
}

function handlePointerDown(event) {
  pointerStartX = event.clientX
  stopAutoplay()
}

function handlePointerUp(event) {
  const distance = event.clientX - pointerStartX
  if (Math.abs(distance) >= 45) {
    goTo(activeIndex.value + (distance < 0 ? 1 : -1))
  }
  restartAutoplay()
}

onMounted(startAutoplay)
onBeforeUnmount(stopAutoplay)
</script>

<template>
  <section
    class="hero"
    role="region"
    aria-roledescription="carousel"
    aria-label="网站五大板块介绍"
    tabindex="0"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="pause"
    @focusout="resume"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
    @pointerdown="handlePointerDown"
    @pointerup="handlePointerUp"
    @pointercancel="restartAutoplay"
  >
    <Transition name="hero-fade" mode="out-in">
      <article :key="activeSlide.id" class="hero-slide" aria-live="polite">
        <img
          class="hero-bg"
          :src="assetUrl(activeSlide.image)"
          :alt="activeSlide.imageAlt"
          :style="{ objectPosition: activeSlide.focus }"
          decoding="async"
          fetchpriority="high"
          draggable="false"
        />
        <span class="hero-scrim" aria-hidden="true" />

        <div class="hero-copy">
          <h1>
            <span v-for="line in activeSlide.titleLines" :key="line">{{ line }}</span>
          </h1>
          <p>{{ activeSlide.description }}</p>
        </div>
      </article>
    </Transition>

    <div class="hero-dots" role="tablist" aria-label="轮播进度">
      <button
        v-for="(slide, index) in slides"
        :key="slide.id"
        type="button"
        role="tab"
        :aria-selected="index === activeIndex"
        :aria-label="`第 ${index + 1} 张：${slide.nav}`"
        :class="{ active: index === activeIndex }"
        @click="goTo(index)"
      />
    </div>

    <div class="hero-arrows">
      <button type="button" aria-label="上一张" @click="previous">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <button type="button" aria-label="下一张" @click="next">
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>

    <span class="hero-progress" aria-hidden="true">
      <i :key="activeSlide.id" :class="{ paused }" :style="{ animationDuration: `${DURATION}ms` }" />
    </span>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  height: 444px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px 42px 42px 10px;
  background: #2c2114;
  box-shadow: var(--shadow-md);
  cursor: grab;
  touch-action: pan-y;
  user-select: none;
}

.hero:active { cursor: grabbing; }

.hero-slide { position: absolute; inset: 0; }

.hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
}

.hero-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(0deg, rgba(35, 26, 14, .42) 0%, rgba(35, 26, 14, 0) 40%),
    linear-gradient(90deg, rgba(33, 24, 13, .92) 0%, rgba(33, 24, 13, .74) 34%, rgba(33, 24, 13, .3) 64%, rgba(33, 24, 13, .04) 92%);
}

.hero-copy {
  position: absolute;
  z-index: 2;
  top: 50%;
  left: clamp(26px, 4vw, 58px);
  width: min(560px, 54%);
  transform: translateY(-50%);
  color: var(--color-white);
}

.hero-copy h1 {
  margin: 0 0 16px;
  color: var(--color-white);
  font-family: var(--font-family-display);
  font-size: clamp(2.1rem, 3.6vw, 3.15rem);
  font-weight: 700;
  line-height: 1.22;
  letter-spacing: -.02em;
  text-shadow: 0 4px 24px rgba(20, 14, 6, .45);
}

.hero-copy h1 span { display: block; }

.hero-copy p {
  margin: 0;
  max-width: 31em;
  color: rgba(255, 255, 255, .84);
  font-size: 0.94rem;
  line-height: 1.9;
  text-shadow: 0 2px 14px rgba(20, 14, 6, .4);
}

.hero-dots {
  position: absolute;
  z-index: 3;
  left: clamp(26px, 4vw, 58px);
  bottom: 24px;
  display: flex;
  align-items: center;
  gap: 7px;
}

.hero-dots button {
  width: 7px;
  height: 7px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, .44);
  transition: width .24s ease, background .24s ease;
}

.hero-dots button:hover { background: rgba(255, 255, 255, .8); }

.hero-dots button.active { width: 26px; background: var(--color-butter); }

.hero-arrows {
  position: absolute;
  z-index: 3;
  right: 26px;
  bottom: 22px;
  display: flex;
  gap: 8px;
}

.hero-arrows button {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, .38);
  border-radius: 50%;
  background: rgba(28, 20, 10, .26);
  color: var(--color-white);
  backdrop-filter: blur(8px);
  transition: var(--transition-base);
}

.hero-arrows button:hover { background: rgba(255, 255, 255, .22); border-color: rgba(255, 255, 255, .6); }

.hero-progress {
  position: absolute;
  z-index: 4;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: rgba(255, 255, 255, .16);
}

.hero-progress i {
  display: block;
  width: 0;
  height: 100%;
  background: linear-gradient(90deg, var(--color-butter), #fff4c9);
  animation-name: hero-progress-grow;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

.hero-progress i.paused { animation-play-state: paused; }

@keyframes hero-progress-grow {
  from { width: 0; }
  to { width: 100%; }
}

.hero-fade-enter-active,
.hero-fade-leave-active { transition: opacity .26s ease, transform .26s ease; }

.hero-fade-enter-from { opacity: 0; transform: translateX(14px); }

.hero-fade-leave-to { opacity: 0; transform: translateX(-14px); }

@media (max-width: 760px) {
  .hero { height: 400px; border-radius: 8px 30px 30px 8px; }
  .hero-scrim {
    background:
      linear-gradient(0deg, rgba(33, 24, 13, .92) 0%, rgba(33, 24, 13, .66) 44%, rgba(33, 24, 13, .12) 80%);
  }
  .hero-copy {
    top: auto;
    bottom: 56px;
    left: 20px;
    width: calc(100% - 40px);
    transform: none;
  }
  .hero-copy h1 { margin-bottom: 10px; font-size: 1.72rem; }
  .hero-copy p { font-size: 0.84rem; line-height: 1.75; }
  .hero-arrows { display: none; }
  .hero-dots { left: 20px; bottom: 18px; }
}

@media (prefers-reduced-motion: reduce) {
  .hero-fade-enter-active,
  .hero-fade-leave-to { transition: none; }
  .hero-progress i { animation: none; width: 100%; }
}
</style>
