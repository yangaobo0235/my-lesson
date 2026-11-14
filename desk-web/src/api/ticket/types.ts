import {
  array,
  bool,
  choices,
  decimal,
  identifier,
  integer,
  nullable,
  record,
  text,
} from '../validation'
import {
  affectedScope,
  connectionType,
  type SupportContextForm,
} from '../support/types'

export const status = choices(
  'SUBMITTED',
  'PROCESSING',
  'WAITING_CUSTOMER',
  'RESOLVED',
  'CLOSED',
  'CANCELLED',
)
export type TicketStatus = ReturnType<typeof status>
export const statusNames: Record<TicketStatus, string> = {
  SUBMITTED: '待受理',
  PROCESSING: '处理中',
  WAITING_CUSTOMER: '待补充',
  RESOLVED: '待确认',
  CLOSED: '已结单',
  CANCELLED: '已取消',
}
/** 固定环境快照转为编辑表单时保留空值，不从历史模型回答推断。 */
export function supportContext(v: unknown): SupportContextForm {
  const o = record(v)
  return {
    osName: nullable(text)(o.osName),
    osVersion: nullable(text)(o.osVersion),
    softwareName: nullable(text)(o.softwareName),
    softwareVersion: nullable(text)(o.softwareVersion),
    errorCode: nullable(text)(o.errorCode),
    connectionType: connectionType(o.connectionType),
    affectedScope: affectedScope(o.affectedScope),
  }
}
/** 页面消费明确投影，额外服务端字段不进入编辑表单。 @author yangaobo */
export function ticket(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    ticketNo: text(o.ticketNo),
    customerId: identifier(o.customerId),
    requesterId: identifier(o.requesterId),
    title: text(o.title),
    symptom: text(o.symptom),
    attemptedActions: nullable(text)(o.attemptedActions),
    serviceItemId: nullable(identifier)(o.serviceItemId),
    deviceId: nullable(identifier)(o.deviceId),
    status: status(o.status),
    priority: choices('NORMAL', 'HIGH')(o.priority),
    assigneeId: nullable(identifier)(o.assigneeId),
    rowVersion: decimal(o.rowVersion),
    createdAt: text(o.createdAt),
    updatedAt: text(o.updatedAt),
    acceptanceDueAt: text(o.acceptanceDueAt),
    confirmationDueAt: nullable(text)(o.confirmationDueAt),
    resolutionType: nullable(
      choices('RESTORED', 'GUIDANCE', 'REFERRED', 'LEGACY_UNKNOWN'),
    )(o.resolutionType),
    closureReason: nullable(
      choices('CUSTOMER_CONFIRMED', 'CONFIRMATION_TIMEOUT', 'LEGACY_UNKNOWN'),
    )(o.closureReason),
    supportContext: supportContext(o.supportContext),
    serviceAreaCode: nullable(text)(o.serviceAreaCode),
    serviceLocation: nullable(text)(o.serviceLocation),
    onsiteEligible: bool(o.onsiteEligible),
  }
}
export type Ticket = ReturnType<typeof ticket>
export function ticketEvent(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    sequenceNumber: integer(o.sequenceNumber),
    eventType: text(o.eventType),
    visibility: choices('PUBLIC', 'INTERNAL')(o.visibility),
    content: nullable(text)(o.content),
    createdAt: text(o.createdAt),
    toStatus: nullable(status)(o.toStatus),
  }
}
export type TicketEvent = ReturnType<typeof ticketEvent>
export function detail(v: unknown) {
  const o = record(v)
  return { ticket: ticket(o.ticket), events: array(ticketEvent)(o.events) }
}
export function receipt(v: unknown) {
  const o = record(v)
  return {
    eventId: identifier(o.eventId),
    ticketId: identifier(o.ticketId),
    sequenceNumber: integer(o.sequenceNumber),
    toStatus: status(o.toStatus),
  }
}
export function overview(v: unknown) {
  const o = record(v)
  return {
    submitted: decimal(o.submitted),
    processing: decimal(o.processing),
    waitingCustomer: decimal(o.waitingCustomer),
    resolved: decimal(o.resolved),
    overdue: decimal(o.overdue),
    triage: decimal(o.triage),
    customerConfirmed: decimal(o.customerConfirmed),
    confirmationTimeout: decimal(o.confirmationTimeout),
  }
}
export type Overview = ReturnType<typeof overview>
export function notification(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    ticketId: identifier(o.ticketId),
    title: text(o.title),
    eventType: text(o.eventType),
    createdAt: text(o.createdAt),
    readAt: nullable(text)(o.readAt),
  }
}
export type Notification = ReturnType<typeof notification>
export interface TicketCreateForm {
  clientRequestId: string
  serviceItemId: string | null
  deviceId: string | null
  title: string
  symptom: string
  attemptedActions: string | null
  supportContext: SupportContextForm
  serviceAreaCode: string | null
  serviceLocation: string | null
  sourceDraftId: string | null
}
export interface ActionForm {
  clientActionId: string
  rowVersion: string
}
export type TicketAction =
  | { name: 'accept' | 'confirm'; form: ActionForm }
  | {
      name: 'request-information' | 'supplements' | 'unresolved' | 'cancel'
      form: ActionForm & { content: string }
    }
  | {
      name: 'assign' | 'reassign'
      form: ActionForm & { assigneeId: string; reason: string }
    }
  | {
      name: 'priority'
      form: ActionForm & { priority: 'NORMAL' | 'HIGH'; reason: string }
    }
  | {
      name: 'comments'
      form: ActionForm & {
        content: string
        visibility: 'PUBLIC' | 'INTERNAL'
        sourceDraftId: string | null
      }
    }
  | {
      name: 'resolve'
      form: ActionForm & {
        content: string
        resolutionType: 'RESTORED' | 'GUIDANCE' | 'REFERRED'
        referralTarget: string | null
        sourceDraftId: string | null
      }
    }
  | {
      name: 'context'
      form: ActionForm & {
        serviceItemId: string | null
        supportContext: SupportContextForm
        serviceAreaCode: string | null
        serviceLocation: string | null
        reason: string
      }
    }
