import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { createWorkspaceRouter } from '../../src/router'
import {
  STAFF_ROLES,
  WORKSPACE_SECTIONS,
} from '../../src/router/workspaceAccess'
import type { Role } from '../../src/api/auth/types'
import { useIntakeStore } from '../../src/stores/intakeStore'
import {
  readTicketQueue,
  ticketQueueQuery,
  ticketQueueReturn,
} from '../../src/views/ticket/ticketQueueRoute'

const ownerId = 'a'.repeat(32)
const organizationId = 'b'.repeat(32)

function identity(roles: readonly Role[], mustChangePassword = false) {
  const data = {
    ownerId,
    organizationId,
    organizationType: roles.includes('CUSTOMER_USER')
      ? 'CUSTOMER'
      : 'SERVICE_PROVIDER',
    displayName: '测试账号',
    roles,
    customerIds: [],
    authVersion: '1',
    sessionRef: null,
    mustChangePassword,
  }
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          JSON.stringify({ code: 0, message: '成功', data, traceId: 'trace' }),
          { headers: { 'Content-Type': 'application/json' } },
        ),
    ),
  )
}

beforeEach(() => setActivePinia(createPinia()))
afterEach(() => vi.unstubAllGlobals())

/** 组合角色只叠加实际有权的路由，不覆盖客户入口和首次改密。 @author yangaobo */
describe('工作台身份入口', () => {
  it('同一业务页面共用侧栏入口，管理子页保留原地址', () => {
    const router = createWorkspaceRouter(createMemoryHistory())
    for (const section of WORKSPACE_SECTIONS) {
      for (const name of section.entries) {
        const route = router.resolve({ name })
        expect(route.meta.section ?? route.name).toBe(section.id)
      }
    }
    expect(
      router.resolve({ name: 'ticket-detail', params: { id: ownerId } }).meta
        .section,
    ).toBe('tickets')
  })

  it('工单详情返回实际来源和筛选，拒绝无权的技术人员队列', () => {
    const state = readTicketQueue(
      { status: 'PROCESSING', page: '4', overdue: '1' },
      true,
    )
    expect(
      ticketQueueReturn(
        { status: 'PROCESSING', page: '4', overdue: '1', from: 'assigned' },
        true,
      ),
    ).toEqual({
      name: 'assigned-tickets',
      query: { ...ticketQueueQuery(state.filters, 4) },
    })
    expect(
      ticketQueueReturn(
        { from: 'assigned', page: '-1', status: ['SUBMITTED'] },
        false,
      ),
    ).toEqual({
      name: 'tickets',
      query: ticketQueueQuery(readTicketQueue({}, false).filters, 1),
    })
  })
  it('全服务商角色可进入客服、技术、知识和管理页面', async () => {
    identity(STAFF_ROLES)
    const router = createWorkspaceRouter(createMemoryHistory())
    for (const name of [
      'tickets',
      'assigned-tickets',
      'support-admin',
      'assets',
      'onsite-slots',
      'knowledge-admin',
      'knowledge-index',
      'knowledge-audit',
      'identity-admin',
      'identity-audit',
    ]) {
      await router.push({ name })
      expect(router.currentRoute.value.name).toBe(name)
    }
    await router.push({ name: 'report' })
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('客户可看本人入口，直达后台地址仍回到客户首页', async () => {
    identity(['CUSTOMER_USER'])
    const router = createWorkspaceRouter(createMemoryHistory())
    for (const name of ['my-services', 'report', 'diagnosis', 'knowledge']) {
      await router.push({ name })
      expect(router.currentRoute.value.name).toBe(name)
    }
    await router.push({ name: 'support-admin' })
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('首次改密优先于工作台权限', async () => {
    identity(STAFF_ROLES, true)
    const router = createWorkspaceRouter(createMemoryHistory())
    await router.push({ name: 'identity-admin' })
    expect(router.currentRoute.value.name).toBe('password')
  })

  it('首页草稿仅供匹配账号与目的页消费一次', () => {
    const intake = useIntakeStore()
    expect(intake.take(undefined, 'report')).toBe('')
    intake.prepare(ownerId, 'report', ' 无法连接办公网络 ')
    expect(intake.take(ownerId, 'report')).toBe('无法连接办公网络')
    expect(intake.take(ownerId, 'report')).toBe('')
    intake.prepare(ownerId, 'diagnosis', '另一问题')
    expect(intake.take(organizationId, 'diagnosis')).toBe('')
    expect(intake.take(ownerId, 'diagnosis')).toBe('')
  })
})
