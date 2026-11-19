import type { LocationQuery } from 'vue-router'
import { statusNames, type TicketStatus } from '../../api/ticket/types'

/** 恢复有效的工单筛选，地址只承载公开的查询条件。 @author yangaobo */
export function readTicketQueue(
  query: LocationQuery,
  canViewAssigned: boolean,
) {
  const status = Object.keys(statusNames).find(
    (key) => key === query.status,
  ) as TicketStatus | undefined
  const page = typeof query.page === 'string' ? Number(query.page) : 1
  return {
    page:
      Number.isSafeInteger(page) && page > 0 && page <= 2147483647 ? page : 1,
    filters: {
      status: status ?? ('' as const),
      triage: query.triage === '1',
      overdue: query.overdue === '1',
      assignedToMe: canViewAssigned && query.assignedToMe === '1',
    },
  }
}

/** 工单详情保留已成功查询的队列页码及筛选。 @author yangaobo */
export function ticketQueueQuery(
  filters: ReturnType<typeof readTicketQueue>['filters'],
  page: number,
) {
  return {
    status: filters.status || undefined,
    triage: filters.triage ? '1' : undefined,
    overdue: filters.overdue ? '1' : undefined,
    assignedToMe: filters.assignedToMe ? '1' : undefined,
    page: page > 1 ? String(page) : undefined,
  }
}

/** 详情只返回具体的合法队列，不接受外部跳转地址。 @author yangaobo */
export function ticketQueueReturn(
  query: LocationQuery,
  canViewAssigned: boolean,
) {
  const queue = readTicketQueue(query, canViewAssigned)
  return {
    name:
      query.from === 'assigned' && canViewAssigned
        ? 'assigned-tickets'
        : 'tickets',
    query: ticketQueueQuery(queue.filters, queue.page),
  }
}
