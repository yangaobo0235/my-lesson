import {
  bool,
  choices,
  decimal,
  identifier,
  nullable,
  record,
  text,
} from '../validation'

export const category = choices(
  'TERMINAL',
  'PRINTING',
  'NETWORK',
  'OFFICE_SOFTWARE',
)
export type Category = ReturnType<typeof category>
export const categoryNames: Record<Category, string> = {
  TERMINAL: '办公终端',
  PRINTING: '打印服务',
  NETWORK: '网络连接',
  OFFICE_SOFTWARE: '办公软件',
}
export const connectionType = choices(
  'USB',
  'LAN',
  'WIFI',
  'VPN',
  'OTHER',
  'UNKNOWN',
)
export const affectedScope = choices('SINGLE_USER', 'MULTIPLE_USERS', 'UNKNOWN')
/** 客户主动填写的环境，不把未知条件推断为已知。 @author yangaobo */
export interface SupportContextForm {
  osName: string | null
  osVersion: string | null
  softwareName: string | null
  softwareVersion: string | null
  errorCode: string | null
  connectionType: ReturnType<typeof connectionType>
  affectedScope: ReturnType<typeof affectedScope>
}
export const blankContext = (): SupportContextForm => ({
  osName: null,
  osVersion: null,
  softwareName: null,
  softwareVersion: null,
  errorCode: null,
  connectionType: 'UNKNOWN',
  affectedScope: 'UNKNOWN',
})
export function serviceItem(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    serviceCode: text(o.serviceCode),
    category: category(o.category),
    serviceName: text(o.serviceName),
    supportScope: text(o.supportScope),
    onsiteSupported: bool(o.onsiteSupported),
    status: choices('ACTIVE', 'DISABLED')(o.status),
    rowVersion: decimal(o.rowVersion),
  }
}
export type ServiceItem = ReturnType<typeof serviceItem>
export function entitledService(v: unknown) {
  const o = record(v)
  return {
    item: serviceItem(o.item),
    startsOn: text(o.startsOn),
    endsOn: text(o.endsOn),
  }
}
export type EntitledService = ReturnType<typeof entitledService>
export function entitlement(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    customerId: identifier(o.customerId),
    serviceItemId: identifier(o.serviceItemId),
    startsOn: text(o.startsOn),
    endsOn: text(o.endsOn),
    status: choices('ACTIVE', 'SUSPENDED')(o.status),
    rowVersion: decimal(o.rowVersion),
    changeReason: text(o.changeReason),
  }
}
export type Entitlement = ReturnType<typeof entitlement>
export function productModel(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    category: text(o.category),
    brand: text(o.brand),
    modelName: text(o.modelName),
    supportedSystems: nullable(text)(o.supportedSystems),
    status: choices('ACTIVE', 'DISABLED')(o.status),
    rowVersion: decimal(o.rowVersion),
  }
}
export type ProductModel = ReturnType<typeof productModel>
export function device(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    customerId: identifier(o.customerId),
    productModelId: identifier(o.productModelId),
    assetCode: text(o.assetCode),
    serialNumber: nullable(text)(o.serialNumber),
    location: nullable(text)(o.location),
    serviceAreaCode: nullable(text)(o.serviceAreaCode),
    status: choices('ACTIVE', 'RETIRED')(o.status),
    rowVersion: decimal(o.rowVersion),
  }
}
export type Device = ReturnType<typeof device>
