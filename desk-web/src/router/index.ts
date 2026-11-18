import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/authStore'

/**
 * 同源页面按当前身份进入；角色守卫只辅助导航，后端逐次授权。
 * @author yangaobo
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/connection-error',
      component: () => import('../views/auth/ConnectionErrorView.vue'),
    },
    { path: '/login', component: () => import('../views/auth/LoginView.vue') },
    {
      path: '/password',
      component: () => import('../views/auth/PasswordView.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/HomeView.vue'),
        },
        {
          path: 'support',
          component: () => import('../views/support/SupportView.vue'),
          meta: { role: 'CUSTOMER_USER' },
        },
        {
          path: 'diagnosis',
          component: () => import('../views/diagnosis/DiagnosisView.vue'),
          meta: { role: 'CUSTOMER_USER' },
        },
        {
          path: 'administration/knowledge',
          component: () => import('../views/knowledge/KnowledgeAdminView.vue'),
          meta: { role: 'KNOWLEDGE_ADMIN' },
        },
        {
          path: 'tickets',
          component: () => import('../views/ticket/TicketListView.vue'),
        },
        {
          path: 'tickets/:id([0-9a-f]{32})',
          component: () => import('../views/ticket/TicketDetailView.vue'),
        },
        {
          path: 'notifications',
          component: () => import('../views/ticket/NotificationsView.vue'),
        },
        {
          path: 'administration/identity',
          component: () => import('../views/administration/IdentityView.vue'),
          meta: { role: 'SYSTEM_ADMIN' },
        },
        {
          path: 'administration/onsite-slots',
          component: () =>
            import('../views/administration/OnsiteSlotsView.vue'),
          meta: { role: 'SYSTEM_ADMIN' },
        },
        {
          path: 'administration/support',
          component: () =>
            import('../views/administration/SupportAdminView.vue'),
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('../views/NotFoundView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return true
  if (to.path === '/connection-error') return true
  try {
    await auth.refresh()
  } catch {
    return '/connection-error'
  }
  if (!auth.user) return '/login'
  if (auth.user.mustChangePassword && to.path !== '/password')
    return '/password'
  if (
    typeof to.meta.role === 'string' &&
    !auth.user.roles.some((role) => role === to.meta.role)
  )
    return '/'
  return true
})
