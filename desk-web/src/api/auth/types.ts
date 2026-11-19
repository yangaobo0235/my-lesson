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
export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER_USER: '客户',
  SERVICE_AGENT: '客服',
  TECHNICIAN: '技术人员',
  KNOWLEDGE_ADMIN: '知识管理员',
  SYSTEM_ADMIN: '系统管理员',
}
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
