import { createRouter, createWebHistory } from 'vue-router'
import { getStoredToken } from '@/api/http'
import StoreLayout from '@/layouts/StoreLayout.vue'
import HomePage from '@/pages/HomePage.vue'
import OrderQueryPage from '@/pages/OrderQueryPage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import RegisterPage from '@/pages/RegisterPage.vue'
import MyOrdersPage from '@/pages/MyOrdersPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: StoreLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: HomePage,
        },
        {
          path: 'query',
          name: 'query',
          component: OrderQueryPage,
        },
        {
          path: 'login',
          name: 'login',
          component: LoginPage,
          meta: { guestOnly: true },
        },
        {
          path: 'register',
          name: 'register',
          component: RegisterPage,
          meta: { guestOnly: true },
        },
        {
          path: 'orders/me',
          name: 'my-orders',
          component: MyOrdersPage,
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
