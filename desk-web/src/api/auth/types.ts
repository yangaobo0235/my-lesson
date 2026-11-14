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

export const role = choices(
  'SYSTEM_ADMIN',
  'SERVICE_AGENT',
  'TECHNICIAN',
  'KNOWLEDGE_ADMIN',
  'CUSTOMER_USER',
)
export type Role = ReturnType<typeof role>
export const organizationType = choices('SERVICE_PROVIDER', 'CUSTOMER')
/** 当前身份必须从后端解码，菜单使用不能代替资源授权。 @author yangaobo */
export function identity(value: unknown) {
  const o = record(value)
  return {
    ownerId: identifier(o.ownerId),
    organizationId: identifier(o.organizationId),
    organizationType: organizationType(o.organizationType),
    displayName: text(o.displayName),
    roles: array(role)(o.roles),
    customerIds: array(identifier)(o.customerIds),
    authVersion: decimal(o.authVersion),
    sessionRef: nullable(text)(o.sessionRef),
    mustChangePassword: bool(o.mustChangePassword),
  }
}
export type Identity = ReturnType<typeof identity>
