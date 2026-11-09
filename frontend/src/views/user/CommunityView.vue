<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ImagePlus, PenLine, Search, X } from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { parseJson } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import { useAuthStore } from '../../stores/auth'
import { useUserAccess } from '../../composables/useUserAccess'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import PostMasonryGrid from '../../components/PostMasonryGrid.vue'
import BackToTopButton from '../../components/BackToTopButton.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import LoadingPanel from '../../components/LoadingPanel.vue'
import UserProfileDrawer from '../../components/UserProfileDrawer.vue'
import EmptyState from '../../components/EmptyState.vue'

const ui = useUiStore()
const auth = useAuthStore()
const profileTarget = ref(null)
function openProfile(post) {
  // 点自己的头像/名字不打开“我的主页”抽屉
  if (Number(post.userId) === Number(auth.user?.id)) return
  profileTarget.value = { userId: post.userId, nickname: post.nickname || '社区用户', avatar: post.avatar || '' }
}
const { isUser, requireUser } = useUserAccess()
const categories = ['全部', '晒宠', '求助', '领养', '好物', '日常']
const currentCategory = ref('全部')
const keyword = ref('')

const page = ref(0)
const pageSize = 6
const posts = ref([])
const totalPages = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const loadSentinel = ref(null)
let loadObserver = null
let requestSequence = 0
const likedIds = ref(new Set())
const publishOpen = ref(false)
const publishing = ref(false)
const imageFile = ref(null)
const imagePreview = ref('')
const form = ref({ category: '晒宠', title: '', content: '' })
const publishGuard = useDialogDraftGuard({
  getSnapshot: () => ({
    category: form.value.category,
    title: form.value.title,
    content: form.value.content,
    imageName: imageFile.value?.name || ''
  }),
  getBusy: () => publishing.value,
  close: closePublish
})

const imageInput = ref(null)
const canPublish = computed(() => form.value.content.trim().length > 0)
const hasMore = computed(() => page.value < totalPages.value)

async function loadLikes() {
  if (!isUser.value) {
    likedIds.value = new Set()
    return
  }
  try {
    const result = await api.get('/user/likedIds')
    likedIds.value = new Set(parseJson(result.data?.likedIds, []))
  } catch (error) {
    // 点赞状态是增强信息，加载失败时仍允许浏览社区。
    likedIds.value = new Set()
  }
}

async function loadPage({ reset = false } = {}) {
  if (!reset && (loading.value || loadingMore.value || !hasMore.value)) return
  const requestId = ++requestSequence
  const targetPage = reset ? 1 : page.value + 1
  if (reset) {
    loading.value = true
    loadingMore.value = false
  } else {
    loadingMore.value = true
  }
  try {
    const params = { page: targetPage, pageSize }
    if (currentCategory.value !== '全部') params.category = currentCategory.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const result = await api.get(`/posts${queryString(params)}`)
    if (requestId !== requestSequence) return
    const nextPosts = result.data?.list || []
    if (reset) {
      posts.value = nextPosts
    } else {
      const existingIds = new Set(posts.value.map((post) => post.id))
      posts.value = [
        ...posts.value,
        ...nextPosts.filter((post) => !existingIds.has(post.id))
      ]
    }
    page.value = targetPage
    totalPages.value = result.data?.totalPages || 0
  } catch (error) {
    if (requestId === requestSequence) {
      ui.toast(error.message, 'error')
    }
  } finally {
    if (requestId === requestSequence) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function resetPosts() {
  requestSequence += 1
  posts.value = []
  page.value = 0
  totalPages.value = 0
  await loadPage({ reset: true })
}

function search() {
  resetPosts()
}

async function toggleLike(post) {
  if (!await requireUser({ action: '点赞帖子' })) return
  try {
    const result = await api.post(`/posts/${post.id}/like`)
    const liked = result.data?.liked
    const next = new Set(likedIds.value)
    if (liked) next.add(post.id)
    else next.delete(post.id)
    likedIds.value = next
    post.likes = result.data?.likes ?? post.likes
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function openPublish() {
  if (!await requireUser({ action: '发布帖子' })) return
  publishOpen.value = true
  publishGuard.markClean()
}

function pickImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ui.toast('图片大小不能超过 5MB', 'warning')
    return
  }
  imageFile.value = file
  imagePreview.value = URL.createObjectURL(file)
}

function clearImage() {
  if (imagePreview.value) URL.revokeObjectURL(imagePreview.value)
  imageFile.value = null
  imagePreview.value = ''
  if (imageInput.value) imageInput.value.value = ''
}

function closePublish() {
  publishOpen.value = false
  form.value = { category: '晒宠', title: '', content: '' }
  clearImage()
}

async function publish() {
  if (!canPublish.value) {
    ui.toast('帖子正文不能为空', 'warning')
    return
  }
  if (publishing.value) return
  const content = form.value.content.trim()
  publishing.value = true
  try {
    let imageUrl = null
    if (imageFile.value) {
      const upload = await api.upload('/post/image', imageFile.value)
      imageUrl = upload.data
    }
    const result = await api.post('/posts', {
      ...form.value,
      title: form.value.title.trim(),
      content,
      image: imageUrl
    })
    const createdStatus = result.data?.status
    const toastType = createdStatus === 'normal' ? 'success' : 'warning'
    ui.toast(result.msg || '发布成功', toastType)
    closePublish()
    await resetPosts()
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    publishing.value = false
  }
}

watch(currentCategory, search)
watch(isUser, async (hasUserAccess) => {
  if (hasUserAccess) {
    await loadLikes()
    return
  }
  likedIds.value = new Set()
})
watch(loadSentinel, (element, previous) => {
  if (previous) loadObserver?.unobserve(previous)
  if (element) loadObserver?.observe(element)
}, { flush: 'post' })
onMounted(async () => {
  loadObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) loadPage()
  }, { rootMargin: '320px 0px' })
  await Promise.all([loadPage({ reset: true }), loadLikes()])
})
onBeforeUnmount(() => {
  loadObserver?.disconnect()
})
</script>

<template>
  <div class="page-layout">
    <header class="page-heading">
      <div>
        <h1>宠物社区</h1>
      </div>
      <button class="btn btn-primary" type="button" @click="openPublish">
        <PenLine :size="17" />发布帖子
      </button>
    </header>
    <div class="community-toolbar">
      <div class="category-tabs">
        <button
          v-for="item in categories"
          :key="item"
          type="button"
          :class="{ active: currentCategory === item }"
          @click="currentCategory = item"
        >
          {{ item }}
        </button>
      </div>
      <form class="community-search" @submit.prevent="search">
        <Search :size="17" />
        <input v-model="keyword" placeholder="搜索帖子或作者" aria-label="搜索帖子或作者" />
      </form>
    </div>
    <LoadingPanel v-if="loading" label="正在浏览社区" />
    <EmptyState v-else-if="!posts.length" title="这里还很安静" />
    <template v-else>
      <PostMasonryGrid
        :posts="posts"
        :liked-ids="likedIds"
        display-mode="community"
        @like="toggleLike"
        @author="openProfile"
      />
      <div v-if="hasMore" ref="loadSentinel" class="infinite-status">
        <span v-if="loadingMore" class="spinner" />
        <span>{{ loadingMore ? '正在加载更多' : '继续下滑加载更多' }}</span>
      </div>
      <p v-else class="infinite-status">已显示全部帖子</p>
    </template>

    <ModalDialog
      :open="publishOpen"
      title="发布到社区"
      width="620px"
      :close-disabled="publishing"
      @close="publishGuard.requestClose"
    >
      <form @submit.prevent="publish">
        <div class="field">
          <label>内容分类</label>
          <div class="publish-cats">
            <button
              v-for="item in categories.slice(1)"
              :key="item"
              type="button"
              :class="{ active: form.category === item }"
              @click="form.category = item"
            >
              {{ item }}
            </button>
          </div>
        </div>
        <label class="field"><span>标题（选填）</span><input v-model="form.title" class="input" maxlength="200" placeholder="给这次分享起个标题" /></label>
        <label class="field"><span>正文（必填）</span><textarea v-model="form.content" class="textarea" maxlength="4000" required aria-required="true" placeholder="说说发生了什么，或分享你的经验…"></textarea></label>
        <div class="field">
          <span>图片（选填，最多 5MB）</span>
          <input ref="imageInput" type="file" accept="image/*" hidden @change="pickImage" />
          <button v-if="!imagePreview" class="upload-placeholder" type="button" @click="imageInput?.click()">
            <ImagePlus :size="23" /><span>添加一张照片</span>
          </button>
          <div v-else class="image-preview">
            <img :src="imagePreview" alt="待发布图片预览" />
            <button type="button" aria-label="移除图片" @click="clearImage"><X :size="17" /></button>
          </div>
        </div>
      </form>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="publishGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="!canPublish || publishing" @click="publish">
          {{ publishing ? '正在审核…' : '发布' }}
        </button>
      </template>
    </ModalDialog>
    <BackToTopButton />
    <UserProfileDrawer
      :open="Boolean(profileTarget)"
      :user-id="profileTarget?.userId"
      :nickname="profileTarget?.nickname || 'TA'"
      :avatar="profileTarget?.avatar || ''"
      @close="profileTarget = null"
    />
  </div>
</template>

<style scoped>
.community-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) minmax(180px, 240px); align-items: center; gap: 18px; margin-bottom: 24px; }
.category-tabs { display: flex; gap: 5px; overflow-x: auto; padding-bottom: 2px; }
.category-tabs button { border: 1px solid var(--color-border); background: var(--color-white); border-radius: 999px; padding: 7px 14px; min-height: 36px; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); white-space: nowrap; }
.category-tabs button.active { background: var(--color-primary-soft); border-color: var(--color-border-primary-soft); color: var(--color-primary-strong); }
.community-search { min-width: 0; min-height: 42px; display: flex; align-items: center; gap: 8px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-white); padding: 0 14px; }

.community-search svg { color: var(--color-text-muted); }
.community-search input { min-width: 0; flex: 1; border: 0; background: transparent; outline: 0; }
.publish-cats { display: flex; flex-wrap: wrap; gap: 6px; }
.publish-cats button { border: 1px solid var(--color-border); background: var(--color-white); border-radius: 999px; padding: 6px 12px; color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: var(--font-weight-bold); }
.publish-cats button.active { background: var(--color-text); border-color: var(--color-text); color: var(--color-white); }
.upload-placeholder { min-height: 110px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; width: 100%; border: 1.5px dashed var(--color-border-strong); border-radius: var(--radius-md); background: var(--color-surface-warm); color: var(--color-text-muted); }
.image-preview { position: relative; width: 100%; max-height: 260px; overflow: hidden; border-radius: var(--radius-md); }
.image-preview img { width: 100%; max-height: 260px; object-fit: cover; }
.image-preview button { position: absolute; top: 9px; right: 9px; width: 34px; height: 34px; border: 0; border-radius: 50%; background: var(--color-overlay-dark-soft); color: var(--color-white); display: grid; place-items: center; }
@media (max-width: 760px) {
  .community-toolbar { grid-template-columns: 1fr; align-items: stretch; }
}
</style>
