import { createRouter, createWebHistory } from 'vue-router'
import { getStoredProfile, hasAdminPermission, type AdminPermission } from '@/api/auth'
import { getStoredToken } from '@/api/http'
import LoginPage from '@/pages/LoginPage.vue'
import AdminLayout from '@/pages/AdminLayout.vue'
import ProductPage from '@/pages/ProductPage.vue'
import AccountPage from '@/pages/AccountPage.vue'
import OrderPage from '@/pages/OrderPage.vue'
import NoticePage from '@/pages/NoticePage.vue'
import UserPage from '@/pages/UserPage.vue'
import AdminUserPage from '@/pages/AdminUserPage.vue'
import RechargePage from '@/pages/RechargePage.vue'
import PaymentQrPage from '@/pages/PaymentQrPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginPage,
      meta: { public: true },
    },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      redirect: '/products',
      children: [
        { path: 'products', name: 'products', component: ProductPage, meta: { requiresAuth: true } },
        { path: 'accounts', name: 'accounts', component: AccountPage, meta: { requiresAuth: true } },
        { path: 'orders', name: 'orders', component: OrderPage, meta: { requiresAuth: true } },
        { path: 'users', name: 'users', component: UserPage, meta: { requiresAuth: true } },
        { path: 'recharges', name: 'recharges', component: RechargePage, meta: { requiresAuth: true } },
        {
          path: 'admins',
          name: 'admins',
          component: AdminUserPage,
          meta: { requiresAuth: true, requiredPermission: 'CREATE_ADMIN' },
        },
        {
          path: 'payment-qr',
          name: 'payment-qr',
          component: PaymentQrPage,
          meta: { requiresAuth: true, superAdminOnly: true },
        },
        { path: 'notices', name: 'notices', component: NoticePage, meta: { requiresAuth: true } },
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