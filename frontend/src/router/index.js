import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUserAccessPromptStore } from '../stores/userAccessPrompt'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../components/UserLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('../views/user/HomeView.vue'), meta: { public: true } },
      { path: 'community', name: 'community', component: () => import('../views/user/CommunityView.vue'), meta: { public: true } },
      { path: 'community/:id', name: 'post-detail', component: () => import('../views/user/PostDetailView.vue'), meta: { public: true } },
      { path: 'posts/:id', redirect: (to) => `/community/${to.params.id}` },
      { path: 'mall', name: 'mall', component: () => import('../views/user/MallView.vue'), meta: { public: true } },
      { path: 'mall/:id', name: 'product-detail', component: () => import('../views/user/ProductDetailView.vue'), meta: { public: true } },
      { path: 'products/:id', redirect: (to) => `/mall/${to.params.id}` },
      { path: 'services', name: 'services', component: () => import('../views/user/ServiceView.vue'), meta: { public: true } },
      { path: 'services/:id', name: 'service-detail', component: () => import('../views/user/ServiceDetailView.vue'), meta: { public: true } },
      {
        path: 'health',
        name: 'health',
        component: () => import('../views/user/HealthManagementView.vue'),
        meta: { requiresUser: true, featureName: '宠物养护' }
      },
      {
        path: 'me',
        name: 'me',
        component: () => import('../views/user/MyView.vue'),
        meta: { requiresUser: true, featureName: '个人中心' }
      },
      { path: 'account', redirect: '/me' },
      {
        path: 'cart',
        name: 'cart',
        component: () => import('../views/user/CartView.vue'),
        meta: { requiresUser: true, featureName: '购物车' }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('../views/user/MessagesView.vue'),
        meta: { requiresUser: true, featureName: '消息中心' }
      },
      {
        path: 'messages/:userId',
        name: 'message-chat',
        component: () => import('../views/user/ChatView.vue'),
        meta: { requiresUser: true, featureName: '会话' }
      },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('../views/NotFoundView.vue')
      }
    ]
  },
  {
    path: '/merchant',
    component: () => import('../components/ConsoleLayout.vue'),
    props: { mode: 'merchant' },
    meta: { auth: true, role: 'business' },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      {
        path: 'dashboard',
        name: 'merchant-dashboard',
        component: () => import('../views/merchant/MerchantDashboardView.vue')
      },
      { path: 'shops', name: 'merchant-shops', component: () => import('../views/merchant/ShopManageView.vue') },
      { path: 'shop', redirect: '/merchant/shops' },
      { path: 'mall', name: 'merchant-mall', component: () => import('../views/merchant/MallManageView.vue') },
      { path: 'products', redirect: '/merchant/mall' },
      { path: 'services', name: 'merchant-services', component: () => import('../views/merchant/ServiceManageView.vue') },
      { path: 'orders', redirect: '/merchant/mall?tab=orders' },
      { path: 'messages', name: 'merchant-messages', component: () => import('../views/merchant/MerchantMessagesView.vue') }
    ]
  },
  {
    path: '/admin',
    component: () => import('../components/ConsoleLayout.vue'),
    props: { mode: 'admin' },
    meta: { auth: true, role: 'admin' },
    children: [
      { path: '', redirect: '/admin/overview' },
      {
        path: 'overview',
        name: 'admin-overview',
        component: () => import('../views/admin/AdminOverviewView.vue')
      },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserManageView.vue') },
      { path: 'merchants', name: 'admin-merchants', component: () => import('../views/admin/MerchantManageView.vue') },
      { path: 'community', name: 'admin-community', component: () => import('../views/admin/CommunityManageView.vue') },
      { path: 'knowledge', name: 'admin-knowledge', component: () => import('../views/admin/KnowledgeManageView.vue') },
      { path: 'messages', name: 'admin-messages', component: () => import('../views/admin/AdminMessagesView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  }
})

router.beforeEach(async (to, from) => {
  const auth = useAuthStore()
  await auth.checkSession()
  if (to.meta.public) {
    if (to.name === 'login' && auth.isAuthenticated) return auth.homePath()
    return true
  }
  if (to.meta.auth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: {
        mode: 'login',
        role: 'user',
        redirect: to.fullPath
      }
    }
  }
  const matchedRoute = to.matched.find((record) => record.meta.role)
  const role = to.meta.role || matchedRoute?.meta.role
  if (role && auth.role !== role) {
    if (!auth.isAuthenticated) {
      return {
        name: 'login',
        query: {
          mode: 'login',
          role,
          redirect: to.fullPath
        }
      }
    }
    return auth.homePath()
  }
  const requiresUser = to.matched.some((record) => record.meta.requiresUser)
  if (requiresUser && auth.role !== 'user') {
    const featureName = to.meta.featureName || '该页面'
    const accessPrompt = useUserAccessPromptStore()
    if (!auth.isAuthenticated) {
      accessPrompt.openGuest({ action: featureName, redirect: to.fullPath })
    } else {
      accessPrompt.openRole({
        action: featureName,
        redirect: to.fullPath,
        role: auth.role
      })
    }
    return from.name ? false : '/'
  }
  return true
})

export default router
