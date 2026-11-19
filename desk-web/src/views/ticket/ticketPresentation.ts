import type { TicketStatus } from '../../api/ticket/types'

/** 下一步按工单状态与当前工作台视角展示，不从描述推断。 @author yangaobo */
export const CUSTOMER_TICKET_NEXT: Record<TicketStatus, string> = {
  SUBMITTED: '等待服务团队受理',
  PROCESSING: '技术人员正在处理',
  WAITING_CUSTOMER: '补充问题信息',
  WAITING_EXTERNAL: '技术人员跟进外部反馈',
  RESOLVED: '查看处理结果并确认',
  CLOSED: '查看处理记录',
  CANCELLED: '查看取消记录',
}

export const STAFF_TICKET_NEXT: Record<TicketStatus, string> = {
  ...CUSTOMER_TICKET_NEXT,
  SUBMITTED: '核对类别与受理',
  WAITING_CUSTOMER: '等待客户补充信息',
  RESOLVED: '等待客户确认',
}

export const TICKET_TONES = {
  SUBMITTED: 'warning',
  PROCESSING: 'primary',
  WAITING_CUSTOMER: 'warning',
  WAITING_EXTERNAL: 'info',
  RESOLVED: 'warning',
  CLOSED: 'success',
  CANCELLED: 'info',
} as const satisfies Record<TicketStatus, string>

const ticketDateFormat = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Asia/Shanghai',
})

export function formatTicketDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '时间未知'
    : ticketDateFormat.format(date)
}
