import { choices, identifier, nullable, record } from '../validation'

const view = choices('staff', 'customer')
export type AcceptanceView = ReturnType<typeof view>

/** 本机开发入口只返回验收视角和可选的正式工单标识。 @author yangaobo */
export async function readAcceptance(signal: AbortSignal) {
  const response = await fetch('/__desk_acceptance/status', { signal })
  if (!response.ok) return null
  const data = record(await response.json())
  return {
    view: nullable(view)(data.view),
    ticketId: nullable(identifier)(data.ticketId),
  }
}

/** 使用当前真实会话的令牌切换视角。 @author yangaobo */
export async function changeAcceptance(csrf: string, signal: AbortSignal) {
  const response = await fetch('/__desk_acceptance/switch', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrf },
    signal,
  })
  if (!response.ok) {
    if (response.status === 401)
      throw new Error('服务商会话已过期，请重新登录。')
    if (response.status === 409)
      throw new Error('同一会话正在切换，请刷新后查看当前视角。')
    if (response.status === 429)
      throw new Error('登录请求过多，请稍后再进入客户体验。')
    throw new Error('切换视角失败，请稍后重试。')
  }
  return view(record(await response.json()).view)
}

/** 显式退出时销毁本次验收的两份会话。 @author yangaobo */
export async function leaveAcceptance(csrf: string, signal: AbortSignal) {
  const response = await fetch('/__desk_acceptance/logout', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrf },
    signal,
  })
  if (!response.ok) throw new Error('退出登录失败，请稍后重试。')
}
