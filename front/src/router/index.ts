import { createRouter, createWebHistory } from 'vue-router'
import { getStoredToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/StoreLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/pages/HomePage.vue'),
        },
        {
          path: 'query',
          name: 'query',
          component: () => import('@/pages/OrderQueryPage.vue'),
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/pages/LoginPage.vue'),
          meta: { guestOnly: true },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/pages/RegisterPage.vue'),
          meta: { guestOnly: true },
        },
        {
          path: 'orders/me',
          name: 'my-orders',
          component: () => import('@/pages/MyOrdersPage.vue'),
          meta: { requiresAuth: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = getStoredToken()
  if (to.meta.requiresAuth && !token) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }
  if (to.meta.guestOnly && token) {
    return { name: 'home' }
  }
  return true
})

export default router
