import type { Identity, Role } from '../api/auth/types'

/** 工单、通知和概况对下列角色开放，具体数据范围由服务端校验。 @author yangaobo */
export const TICKET_ROLES: readonly Role[] = [
  'CUSTOMER_USER',
  'SERVICE_AGENT',
  'TECHNICIAN',
]

export const SUPPORT_ADMIN_ROLES: readonly Role[] = [
  'SYSTEM_ADMIN',
  'SERVICE_AGENT',
]

export const STAFF_ROLES: readonly Role[] = [
  'SYSTEM_ADMIN',
  'SERVICE_AGENT',
  'TECHNICIAN',
  'KNOWLEDGE_ADMIN',
]

/** 导航和路由入口共用角色条件。多角色账号叠加各自入口。 @author yangaobo */
export function hasWorkspaceAccess(
  identity: Pick<Identity, 'roles'> | null,
  roles?: readonly Role[],
): boolean {
  return (
    !!identity &&
    (!roles || roles.some((role) => identity.roles.includes(role)))
  )
}

/** 一级入口按业务归属，子页沿用各自的角色守卫。 @author yangaobo */
export const WORKSPACE_SECTIONS = [
  {
    id: 'home',
    title: '工作台',
    customerTitle: '获取帮助',
    entries: ['home', 'report', 'diagnosis'],
  },
  {
    id: 'tickets',
    title: '工单中心',
    customerTitle: '我的工单',
    entries: ['tickets', 'assigned-tickets'],
  },
  {
    id: 'service',
    title: '客户服务',
    entries: ['support-admin', 'assets', 'onsite-slots'],
  },
  { id: 'my-services', title: '服务范围', entries: ['my-services'] },
  {
    id: 'knowledge-admin',
    title: '知识管理',
    entries: ['knowledge-admin', 'knowledge-index', 'knowledge-audit'],
  },
  { id: 'knowledge', title: '帮助知识', entries: ['knowledge'] },
  {
    id: 'identity-admin',
    title: '系统管理',
    entries: ['identity-admin', 'identity-audit'],
  },
] as const

export type WorkspaceSection = (typeof WORKSPACE_SECTIONS)[number]['id']
