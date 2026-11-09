<script setup>
import { ArrowLeft, PawPrint } from 'lucide-vue-next'
</script>

<template>
  <main class="page-layout not-found">
    <span><PawPrint :size="34" /></span>
    <p>404</p>
    <h1>页面不存在</h1>
    <RouterLink class="btn btn-primary" to="/"><ArrowLeft :size="17" />回到首页</RouterLink>
  </main>
</template>

<style scoped>
.not-found { min-height: 520px; display: grid; place-content: center; justify-items: center; text-align: center; }
.not-found > span { width: 80px; height: 80px; border-radius: 50%; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.not-found > p { margin: 18px 0 2px; color: var(--color-primary-strong); font-weight: var(--font-weight-extra-bold); letter-spacing: .2em; }
.not-found h1 { font-size: var(--font-size-detail-title); margin-bottom: 24px; }
</style>
