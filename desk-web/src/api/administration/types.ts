import {
  array,
  bool,
  choices,
  decimal,
  identifier,
  nullable,
  record,
  text,
} from '../validation'
import { organizationType, role, type Role } from '../auth/types'

/** 管理响应投影不包含口令、哈希或会话凭据。 @author yangaobo */
export function organization(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    organizationCode: text(o.organizationCode),
    organizationName: text(o.organizationName),
    organizationType: organizationType(o.organizationType),
    status: choices('ACTIVE', 'DISABLED')(o.status),
    rowVersion: decimal(o.rowVersion),
  }
}
export type Organization = ReturnType<typeof organization>
export function account(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    organizationId: identifier(o.organizationId),
    username: text(o.username),
    displayName: text(o.displayName),
    status: choices('ACTIVE', 'DISABLED')(o.status),
    mustChangePassword: bool(o.mustChangePassword),
    rowVersion: decimal(o.rowVersion),
    roles: array(role)(o.roles),
    customerIds: array(identifier)(o.customerIds),
  }
}
export type Account = ReturnType<typeof account>
export function audit(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    eventType: text(o.eventType),
    actorUserId: nullable(identifier)(o.actorUserId),
    targetUserId: nullable(identifier)(o.targetUserId),
    targetOrganizationId: nullable(identifier)(o.targetOrganizationId),
    details: JSON.stringify(record(o.details), null, 2),
    createdAt: text(o.createdAt),
  }
}
export type Audit = ReturnType<typeof audit>
export interface OrganizationCreateForm {
  organizationCode: string
  organizationName: string
}
export interface OrganizationUpdateForm {
  id: string
  rowVersion: string
  organizationName: string
  status: 'ACTIVE' | 'DISABLED'
  reason: string
}
export interface AccountCreateForm {
  organizationId: string
  username: string
  displayName: string
  initialPassword: string
  roles: Role[]
}
export interface AccountUpdateForm {
  id: string
  rowVersion: string
  organizationId: string
  displayName: string
  reason: string
}
export interface AccountMutationForm {
  id: string
  rowVersion: string
  reason: string
}
