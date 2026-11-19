<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { LocationQueryRaw } from 'vue-router'
import { ElTag } from 'element-plus'
import { statusNames, type Ticket } from '../../api/ticket/types'
import {
  CUSTOMER_TICKET_NEXT,
  STAFF_TICKET_NEXT,
  TICKET_TONES,
  formatTicketDate,
} from './ticketPresentation'

defineProps<{
  rows: Ticket[]
  customer: boolean
  detailQuery?: LocationQueryRaw
  compact?: boolean
}>()
</script>

<template>
  <div class="table-scroll queue-table" :class="{ 'compact-queue': compact }">
    <table>
      <thead>
        <tr>
          <th scope="col">问题</th>
          <th scope="col">状态</th>
          <th v-if="!compact" scope="col">下一步</th>
          <th v-if="!compact" scope="col">最近更新</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>
            <RouterLink
              :to="{
                name: 'ticket-detail',
                params: { id: row.id },
                query: detailQuery,
              }"
              >{{ row.title }}</RouterLink
            >
            <small class="subtle" :title="row.ticketNo"
              >{{ row.ticketNo }} · {{ row.serviceItemId ? '已分类' : '待分类'
              }}<template v-if="row.priority === 'HIGH'">
                · 高优先级</template
              ></small
            >
          </td>
          <td>
            <ElTag :type="TICKET_TONES[row.status]" effect="light">{{
              statusNames[row.status]
            }}</ElTag>
          </td>
          <td v-if="!compact">
            {{
              (customer ? CUSTOMER_TICKET_NEXT : STAFF_TICKET_NEXT)[row.status]
            }}
          </td>
          <td v-if="!compact">
            <time :datetime="row.updatedAt">{{
              formatTicketDate(row.updatedAt)
            }}</time>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.queue-table td:first-child {
  min-width: 12rem;
}
.compact-queue table {
  table-layout: fixed;
}
.compact-queue :is(th, td):nth-child(2) {
  width: 6.5rem;
}
.compact-queue td:first-child {
  min-width: 0;
}
.compact-queue .subtle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-table td:nth-child(2),
time {
  white-space: nowrap;
}
.queue-table td:nth-child(3) {
  min-width: 9rem;
}
.queue-table a {
  font-weight: 650;
}
.queue-table .subtle {
  margin-top: 3px;
}
</style>
