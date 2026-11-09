<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Compass,
  LockKeyhole,
  PawPrint,
  ShieldCheck,
  Store,
  UserRound
} from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { assetUrl } from '../services/api'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const role = ref(resolveInitialRole())
const nickname = ref('')
const phone = ref('')
const confirmPassword = ref('')
const registering = ref(route.query.mode === 'register')
const error = ref('')

const roles = [
  { value: 'user', label: '用户', icon: UserRound },
  { value: 'business', label: '商家', icon: Store },
  { value: 'admin', label: '管理员', icon: ShieldCheck }
]
const activeRole = computed(() => roles.find((item) => item.value === role.value))
function resolveInitialRole() {
  const requestedRole = String(route.query.role || '')
  if (['user', 'business', 'admin'].includes(requestedRole)) return requestedRole
  const redirect = String(route.query.redirect || '')
  if (redirect.startsWith('/merchant')) return 'business'
  if (redirect.startsWith('/admin')) return 'admin'
  return 'user'
}

function resolvePostLoginPath(userRole, redirect) {
  const homePath = auth.homePathFor(userRole)
  if (!redirect || redirect === '/login' || !redirect.startsWith('/')
    || redirect.startsWith('//')) {
    return homePath
  }
  if (userRole === 'business') {
    return redirect === '/merchant' || redirect.startsWith('/merchant/')
      ? redirect : homePath
  }
  if (userRole === 'admin') {
    return redirect === '/admin' || redirect.startsWith('/admin/')
      ? redirect : homePath
  }
  return redirect.startsWith('/merchant') || redirect.startsWith('/admin')
    ? homePath : redirect
}

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  try {
    if (registering.value && password.value !== confirmPassword.value) {
      error.value = '两次输入的密码不一致'
      return
    }
    if (registering.value && !nickname.value.trim()) {
      error.value = '请输入昵称'
      return
    }
    const user = registering.value
      ? await auth.register({
          username: username.value.trim(),
          password: password.value,
          nickname: nickname.value.trim(),
          phone: phone.value.trim(),
          role: role.value
        })
      : await auth.login({
          username: username.value.trim(),
          password: password.value,
          role: role.value
        })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const destination = resolvePostLoginPath(user?.role || role.value, redirect)
    await router.replace(user ? destination : '/')
  } catch (exception) {
    error.value = exception.message || '登录失败'
  }
}

function switchMode() {
  registering.value = !registering.value
  error.value = ''
  confirmPassword.value = ''
  if (registering.value && role.value === 'admin') role.value = 'user'
}

function enterAsGuest() {
  router.push('/')
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <RouterLink class="auth-brand" to="/" aria-label="猫狗宠物之家首页">
        <span class="auth-brand-mark"><PawPrint :size="21" /></span>
        <b>猫狗宠物之家</b>
      </RouterLink>

      <div class="auth-story-copy">
        <span class="auth-story-eyebrow">社区 · 商城 · 服务 · 养护</span>
        <h1>猫狗的日常，<br />从这里继续</h1>
      </div>

      <figure class="auth-postcard">
        <img :src="assetUrl('/images/webphoto/slide5.jpg')" alt="小熊与兔子的卡通插画" />
      </figure>

      <p class="auth-story-note">AI 养护建议仅供参考，紧急情况请联系执业兽医。</p>
    </section>

    <section class="auth-panel">
      <RouterLink class="auth-mobile-brand" to="/" aria-label="猫狗宠物之家首页">
        <span class="auth-brand-mark"><PawPrint :size="19" /></span>
        <b>猫狗宠物之家</b>
      </RouterLink>

      <div class="auth-card">
        <header class="auth-head">
          <h2>{{ registering ? `${activeRole?.label}注册` : `${activeRole?.label}登录` }}</h2>
          <p v-if="registering">填写下面的信息，即可创建账号。</p>
        </header>

        <div class="auth-roles" role="tablist" aria-label="登录身份">
          <button
            v-for="item in roles"
            v-show="!registering || item.value !== 'admin'"
            :key="item.value"
            type="button"
            role="tab"
            :aria-selected="role === item.value"
            :class="{ active: role === item.value }"
            @click="role = item.value"
          >
            <component :is="item.icon" :size="15" />{{ item.label }}
          </button>
        </div>

        <form class="auth-form" @submit.prevent="submit">
          <label v-if="registering" class="auth-field">
            <span>昵称</span>
            <input v-model="nickname" class="auth-input" autocomplete="nickname" placeholder="大家怎么称呼你" />
          </label>

          <label class="auth-field">
            <span>用户名</span>
            <span class="auth-input-wrap">
              <UserRound :size="17" />
              <input v-model="username" class="auth-input" autocomplete="username" placeholder="输入用户名" />
            </span>
          </label>

          <label class="auth-field">
            <span>密码</span>
            <span class="auth-input-wrap">
              <LockKeyhole :size="17" />
              <input
                v-model="password"
                class="auth-input"
                type="password"
                autocomplete="current-password"
                placeholder="输入密码"
              />
            </span>
          </label>

          <label v-if="registering" class="auth-field">
            <span>确认密码</span>
            <span class="auth-input-wrap">
              <LockKeyhole :size="17" />
              <input v-model="confirmPassword" class="auth-input" type="password" autocomplete="new-password" placeholder="再次输入密码" />
            </span>
          </label>

          <label v-if="registering" class="auth-field">
            <span>手机号（选填）</span>
            <span class="auth-input-wrap">
              <UserRound :size="17" />
              <input v-model="phone" class="auth-input" autocomplete="tel" placeholder="便于商家联系" />
            </span>
          </label>

          <p v-if="error" class="auth-error" role="alert">{{ error }}</p>

          <button class="auth-submit" type="submit" :disabled="auth.loading">
            {{ auth.loading ? '处理中…' : registering ? '创建账号' : '登录' }}
            <ArrowRight v-if="!auth.loading" :size="17" />
          </button>
        </form>

        <template v-if="role !== 'admin'">
          <button class="auth-guest" type="button" @click="enterAsGuest">
            <span class="auth-guest-icon"><Compass :size="16" /></span>
            <span class="auth-guest-copy"><b>游客身份进入</b></span>
          </button>

          <button class="auth-switch" type="button" @click="switchMode">
            {{ registering ? '已有账号，返回登录' : '没有账号？立即注册' }}
          </button>
        </template>
      </div>

      <p class="auth-foot">养护问答与社区审核由 DeepSeek 提供能力支持</p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(460px, .9fr);
  background: var(--color-canvas);
}

.auth-story {
  position: relative;
  min-height: 100vh;
  padding: 38px clamp(32px, 5vw, 74px) 32px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(circle at 84% 6%, #fff4d2 0%, transparent 46%),
    linear-gradient(158deg, #fffaf0 0%, #fdf2da 58%, #fbe9c8 100%);
}

.auth-story::after {
  content: '';
  position: absolute;
  left: -110px;
  bottom: -120px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  border: 42px solid rgba(209, 161, 29, .1);
}

.auth-brand,
.auth-mobile-brand {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: fit-content;
  padding: 6px 15px 6px 6px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, .76);
  color: var(--color-text);
  box-shadow: var(--box-shadow-sm);
}

.auth-brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-text);
}

.auth-story-note {
  position: relative;
  z-index: 1;
  margin: 16px 0 0;
  color: var(--color-text-muted);
  font-size: 0.74rem;
}

.auth-story-note {
  position: relative;
  z-index: 1;
  margin: 16px 0 0;
  color: var(--color-text-muted);
  font-size: 0.74rem;
}

.auth-brand b,
.auth-mobile-brand b {
  font-family: var(--font-family-display);
  font-size: 0.98rem;
  font-weight: 700;
}

.auth-story-copy {
  position: relative;
  z-index: 1;
  margin-top: auto;
  max-width: 26em;
}

.auth-story-eyebrow {
  display: inline-block;
  margin-bottom: 14px;
  color: var(--color-primary-strong);
  font-size: 0.72rem;
  font-weight: var(--font-weight-black);
  letter-spacing: .18em;
}

.auth-story-copy h1 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: clamp(2rem, 3.2vw, 2.85rem);
  font-weight: 700;
  line-height: 1.26;
  letter-spacing: -.02em;
}

.auth-postcard {
  position: relative;
  z-index: 1;
  width: min(470px, 88%);
  margin: clamp(26px, 5vh, 46px) 0 auto;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px 32px 32px 10px;
  background: var(--color-surface);
  box-shadow: 0 26px 60px rgba(92, 63, 14, .16);
  transform: rotate(-1.4deg);
}

.auth-postcard img {
  width: 100%;
  height: 292px;
  display: block;
  object-fit: cover;
}

.auth-panel {
  padding: 34px 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: var(--color-surface);
}

.auth-mobile-brand { display: none; }

.auth-card {
  width: min(424px, 100%);
  padding: 30px 30px 26px;
  border: 1px solid var(--color-border);
  border-radius: 26px;
  background: var(--color-white);
  box-shadow: 0 22px 56px rgba(92, 63, 14, .1);
}

.auth-head h2 {
  margin: 0;
  font-family: var(--font-family-display);
  font-size: 1.68rem;
  font-weight: 700;
  letter-spacing: -.01em;
}

.auth-head p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  line-height: 1.7;
}

.auth-head p {
  margin: 8px 0 0;
  color: var(--color-text-muted);
  font-size: 0.82rem;
  line-height: 1.7;
}

.auth-roles {
  margin: 20px 0 18px;
  padding: 4px;
  display: flex;
  gap: 4px;
  border-radius: 14px;
  background: var(--color-surface-warm);
}

.auth-roles button {
  flex: 1;
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
  transition: var(--transition-base);
}

.auth-roles button:hover { color: var(--color-text); }

.auth-roles button.active {
  background: var(--color-white);
  color: var(--color-text);
  box-shadow: 0 4px 12px rgba(92, 63, 14, .1);
}

.auth-form { display: grid; gap: 13px; }

.auth-field { display: block; }

.auth-field > span:first-child {
  display: block;
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: 0.76rem;
  font-weight: var(--font-weight-extra-bold);
}

.auth-input-wrap { position: relative; display: block; }

.auth-input-wrap > svg {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-muted);
  pointer-events: none;
}

.auth-input {
  width: 100%;
  min-height: 47px;
  padding: 0 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface-raised);
  color: var(--color-text);
  font-family: inherit;
  font-size: 0.9rem;
  outline: none;
  transition: var(--transition-base);
}

.auth-input-wrap .auth-input { padding-left: 42px; }

.auth-input::placeholder { color: var(--color-text-muted); opacity: .8; }

.auth-input:focus {
  border-color: var(--color-primary);
  background: var(--color-white);
  box-shadow: 0 0 0 3px rgba(209, 161, 29, .16);
}

.auth-error {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.78rem;
}

.auth-submit {
  width: 100%;
  min-height: 48px;
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #e3b22c, #f2ce56);
  color: #3b2b14;
  font-family: inherit;
  font-size: 0.9rem;
  font-weight: var(--font-weight-extra-bold);
  box-shadow: 0 12px 26px rgba(209, 161, 29, .3);
  transition: var(--transition-base);
}

.auth-submit:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 16px 30px rgba(209, 161, 29, .34); }

.auth-submit:disabled { opacity: .6; cursor: not-allowed; }

.auth-guest {
  width: fit-content;
  margin: 18px auto 0;
  padding: 8px 18px 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--color-surface-warm);
  color: var(--color-text-secondary);
  font-family: inherit;
  transition: var(--transition-base);
}

.auth-guest:hover {
  border-color: var(--color-border-primary-soft);
  background: var(--color-primary-soft);
  color: var(--color-text);
}

.auth-guest-icon {
  display: inline-flex;
  color: var(--color-primary-strong);
}

.auth-guest-copy b {
  font-size: 0.8rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: .01em;
}

.auth-switch {
  width: 100%;
  margin-top: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary-strong);
  font-family: inherit;
  font-size: 0.8rem;
  font-weight: var(--font-weight-extra-bold);
}

.auth-switch:hover { text-decoration: underline; }

.auth-foot {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  text-align: center;
}

.auth-foot {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  text-align: center;
}

@media (max-width: 960px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-story { display: none; }
  .auth-panel { min-height: 100vh; padding: 26px 20px 34px; gap: 14px; }
  .auth-mobile-brand { display: inline-flex; align-self: center; }
  .auth-card { padding: 24px 20px 22px; border: 0; box-shadow: none; background: transparent; }
  .auth-head h2 { font-size: 1.45rem; }
}
</style>
