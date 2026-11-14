import { request } from '../http'
import { identifier, page, record, text } from '../validation'
import {
  entitlement,
  serviceItem,
  productModel,
  device,
  type Category,
} from '../support/types'

export interface ServiceCreateForm {
  clientActionId: string
  serviceCode: string
  category: Category
  serviceName: string
  supportScope: string
  onsiteSupported: boolean
  reason: string
}
export interface ServiceUpdateForm {
  id: string
  rowVersion: string
  clientActionId: string
  serviceName: string
  supportScope: string
  onsiteSupported: boolean
  reason: string
}
export interface StatusForm {
  id: string
  rowVersion: string
  clientActionId: string
  reason: string
}
export interface GrantForm {
  clientActionId: string
  customerId: string
  serviceItemId: string
  startsOn: string
  endsOn: string
  reason: string
}
export interface EntitlementUpdateForm {
  id: string
  rowVersion: string
  clientActionId: string
  startsOn: string
  endsOn: string
  reason: string
}
export interface ModelForm {
  category: 'COMPUTER' | 'PRINTER'
  brand: string
  modelName: string
  supportedSystems: string | null
}
export interface DeviceForm {
  customerId: string
  productModelId: string
  assetCode: string
  serialNumber: string | null
  location: string | null
  serviceAreaCode: string | null
}
function serviceEvent(v: unknown) {
  const o = record(v)
  return {
    id: identifier(o.id),
    eventType: text(o.eventType),
    actorId: identifier(o.actorId),
    createdAt: text(o.createdAt),
    details: JSON.stringify(record(o.details), null, 2),
  }
}
export type ServiceEvent = ReturnType<typeof serviceEvent>
/** 目录与资格分别授权，变更沿原动作键与版本调用。 @author yangaobo */
export const listItems = (
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/service-item/list', page(serviceItem), {
    body: { pageNum, pageSize: 100 },
    csrf,
    signal,
  })
export const createItem = (body: ServiceCreateForm, csrf: string) =>
  request('admin/service-item/add', serviceItem, { body, csrf })
export const updateItem = (body: ServiceUpdateForm, csrf: string) =>
  request('admin/service-item/update', serviceItem, { body, csrf })
export const disableItem = (body: StatusForm, csrf: string) =>
  request('admin/service-item/disable', serviceItem, { body, csrf })
export const listEntitlements = (
  customerId: string | null,
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/service-entitlement/list', page(entitlement), {
    body: { customerId, pageNum },
    csrf,
    signal,
  })
export const grant = (body: GrantForm, csrf: string) =>
  request('admin/service-entitlement/grant', entitlement, { body, csrf })
export const updateEntitlement = (body: EntitlementUpdateForm, csrf: string) =>
  request('admin/service-entitlement/update', entitlement, { body, csrf })
export const suspend = (body: StatusForm, suspended: boolean, csrf: string) =>
  request(
    `admin/service-entitlement/${suspended ? 'suspend' : 'resume'}`,
    entitlement,
    { body, csrf },
  )
export const listServiceEvents = (
  resource: 'service-item' | 'service-entitlement',
  id: string,
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request(`admin/${resource}/events`, page(serviceEvent), {
    body: { id, pageNum },
    csrf,
    signal,
  })
export const listModels = (
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/product-model/list', page(productModel), {
    body: { pageNum, pageSize: 100 },
    csrf,
    signal,
  })
export const createModel = (body: ModelForm, csrf: string) =>
  request('admin/product-model/add', productModel, { body, csrf })
export const updateModel = (
  body: Omit<ModelForm, 'category'> & { id: string; rowVersion: string },
  csrf: string,
) => request('admin/product-model/update', productModel, { body, csrf })
export const disableModel = (id: string, rowVersion: string, csrf: string) =>
  request('admin/product-model/disable', productModel, {
    body: { id, rowVersion },
    csrf,
  })
export const listDevices = (
  customerId: string | null,
  pageNum: number,
  csrf: string,
  signal?: AbortSignal,
) =>
  request('admin/device/list', page(device), {
    body: { customerId, pageNum },
    csrf,
    signal,
  })
export const createDevice = (body: DeviceForm, csrf: string) =>
  request('admin/device/add', device, { body, csrf })
export const updateDevice = (
  body: Omit<DeviceForm, 'customerId' | 'productModelId'> & {
    id: string
    rowVersion: string
  },
  csrf: string,
) => request('admin/device/update', device, { body, csrf })
export const retireDevice = (id: string, rowVersion: string, csrf: string) =>
  request('admin/device/retire', device, { body: { id, rowVersion }, csrf })
