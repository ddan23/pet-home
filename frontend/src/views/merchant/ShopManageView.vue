<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Clock3, ImagePlus, MapPin, PenLine, Phone, Plus, Power, Star, Store
} from 'lucide-vue-next'
import { api, assetUrl } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import { shopStatus } from '../../utils/merchantStatus'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const ui = useUiStore()
const organization = ref({ flagship: null, serviceOutlets: [] })
const loading = ref(true)
const saving = ref(false)
const uploadingLogo = ref(false)
const modalOpen = ref(false)
const editMode = ref('flagship')
const editingId = ref(null)
const form = ref(createEmptyForm())
const logoInput = ref(null)
const shopGuard = useDialogDraftGuard({
  getSnapshot: () => form.value,
  getBusy: () => saving.value || uploadingLogo.value,
  close: closeShop
})

const flagship = computed(() => organization.value.flagship)
const serviceOutlets = computed(() => organization.value.serviceOutlets || [])
const shops = computed(() => (
  flagship.value ? [flagship.value, ...serviceOutlets.value] : serviceOutlets.value
))

const editingShop = computed(() => (
  editingId.value
    ? shops.value.find((shop) => shop.id === editingId.value) || null
    : null
))

function createEmptyForm() {
  return {
    name: '',
    shopCode: '',
    contactPhone: '',
    logo: '',
    description: '',
    address: '',
    businessHours: ''
  }
}

async function load() {
  loading.value = true
  try {
    const result = await api.get('/merchant/organization')
    organization.value = {
      flagship: result.data?.flagship || null,
      serviceOutlets: result.data?.serviceOutlets || []
    }
  } catch (error) {
    ui.toast(error.message || '读取店铺资料失败', 'error')
  } finally {
    loading.value = false
  }
}

function openCreateFlagship() {
  if (flagship.value) {
    ui.toast('每个商家只能创建一个商城店铺', 'warning')
    return
  }
  editMode.value = 'flagship'
  editingId.value = null
  form.value = createEmptyForm()
  modalOpen.value = true
  shopGuard.markClean()
}

function openCreateOutlet() {
  if (!flagship.value) {
    ui.toast('请先创建商城店铺', 'warning')
    return
  }
  editMode.value = 'outlet'
  editingId.value = null
  form.value = createEmptyForm()
  modalOpen.value = true
  shopGuard.markClean()
}

function openEdit(shop) {
  editMode.value = shop.type === 'mall' ? 'flagship' : 'outlet'
  editingId.value = shop.id
  form.value = {
    id: shop.id,
    name: shop.name || '',
    shopCode: shop.shopCode || '',
    contactPhone: shop.contactPhone || '',
    logo: shop.logo || '',
    description: shop.description || '',
    address: shop.address || '',
    businessHours: shop.businessHours || ''
  }
  modalOpen.value = true
  shopGuard.markClean()
}

function closeShop() {
  modalOpen.value = false
}

async function uploadLogo(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadingLogo.value = true
  try {
    const result = await api.upload('/logo/image', file)
    form.value.logo = result.data
    ui.toast('店铺标识已上传', 'success')
  } catch (error) {
    ui.toast(error.message || '店铺标识上传失败', 'error')
  } finally {
    uploadingLogo.value = false
    if (logoInput.value) logoInput.value.value = ''
  }
}

async function save() {
  if (saving.value) return
  if (!form.value.name.trim()) {
    ui.toast('请填写店铺名称', 'warning')
    return
  }
  if (!form.value.address.trim()) {
    ui.toast('请填写店铺地址', 'warning')
    return
  }
  if (!form.value.businessHours.trim()) {
    ui.toast('请填写营业时间', 'warning')
    return
  }
  saving.value = true
  const payload = {
    ...form.value,
    name: form.value.name.trim(),
    shopCode: form.value.shopCode.trim(),
    contactPhone: form.value.contactPhone.trim(),
    address: form.value.address.trim(),
    businessHours: form.value.businessHours.trim()
  }
  try {
    const result = editMode.value === 'flagship'
      ? editingId.value
        ? await api.put('/merchant/flagship', payload)
        : await api.post('/merchant/flagship', payload)
      : editingId.value
        ? await api.put(`/merchant/service-outlets/${editingId.value}`, payload)
        : await api.post('/merchant/service-outlets', payload)
    ui.toast(result.msg || '保存成功', 'success')
    closeShop()
    await load()
  } catch (error) {
    ui.toast(error.message || '保存店铺失败', 'error')
  } finally {
    saving.value = false
  }
}

async function toggleOutlet(shop) {
  if (!['open', 'closed'].includes(shop.status)) {
    ui.toast('店铺审核通过后才能切换营业状态', 'warning')
    return
  }
  const nextStatus = shop.status === 'open' ? 'closed' : 'open'
  const confirmed = await ui.confirm({
    message: `确定${nextStatus === 'open' ? '恢复营业' : '暂停营业'}「${shop.name}」吗？`
  })
  if (!confirmed) return
  try {
    await api.put(`/merchant/service-outlets/${shop.id}/status`, { status: nextStatus })
    ui.toast('店铺状态已更新', 'success')
    await load()
  } catch (error) {
    ui.toast(error.message || '更新店铺状态失败', 'error')
  }
}

async function toggleFlagship(shop) {
  if (!['open', 'closed'].includes(shop.status)) {
    ui.toast('店铺审核通过后才能切换营业状态', 'warning')
    return
  }
  const nextStatus = shop.status === 'open' ? 'closed' : 'open'
  const confirmed = await ui.confirm({
    message: nextStatus === 'closed'
      ? `确定暂停营业「${shop.name}」吗？暂停后商城商品将对用户隐藏，需恢复营业才能继续管理商城。`
      : `确定恢复营业「${shop.name}」吗？`
  })
  if (!confirmed) return
  try {
    await api.put('/merchant/flagship/status', { status: nextStatus })
    ui.toast('店铺状态已更新', 'success')
    await load()
  } catch (error) {
    ui.toast(error.message || '更新店铺状态失败', 'error')
  }
}

function shopRating(value) {
  const score = Number(value || 0)
  return score > 0 ? score.toFixed(1) : '暂无评分'
}

function shopTypeLabel(shop) {
  return shop.type === 'mall' ? '商城店铺' : '服务店铺'
}

onMounted(load)
</script>

<template>
  <div class="console-page store-page">
    <header class="console-head">
      <div>
        <h1>店铺管理</h1>
      </div>
      <div class="console-head-actions">
        <button
          class="btn btn-ghost"
          type="button"
          :disabled="Boolean(flagship)"
          :title="flagship ? '已有商城店铺，每个商家仅能创建一个' : '新增商城店铺'"
          @click="openCreateFlagship"
        >
          <Plus :size="16" />新增商城店铺
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!flagship"
          :title="flagship ? '新增服务店铺' : '请先创建商城店铺'"
          @click="openCreateOutlet"
        >
          <Plus :size="16" />新增服务店铺
        </button>
      </div>
    </header>

    <LoadingPanel v-if="loading" label="正在读取店铺资料" />
    <template v-else>
      <EmptyState
        v-if="!flagship"
        title="还没有商城店铺"
        description="商城店铺是品牌的唯一线上店铺。创建并通过审核后，才能发布商品和新增服务店铺。"
      >
        <button class="btn btn-primary" type="button" @click="openCreateFlagship">
          新增商城店铺
        </button>
      </EmptyState>

      <section v-else class="shop-grid">
        <article
          v-for="shop in shops"
          :key="shop.id"
          class="shop-card"
          :class="{
            'is-mall': shop.type === 'mall',
            'is-inactive': shop.status !== 'open'
          }"
        >
          <header class="shop-card-head">
            <span class="shop-logo">
              <img v-if="shop.logo" :src="assetUrl(shop.logo)" :alt="shop.name" />
              <Store v-else :size="21" />
            </span>
            <div class="shop-identity">
              <div class="shop-tags">
                <span class="status-label" :class="shopStatus(shop.status).tone">
                  {{ shopStatus(shop.status).label }}
                </span>
                <span class="shop-type">{{ shopTypeLabel(shop) }}</span>
              </div>
              <h3>{{ shop.name }}</h3>
              <span class="shop-rating" title="店铺评分由用户评价产生，商家不可修改">
                <Star :size="13" />
                <b>{{ shopRating(shop.rating) }}</b>
                <em>店铺评分</em>
              </span>
            </div>
          </header>

          <div class="shop-meta">
            <span><MapPin :size="13" />{{ shop.address || '地址未填写' }}</span>
            <span><Clock3 :size="13" />{{ shop.businessHours || '营业时间未填写' }}</span>
            <span><Phone :size="13" />{{ shop.contactPhone || '联系电话未填写' }}</span>
          </div>

          <p class="shop-description">{{ shop.description || '还没有填写店铺介绍' }}</p>

          <footer>
            <div class="shop-actions">
              <button class="tiny-btn" type="button" @click="openEdit(shop)">
                <PenLine :size="13" />{{ shop.status === 'rejected' ? '修改并重新提交' : '编辑' }}
              </button>
              <button
                v-if="shop.type === 'mall' && ['open', 'closed'].includes(shop.status)"
                class="tiny-btn"
                type="button"
                @click="toggleFlagship(shop)"
              >
                <Power :size="13" />{{ shop.status === 'open' ? '暂停营业' : '恢复营业' }}
              </button>
              <button
                v-if="shop.type === 'service' && ['open', 'closed'].includes(shop.status)"
                class="tiny-btn"
                type="button"
                @click="toggleOutlet(shop)"
              >
                <Power :size="13" />{{ shop.status === 'open' ? '暂停营业' : '恢复营业' }}
              </button>
            </div>
          </footer>
        </article>

        <button
          v-if="!serviceOutlets.length"
          class="shop-card shop-card-empty"
          type="button"
          @click="openCreateOutlet"
        >
          <span class="empty-mark"><Plus :size="20" /></span>
          <b>新增服务店铺</b>
          <small>按城市开设线下门店，独立维护服务、排班与预约。</small>
        </button>
      </section>
    </template>

    <ModalDialog
      :open="modalOpen"
      :title="editMode === 'flagship'
        ? editingId ? '编辑商城店铺' : '创建商城店铺'
        : editingId ? '编辑服务店铺' : '新增服务店铺'"
      :close-disabled="saving || uploadingLogo"
      @close="shopGuard.requestClose"
    >
      <div class="form-row">
        <label class="field" :class="{ 'field-full': !editingShop }">
          <span>{{ editMode === 'flagship' ? '商城店铺名称' : '服务店铺名称' }}</span>
          <input v-model="form.name" class="input" maxlength="100" />
        </label>
        <div v-if="editingShop" class="field">
          <span>店铺评分</span>
          <div class="rating-readonly">
            <Star :size="15" />
            <b>{{ shopRating(editingShop.rating) }}</b>
          </div>
        </div>
      </div>
      <div class="form-row">
        <label class="field">
          <span>营业时间</span>
          <input v-model="form.businessHours" class="input" placeholder="例如 09:00-20:00" />
        </label>
        <label class="field">
          <span>联系电话</span>
          <input v-model="form.contactPhone" class="input" maxlength="20" />
        </label>
      </div>
      <label class="field">
        <span>店铺地址</span>
        <input v-model="form.address" class="input" />
      </label>
      <label class="field">
        <span>{{ editMode === 'flagship' ? '店铺介绍' : '门店介绍' }}</span>
        <textarea v-model="form.description" class="textarea" />
      </label>
      <div class="field">
        <span>店铺标识</span>
        <input ref="logoInput" type="file" accept="image/*" hidden @change="uploadLogo" />
        <button class="logo-upload" type="button" :disabled="uploadingLogo" @click="logoInput?.click()">
          <img v-if="form.logo" :src="assetUrl(form.logo)" alt="店铺标识预览" />
          <span v-else><ImagePlus :size="22" />{{ uploadingLogo ? '正在上传' : '上传店铺标识' }}</span>
        </button>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" :disabled="saving || uploadingLogo" @click="shopGuard.requestClose">
          取消
        </button>
        <button class="btn btn-primary" type="button" :disabled="saving || uploadingLogo" @click="save">
          {{ saving ? '正在保存' : editingId ? '保存修改' : '提交审核' }}
        </button>
      </template>
    </ModalDialog>
  </div>
</template>

<style scoped>
.store-page { display: grid; gap: var(--console-gap); }

.field-full { grid-column: 1 / -1; }

/* 编辑弹窗：只读店铺评分（占原店铺编号位置） */
.rating-readonly {
  min-height: 44px;
  padding: 0 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1.5px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-warm);
  color: var(--color-text);
  font-size: var(--font-size-sm);
  cursor: default;
}

.rating-readonly svg {
  color: var(--color-primary-strong);
  fill: var(--color-primary);
}

.rating-readonly b {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-extra-bold);
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.shop-card {
  min-width: 0;
  min-height: 232px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--color-success);
  border-radius: var(--radius-console-card);
  background: var(--color-surface);
  box-shadow: var(--box-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 11px;
  text-align: left;
}
.shop-card.is-mall {
  border-left-color: var(--color-accent);
  background: linear-gradient(120deg, var(--color-surface), var(--color-surface-ivory));
}
.shop-card.is-inactive { border-left-color: var(--color-border-strong); }

.shop-card-head { display: flex; align-items: flex-start; gap: 11px; }
.shop-logo {
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  border-radius: 14px;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--color-surface-warm);
  color: var(--color-primary-strong);
}
.shop-card.is-mall .shop-logo { background: var(--color-accent-soft); color: var(--color-accent-strong); }
.shop-logo img { width: 100%; height: 100%; object-fit: cover; }

.shop-identity { min-width: 0; }
.shop-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.shop-identity h3 {
  margin: 5px 0 2px;
  font-size: var(--font-size-xl);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.shop-type { color: var(--color-text-muted); font-size: var(--font-size-sm); }
/* 店铺评分：只读展示（由用户评价产生，商家不可修改） */
.shop-rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.shop-rating svg {
  color: var(--color-primary-strong);
  fill: var(--color-primary);
}

.shop-rating b {
  color: var(--color-text);
  font-size: 0.8rem;
  font-weight: var(--font-weight-extra-bold);
}

.shop-rating em {
  font-style: normal;
}

.shop-meta { display: grid; gap: 5px; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.shop-meta span { display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.shop-meta svg { flex: 0 0 auto; color: var(--color-text-muted); }

.shop-description {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}

.shop-card footer {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-soft);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.shop-actions { display: flex; gap: 5px; flex-wrap: wrap; }

.tiny-btn {
  min-height: 30px;
  padding: 5px 9px;
  border: 1px solid var(--color-border);
  border-radius: 7px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
}
.tiny-btn:hover { border-color: var(--color-border-strong); background: var(--color-surface-warm); }

.shop-card-empty {
  border: 1.5px dashed var(--color-border-strong);
  border-left: 4px dashed var(--color-border-strong);
  background: transparent;
  color: var(--color-text-muted);
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
}
.shop-card-empty:hover { background: var(--color-surface-ivory); }
.shop-card-empty b { color: var(--color-text); font-size: var(--font-size-base); }
.shop-card-empty small { max-width: 240px; line-height: 1.6; font-size: var(--font-size-sm); }
.shop-card-empty .empty-mark {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--color-primary-soft);
  color: var(--color-primary-strong);
}

.status-label {
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--color-surface-warm);
  color: var(--color-text-secondary);
  display: inline-block;
  font-size: 0.75rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
}
.status-label.success { background: var(--color-success-soft); color: var(--color-success); }
.status-label.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.status-label.info { background: var(--color-info-soft); color: var(--color-info); }
.status-label.danger { background: var(--color-danger-soft); color: var(--color-danger); }

.logo-upload {
  width: 100%;
  min-height: 100px;
  border: 1.5px dashed var(--color-border-strong);
  border-radius: 14px;
  background: var(--color-surface-warm);
  color: var(--color-text-muted);
}
.logo-upload img { width: 100%; max-height: 150px; object-fit: contain; }
.logo-upload span { display: flex; align-items: center; justify-content: center; gap: 8px; }

@media (max-width: 1240px) {
  .shop-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .shop-grid { grid-template-columns: 1fr; }
  .shop-card { min-height: 0; }
  .shop-card footer { align-items: flex-start; }
  .shop-actions { width: 100%; }
}
</style>
