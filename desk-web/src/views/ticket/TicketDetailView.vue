<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElSkeleton,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import { getTicket, listEvents } from '../../api/ticket/ticketApi'
import { ApiError } from '../../api/http'
import {
  statusNames,
  type Ticket,
  type TicketEvent,
} from '../../api/ticket/types'
import { useOperation } from '../../composables/useOperation'
import { useAuthStore } from '../../stores/authStore'
import DiagnosisPanel from '../diagnosis/DiagnosisPanel.vue'
import AttachmentPanel from './AttachmentPanel.vue'
import OnsitePanel from './OnsitePanel.vue'
import TicketActionsPanel from './TicketActionsPanel.vue'
import { ticketQueueReturn } from './ticketQueueRoute'
import {
  CUSTOMER_TICKET_NEXT,
  STAFF_TICKET_NEXT,
  TICKET_TONES,
} from './ticketPresentation'

const auth = useAuthStore()
const route = useRoute()
const id = String(route.params.id)
const queueReturn = computed(() =>
  ticketQueueReturn(route.query, auth.has('TECHNICIAN')),
)
const ticket = ref<Ticket | null>(null)
const events = ref<TicketEvent[]>([])
const eventPage = ref(1)
const eventTotal = ref('0')
const section = ref('timeline')
const customer = computed(() => auth.has('CUSTOMER_USER'))
const { busy, error, run, signal } = useOperation()

async function load() {
  try {
    const [data, timeline] = await Promise.all([
      getTicket(id, signal),
      listEvents(id, eventPage.value, signal),
    ])
    ticket.value = data.ticket
    events.value = timeline.records
    eventTotal.value = timeline.total
  } catch (failure) {
    if (failure instanceof ApiError && [403, 404].includes(failure.status)) {
      ticket.value = null
      events.value = []
    }
    throw failure
  }
}

onMounted(() => void run(load))
function next(delta: number) {
  void run(async () => {
    const page = await listEvents(id, eventPage.value + delta, signal)
    eventPage.value = page.pageNum
    events.value = page.records
    eventTotal.value = page.total
  })
}
const eventNames: Record<string, string> = {
  CREATED: '已提交',
  ACCEPTED: '已接单',
  ASSIGNED: '已分派',
  REASSIGNED: '已转派',
  CONTEXT_UPDATED: '环境已更新',
  PRIORITY_CHANGED: '优先级已更新',
  NEED_INFO: '请求补充',
  WAIT_EXTERNAL: '等待外部反馈',
  END_WAIT: '结束等待',
  FOLLOW_UP: '跟进并续期',
  FOLLOW_UP_DUE: '跟进期限已到',
  SUPPLEMENTED: '已补充',
  COMMENTED: '处理记录',
  RESOLVED: '处理结果',
  CLOSED: '已结单',
  UNRESOLVED: '客户反馈未解决',
  CANCELLED: '已取消',
  ACCEPTANCE_OVERDUE: '受理超期',
}
</script>
<template>
  <main>
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton v-if="!ticket" text @click="run(load)">重新读取工单</ElButton>
    </ElAlert>
    <ElSkeleton v-if="busy && !ticket" :rows="5" animated />
    <template v-if="ticket">
      <RouterLink :to="queueReturn">{{
        queueReturn.name === 'assigned-tickets'
          ? '返回我的处理'
          : '返回工单列表'
      }}</RouterLink>
      <section class="card ticket-heading" aria-labelledby="ticket-title">
        <p class="subtle">{{ ticket.ticketNo }}</p>
        <h1 id="ticket-title">{{ ticket.title }}</h1>
        <div class="toolbar">
          <ElTag :type="TICKET_TONES[ticket.status]">{{
            statusNames[ticket.status]
          }}</ElTag
          ><span>{{
            ticket.serviceItemId ? '已分类' : '待分类 · 仅承诺分流'
          }}</span
          ><span>{{
            (customer ? CUSTOMER_TICKET_NEXT : STAFF_TICKET_NEXT)[ticket.status]
          }}</span>
          <ElButton :disabled="busy" text @click="run(load)">刷新</ElButton>
        </div>
        <p v-if="ticket.followUpAt">
          等待原因：{{ ticket.waitReason }} · 第 {{ ticket.waitCycle }} 轮 ·
          下次跟进：
          {{
            new Date(ticket.followUpAt).toLocaleString('zh-CN', {
              timeZone: 'Asia/Shanghai',
            })
          }}
          {{ ticket.followUpNotifiedAt ? '（已提醒处理人）' : '' }}
        </p>
        <p v-if="ticket.closureReason">
          结单原因：{{
            ticket.closureReason === 'CUSTOMER_CONFIRMED'
              ? '客户确认处理结果'
              : ticket.closureReason === 'CONFIRMATION_TIMEOUT'
                ? '72小时未反馈自动结单，不代表已修复'
                : '历史未知'
          }}
        </p>
        <p v-if="ticket.confirmationDueAt">
          反馈期限：{{
            new Date(ticket.confirmationDueAt).toLocaleString('zh-CN', {
              timeZone: 'Asia/Shanghai',
            })
          }}
        </p>
        <p class="preserve">{{ ticket.symptom }}</p>
        <TicketActionsPanel
          :ticket="ticket"
          :refreshing="busy"
          @changed="run(load)"
        />
      </section>
      <ElTabs v-model="section" class="ticket-sections">
        <ElTabPane label="处理时间线" name="timeline">
          <section class="card" aria-label="处理时间线">
            <p v-if="!events.length" class="subtle">暂无处理记录。</p>
            <ol class="timeline">
              <li v-for="event in events" :key="event.id">
                <div>
                  <strong>{{
                    eventNames[event.eventType] || event.eventType
                  }}</strong
                  ><ElTag v-if="event.visibility === 'INTERNAL'" type="warning"
                    >内部</ElTag
                  ><time>{{
                    new Date(event.createdAt).toLocaleString('zh-CN', {
                      timeZone: 'Asia/Shanghai',
                    })
                  }}</time>
                </div>
                <p class="preserve">{{ event.content }}</p>
              </li>
            </ol>
            <div class="toolbar">
              <ElButton :disabled="busy || eventPage === 1" @click="next(-1)"
                >上一页</ElButton
              ><span>第 {{ eventPage }} 页 · 共 {{ eventTotal }} 条</span
              ><ElButton
                :disabled="busy || BigInt(eventPage * 50) >= BigInt(eventTotal)"
                @click="next(1)"
                >下一页</ElButton
              >
            </div>
          </section>
        </ElTabPane>
        <ElTabPane label="问题与环境" name="environment" lazy>
          <article class="card">
            <h2>问题与环境</h2>
            <p class="preserve">{{ ticket.symptom }}</p>
            <h3>已尝试的操作</h3>
            <p class="preserve">{{ ticket.attemptedActions || '未提供' }}</p>
            <dl>
              <dt>系统</dt>
              <dd>
                {{ ticket.supportContext.osName || '未知' }}
                {{ ticket.supportContext.osVersion }}
              </dd>
              <dt>软件</dt>
              <dd>
                {{ ticket.supportContext.softwareName || '未知' }}
                {{ ticket.supportContext.softwareVersion }}
              </dd>
              <dt>错误码</dt>
              <dd>{{ ticket.supportContext.errorCode || '未知' }}</dd>
              <dt>连接</dt>
              <dd>{{ ticket.supportContext.connectionType }}</dd>
              <dt>影响范围</dt>
              <dd>{{ ticket.supportContext.affectedScope }}</dd>
            </dl>
          </article>
        </ElTabPane>
        <ElTabPane label="上门服务" name="onsite" lazy>
          <OnsitePanel :ticket="ticket" @changed="run(load)" />
        </ElTabPane>
        <ElTabPane label="诊断附件" name="attachments" lazy>
          <AttachmentPanel :ticket="ticket" @changed="run(load)" />
        </ElTabPane>
        <ElTabPane
          v-if="auth.has('TECHNICIAN')"
          name="assistant"
          label="私有助手"
          lazy
        >
          <DiagnosisPanel :ticket="ticket" @changed="run(load)" />
        </ElTabPane>
      </ElTabs>
    </template>
  </main>
</template>

<style scoped>
.ticket-heading {
  margin-bottom: 16px;
}
.ticket-heading h1 {
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.ticket-sections :deep(.el-tabs__content) {
  overflow: visible;
}
.ticket-sections .card {
  margin-top: 0;
}
</style>
