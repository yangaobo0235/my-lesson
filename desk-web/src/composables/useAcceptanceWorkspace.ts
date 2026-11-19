import { onScopeDispose, ref } from 'vue'
import type { InjectionKey, Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  changeAcceptance,
  leaveAcceptance,
  readAcceptance,
} from '../api/auth/acceptanceApi'
import type { AcceptanceView } from '../api/auth/acceptanceApi'

export const acceptanceTicketKey: InjectionKey<Readonly<Ref<string | null>>> =
  Symbol('acceptanceTicket')

const RETURN_PATH = 'desk.acceptance.return'
const LAST_TICKET = 'desk.acceptance.ticket'

/** 本机验收跨真实身份重载页面，旧账号的草稿、请求和订阅随页面作用域释放。 @author yangaobo */
export function useAcceptanceWorkspace() {
  const route = useRoute()
  const router = useRouter()
  const view = ref<AcceptanceView | null>(null)
  const ticket = ref<string | null>(null)
  const controller = new AbortController()
  onScopeDispose(() => controller.abort())

  async function load() {
    if (!import.meta.env.DEV) return
    try {
      const result = await readAcceptance(controller.signal)
      if (controller.signal.aborted || !result) return
      view.value = result.view
      if (result.view === 'staff') {
        const saved = sessionStorage.getItem(LAST_TICKET)
        ticket.value =
          saved && /^[0-9a-f]{32}$/.test(saved) ? saved : result.ticketId
      }
    } catch {
      // 本机验收服务不可用时工作台仍按当前正常身份运行。
    }
  }

  function rememberTicket() {
    if (view.value !== 'customer' || route.name !== 'ticket-detail') return
    const id = route.params.id
    if (typeof id === 'string' && /^[0-9a-f]{32}$/.test(id)) {
      sessionStorage.setItem(LAST_TICKET, id)
    }
  }

  async function change(csrf: string) {
    if (!import.meta.env.DEV || !view.value) return
    if (view.value === 'staff')
      sessionStorage.setItem(RETURN_PATH, route.fullPath)
    else rememberTicket()
    const next = await changeAcceptance(csrf, controller.signal)
    let destination = router.resolve({ name: 'home' }).href
    if (next === 'staff') {
      const previous = sessionStorage.getItem(RETURN_PATH)
      if (previous && URL.canParse(previous, window.location.origin)) {
        const location = new URL(previous, window.location.origin)
        if (location.origin === window.location.origin) {
          destination = router.resolve(
            location.pathname + location.search + location.hash,
          ).href
        }
      }
      sessionStorage.removeItem(RETURN_PATH)
    }
    window.location.assign(destination)
  }

  async function leave(csrf: string) {
    if (!import.meta.env.DEV || !view.value) return
    await leaveAcceptance(csrf, controller.signal)
    sessionStorage.removeItem(RETURN_PATH)
    sessionStorage.removeItem(LAST_TICKET)
  }

  return { view, ticket, load, change, leave, rememberTicket }
}
