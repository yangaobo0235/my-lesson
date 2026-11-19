import { query, request } from '../http'
import { identifier, page } from '../validation'
import { document } from './knowledgeApi'

export interface ReadContext {
  serviceItemId: string
  deviceId?: string
  osName?: string
  osVersion?: string
  softwareName?: string
  softwareVersion?: string
}

function contextQuery(context: ReadContext) {
  return {
    serviceItemId: identifier(context.serviceItemId),
    deviceId: context.deviceId ? identifier(context.deviceId) : undefined,
    'environment.osName': context.osName,
    'environment.osVersion': context.osVersion,
    'environment.softwareName': context.softwareName,
    'environment.softwareVersion': context.softwareVersion,
  }
}

/** 只透传所选服务与设备。资料受众和读取权限由知识服务逐次核验。 @author yangaobo */
export const listReadableDocuments = (
  context: ReadContext,
  pageNum: number,
  signal?: AbortSignal,
) =>
  request(
    `knowledge/documents${query({ ...contextQuery(context), pageNum })}`,
    page(document),
    { signal },
  )

/** 固定同源原件地址，与列表使用同一套适用条件。 @author yangaobo */
export const readableOriginalLink = (id: string, context: ReadContext) =>
  `/api/v1/knowledge/documents/${identifier(id)}/file${query(contextQuery(context))}`
