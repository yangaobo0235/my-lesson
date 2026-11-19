import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ViteDevServer } from 'vite'
import { localAcceptance } from '../../vite/acceptance.mjs'

vi.mock('node:fs', () => ({
  readFileSync: () =>
    JSON.stringify({
      admin: { ownerId: 'a'.repeat(32) },
      customer: {
        ownerId: 'b'.repeat(32),
        organizationId: 'c'.repeat(32),
        username: 'fixture-customer',
        password: 'test-password-123',
      },
    }),
}))

type Request = {
  method: string
  url: string
  headers: Record<string, string>
  socket: { remoteAddress: string }
}
type ResponseHeaders = Record<string, string | string[]>
type Handler = (
  request: Request,
  response: {
    writeHead: (status: number, headers: ResponseHeaders) => void
    end: (payload: string) => void
  },
) => void

/** 模拟本机消费端和真实身份服务会话合同，核对重复切换与退出边界。 @author yangaobo */
function fixture() {
  const staff = {
    ownerId: 'a'.repeat(32),
    organizationType: 'SERVICE_PROVIDER',
    roles: ['SYSTEM_ADMIN'],
    customerIds: ['c'.repeat(32)],
    mustChangePassword: false,
  }
  const customer = {
    ownerId: 'b'.repeat(32),
    organizationId: 'c'.repeat(32),
    organizationType: 'CUSTOMER',
    roles: ['CUSTOMER_USER'],
    mustChangePassword: false,
  }
  const sessions = new Map<string, typeof staff | typeof customer>([
    ['staff-session', staff],
  ])
  const login = vi.fn()
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, request: RequestInit) => {
      const action = url.split('/').at(-1)
      const current = new Headers(request.headers).get('cookie')?.split('=')[1]
      const identity = current ? sessions.get(current) : undefined
      if (action === 'me')
        return Response.json(
          { code: identity ? 0 : 2001, data: identity },
          { status: identity ? 200 : 401 },
        )
      if (action === 'logout' && current) {
        sessions.delete(current)
        return Response.json({ code: 0, data: null })
      }
      if (action === 'login') {
        login()
        sessions.set('customer-session', customer)
        return Response.json(
          { code: 0, data: customer },
          {
            headers: {
              'Set-Cookie': 'DESK_SESSION=customer-session; Path=/; HttpOnly',
            },
          },
        )
      }
      return Response.json(
        { code: 0, data: { token: 'csrf-' + (current ?? 'guest') } },
        {
          headers: current
            ? undefined
            : { 'Set-Cookie': 'DESK_SESSION=guest; Path=/; HttpOnly' },
        },
      )
    }),
  )
  let handle: Handler | undefined
  const config = localAcceptance().configureServer
  if (typeof config !== 'function') throw new Error('缺少 Vite 开发入口')
  config({
    middlewares: {
      use: (_path: string, callback: Handler) => {
        handle = callback
      },
    },
  } as unknown as ViteDevServer)
  const send = (
    url: string,
    session = '',
    marker = '',
    csrf = 'csrf-' + session,
    origin = 'http://127.0.0.1:26199',
  ) =>
    new Promise<{ status: number; data: { view?: string }; cookies: string[] }>(
      (resolve) => {
        if (!handle) throw new Error('验收入口未注册')
        let status = 0
        let cookies: string[] = []
        handle(
          {
            method: url === '/status' ? 'GET' : 'POST',
            url,
            socket: { remoteAddress: '127.0.0.1' },
            headers: {
              host: '127.0.0.1:26199',
              origin,
              'x-csrf-token': csrf,
              cookie: 'DESK_SESSION=' + session + '; DESK_ACCEPTANCE=' + marker,
            },
          },
          {
            writeHead: (code, headers) => {
              status = code
              cookies = (headers['Set-Cookie'] ?? []) as string[]
            },
            end: (payload) =>
              resolve({ status, data: JSON.parse(payload), cookies }),
          },
        )
      },
    )
  return { send, sessions, login }
}

afterEach(() => vi.unstubAllGlobals())

describe('本机验收会话', () => {
  it('验证 CSRF 和来源并拒绝匿名切换', async () => {
    const { send, login } = fixture()
    expect((await send('/switch')).status).toBe(403)
    expect((await send('/switch', 'staff-session', '', 'bad')).status).toBe(403)
    expect(
      (
        await send(
          '/switch',
          'staff-session',
          '',
          'csrf-staff-session',
          'https://example.com',
        )
      ).status,
    ).toBe(403)
    expect(login).not.toHaveBeenCalled()
  })

  it('重复往返只登录客户一次，退出注销实际双方会话', async () => {
    const { send, sessions, login } = fixture()
    const entered = await send('/switch', 'staff-session')
    expect(entered.data.view).toBe('customer')
    const marker = entered.cookies
      .find((cookie) => cookie.startsWith('DESK_ACCEPTANCE='))
      ?.split(';')[0]
      ?.split('=')[1]
    expect(marker).toHaveLength(64)
    for (let index = 0; index < 12; index++) {
      expect(
        (await send('/switch', 'customer-session', marker)).data.view,
      ).toBe('staff')
      expect((await send('/switch', 'staff-session', marker)).data.view).toBe(
        'customer',
      )
    }
    expect(login).toHaveBeenCalledTimes(1)
    expect((await send('/logout', 'customer-session', marker)).status).toBe(200)
    expect(sessions.size).toBe(0)
    expect((await send('/switch', 'customer-session', marker)).status).toBe(403)
  })
})
