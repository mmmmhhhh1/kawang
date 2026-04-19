import { createRouter, createWebHistory } from 'vue-router'
import { getStoredProfile, hasAdminPermission, type AdminPermission } from '@/api/auth'
import { getStoredToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/pages/LoginPage.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/pages/AdminLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/products',
      children: [
        { path: 'products', name: 'products', component: () => import('@/pages/ProductPage.vue'), meta: { requiresAuth: true } },
        { path: 'accounts', name: 'accounts', component: () => import('@/pages/AccountPage.vue'), meta: { requiresAuth: true } },
        { path: 'orders', name: 'orders', component: () => import('@/pages/OrderPage.vue'), meta: { requiresAuth: true } },
        { path: 'users', name: 'users', component: () => import('@/pages/UserPage.vue'), meta: { requiresAuth: true } },
        { path: 'recharges', name: 'recharges', component: () => import('@/pages/RechargePage.vue'), meta: { requiresAuth: true } },
        {
          path: 'admins',
          name: 'admins',
          component: () => import('@/pages/AdminUserPage.vue'),
          meta: { requiresAuth: true, requiredPermission: 'CREATE_ADMIN' },
        },
        {
          path: 'payment-qr',
          name: 'payment-qr',
          component: () => import('@/pages/PaymentQrPage.vue'),
          meta: { requiresAuth: true, superAdminOnly: true },
        },
        { path: 'notices', name: 'notices', component: () => import('@/pages/NoticePage.vue'), meta: { requiresAuth: true } },
        { path: 'runtime', name: 'runtime', component: () => import('@/pages/RuntimePage.vue'), meta: { requiresAuth: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = getStoredToken()
  if (to.meta.requiresAuth && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'products' }
  }

  const profile = getStoredProfile()
  const requiredPermission = to.meta.requiredPermission as AdminPermission | undefined
  const superAdminOnly = Boolean(to.meta.superAdminOnly)

  if (superAdminOnly && profile && !profile.isSuperAdmin) {
    return { name: 'products' }
  }

  if (requiredPermission && profile && !hasAdminPermission(requiredPermission, profile)) {
    return { name: 'products' }
  }

  return true
})

export default router
