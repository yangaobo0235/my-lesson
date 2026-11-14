import { request, query } from '../http'
import { page } from '../validation'
import { device, entitledService, productModel } from './types'
/** 支持范围按后端当前有效资格返回，设备是可选资产。 @author yangaobo */
export const listServices = (
  customerId?: string,
  signal?: AbortSignal,
  pageNum = 1,
) =>
  request(
    `tickets/service-items${query({ customerId, pageNum, pageSize: 100 })}`,
    page(entitledService),
    { signal },
  )
export const listDevices = (
  customerId?: string,
  signal?: AbortSignal,
  pageNum = 1,
) =>
  request(
    `tickets/devices${query({ customerId, pageNum, pageSize: 100 })}`,
    page(device),
    { signal },
  )
export const listModels = (signal?: AbortSignal) =>
  request('tickets/product-models?pageSize=100', page(productModel), { signal })
