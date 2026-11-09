<script setup>
import { computed } from 'vue'
import { LockKeyhole, ShieldCheck } from 'lucide-vue-next'
import ModalDialog from './ModalDialog.vue'
import { useUserAccessPromptStore } from '../stores/userAccessPrompt'
import { useUserAccess } from '../composables/useUserAccess'

const promptStore = useUserAccessPromptStore()
const { redirectToLogin } = useUserAccess()
const prompt = computed(() => promptStore.prompt)
const isGuest = computed(() => prompt.value?.kind === 'guest')
const roleLabel = computed(() => prompt.value?.role === 'admin' ? '管理员账号' : '商家账号')
const title = computed(() => (
  isGuest.value ? '需要登录普通用户账号' : '当前账号无法使用此功能'
))

function goToLogin() {
  const redirect = prompt.value?.redirect || '/'
  promptStore.close()
  redirectToLogin({ redirect })
}
</script>

<template>
  <ModalDialog
    :open="Boolean(prompt)"
    :title="title"
    width="480px"
    :close-on-escape="isGuest"
    :close-on-backdrop="isGuest"
    @close="promptStore.close"
  >
    <div v-if="prompt" class="access-prompt-copy">
      <span class="access-prompt-icon" aria-hidden="true">
        <LockKeyhole v-if="isGuest" :size="24" />
        <ShieldCheck v-else :size="24" />
      </span>
      <p v-if="isGuest">
        「{{ prompt.action }}」需要登录普通用户账号，登录后将返回当前页面。
      </p>
      <p v-else>
        当前登录的是{{ roleLabel }}，用户端的「{{ prompt.action }}」仅支持普通用户账号。
        请使用普通用户账号后再操作。
      </p>
    </div>
    <template v-if="isGuest" #footer>
      <button class="btn btn-ghost" type="button" @click="promptStore.close">取消</button>
      <button class="btn btn-primary" type="button" @click="goToLogin">去登录</button>
    </template>
  </ModalDialog>
</template>

<style scoped>
.access-prompt-copy {
  display: flex;
  align-items: flex-start;
  gap: 13px;
}

.access-prompt-copy p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.75;
}

.access-prompt-icon {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}
</style>
