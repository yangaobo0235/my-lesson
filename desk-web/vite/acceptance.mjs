import { randomBytes, timingSafeEqual } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const origin = 'http://127.0.0.1:26199'
const gateway = 'http://127.0.0.1:26101/api/v1/users/auth/'
const sessionName = 'DESK_SESSION'
const switchName = 'DESK_ACCEPTANCE'
const duration = 30 * 60
const id = /^[0-9a-f]{32}$/
class AcceptanceRateLimit extends Error {}

function profiles() {
  try {
    const data = JSON.parse(
      readFileSync(
        join(homedir(), '.config/service-desk/acceptance-accounts.json'),
        'utf8',
      ),
    )
    const { admin, customer, ticketId } = data
    if (
      id.test(admin?.ownerId) &&
      id.test(customer?.ownerId) &&
      id.test(customer?.organizationId) &&
      typeof customer.username === 'string' &&
      typeof customer.password === 'string' &&
      customer.password.length >= 12
    )
      return {
        adminId: admin.ownerId,
        customer,
        ticketId: id.test(ticketId) ? ticketId : null,
      }
  } catch {
    // 本机没有配好验收身份时不开放切换入口。
  }
}

function cookie(header, name) {
  const pair = (header ?? '')
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(name + '='))
  const value = pair?.slice(name.length + 1)
  return value && /^[\w.=-]+$/.test(value) ? value : undefined
}

function cookieHeader(name, value, maxAge) {
  return (
    name +
    '=' +
    value +
    '; Path=/; HttpOnly; SameSite=Strict' +
    (maxAge === undefined ? '' : '; Max-Age=' + maxAge)
  )
}

async function auth(action, session, body, token) {
  const response = await fetch(gateway + action, {
    method: body ? 'POST' : 'GET',
    redirect: 'error',
    headers: {
      ...(session ? { cookie: sessionName + '=' + session } : {}),
      ...(body
        ? { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token }
        : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(5000),
  })
  if (response.status === 429) throw new AcceptanceRateLimit()
  const result = await response.json()
  if (!response.ok || result.code !== 0) return null
  return {
    data: result.data,
    session: response.headers
      .getSetCookie()
      .map((value) => cookie(value, sessionName))
      .find(Boolean),
  }
}

function equalToken(left, right) {
  return (
    typeof left === 'string' &&
    typeof right === 'string' &&
    Buffer.byteLength(left) === Buffer.byteLength(right) &&
    timingSafeEqual(Buffer.from(left), Buffer.from(right))
  )
}

function send(response, status, data, cookies = []) {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    ...(cookies.length ? { 'Set-Cookie': cookies } : {}),
  })
  response.end(JSON.stringify(data))
}

function staff(identity, profile) {
  return (
    identity?.ownerId === profile.adminId &&
    identity.organizationType === 'SERVICE_PROVIDER' &&
    !identity.mustChangePassword &&
    identity.roles.includes('SYSTEM_ADMIN') &&
    identity.customerIds.includes(profile.customer.organizationId)
  )
}

function customer(identity, profile) {
  return (
    identity?.ownerId === profile.customer.ownerId &&
    identity.organizationId === profile.customer.organizationId &&
    identity.organizationType === 'CUSTOMER' &&
    !identity.mustChangePassword &&
    identity.roles.length === 1 &&
    identity.roles[0] === 'CUSTOMER_USER'
  )
}

/** 本机 Vite 验收入口通过正式身份服务建立独立客户会话，只允许已登记管理员切换。 @author yangaobo */
export function localAcceptance() {
  const visits = new Map()
  const switching = new Set()
  return {
    name: 'desk-local-acceptance',
    configureServer(server) {
      server.middlewares.use('/__desk_acceptance', (request, response) => {
        void handle(request, response).catch((error) =>
          send(response, error instanceof AcceptanceRateLimit ? 429 : 503, {}),
        )
      })
    },
  }

  async function handle(request, response) {
    const local = ['127.0.0.1', '::ffff:127.0.0.1'].includes(
      request.socket.remoteAddress,
    )
    const read = request.method === 'GET' && request.url === '/status'
    const change =
      request.method === 'POST' && ['/switch', '/logout'].includes(request.url)
    if (
      !local ||
      request.headers.host !== new URL(origin).host ||
      (!read && !change)
    ) {
      return send(response, 404, {})
    }
    if (change && request.headers.origin !== origin)
      return send(response, 403, {})
    const profile = profiles()
    const currentSession = cookie(request.headers.cookie, sessionName)
    const visitKey = cookie(request.headers.cookie, switchName)
    for (const [key, visit] of visits)
      if (visit.expires <= Date.now()) visits.delete(key)
    if (!profile || !currentSession)
      return send(response, read ? 200 : 403, { view: null, ticketId: null })

    const identity = (await auth('me', currentSession))?.data
    const visit = visits.get(visitKey)
    const canEnter = staff(identity, profile)
    const canReturn =
      visit?.customer === currentSession && customer(identity, profile)
    const owned =
      canReturn || (canEnter && visit?.admin === currentSession)
        ? visit
        : undefined
    if (read) {
      return send(response, 200, {
        view: canEnter ? 'staff' : canReturn ? 'customer' : null,
        ticketId: canEnter ? profile.ticketId : null,
      })
    }
    if (!canEnter && !canReturn) return send(response, 403, {})
    if (switching.has(currentSession)) return send(response, 409, {})
    switching.add(currentSession)
    try {
      const token = (await auth('csrf', currentSession))?.data?.token
      if (!equalToken(token, request.headers['x-csrf-token']))
        return send(response, 403, {})
      if (request.url === '/logout') {
        if (owned) {
          const other = canReturn ? owned.admin : owned.customer
          if ((await auth('me', other))?.data) {
            const otherToken = (await auth('csrf', other))?.data?.token
            if (!otherToken || !(await auth('logout', other, {}, otherToken))) {
              return send(response, 503, {})
            }
          }
        }
        if (!(await auth('logout', currentSession, {}, token)))
          return send(response, 503, {})
        if (owned) visits.delete(visitKey)
        return send(response, 200, {}, [
          cookieHeader(sessionName, '', 0),
          cookieHeader(switchName, '', 0),
        ])
      }
      if (canReturn) {
        if (!staff((await auth('me', visit.admin))?.data, profile)) {
          visits.delete(visitKey)
          return send(response, 401, {}, [cookieHeader(switchName, '', 0)])
        }
        return send(response, 200, { view: 'staff' }, [
          cookieHeader(sessionName, visit.admin),
        ])
      }
      if (owned) {
        if (customer((await auth('me', owned.customer))?.data, profile)) {
          return send(response, 200, { view: 'customer' }, [
            cookieHeader(sessionName, owned.customer),
          ])
        }
        visits.delete(visitKey)
      }
      if (
        [...visits.values()].some((entry) => entry.admin === currentSession) ||
        visits.size >= 8
      ) {
        return send(response, 409, {})
      }
      const ready = await auth('csrf')
      if (!ready?.session) return send(response, 503, {})
      const login = await auth(
        'login',
        ready.session,
        {
          username: profile.customer.username,
          password: profile.customer.password,
        },
        ready.data.token,
      )
      if (!login?.session || !customer(login.data, profile))
        return send(response, 403, {})
      const key = randomBytes(32).toString('hex')
      visits.set(key, {
        admin: currentSession,
        customer: login.session,
        expires: Date.now() + duration * 1000,
      })
      return send(response, 200, { view: 'customer' }, [
        cookieHeader(sessionName, login.session),
        cookieHeader(switchName, key, duration),
      ])
    } finally {
      switching.delete(currentSession)
    }
  }
}
