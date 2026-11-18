import { request, query } from '../http'
import {
  choices,
  decimal,
  identifier,
  integer,
  nullable,
  page,
  record,
  text,
} from '../validation'
import { receipt } from '../ticket/types'

/** 区域窗口与容量来自后端，展示量不预占名额。 @author yangaobo */
export function slot(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    serviceAreaCode: text(o.serviceAreaCode),
    startsAt: text(o.startsAt),
    endsAt: text(o.endsAt),
    bookingOpensAt: text(o.bookingOpensAt),
    bookingClosesAt: text(o.bookingClosesAt),
    capacity: integer(o.capacity),
    reservedCount: integer(o.reservedCount),
    status: choices('DRAFT', 'PUBLISHED', 'CLOSED')(o.status),
    rowVersion: decimal(o.rowVersion),
  }
}

/** 约满与关停是已保存的拒绝结果；取消不覆盖履约。 */
export function booking(value: unknown) {
  const o = record(value)
  return {
    id: identifier(o.id),
    ticketId: identifier(o.ticketId),
    slotId: identifier(o.slotId),
    clientRequestId: identifier(o.clientRequestId),
    serviceAreaCode: text(o.serviceAreaCode),
    visitAddress: text(o.visitAddress),
    status: choices('CONFIRMED', 'REJECTED', 'CANCELLED', 'FINISHED')(o.status),
    resultCode: nullable(choices('SOLD_OUT', 'SLOT_CLOSED'))(o.resultCode),
    finishResult: nullable(choices('VISITED', 'NOT_PERFORMED'))(o.finishResult),
    assignedTechnicianId: nullable(identifier)(o.assignedTechnicianId),
    performedBy: nullable(identifier)(o.performedBy),
    finishNote: nullable(text)(o.finishNote),
    cancelReason: nullable(text)(o.cancelReason),
    rowVersion: decimal(o.rowVersion),
    createdAt: text(o.createdAt),
  }
}
export type OnsiteSlot = ReturnType<typeof slot>
export type OnsiteBooking = ReturnType<typeof booking>
export interface SlotInput {
  serviceAreaCode: string
  startsAt: string
  bookingOpensAt: string
  bookingClosesAt: string
  capacity: number
}
/** 管理动作同样校验CSRF，原版本不被容量刷新覆盖。 */
export const listSlots = (
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/onsite-slot/list', page(slot), {
    body: { pageNum },
    csrf,
    signal,
  })
export const addSlot = (body: SlotInput, csrf: string) =>
  request('admin/onsite-slot/add', slot, { body, csrf })
export const querySlot = (id: string, csrf: string, signal?: AbortSignal) =>
  request('admin/onsite-slot/query', slot, { body: { id }, csrf, signal })
export const updateSlot = (
  body: SlotInput & { id: string; rowVersion: string },
  csrf: string,
) => request('admin/onsite-slot/update', slot, { body, csrf })
export const changeSlot = (
  action: 'publish' | 'close',
  value: OnsiteSlot,
  csrf: string,
) =>
  request(`admin/onsite-slot/${action}`, slot, {
    body: { id: value.id, rowVersion: value.rowVersion },
    csrf,
  })
export const ticketSlots = (
  id: string,
  pageNum: number,
  signal?: AbortSignal,
) =>
  request(`tickets/${id}/onsite-slots${query({ pageNum })}`, page(slot), {
    signal,
  })
export const bookingHistory = (
  id: string,
  pageNum: number,
  signal?: AbortSignal,
) =>
  request(`tickets/${id}/onsite-bookings${query({ pageNum })}`, page(booking), {
    signal,
  })
export const bookOnsite = (
  id: string,
  body: {
    slotId: string
    clientRequestId: string
    rowVersion: string
    sourceDraftId?: string
    sourceDraftRootId?: string
    contentSha256?: string
  },
  csrf: string,
) => request(`tickets/${id}/onsite-bookings`, booking, { body, csrf })
export const bookingReceipt = (id: string, key: string, signal?: AbortSignal) =>
  request(`tickets/${id}/onsite-bookings/by-request/${key}`, booking, {
    signal,
  })
export const queryBooking = (id: string, csrf: string, signal?: AbortSignal) =>
  request('admin/onsite-booking/query', booking, { body: { id }, csrf, signal })
export type BookingAction =
  | {
      name: 'cancel'
      body: { clientActionId: string; rowVersion: string; reason: string }
    }
  | {
      name: 'assign'
      body: {
        clientActionId: string
        rowVersion: string
        technicianId: string
        reason: string
      }
    }
  | {
      name: 'finish'
      body: {
        clientActionId: string
        rowVersion: string
        finishResult: 'VISITED' | 'NOT_PERFORMED'
        performedBy: string
        finishNote: string
      }
    }
/** 所有预约修改均返回原提交回执，当前预约另行查询。 */
export const changeBooking = (
  id: string,
  bookingId: string,
  action: BookingAction,
  csrf: string,
) =>
  request(
    `tickets/${id}/onsite-bookings/${bookingId}/${action.name}`,
    receipt,
    { body: action.body, csrf },
  )
export const unifiedReceipt = (key: string, signal?: AbortSignal) =>
  request(`tickets/action-receipts/by-action/${key}`, receipt, { signal })
export const setOnsiteEligibility = (
  id: string,
  rowVersion: string,
  clientActionId: string,
  onsiteEligible: boolean,
  reason: string,
  csrf: string,
) =>
  request(`tickets/${id}/onsite-eligibility`, receipt, {
    body: { rowVersion, clientActionId, onsiteEligible, reason },
    csrf,
  })
