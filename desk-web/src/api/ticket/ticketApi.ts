import { request, query } from '../http'
import { empty, page } from '../validation'
import {
  detail,
  notification,
  overview,
  receipt,
  ticket,
  ticketEvent,
  type TicketAction,
  type TicketCreateForm,
  type TicketStatus,
} from './types'

/** 所有写入均携带原动作键；网络失败时由页面保留输入并查询回执。 @author yangaobo */
export const createTicket = (
  body: TicketCreateForm,
  csrf: string,
  signal?: AbortSignal,
) => request('tickets', ticket, { body, csrf, signal })
export const creationReceipt = (key: string, signal?: AbortSignal) =>
  request(`tickets/by-request/${key}`, ticket, { signal })
export const getTicket = (id: string, signal?: AbortSignal) =>
  request(`tickets/${id}`, detail, { signal })
export const listTickets = (
  filters: {
    pageNum: number
    status?: TicketStatus
    triage?: boolean
    overdue?: boolean
    assignedToMe?: boolean
  },
  signal?: AbortSignal,
) => request(`tickets${query(filters)}`, page(ticket), { signal })
export const listEvents = (id: string, pageNum: number, signal?: AbortSignal) =>
  request(
    `tickets/${id}/events${query({ pageNum, pageSize: 50 })}`,
    page(ticketEvent),
    { signal },
  )
export const getOverview = (signal?: AbortSignal) =>
  request('tickets/overview', overview, { signal })
export const listNotifications = (pageNum: number, signal?: AbortSignal) =>
  request(`tickets/notifications${query({ pageNum })}`, page(notification), {
    signal,
  })
export const readNotification = (id: string, csrf: string) =>
  request(`tickets/notifications/${id}/read`, empty, { body: {}, csrf })
export const actionReceipt = (id: string, key: string, signal?: AbortSignal) =>
  request(`tickets/${id}/actions/${key}`, receipt, { signal })
export const act = (
  id: string,
  action: TicketAction,
  csrf: string,
  signal?: AbortSignal,
) =>
  action.name === 'context'
    ? request(`tickets/${id}/context`, ticket, {
        body: action.form,
        csrf,
        signal,
      })
    : request(`tickets/${id}/${action.name}`, receipt, {
        body: action.form,
        csrf,
        signal,
      })
