<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CalendarCheck, Camera, Heart, KeyRound, MapPin, PawPrint, PenLine, Plus,
  ShieldCheck, ShoppingBag, Trash2, UserRound
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { formatPrice, parseJson } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import { useUserAccess } from '../../composables/useUserAccess'
import { useDialogDraftGuard } from '../../composables/useDialogDraftGuard'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import ModalDialog from '../../components/ModalDialog.vue'
import MyCommentCard from '../../components/MyCommentCard.vue'
import OrderRecordCard from '../../components/OrderRecordCard.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import PostMasonryGrid from '../../components/PostMasonryGrid.vue'
import UserProfileDrawer from '../../components/UserProfileDrawer.vue'

const ORDER_PAGE_SIZE = 10
const POST_PAGE_SIZE = 12
const COMMENT_PAGE_SIZE = 8
const SOCIAL_PAGE_SIZE = 20
const MOBILE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const roleLabels = {
  user: '普通用户',
  business: '商家',
  admin: '管理员'
}
const tabs = [
  { id: 'profile', label: '个人资料', icon: UserRound },
  { id: 'pets', label: '我的宠物', icon: PawPrint },
  { id: 'content', label: '我的社区', icon: Heart },
  { id: 'orders', label: '商城订单', icon: ShoppingBag },
  { id: 'appointments', label: '服务预约', icon: CalendarCheck },
  { id: 'addresses', label: '收货地址', icon: MapPin }
]
const orderStatuses = new Set(['', 'pending_payment', 'paid', 'shipped', 'completed', 'cancelled'])
const appointmentStatuses = new Set(['', 'confirmed', 'completed', 'cancelled'])
const auth = useAuthStore()
const ui = useUiStore()
const { isUser } = useUserAccess()
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const user = ref(null)
const pets = ref([])
const petsLoading = ref(false)
const mallOrders = ref([])
const mallOrderTotal = ref(0)
const mallOrderPage = ref(1)
const mallOrderTotalPages = ref(1)
const mallOrdersLoading = ref(false)
const appointments = ref([])
const appointmentTotal = ref(0)
const appointmentPage = ref(1)
const appointmentTotalPages = ref(1)
const appointmentsLoading = ref(false)
const myPosts = ref([])
const myPostTotal = ref(0)
const myPostPage = ref(1)
const myPostTotalPages = ref(1)
const myPostsLoading = ref(false)
const likedPosts = ref([])
const likedPostTotal = ref(0)
const likedPostPage = ref(1)
const likedPostTotalPages = ref(1)
const likedPostsLoading = ref(false)
const likedPostBusyId = ref(null)
const myComments = ref([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentTotalPages = ref(1)
const commentLoading = ref(false)
const following = ref([])
const followers = ref([])
const socialPage = ref(1)
const socialTotalPages = ref(1)
const socialLoading = ref(false)
const socialLoadingMore = ref(false)
let socialRequestSequence = 0
const addresses = ref([])
const addressesLoading = ref(false)
const profileOpen = ref(false)
const petOpen = ref(false)
const addressOpen = ref(false)
const passwordOpen = ref(false)
const profileSaving = ref(false)
const petSaving = ref(false)
const addressSaving = ref(false)
const passwordSaving = ref(false)
const avatarUploading = ref(false)
const petPhotoUploading = ref(false)
const orderActionBusyId = ref(null)
const socialOpen = ref(false)
const socialMode = ref('following')
const socialProfileTarget = ref(null)
const contentMode = ref('posts')
const profileForm = ref({ nickname: '', phone: '', email: '', city: '' })
const passwordForm = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const petForm = ref({})
const editingPetIndex = ref(-1)
const editingAddressIndex = ref(-1)
const addressForm = ref({
  id: '',
  name: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: false
})
const avatarInput = ref(null)
const petPhotoInput = ref(null)
const contentScrollRef = ref(null)
const activeTab = ref(resolveTab(route.query.tab))
const mallOrderStatus = ref(resolveStatus(
  activeTab.value,
  route.query.status,
  orderStatuses
))
const appointmentStatus = ref(resolveStatus(
  activeTab.value,
  route.query.status,
  appointmentStatuses
))
const profileGuard = useDialogDraftGuard({
  getSnapshot: () => profileForm.value,
  getBusy: () => profileSaving.value,
  close: closeProfile
})
const passwordGuard = useDialogDraftGuard({
  getSnapshot: () => passwordForm.value,
  getBusy: () => passwordSaving.value,
  close: closePassword
})
const petGuard = useDialogDraftGuard({
  getSnapshot: () => ({
    editingIndex: editingPetIndex.value,
    pet: petForm.value
  }),
  getBusy: () => petSaving.value,
  close: closePet
})
const addressGuard = useDialogDraftGuard({
  getSnapshot: () => addressForm.value,
  getBusy: () => addressSaving.value,
  close: closeAddress
})
const roleLabel = computed(() => roleLabels[user.value?.role] || user.value?.role || '普通用户')
const petFormTitle = computed(() => (
  editingPetIndex.value >= 0 ? '编辑宠物档案' : '添加宠物'
))
const addressFormTitle = computed(() => (
  editingAddressIndex.value >= 0 ? '编辑收货地址' : '添加收货地址'
))
const socialUsers = computed(() => (
  socialMode.value === 'following' ? following.value : followers.value
))
const socialTitle = computed(() => (
  socialMode.value === 'following' ? '我关注的人' : '关注我的人'
))
const socialHasMore = computed(() => socialPage.value < socialTotalPages.value)
const likedPostIds = computed(() => new Set(likedPosts.value.map((post) => post.id)))
const todayDate = new Date().toLocaleDateString('en-CA')
const returnTarget = computed(() => resolveReturnPath(route.query.return))
const returnHint = computed(() => (
  activeTab.value === 'addresses'
    ? '地址保存后可返回刚才的商品结算页面。'
    : '宠物档案保存后可返回刚才的预约页面。'
))

async function load() {
  if (!isUser.value) {
    resetPrivateState()
    return
  }
  loading.value = true
  try {
    const userLoaded = await loadCurrentUser()
    if (userLoaded) {
      await loadActiveTab()
    }
  } finally {
    loading.value = false
  }
}

async function loadCurrentUser() {
  try {
    const result = await api.get('/user/info')
    if (!isUser.value) return false
    user.value = result.data
    auth.updateProfile(result.data)
    updateProfileForm()
    return true
  } catch (error) {
    if (isUser.value) ui.toast(error.message || '读取个人资料失败', 'error')
    return false
  }
}

async function loadActiveTab() {
  switch (activeTab.value) {
    case 'profile':
    case 'pets':
      await loadPets()
      break
    case 'content':
      await loadContentMode()
      break
    case 'orders':
      await loadMallOrders(1)
      break
    case 'appointments':
      await loadAppointments(1)
      break
    case 'addresses':
      await loadAddresses()
      break
    default:
      break
  }
}

async function loadContentMode() {
  if (contentMode.value === 'posts') {
    await Promise.allSettled([
      loadMyPosts(1),
      loadLikedPosts(1),
      loadMyComments(1)
    ])
  } else if (contentMode.value === 'likes') {
    await loadLikedPosts(1)
  } else {
    await loadMyComments(1)
  }
}

async function loadPets() {
  if (!isUser.value || petsLoading.value) return
  petsLoading.value = true
  try {
    const result = await api.get('/user/pets')
    pets.value = parseJson(result.data?.pets, [])
  } catch (error) {
    ui.toast(error.message || '读取宠物档案失败', 'error')
  } finally {
    petsLoading.value = false
  }
}

async function loadAddresses() {
  if (!isUser.value || addressesLoading.value) return
  addressesLoading.value = true
  try {
    const result = await api.get('/user/addresses')
    addresses.value = normalizeAddresses(parseJson(result.data?.addresses, []))
  } catch (error) {
    ui.toast(error.message || '读取收货地址失败', 'error')
  } finally {
    addressesLoading.value = false
  }
}

async function loadMallOrders(page = mallOrderPage.value) {
  if (!isUser.value || mallOrdersLoading.value) return
  const targetPage = normalizePage(page)
  mallOrdersLoading.value = true
  try {
    const result = await api.get(`/orders${queryString({
      orderType: 'product',
      status: mallOrderStatus.value,
      page: targetPage,
      pageSize: ORDER_PAGE_SIZE
    })}`)
    mallOrders.value = result.data?.list || []
    mallOrderTotal.value = result.data?.total || 0
    mallOrderPage.value = result.data?.page || targetPage
    mallOrderTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取商城订单失败', 'error')
  } finally {
    mallOrdersLoading.value = false
  }
}

async function loadAppointments(page = appointmentPage.value) {
  if (!isUser.value || appointmentsLoading.value) return
  const targetPage = normalizePage(page)
  appointmentsLoading.value = true
  try {
    const result = await api.get(`/orders${queryString({
      orderType: 'booking',
      status: appointmentStatus.value,
      page: targetPage,
      pageSize: ORDER_PAGE_SIZE
    })}`)
    appointments.value = result.data?.list || []
    appointmentTotal.value = result.data?.total || 0
    appointmentPage.value = result.data?.page || targetPage
    appointmentTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取服务预约失败', 'error')
  } finally {
    appointmentsLoading.value = false
  }
}

async function loadMyPosts(page = myPostPage.value) {
  if (!isUser.value || myPostsLoading.value) return
  const targetPage = normalizePage(page)
  myPostsLoading.value = true
  try {
    const result = await api.get(`/user/posts${queryString({
      page: targetPage,
      pageSize: POST_PAGE_SIZE
    })}`)
    myPosts.value = result.data?.list || []
    myPostTotal.value = result.data?.total || 0
    myPostPage.value = result.data?.page || targetPage
    myPostTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取我的帖子失败', 'error')
  } finally {
    myPostsLoading.value = false
  }
}

async function loadLikedPosts(page = likedPostPage.value) {
  if (!isUser.value || likedPostsLoading.value) return
  const targetPage = normalizePage(page)
  likedPostsLoading.value = true
  try {
    const result = await api.get(`/user/posts/liked${queryString({
      page: targetPage,
      pageSize: POST_PAGE_SIZE
    })}`)
    likedPosts.value = result.data?.list || []
    likedPostTotal.value = result.data?.total || 0
    likedPostPage.value = result.data?.page || targetPage
    likedPostTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取点赞帖子失败', 'error')
  } finally {
    likedPostsLoading.value = false
  }
}

async function loadMyComments(page = commentPage.value) {
  if (!isUser.value || commentLoading.value) return
  const targetPage = normalizePage(page)
  commentLoading.value = true
  try {
    const result = await api.get(`/user/my-comments${queryString({
      page: targetPage,
      pageSize: COMMENT_PAGE_SIZE
    })}`)
    myComments.value = result.data?.list || []
    commentTotal.value = result.data?.total || 0
    commentPage.value = result.data?.page || targetPage
    commentTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取评论失败', 'error')
  } finally {
    commentLoading.value = false
  }
}

async function loadSocial(mode = socialMode.value, page = 1, reset = false) {
  if (!isUser.value) return
  if (!reset && (socialLoading.value || socialLoadingMore.value)) return
  const requestSequence = ++socialRequestSequence
  const endpoint = mode === 'following' ? '/user/following' : '/user/followers'
  if (reset) socialLoading.value = true
  else socialLoadingMore.value = true
  try {
    const result = await api.get(`${endpoint}${queryString({
      page,
      pageSize: SOCIAL_PAGE_SIZE
    })}`)
    if (requestSequence !== socialRequestSequence) return
    const list = result.data?.list || []
    const target = mode === 'following' ? following : followers
    if (reset) {
      target.value = list
    } else {
      const existingIds = new Set(target.value.map((item) => Number(item.id)))
      target.value.push(...list.filter((item) => !existingIds.has(Number(item.id))))
    }
    socialPage.value = result.data?.page || page
    socialTotalPages.value = result.data?.totalPages || 1
  } catch (error) {
    ui.toast(error.message || '读取关注列表失败', 'error')
  } finally {
    if (requestSequence === socialRequestSequence) {
      socialLoading.value = false
      socialLoadingMore.value = false
    }
  }
}

function normalizePage(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1
}

function normalizeAddresses(items) {
  return Array.isArray(items)
    ? items.map((item) => ({ ...item, id: item.id || createAddressId() }))
    : []
}

function createAddressId() {
  return globalThis.crypto?.randomUUID?.()
    || `address-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function ensureDefaultAddress(items) {
  if (items.length > 0 && !items.some((item) => item.isDefault)) {
    items[0] = { ...items[0], isDefault: true }
  }
  return items
}

function resetPrivateState() {
  user.value = null
  pets.value = []
  mallOrders.value = []
  mallOrderTotal.value = 0
  mallOrderPage.value = 1
  mallOrderTotalPages.value = 1
  appointments.value = []
  appointmentTotal.value = 0
  appointmentPage.value = 1
  appointmentTotalPages.value = 1
  myPosts.value = []
  myPostTotal.value = 0
  myPostPage.value = 1
  myPostTotalPages.value = 1
  likedPosts.value = []
  likedPostTotal.value = 0
  likedPostPage.value = 1
  likedPostTotalPages.value = 1
  myComments.value = []
  commentTotal.value = 0
  commentPage.value = 1
  commentTotalPages.value = 1
  following.value = []
  followers.value = []
  socialPage.value = 1
  socialTotalPages.value = 1
  socialLoading.value = false
  socialLoadingMore.value = false
  addresses.value = []
  profileOpen.value = false
  petOpen.value = false
  addressOpen.value = false
  passwordOpen.value = false
  socialOpen.value = false
  loading.value = false
}

async function uploadAvatar(event) {
  const file = event.target.files?.[0]
  if (!file || avatarUploading.value) return
  avatarUploading.value = true
  try {
    const result = await api.upload('/user/avatar', file)
    user.value = { ...user.value, avatar: result.data }
    auth.updateProfile({ avatar: result.data })
    ui.toast('头像已更新', 'success')
  } catch (error) {
    ui.toast(error.message || '头像上传失败', 'error')
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

async function uploadPetPhoto(event) {
  const file = event.target.files?.[0]
  if (!file || petPhotoUploading.value) return
  petPhotoUploading.value = true
  try {
    const result = await api.upload('/pet/image', file)
    petForm.value.photo = result.data
    ui.toast('宠物照片已上传', 'success')
  } catch (error) {
    ui.toast(error.message || '宠物照片上传失败', 'error')
  } finally {
    petPhotoUploading.value = false
    event.target.value = ''
  }
}

function updateProfileForm() {
  profileForm.value = {
    nickname: user.value?.nickname || '',
    phone: user.value?.phone || '',
    email: user.value?.email || '',
    city: user.value?.city || ''
  }
}

function openProfile() {
  updateProfileForm()
  profileOpen.value = true
  profileGuard.markClean()
}

async function saveProfile() {
  if (profileSaving.value) return
  const form = {
    nickname: profileForm.value.nickname.trim(),
    phone: profileForm.value.phone.trim(),
    email: profileForm.value.email.trim(),
    city: profileForm.value.city.trim()
  }
  if (!form.nickname) {
    ui.toast('昵称不能为空', 'warning')
    return
  }
  if (form.phone && !MOBILE_PATTERN.test(form.phone)) {
    ui.toast('请输入正确的 11 位手机号', 'warning')
    return
  }
  if (form.email && !EMAIL_PATTERN.test(form.email)) {
    ui.toast('邮箱格式不正确', 'warning')
    return
  }
  profileSaving.value = true
  try {
    const result = await api.put('/user/profile', form)
    user.value = { ...user.value, ...result.data }
    auth.updateProfile(result.data)
    closeProfile()
    ui.toast(result.msg || '资料已保存', 'success')
  } catch (error) {
    ui.toast(error.message || '保存资料失败', 'error')
  } finally {
    profileSaving.value = false
  }
}

function closeProfile() {
  profileOpen.value = false
}

function openPassword() {
  passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
  passwordOpen.value = true
  passwordGuard.markClean()
}

function closePassword() {
  passwordOpen.value = false
  passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
}

async function savePassword() {
  if (!passwordForm.value.currentPassword || passwordForm.value.newPassword.length < 8) {
    ui.toast('请输入当前密码，新密码至少 8 个字符', 'warning')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ui.toast('两次输入的新密码不一致', 'warning')
    return
  }
  if (passwordSaving.value) return
  passwordSaving.value = true
  try {
    const result = await api.post('/auth/password', {
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    })
    closePassword()
    ui.toast(result.msg || '密码修改成功', 'success')
  } catch (error) {
    ui.toast(error.message || '修改密码失败', 'error')
  } finally {
    passwordSaving.value = false
  }
}

function openPet(pet = null) {
  editingPetIndex.value = pet ? pets.value.indexOf(pet) : -1
  petForm.value = pet ? JSON.parse(JSON.stringify(pet)) : {
    name: '', species: '猫', gender: '母', breed: '', color: '', birthDate: '',
    weight: '', neutered: false, medicalHistory: '', allergies: [], vaccines: [],
    dewormings: [], weightRecords: [], carePlans: []
  }
  petForm.value.allergies ||= []
  petOpen.value = true
  petGuard.markClean()
}

async function savePet() {
  const form = petForm.value
  if (!form.name?.trim()) {
    ui.toast('请填写宠物名字', 'warning')
    return
  }
  if (!['猫', '狗'].includes(form.species)) {
    ui.toast('宠物物种只能是猫或狗', 'warning')
    return
  }
  if (form.birthDate && form.birthDate > todayDate) {
    ui.toast('出生日期不能晚于今天', 'warning')
    return
  }
  if (form.weight !== '' && form.weight != null
    && (!Number.isFinite(Number(form.weight)) || Number(form.weight) <= 0)) {
    ui.toast('体重必须大于 0', 'warning')
    return
  }
  const snapshot = JSON.parse(JSON.stringify(form))
  snapshot.name = snapshot.name.trim()
  snapshot.weight = snapshot.weight === '' || snapshot.weight == null
    ? null
    : Number(snapshot.weight)
  const nextPets = editingPetIndex.value >= 0
    ? pets.value.map((pet, index) => index === editingPetIndex.value ? snapshot : pet)
    : [...pets.value, snapshot]
  if (petSaving.value) return
  petSaving.value = true
  try {
    await api.put('/user/pets', { pets: JSON.stringify(nextPets) })
    pets.value = nextPets
    closePet()
    ui.toast('宠物档案已保存', 'success')
  } catch (error) {
    ui.toast(error.message || '保存宠物档案失败', 'error')
  } finally {
    petSaving.value = false
  }
}

function closePet() {
  petOpen.value = false
  editingPetIndex.value = -1
  petForm.value = {}
}

async function deletePet(index) {
  const confirmed = await ui.confirm({ message: '确定删除这只宠物及其养护记录吗？', danger: true })
  if (!confirmed) return
  const nextPets = pets.value.filter((pet, petIndex) => petIndex !== index)
  try {
    await api.put('/user/pets', { pets: JSON.stringify(nextPets) })
    pets.value = nextPets
    ui.toast('宠物档案已删除', 'success')
  } catch (error) {
    ui.toast(error.message || '删除宠物失败', 'error')
  }
}

async function persistAddresses(nextAddresses, message) {
  addressSaving.value = true
  try {
    await api.put('/user/addresses', { addresses: JSON.stringify(nextAddresses) })
    addresses.value = nextAddresses
    closeAddress()
    ui.toast(message, 'success')
    return true
  } catch (error) {
    ui.toast(error.message || '保存收货地址失败', 'error')
    return false
  } finally {
    addressSaving.value = false
  }
}

function openAddress(address = null) {
  editingAddressIndex.value = address
    ? addresses.value.findIndex((item) => item.id === address.id)
    : -1
  addressForm.value = address
    ? { ...address }
    : {
        id: '',
        name: '',
        phone: '',
        region: '',
        detail: '',
        isDefault: addresses.value.length === 0
      }
  addressOpen.value = true
  addressGuard.markClean()
}

function closeAddress() {
  addressOpen.value = false
  editingAddressIndex.value = -1
  addressForm.value = {
    id: '',
    name: '',
    phone: '',
    region: '',
    detail: '',
    isDefault: false
  }
}

async function saveAddress() {
  if (addressSaving.value) return
  const address = {
    ...addressForm.value,
    id: addressForm.value.id || createAddressId(),
    name: addressForm.value.name.trim(),
    phone: addressForm.value.phone.trim(),
    region: addressForm.value.region.trim(),
    detail: addressForm.value.detail.trim()
  }
  if (!address.name || !address.phone || !address.region || !address.detail) {
    ui.toast('请完整填写收货信息', 'warning')
    return
  }
  if (address.name.length > 50) {
    ui.toast('收货人不能超过 50 个字符', 'warning')
    return
  }
  if (!MOBILE_PATTERN.test(address.phone)) {
    ui.toast('请输入正确的 11 位手机号', 'warning')
    return
  }
  if (address.region.length > 100 || address.detail.length > 200) {
    ui.toast('省市区或详细地址超过长度限制', 'warning')
    return
  }
  const nextAddresses = addresses.value.map((item) => ({
    ...item,
    isDefault: address.isDefault ? false : item.isDefault
  }))
  if (editingAddressIndex.value >= 0) {
    nextAddresses.splice(editingAddressIndex.value, 1, address)
  } else {
    nextAddresses.push(address)
  }
  await persistAddresses(
    ensureDefaultAddress(nextAddresses),
    editingAddressIndex.value >= 0 ? '地址已更新' : '地址已添加'
  )
}

async function deleteAddress(address) {
  const confirmed = await ui.confirm({ message: '确定删除这个收货地址吗？', danger: true })
  if (!confirmed) return
  const nextAddresses = addresses.value.filter((item) => item.id !== address.id)
  await persistAddresses(ensureDefaultAddress(nextAddresses), '地址已删除')
}

async function setDefaultAddress(address) {
  if (address.isDefault || addressSaving.value) return
  const nextAddresses = addresses.value.map((item) => ({
    ...item,
    isDefault: item.id === address.id
  }))
  await persistAddresses(nextAddresses, '默认地址已更新')
}

async function orderAction(order, action) {
  if (orderActionBusyId.value) return
  let confirmation = null
  if (action === 'pay') {
    confirmation = {
      title: '确认支付订单',
      message: `确认支付订单「${order.orderNo}」吗？商家：${order.shopName || '未知'}；应付金额：¥${formatPrice(order.totalAmount)}。本次为模拟支付。`,
      confirmText: '确认支付'
    }
  } else if (action === 'cancel') {
    confirmation = {
      title: order.orderType === 'booking' ? '取消预约' : '取消订单',
      message: order.orderType === 'booking'
        ? `确认取消预约「${order.orderNo}」吗？取消后将释放预约时段。`
        : `确认取消订单「${order.orderNo}」吗？`,
      confirmText: '确认取消',
      danger: true
    }
  } else if (action === 'confirm') {
    confirmation = {
      title: '确认收货',
      message: `确认已经收到订单「${order.orderNo}」中的全部商品吗？确认后订单将变为已完成。`,
      confirmText: '确认收货'
    }
  }
  if (confirmation && !await ui.confirm(confirmation)) return
  orderActionBusyId.value = order.id
  try {
    let result
    if (action === 'pay') result = await api.put(`/orders/${order.id}/pay`)
    if (action === 'cancel') result = await api.put(`/orders/${order.id}/cancel`)
    if (action === 'remind-shipment') {
      result = await api.put(`/orders/${order.id}/remind-shipment`)
    }
    if (action === 'confirm') result = await api.put(`/orders/${order.id}/confirm`)
    ui.toast(result?.msg || '操作成功', 'success')
    if (order.orderType === 'booking') {
      await loadAppointments(appointmentPage.value)
    } else {
      await loadMallOrders(mallOrderPage.value)
    }
  } catch (error) {
    ui.toast(error.message || '订单操作失败', 'error')
  } finally {
    orderActionBusyId.value = null
  }
}

async function toggleLikedPost(post) {
  if (likedPostBusyId.value) return
  likedPostBusyId.value = post.id
  try {
    const result = await api.post(`/posts/${post.id}/like`)
    if (result.data?.liked) {
      post.likes = result.data?.likes ?? post.likes
      return
    }
    likedPosts.value = likedPosts.value.filter((item) => item.id !== post.id)
    likedPostTotal.value = Math.max(0, likedPostTotal.value - 1)
    if (likedPosts.value.length === 0 && likedPostPage.value > 1) {
      await loadLikedPosts(likedPostPage.value - 1)
    }
  } catch (error) {
    ui.toast(error.message || '取消点赞失败', 'error')
  } finally {
    likedPostBusyId.value = null
  }
}

async function deletePost(post) {
  const confirmed = await ui.confirm({ message: '确定删除这篇帖子吗？', danger: true })
  if (!confirmed) return
  try {
    await api.delete(`/posts/${post.id}`)
    ui.toast('帖子已删除', 'success')
    if (myPosts.value.length === 1 && myPostPage.value > 1) {
      await loadMyPosts(myPostPage.value - 1)
    } else {
      await loadMyPosts(myPostPage.value)
    }
  } catch (error) {
    ui.toast(error.message || '删除帖子失败', 'error')
  }
}

async function deleteMyComment(comment) {
  const summary = String(comment.content || '').slice(0, 30)
  const confirmed = await ui.confirm({
    message: `确定删除评论“${summary}${summary.length >= 30 ? '…' : ''}”吗？`,
    danger: true
  })
  if (!confirmed) return
  try {
    await api.delete(`/posts/${comment.postId}/comments/${comment.id}`)
    ui.toast('评论已删除', 'success')
    if (myComments.value.length === 1 && commentPage.value > 1) {
      await loadMyComments(commentPage.value - 1)
      return
    }
    await loadMyComments(commentPage.value)
  } catch (error) {
    ui.toast(error.message || '删除评论失败', 'error')
  }
}

function openSocial(mode) {
  socialMode.value = mode
  socialOpen.value = true
  socialPage.value = 1
  socialTotalPages.value = 1
  loadSocial(mode, 1, true)
}

function openSocialProfile(member) {
  socialProfileTarget.value = {
    userId: member.id,
    nickname: member.nickname || '社区用户',
    avatar: member.avatar || ''
  }
}

function handleFollowChange(change) {
  if (!change?.userId) return
  const userId = Number(change.userId)
  const followingUser = Boolean(change.following)
  const previousFollowing = following.value.some((item) => Number(item.id) === userId)
  following.value = following.value.filter((item) => Number(item.id) !== userId)
  if (followingUser && socialProfileTarget.value) {
    following.value.push({
      id: socialProfileTarget.value.userId,
      nickname: socialProfileTarget.value.nickname,
      avatar: socialProfileTarget.value.avatar
    })
  }
  if (previousFollowing !== followingUser && user.value) {
    const delta = followingUser ? 1 : -1
    user.value.followCount = Math.max(0, Number(user.value.followCount || 0) + delta)
  }
}

function handleSocialScroll(event) {
  if (socialLoading.value || socialLoadingMore.value || !socialHasMore.value) return
  const element = event.target
  if (element.scrollTop + element.clientHeight >= element.scrollHeight - 60) {
    loadSocial(socialMode.value, socialPage.value + 1)
  }
}

function resolveTab(value) {
  const requested = String(value || '')
  return tabs.some((tab) => tab.id === requested) ? requested : 'profile'
}

function resolveStatus(tab, value, allowedStatuses) {
  const status = String(value || '')
  if (tab !== 'orders' && tab !== 'appointments') return ''
  return allowedStatuses.has(status) ? status : ''
}

function resolveReturnPath(value) {
  const path = String(value || '')
  const forbidden = path === '/login'
    || path.startsWith('/login?')
    || path === '/me'
    || path.startsWith('/me?')
    || path === '/account'
    || path.startsWith('/account?')
  return path.startsWith('/') && !path.startsWith('//') && !forbidden ? path : ''
}

function replaceTab(tabId, status = '') {
  const query = { ...route.query, tab: tabId }
  delete query.return
  if (status) query.status = status
  else delete query.status
  router.replace({ name: 'me', query })
}

function selectTab(tabId) {
  activeTab.value = tabId
  if (tabId === 'content') {
    contentMode.value = 'posts'
  }
  const status = tabId === 'orders'
    ? mallOrderStatus.value
    : tabId === 'appointments'
      ? appointmentStatus.value
      : ''
  replaceTab(tabId, status)
  nextTick(() => {
    contentScrollRef.value?.scrollTo({ top: 0 })
  })
}

function selectMallOrderStatus(status) {
  mallOrderStatus.value = status
  replaceTab('orders', status)
}

function selectAppointmentStatus(status) {
  appointmentStatus.value = status
  replaceTab('appointments', status)
}

function switchContentMode(mode) {
  contentMode.value = mode
  if (mode === 'posts') loadMyPosts(1)
  else if (mode === 'likes') loadLikedPosts(1)
  else loadMyComments(1)
}

function returnToSource() {
  if (returnTarget.value) router.push(returnTarget.value)
}

watch(isUser, (value) => {
  if (value) {
    load()
    return
  }
  resetPrivateState()
}, { immediate: true })

watch(() => [route.query.tab, route.query.status], ([tab, status]) => {
  const nextTab = resolveTab(tab)
  activeTab.value = nextTab
  if (nextTab === 'orders') {
    mallOrderStatus.value = resolveStatus(nextTab, status, orderStatuses)
  } else if (nextTab === 'appointments') {
    appointmentStatus.value = resolveStatus(nextTab, status, appointmentStatuses)
  }
  if (isUser.value) {
    loadActiveTab()
  }
})
</script>

<template>
  <div class="page-layout profile-page">
    <header class="profile-hero">
      <div class="profile-avatar">
        <img v-if="user?.avatar" :src="assetUrl(user.avatar)" alt="用户头像" />
        <UserRound v-else :size="42" />
        <button
          type="button"
          aria-label="更换头像"
          :disabled="avatarUploading"
          @click="avatarInput?.click()"
        >
          <Camera :size="16" />
        </button>
        <input ref="avatarInput" type="file" accept="image/*" hidden @change="uploadAvatar" />
      </div>
      <div>
        <h1>{{ isUser ? user?.nickname || user?.username || '我的主页' : '个人中心' }}</h1>
        <span class="profile-role">{{ isUser ? roleLabel : '游客预览' }}</span>
        <p class="profile-meta-line">
          {{ isUser
            ? `${user?.city || '还没有填写城市'} · 加入于 ${user?.createdAt?.slice(0, 10) || '--'}`
            : '登录后查看个人资料、宠物、订单与养宠数据' }}
        </p>
      </div>
      <div class="profile-stats" aria-label="关注与粉丝">
        <button
          type="button"
          @click="openSocial('following')"
        >
          <b>{{ isUser ? user?.followCount || 0 : '--' }}</b><span>关注</span>
        </button>
        <button
          type="button"
          @click="openSocial('followers')"
        >
          <b>{{ isUser ? user?.fanCount || 0 : '--' }}</b><span>粉丝</span>
        </button>
      </div>
      <button
        class="btn btn-ghost"
        type="button"
        @click="openProfile()"
      >
        <PenLine :size="16" />{{ isUser ? '编辑资料' : '登录' }}
      </button>
    </header>

    <div class="profile-workspace">
      <nav class="profile-tabs" aria-label="个人中心导航">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          :class="{ active: activeTab === tab.id }"
          :aria-current="activeTab === tab.id ? 'page' : undefined"
          :aria-controls="`profile-panel-${tab.id}`"
          @click="selectTab(tab.id)"
        >
          <component :is="tab.icon" :size="17" />{{ tab.label }}
        </button>
        <p class="profile-nav-note">个人资料、宠物养护、订单和消息仅展示当前账号自己的数据。</p>
      </nav>

      <div ref="contentScrollRef" class="profile-content profile-content-scroll">
        <LoadingPanel v-if="loading" label="正在整理个人中心" />
        <template v-else>
          <div
            v-if="returnTarget && (activeTab === 'pets' || activeTab === 'addresses')"
            class="return-banner"
          >
            <span>{{ returnHint }}</span>
            <button class="btn btn-ghost btn-sm" type="button" @click="returnToSource">
              完成并返回
            </button>
          </div>
          <section v-if="activeTab === 'profile'" id="profile-panel-profile">
            <div class="profile-section-head">
              <div><h2>个人资料</h2></div>
              <button class="btn btn-primary" type="button" @click="openProfile"><PenLine :size="16" />编辑资料</button>
            </div>
            <div class="profile-detail-grid">
              <div class="panel panel-pad">
                <div class="panel-title compact"><h2>基本资料</h2></div>
                <div class="info-grid">
                  <div class="info-pair"><span>用户名</span><b>{{ user?.username || '--' }}</b></div>
                  <div class="info-pair"><span>昵称</span><b>{{ user?.nickname || '--' }}</b></div>
                  <div class="info-pair"><span>手机号</span><b>{{ user?.phone || '--' }}</b></div>
                  <div class="info-pair"><span>邮箱</span><b>{{ user?.email || '--' }}</b></div>
                  <div class="info-pair"><span>城市</span><b>{{ user?.city || '--' }}</b></div>
                  <div class="info-pair"><span>身份</span><b>{{ roleLabel }}</b></div>
                  <div class="info-pair"><span>加入时间</span><b>{{ user?.createdAt?.slice(0, 10) || '--' }}</b></div>
                  <div class="info-pair"><span>宠物档案</span><b>{{ petsLoading ? '读取中' : `${pets.length} 只` }}</b></div>
                </div>
              </div>
              <div class="panel panel-pad account-security">
                <div class="panel-title compact"><h2>账号安全</h2></div>
                <div class="security-row">
                  <span><KeyRound :size="17" /></span>
                  <div><b>登录密码</b></div>
                  <button class="text-link" type="button" @click="openPassword">修改</button>
                </div>
                <div class="security-row">
                  <span><ShieldCheck :size="17" /></span>
                  <div><b>账号状态</b></div>
                  <span class="tag sage">正常</span>
                </div>
              </div>
            </div>
          </section>

      <section v-else-if="activeTab === 'pets'" id="profile-panel-pets">
        <div class="profile-section-head">
          <div><h2>我的宠物</h2></div>
          <button class="btn btn-primary" type="button" @click="openPet()"><Plus :size="16" />添加宠物</button>
        </div>
        <LoadingPanel v-if="petsLoading" label="正在读取宠物档案" />
        <div v-else-if="pets.length" class="card-grid">
          <article v-for="(pet, index) in pets" :key="pet.id || `${pet.name}-${index}`" class="pet-card panel">
            <span class="pet-card-avatar">
              <img v-if="pet.photo" :src="assetUrl(pet.photo)" :alt="`${pet.name}的照片`" />
              <PawPrint v-else :size="28" />
            </span>
            <h2>{{ pet.name }}</h2>
            <p>{{ pet.breed || '品种未填写' }} · {{ pet.species || pet.type }}</p>
            <div class="pet-card-facts">
              <span>{{ pet.gender || '性别未填' }}</span>
              <span>{{ pet.weight ? `${pet.weight} kg` : '体重未记录' }}</span>
              <span>{{ pet.neutered ? '已绝育' : '未绝育' }}</span>
            </div>
            <div class="pet-card-actions">
              <div class="pet-card-actions-main">
                <button class="btn btn-ghost btn-sm" type="button" @click="openPet(pet)">编辑</button>
                <RouterLink
                  v-if="pet.id"
                  class="btn btn-ghost btn-sm"
                  :to="{ name: 'health', query: { petId: pet.id } }"
                >
                  养护档案
                </RouterLink>
              </div>
              <button class="icon-btn" type="button" aria-label="删除宠物" @click="deletePet(index)">
                <Trash2 :size="16" />
              </button>
            </div>
          </article>
        </div>
        <EmptyState v-else title="还没有宠物档案" />
      </section>

      <section v-else-if="activeTab === 'orders'" id="profile-panel-orders">
        <div class="profile-section-head">
          <div><h2>商城订单</h2></div>
        </div>
        <div class="status-filter">
          <button type="button" :class="{ active: mallOrderStatus === '' }" @click="selectMallOrderStatus('')">全部</button>
          <button type="button" :class="{ active: mallOrderStatus === 'pending_payment' }" @click="selectMallOrderStatus('pending_payment')">待支付</button>
          <button type="button" :class="{ active: mallOrderStatus === 'paid' }" @click="selectMallOrderStatus('paid')">待发货</button>
          <button type="button" :class="{ active: mallOrderStatus === 'shipped' }" @click="selectMallOrderStatus('shipped')">待收货</button>
          <button type="button" :class="{ active: mallOrderStatus === 'completed' }" @click="selectMallOrderStatus('completed')">已完成</button>
          <button type="button" :class="{ active: mallOrderStatus === 'cancelled' }" @click="selectMallOrderStatus('cancelled')">已取消</button>
        </div>
        <LoadingPanel v-if="mallOrdersLoading" label="正在读取商城订单" />
        <template v-else>
          <div v-if="mallOrders.length" class="order-list">
          <OrderRecordCard
            v-for="order in mallOrders"
            :key="order.id"
            :order="order"
            :busy="orderActionBusyId === order.id"
            @action="orderAction"
          />
          </div>
          <EmptyState v-else title="暂无商城订单" />
          <PaginationBar
            :page="mallOrderPage"
            :total-pages="mallOrderTotalPages"
            @change="loadMallOrders"
          />
          <p v-if="mallOrderTotal" class="list-total">共 {{ mallOrderTotal }} 笔订单</p>
        </template>
      </section>

      <section v-else-if="activeTab === 'appointments'" id="profile-panel-appointments">
        <div class="profile-section-head">
          <div><h2>服务预约</h2></div>
        </div>
        <div class="status-filter">
          <button type="button" :class="{ active: appointmentStatus === '' }" @click="selectAppointmentStatus('')">全部</button>
          <button type="button" :class="{ active: appointmentStatus === 'confirmed' }" @click="selectAppointmentStatus('confirmed')">已预约</button>
          <button type="button" :class="{ active: appointmentStatus === 'completed' }" @click="selectAppointmentStatus('completed')">已完成</button>
          <button type="button" :class="{ active: appointmentStatus === 'cancelled' }" @click="selectAppointmentStatus('cancelled')">已取消</button>
        </div>
        <LoadingPanel v-if="appointmentsLoading" label="正在读取服务预约" />
        <template v-else>
          <div v-if="appointments.length" class="order-list">
          <OrderRecordCard
            v-for="order in appointments"
            :key="order.id"
            :order="order"
            :busy="orderActionBusyId === order.id"
            @action="orderAction"
          />
          </div>
          <EmptyState v-else title="暂无服务预约" />
          <PaginationBar
            :page="appointmentPage"
            :total-pages="appointmentTotalPages"
            @change="loadAppointments"
          />
          <p v-if="appointmentTotal" class="list-total">共 {{ appointmentTotal }} 条预约</p>
        </template>
      </section>

      <section v-else-if="activeTab === 'content'" id="profile-panel-content">
        <div class="profile-section-head">
          <div><h2>我的社区</h2></div>
        </div>
        <div class="community-switch" role="tablist" aria-label="社区内容类型">
          <button
            type="button"
            role="tab"
            :class="{ active: contentMode === 'posts' }"
            :aria-selected="contentMode === 'posts'"
            aria-controls="community-posts-panel"
            @click="switchContentMode('posts')"
          >
            我的帖子<span>{{ myPostTotal }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :class="{ active: contentMode === 'likes' }"
            :aria-selected="contentMode === 'likes'"
            aria-controls="community-likes-panel"
            @click="switchContentMode('likes')"
          >
            点赞<span>{{ likedPostTotal }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :class="{ active: contentMode === 'comments' }"
            :aria-selected="contentMode === 'comments'"
            aria-controls="community-comments-panel"
            @click="switchContentMode('comments')"
          >
            评论<span>{{ commentTotal }}</span>
          </button>
        </div>
        <div
          v-if="contentMode === 'posts'"
          id="community-posts-panel"
          role="tabpanel"
          aria-label="我的帖子"
        >
          <LoadingPanel v-if="myPostsLoading" label="正在读取我的帖子" />
          <template v-else>
            <PostMasonryGrid
              v-if="myPosts.length"
              :posts="myPosts"
              display-mode="profile"
              :author-clickable="false"
              :interactive-like="false"
              show-status
            >
              <template #overlay="{ post }">
                <button class="content-delete" type="button" @click="deletePost(post)">
                  <Trash2 :size="15" />删除帖子
                </button>
              </template>
            </PostMasonryGrid>
            <EmptyState v-else title="还没有发布帖子" />
            <PaginationBar
              :page="myPostPage"
              :total-pages="myPostTotalPages"
              @change="loadMyPosts"
            />
          </template>
        </div>
        <div
          v-else-if="contentMode === 'likes'"
          id="community-likes-panel"
          role="tabpanel"
          aria-label="点赞"
        >
          <LoadingPanel v-if="likedPostsLoading" label="正在读取点赞帖子" />
          <template v-else>
            <PostMasonryGrid
              v-if="likedPosts.length"
              :posts="likedPosts"
              :liked-ids="likedPostIds"
              display-mode="profile"
              :author-clickable="false"
              @like="toggleLikedPost"
            />
            <EmptyState v-else title="还没有点赞内容" />
            <PaginationBar
              :page="likedPostPage"
              :total-pages="likedPostTotalPages"
              @change="loadLikedPosts"
            />
          </template>
        </div>
        <div
          v-else
          id="community-comments-panel"
          role="tabpanel"
          aria-label="我的评论"
        >
          <LoadingPanel v-if="commentLoading" label="正在读取评论" />
          <template v-else-if="myComments.length">
            <div class="my-comment-list">
              <MyCommentCard
                v-for="comment in myComments"
                :key="comment.id"
                :comment="comment"
                @delete="deleteMyComment"
              />
            </div>
            <PaginationBar
              :page="commentPage"
              :total-pages="commentTotalPages"
              @change="loadMyComments"
            />
          </template>
          <EmptyState v-else title="还没有发表过评论" />
        </div>
      </section>

      <section v-else-if="activeTab === 'addresses'" id="profile-panel-addresses">
        <div class="profile-section-head">
          <div><h2>收货地址</h2></div>
          <button class="btn btn-primary" type="button" @click="openAddress"><Plus :size="16" />添加地址</button>
        </div>
        <LoadingPanel v-if="addressesLoading" label="正在读取收货地址" />
        <div v-else-if="addresses.length" class="address-grid">
          <article v-for="address in addresses" :key="address.id" class="address-card panel">
            <MapPin :size="20" />
            <div><b>{{ address.name }} {{ address.phone }}</b><p>{{ [address.region, address.detail || address.address].filter(Boolean).join(' ') }}</p></div>
            <span v-if="address.isDefault" class="tag sage">默认</span>
            <div class="address-actions">
              <button class="text-link" type="button" @click="openAddress(address)">编辑</button>
              <button
                v-if="!address.isDefault"
                class="text-link"
                type="button"
                :disabled="addressSaving"
                @click="setDefaultAddress(address)"
              >
                设为默认
              </button>
              <button
                class="icon-btn"
                type="button"
                aria-label="删除地址"
                :disabled="addressSaving"
                @click="deleteAddress(address)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
          </article>
        </div>
        <EmptyState v-else title="还没有收货地址" />
      </section>
        </template>
      </div>
    </div>

    <ModalDialog :open="socialOpen" :title="socialTitle" @close="socialOpen = false">
      <LoadingPanel v-if="socialLoading" label="正在读取用户列表" />
      <template v-else>
        <div v-if="socialUsers.length" class="social-user-scroll" @scroll.passive="handleSocialScroll">
          <div class="user-list">
            <article v-for="item in socialUsers" :key="item.id" class="user-card">
              <button
                class="user-identity"
                type="button"
                :aria-label="`查看 ${item.nickname || '社区用户'} 的主页和全部帖子`"
                @click="openSocialProfile(item)"
              >
                <img v-if="item.avatar" :src="assetUrl(item.avatar)" :alt="`${item.nickname || '社区用户'}的头像`" />
                <span v-else><UserRound :size="23" /></span>
                <div>
                  <b>{{ item.nickname || '社区用户' }}</b>
                  <small>{{ socialMode === 'following' ? '你已关注' : '关注了你' }}</small>
                </div>
              </button>
              <RouterLink class="text-link" :to="`/messages/${item.id}?category=community`">发消息</RouterLink>
            </article>
          </div>
          <p v-if="socialLoadingMore" class="social-load-status">正在加载更多…</p>
          <p v-else-if="!socialHasMore" class="social-load-status">没有更多用户了</p>
        </div>
        <EmptyState
          v-else
          :title="socialMode === 'following' ? '还没有关注用户' : '还没有粉丝'"
        />
      </template>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="socialOpen = false">关闭</button>
      </template>
    </ModalDialog>

    <UserProfileDrawer
      :open="Boolean(socialProfileTarget)"
      :user-id="socialProfileTarget?.userId"
      :nickname="socialProfileTarget?.nickname || 'TA'"
      :avatar="socialProfileTarget?.avatar || ''"
      @close="socialProfileTarget = null"
      @follow-change="handleFollowChange"
    />

    <ModalDialog
      :open="profileOpen"
      title="编辑个人资料"
      :close-disabled="profileSaving"
      @close="profileGuard.requestClose"
    >
      <div class="form-row">
        <label class="field"><span>昵称</span><input v-model="profileForm.nickname" class="input" maxlength="50" required /></label>
        <label class="field"><span>城市</span><input v-model="profileForm.city" class="input" maxlength="50" /></label>
      </div>
      <div class="form-row">
        <label class="field"><span>手机号</span><input v-model="profileForm.phone" class="input" maxlength="11" inputmode="numeric" /></label>
        <label class="field"><span>邮箱</span><input v-model="profileForm.email" class="input" type="email" maxlength="100" /></label>
      </div>
      <template #footer><button class="btn btn-ghost" @click="profileGuard.requestClose">取消</button><button class="btn btn-primary" :disabled="profileSaving" @click="saveProfile">保存</button></template>
    </ModalDialog>

    <ModalDialog
      :open="passwordOpen"
      title="修改密码"
      :close-disabled="passwordSaving"
      @close="passwordGuard.requestClose"
    >
      <label class="field"><span>当前密码</span><input v-model="passwordForm.currentPassword" class="input" type="password" autocomplete="current-password" /></label>
      <label class="field"><span>新密码</span><input v-model="passwordForm.newPassword" class="input" type="password" autocomplete="new-password" placeholder="至少 8 个字符" /></label>
      <label class="field"><span>确认新密码</span><input v-model="passwordForm.confirmPassword" class="input" type="password" autocomplete="new-password" /></label>
      <template #footer><button class="btn btn-ghost" @click="passwordGuard.requestClose">取消</button><button class="btn btn-primary" :disabled="passwordSaving" @click="savePassword">修改密码</button></template>
    </ModalDialog>

    <ModalDialog
      :open="petOpen"
      :title="petFormTitle"
      width="580px"
      :close-disabled="petSaving"
      @close="petGuard.requestClose"
    >
      <div class="pet-photo-editor">
        <span class="pet-photo-preview">
          <img v-if="petForm.photo" :src="assetUrl(petForm.photo)" alt="宠物照片预览" />
          <PawPrint v-else :size="30" />
        </span>
        <div>
          <b>宠物照片</b>
          <small>支持 JPG、PNG 等常见图片格式。</small>
          <button
            class="btn btn-ghost btn-sm"
            type="button"
            :disabled="petPhotoUploading"
            @click="petPhotoInput?.click()"
          >
            <Camera :size="15" />{{ petPhotoUploading ? '上传中…' : '选择照片' }}
          </button>
          <input ref="petPhotoInput" type="file" accept="image/*" hidden @change="uploadPetPhoto" />
        </div>
      </div>
      <div class="form-row">
        <label class="field"><span>名字</span><input v-model="petForm.name" class="input" /></label>
        <label class="field"><span>物种</span><select v-model="petForm.species" class="select"><option>猫</option><option>狗</option></select></label>
      </div>
      <div class="form-row">
        <label class="field"><span>品种</span><input v-model="petForm.breed" class="input" /></label>
        <label class="field"><span>毛色</span><input v-model="petForm.color" class="input" /></label>
      </div>
      <div class="form-row">
        <label class="field"><span>性别</span><select v-model="petForm.gender" class="select"><option>公</option><option>母</option></select></label>
        <label class="field"><span>出生日期</span><input v-model="petForm.birthDate" class="input" type="date" :max="todayDate" /></label>
      </div>
      <div class="form-row">
        <label class="field"><span>当前体重 kg</span><input v-model="petForm.weight" class="input" type="number" step="0.1" min="0.1" /></label>
        <label class="checkbox pet-neutered-check"><input v-model="petForm.neutered" type="checkbox" />已绝育</label>
      </div>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="petGuard.requestClose">取消</button>
        <button class="btn btn-primary" type="button" :disabled="petSaving" @click="savePet">保存宠物</button>
      </template>
    </ModalDialog>

    <ModalDialog
      :open="addressOpen"
      :title="addressFormTitle"
      :close-disabled="addressSaving"
      @close="addressGuard.requestClose"
    >
      <div class="form-row">
        <label class="field"><span>收货人</span><input v-model="addressForm.name" class="input" maxlength="50" /></label>
        <label class="field"><span>手机号</span><input v-model="addressForm.phone" class="input" maxlength="11" inputmode="numeric" /></label>
      </div>
      <label class="field"><span>省市区</span><input v-model="addressForm.region" class="input" maxlength="100" /></label>
      <label class="field"><span>详细地址</span><textarea v-model="addressForm.detail" class="textarea" maxlength="200" /></label>
      <label class="checkbox"><input v-model="addressForm.isDefault" type="checkbox" />设为默认地址</label>
      <template #footer>
        <button class="btn btn-ghost" @click="addressGuard.requestClose">取消</button>
        <button class="btn btn-primary" :disabled="addressSaving" @click="saveAddress">
          {{ addressSaving ? '保存中…' : '保存' }}
        </button>
      </template>
    </ModalDialog>

  </div>
</template>

<style scoped>
.profile-hero { display: grid; grid-template-columns: auto 1fr auto auto; gap: 20px; align-items: center; padding: 26px; border-radius: 8px 34px 34px 8px; background: linear-gradient(130deg, var(--color-surface), var(--color-primary-soft) 55%, var(--color-warning-soft)); border: 1px solid var(--color-border); }
.profile-avatar { position: relative; width: 94px; height: 94px; border-radius: 30px 30px 10px 30px; overflow: hidden; display: grid; place-items: center; background: var(--color-surface); color: var(--color-primary-strong); }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-avatar button { position: absolute; right: 6px; bottom: 6px; width: 30px; height: 30px; border: 0; border-radius: 50%; background: var(--color-surface-white-glass); display: grid; place-items: center; color: var(--color-text); }
.profile-hero h1 { margin: 0 0 4px; font-size: var(--font-size-5xl); }
.profile-role { display: inline-flex; margin-bottom: 5px; border-radius: 999px; padding: 3px 9px; background: var(--color-surface-veil); border: 1px solid var(--color-border); color: var(--color-primary-strong); font-size: var(--font-size-sm); font-weight: var(--font-weight-black); }
.profile-meta-line { margin: 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.profile-stats { display: flex; gap: 7px; }
.profile-stats button { min-width: 58px; padding: 6px 10px; border: 1px solid transparent; border-radius: 16px; background: var(--color-surface-veil-faint); display: grid; text-align: center; color: var(--color-text-muted); font-size: var(--font-size-sm); transition: .2s ease; }
.profile-stats button:hover { border-color: var(--color-border-strong); background: var(--color-surface); color: var(--color-primary-strong); transform: translateY(-1px); }
.profile-stats b { color: var(--color-text); font-size: var(--font-size-xl); }
.profile-workspace { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 18px; align-items: start; margin-top: 18px; }
.profile-tabs { position: sticky; top: 100px; display: grid; gap: 5px; padding: 10px; border: 1px solid var(--color-border); border-radius: 7px 22px 22px 7px; background: var(--color-surface); scrollbar-width: none; }
.profile-tabs::-webkit-scrollbar { display: none; }
.profile-tabs button { width: 100%; min-height: 42px; padding: 9px 11px; border: 0; border-radius: 8px; background: transparent; color: var(--color-text-secondary); display: flex; align-items: center; gap: 9px; text-align: left; font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); white-space: nowrap; }
.profile-tabs button:hover { background: var(--color-surface-warm); }
.profile-tabs button.active { background: var(--color-primary); color: var(--color-white); }
.profile-tabs button.active svg { color: var(--color-white); }
.profile-nav-note { margin: 7px 3px 2px; padding: 11px 8px 2px; border-top: 1px solid var(--color-border-soft); color: var(--color-text-muted); font-size: var(--font-size-sm); line-height: 1.65; }
.profile-content { min-width: 0; }
.return-banner { margin-bottom: 14px; padding: 10px 13px; border: 1px solid var(--color-border-primary-subtle); border-radius: var(--radius-md); background: var(--color-primary-soft); color: var(--color-text-secondary); display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: var(--font-size-sm); }
.profile-section-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; margin-bottom: 15px; }
.profile-section-head h2 { margin: 0; font-size: var(--font-size-section-title); }
.profile-detail-grid { display: grid; grid-template-columns: 1.15fr .85fr; gap: 16px; }
.account-security { display: grid; align-content: start; }
.security-row { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding: 12px 0; border-bottom: 1px dashed var(--color-border); }
.security-row:last-child { border-bottom: 0; }
.security-row > span:first-child { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.security-row b { display: block; font-size: var(--font-size-sm); }
.status-filter { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
.status-filter button { min-height: 34px; padding: 6px 11px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); color: var(--color-text-secondary); font-size: var(--font-size-sm); font-weight: var(--font-weight-extra-bold); }
.status-filter button.active { border-color: var(--color-primary); background: var(--color-primary); color: var(--color-white); }
.community-switch { display: inline-flex; gap: 6px; margin-bottom: 17px; padding: 5px; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); box-shadow: var(--box-shadow-sm); }
.community-switch button { min-height: 38px; padding: 7px 10px 7px 14px; border: 0; border-radius: 999px; background: transparent; color: var(--color-text-secondary); display: inline-flex; align-items: center; gap: 9px; font-size: var(--font-size-sm); font-weight: var(--font-weight-black); transition: .2s ease; }
.community-switch button > span { min-width: 24px; height: 24px; padding: 0 6px; border-radius: 999px; display: inline-grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); font-size: var(--font-size-sm); }
.community-switch button:hover { background: var(--color-surface-warm); }
.community-switch button.active { background: var(--color-primary); color: var(--color-white); }
.community-switch button.active > span { background: var(--color-white); color: var(--color-primary-strong); }
.panel-title.compact { padding: 0 0 14px; }
.pet-card { padding: 24px; position: relative; }
.pet-card-avatar { width: 60px; height: 60px; border-radius: 50%; display: grid; place-items: center; overflow: hidden; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.pet-card-avatar img { width: 100%; height: 100%; object-fit: cover; }
.pet-card h2 { margin: 16px 0 4px; }
.pet-card p { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.pet-card-facts { display: flex; flex-wrap: wrap; gap: 6px; }
.pet-card-facts span { background: var(--color-surface-warm); border-radius: 999px; padding: 4px 9px; font-size: var(--font-size-sm); }
.pet-card-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 20px; border-top: 1px solid var(--color-border); padding-top: 13px; }
.pet-card-actions-main { display: flex; flex-wrap: wrap; gap: 7px; }
.order-list { display: grid; gap: 13px; }
.list-total { margin: 10px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; }
.content-delete { position: absolute; right: 10px; top: 10px; border: 0; border-radius: 999px; background: var(--color-surface-white-glass); padding: 6px 9px; display: flex; align-items: center; gap: 4px; color: var(--color-danger); font-size: var(--font-size-sm); z-index: 2; }
.my-comment-list { display: grid; gap: 10px; }
.social-user-scroll { max-height: min(58dvh, 520px); overflow-y: auto; padding-right: 4px; overscroll-behavior: contain; }
.social-load-status { margin: 8px 0 0; color: var(--color-text-muted); text-align: center; font-size: var(--font-size-sm); }
.user-list, .address-grid { display: grid; gap: 11px; }
.user-card, .address-card { display: flex; align-items: center; gap: 12px; padding: 15px 18px; }
.user-identity { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12px; padding: 0; border: 0; background: transparent; color: inherit; text-align: left; }
.user-identity img, .user-identity > span { width: 42px; height: 42px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary-strong); }
.user-identity div { min-width: 0; flex: 1; }
.user-identity b, .user-identity small { display: block; }
.user-identity b { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.user-identity small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.user-identity:hover b { color: var(--color-primary); }
.address-card > svg { color: var(--color-primary-strong); }
.address-card > div:not(.address-actions) { flex: 1; }
.address-card p { margin: 3px 0 0; color: var(--color-text-muted); font-size: var(--font-size-sm); }
.address-actions { display: flex; align-items: center; gap: 10px; flex: 0 0 auto; }
.checkbox { display: flex; align-items: center; gap: 8px; color: var(--color-text-secondary); font-size: var(--font-size-sm); }
.pet-photo-editor { display: flex; align-items: center; gap: 15px; margin-bottom: 18px; padding: 14px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-warm); }
.pet-photo-preview { width: 72px; height: 72px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border-radius: 24px 24px 8px 24px; background: var(--color-surface); color: var(--color-primary-strong); }
.pet-photo-preview img { width: 100%; height: 100%; object-fit: cover; }
.pet-photo-editor > div { display: grid; justify-items: start; gap: 4px; }
.pet-photo-editor small { color: var(--color-text-muted); font-size: var(--font-size-sm); }
.pet-photo-editor .btn { margin-top: 5px; }
.pet-neutered-check { align-self: end; min-height: 44px; margin-bottom: 16px; padding: 0 13px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-white); }
@media (max-width: 840px) {
  .profile-hero { grid-template-columns: auto 1fr; }
  .profile-stats { grid-column: 1 / -1; justify-content: space-around; }
  .profile-hero > .btn { grid-column: 1 / -1; justify-self: start; }
  .profile-workspace { grid-template-columns: 1fr; }
  .profile-tabs { position: static; display: flex; gap: 5px; overflow-x: auto; border-radius: 8px 18px 18px 8px; }
  .profile-tabs button { width: auto; min-width: max-content; }
  .profile-nav-note { display: none; }
  .profile-detail-grid { grid-template-columns: 1fr; }
}
@media (max-width: 540px) {
  .profile-hero { padding: 20px; }
  .profile-avatar { width: 74px; height: 74px; }
  .profile-hero h1 { font-size: var(--font-size-3xl); }
  .profile-section-head { align-items: flex-start; flex-direction: column; }
  .community-switch { width: 100%; }
  .community-switch button { flex: 1; justify-content: center; }
}
.profile-page { height: calc(100dvh - 84px); padding-bottom: 0; display: flex; flex-direction: column; overflow: hidden; }
.profile-page .profile-hero { flex: 0 0 auto; }
.profile-page .profile-workspace { flex: 1; min-height: 0; align-items: stretch; }
.profile-page .profile-tabs { position: static; height: 100%; overflow-y: auto; }
.profile-page .profile-content-scroll { height: 100%; min-height: 0; overflow-y: auto; padding: 0 8px 24px 0; overscroll-behavior: contain; scrollbar-gutter: stable; }
@media (max-width: 840px) {
  .profile-page { height: calc(100dvh - 70px); }
  .profile-page .profile-workspace { grid-template-rows: auto minmax(0, 1fr); }
  .profile-page .profile-tabs { height: auto; overflow-x: auto; overflow-y: hidden; }
  .profile-page .profile-content-scroll { padding-right: 2px; }
}
</style>
