import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'

/**
 * 组织服务台页面与公共布局；业务守卫随身份入口接入。
 * @author yangaobo
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
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
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('../views/NotFoundView.vue'),
        },
      ],
    },
  ],
})
