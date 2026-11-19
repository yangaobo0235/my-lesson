import { createRouter, createWebHistory } from 'vue-router'
import type { RouterHistory, RouteRecordRaw } from 'vue-router'
import type { Role } from '../api/auth/types'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/authStore'
import {
  hasWorkspaceAccess,
  SUPPORT_ADMIN_ROLES,
  TICKET_ROLES,
  type WorkspaceSection,
} from './workspaceAccess'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    customerTitle?: string
    roles?: readonly Role[]
    section?: WorkspaceSection
    public?: boolean
  }
}

/** 工作台入口登记访问角色、展示位置及详情所属分区。 @author yangaobo */
const workspace: RouteRecordRaw[] = [
  {
    path: '',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: {
      title: '工作台',
      customerTitle: '获取帮助',
    },
  },
  {
    path: 'support',
    name: 'report',
    component: () => import('../views/support/SupportView.vue'),
    meta: { title: '直接报修', roles: ['CUSTOMER_USER'], section: 'home' },
  },
  {
    path: 'support/services',
    name: 'my-services',
    component: () => import('../views/support/ServiceScopeView.vue'),
    meta: {
      title: '服务范围',
      roles: ['CUSTOMER_USER'],
    },
  },
  {
    path: 'tickets',
    name: 'tickets',
    component: () => import('../views/ticket/TicketListView.vue'),
    meta: {
      title: '工单中心',
      customerTitle: '我的工单',
      roles: TICKET_ROLES,
    },
  },
  {
    path: 'tickets/assigned',
    name: 'assigned-tickets',
    component: () => import('../views/ticket/TicketListView.vue'),
    props: { assigned: true },
    meta: {
      title: '我的处理',
      roles: ['TECHNICIAN'],
      section: 'tickets',
    },
  },
  {
    path: 'tickets/:id([0-9a-f]{32})',
    name: 'ticket-detail',
    component: () => import('../views/ticket/TicketDetailView.vue'),
    meta: { title: '工单详情', roles: TICKET_ROLES, section: 'tickets' },
  },
  {
    path: 'notifications',
    name: 'notifications',
    component: () => import('../views/ticket/NotificationsView.vue'),
    meta: { title: '通知', roles: TICKET_ROLES },
  },
  {
    path: 'diagnosis',
    name: 'diagnosis',
    component: () => import('../views/diagnosis/DiagnosisView.vue'),
    meta: {
      title: '咨询与排查',
      roles: ['CUSTOMER_USER'],
      section: 'home',
    },
  },
  {
    path: 'knowledge',
    name: 'knowledge',
    component: () => import('../views/knowledge/KnowledgeReadView.vue'),
    meta: {
      title: '帮助知识',
      roles: ['CUSTOMER_USER'],
    },
  },
  {
    path: 'administration/knowledge',
    name: 'knowledge-admin',
    component: () => import('../views/knowledge/KnowledgeAdminView.vue'),
    meta: {
      title: '资料修订',
      roles: ['KNOWLEDGE_ADMIN'],
    },
  },
  {
    path: 'administration/knowledge/indexes',
    name: 'knowledge-index',
    component: () => import('../views/knowledge/KnowledgeIndexView.vue'),
    meta: {
      title: '索引任务',
      roles: ['KNOWLEDGE_ADMIN'],
      section: 'knowledge-admin',
    },
  },
  {
    path: 'administration/knowledge/audit',
    name: 'knowledge-audit',
    component: () => import('../views/knowledge/KnowledgeAuditView.vue'),
    meta: {
      title: '发布审计',
      roles: ['KNOWLEDGE_ADMIN'],
      section: 'knowledge-admin',
    },
  },
  {
    path: 'administration/support',
    name: 'support-admin',
    component: () => import('../views/administration/SupportAdminView.vue'),
    meta: {
      title: '服务目录与资格',
      roles: SUPPORT_ADMIN_ROLES,
      section: 'service',
    },
  },
  {
    path: 'administration/assets',
    name: 'assets',
    component: () => import('../views/administration/AssetAdminView.vue'),
    meta: {
      title: '设备与型号',
      roles: ['SERVICE_AGENT'],
      section: 'service',
    },
  },
  {
    path: 'administration/onsite-slots',
    name: 'onsite-slots',
    component: () => import('../views/administration/OnsiteSlotsView.vue'),
    meta: {
      title: '上门时段',
      roles: ['SYSTEM_ADMIN'],
      section: 'service',
    },
  },
  {
    path: 'administration/identity',
    name: 'identity-admin',
    component: () => import('../views/administration/IdentityView.vue'),
    meta: {
      title: '组织与账号',
      roles: ['SYSTEM_ADMIN'],
    },
  },
  {
    path: 'administration/identity/audit',
    name: 'identity-audit',
    component: () => import('../views/administration/IdentityAuditView.vue'),
    meta: {
      title: '身份变更审计',
      roles: ['SYSTEM_ADMIN'],
      section: 'identity-admin',
    },
  },
  {
    path: ':pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: { title: '页面未找到' },
  },
]

/** 同源页面按当前身份进入。 @author yangaobo */
export function createWorkspaceRouter(
  history: RouterHistory = createWebHistory(),
) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/connection-error',
        name: 'connection-error',
        component: () => import('../views/auth/ConnectionErrorView.vue'),
        meta: { public: true },
      },
      {
        path: '/login',
        name: 'login',
        component: () => import('../views/auth/LoginView.vue'),
        meta: { public: true },
      },
      {
        path: '/password',
        name: 'password',
        component: () => import('../views/auth/PasswordView.vue'),
      },
      { path: '/', component: AppLayout, children: workspace },
    ],
  })

  router.beforeEach(async (to) => {
    if (to.meta.public) return true
    const auth = useAuthStore()
    try {
      await auth.refresh()
    } catch {
      return { name: 'connection-error' }
    }
    if (!auth.user) return { name: 'login' }
    if (auth.user.mustChangePassword && to.name !== 'password')
      return { name: 'password' }
    return hasWorkspaceAccess(auth.user, to.meta.roles) || { name: 'home' }
  })
  return router
}
