<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElInput, ElSkeleton } from 'element-plus'
import { getOverview, getTicket, listTickets } from '../api/ticket/ticketApi'
import { statusNames } from '../api/ticket/types'
import type { Ticket, Overview } from '../api/ticket/types'
import { useOperation } from '../composables/useOperation'
import { hasWorkspaceAccess, TICKET_ROLES } from '../router/workspaceAccess'
import { useAuthStore } from '../stores/authStore'
import { useIntakeStore } from '../stores/intakeStore'
import TicketQueueTable from './ticket/TicketQueueTable.vue'
import { acceptanceTicketKey } from '../composables/useAcceptanceWorkspace'
import { STAFF_TICKET_NEXT } from './ticket/ticketPresentation'

const auth = useAuthStore()
const intake = useIntakeStore()
const router = useRouter()
const customer = computed(() => auth.has('CUSTOMER_USER'))
const canReadTickets = computed(() =>
  hasWorkspaceAccess(auth.user, TICKET_ROLES),
)
const description = ref('')
const overview = ref<Overview | null>(null)
const priority = ref<Ticket[]>([])
const following = ref<Ticket[]>([])
const { busy, error, run, signal } = useOperation()
const acceptanceTicketId = inject(acceptanceTicketKey)
const acceptanceTicket = ref<Ticket | null>(null)
const acceptanceError = ref(false)
watch(
  () => acceptanceTicketId?.value,
  async (id, _previous, onCleanup) => {
    acceptanceTicket.value = null
    acceptanceError.value = false
    if (!id) return
    const request = new AbortController()
    onCleanup(() => request.abort())
    try {
      const result = await getTicket(id, request.signal)
      if (!request.signal.aborted) acceptanceTicket.value = result.ticket
    } catch {
      if (!request.signal.aborted) acceptanceError.value = true
    }
  },
  { immediate: true },
)
const states = [
  { label: '待受理', key: 'submitted', query: { status: 'SUBMITTED' } },
  { label: '受理超期', key: 'overdue', query: { overdue: '1' } },
  { label: '处理中', key: 'processing', query: { status: 'PROCESSING' } },
  { label: '待确认', key: 'resolved', query: { status: 'RESOLVED' } },
] as const
const shortcuts = [
  { label: '网络连不上', value: '公司 Wi-Fi 连不上。' },
  { label: '打印不了', value: '共享打印机无法打印。' },
  { label: '电脑故障', value: '电脑开机后卡在启动画面。' },
  { label: '软件用不了', value: '办公软件无法登录。' },
]

async function load() {
  if (!canReadTickets.value) return
  await run(async () => {
    if (customer.value) {
      const [waiting, resolved, processing] = await Promise.all([
        listTickets(
          { pageNum: 1, pageSize: 3, status: 'WAITING_CUSTOMER' },
          signal,
        ),
        listTickets({ pageNum: 1, pageSize: 3, status: 'RESOLVED' }, signal),
        listTickets({ pageNum: 1, pageSize: 3, status: 'PROCESSING' }, signal),
      ])
      priority.value = [...waiting.records, ...resolved.records]
      following.value = processing.records
    } else {
      const [summary, pending] = await Promise.all([
        getOverview(signal),
        listTickets({ pageNum: 1, pageSize: 5, status: 'SUBMITTED' }, signal),
      ])
      overview.value = summary
      priority.value = pending.records
    }
  })
}

async function openIntake(destination: 'report' | 'diagnosis') {
  const ownerId = auth.user?.ownerId
  if (!ownerId) return
  intake.prepare(ownerId, destination, description.value)
  try {
    await router.push({ name: destination })
    await nextTick()
  } finally {
    intake.reset()
  }
}

onMounted(load)
</script>

<template>
  <main class="home">
    <section
      class="help-hero"
      :class="{ 'customer-hero': customer }"
      aria-labelledby="home-title"
    >
      <div class="hero-copy">
        <h1 id="home-title">
          {{ customer ? '今天遇到了什么 IT 问题？' : '服务工作台' }}
        </h1>
        <p v-if="customer">
          电脑、打印机、办公网络和软件问题，都可以在这里求助。
        </p>
        <p v-else-if="canReadTickets">
          查看待受理工单、当前进展和需要客户反馈的问题。
        </p>
        <p v-else>从侧栏进入已授权的业务页面。</p>
      </div>
      <div v-if="customer" class="help-composer">
        <ElInput
          v-model="description"
          type="textarea"
          :rows="3"
          :maxlength="500"
          aria-label="描述遇到的 IT 问题"
          placeholder="例如：电脑连上公司 Wi-Fi，但打不开网页。"
        />
        <div class="intake-actions">
          <ElButton text @click="openIntake('report')">直接报修</ElButton>
          <ElButton type="primary" @click="openIntake('diagnosis')"
            >开始排查</ElButton
          >
        </div>
      </div>
      <div v-if="customer" class="help-shortcuts" aria-label="常见问题">
        <button
          v-for="item in shortcuts"
          :key="item.label"
          type="button"
          :disabled="
            !!description &&
            !shortcuts.some(({ value }) => value === description)
          "
          @click="description = item.value"
        >
          {{ item.label }}
        </button>
      </div>
    </section>

    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <ElButton text size="small" @click="load">重新加载</ElButton>
    </ElAlert>
    <ElSkeleton
      v-if="busy && !overview && !priority.length"
      class="overview-loading"
      :rows="3"
      animated
    />
    <template v-if="canReadTickets && !busy && !error">
      <div v-if="overview" class="metrics overview-metrics">
        <RouterLink
          v-for="state in states"
          :key="state.key"
          :to="{ name: 'tickets', query: state.query }"
        >
          <span>{{ state.label }}</span>
          <strong>{{ overview[state.key] }}</strong>
        </RouterLink>
      </div>
      <div
        class="home-columns"
        :class="{ 'with-acceptance': acceptanceTicketId }"
      >
        <section class="home-queue" aria-labelledby="priority-heading">
          <div class="queue-heading">
            <h2 id="priority-heading">
              {{ customer ? '需要你处理' : '待受理工单' }}
            </h2>
            <RouterLink :to="{ name: 'tickets' }">{{
              customer ? '查看我的工单' : '全部工单'
            }}</RouterLink>
          </div>
          <TicketQueueTable
            v-if="priority.length"
            :rows="priority"
            :customer="customer"
            compact
          />
          <p v-else class="queue-empty">
            {{
              customer
                ? '当前没有需要补充或确认的工单。'
                : '当前没有待受理工单。'
            }}
          </p>
        </section>
        <section
          v-if="acceptanceTicketId"
          class="home-queue acceptance-card"
          aria-labelledby="acceptance-heading"
        >
          <div class="queue-heading">
            <h2 id="acceptance-heading">验收工单</h2>
            <span class="acceptance-label">本地验收企业</span>
          </div>
          <div v-if="acceptanceTicket" class="acceptance-body">
            <strong>{{ acceptanceTicket.title }}</strong>
            <span class="subtle"
              >{{ acceptanceTicket.ticketNo }} ·
              {{ statusNames[acceptanceTicket.status] }}</span
            >
            <p>{{ STAFF_TICKET_NEXT[acceptanceTicket.status] }}</p>
            <RouterLink
              class="button-link"
              :to="{
                name: 'ticket-detail',
                params: { id: acceptanceTicket.id },
              }"
              >打开工单</RouterLink
            >
          </div>
          <p v-else class="queue-empty">
            {{
              acceptanceError ? '暂时无法读取验收工单。' : '正在读取验收工单。'
            }}
          </p>
        </section>
      </div>
      <section
        v-if="customer && following.length"
        class="home-queue"
        aria-labelledby="following-heading"
      >
        <div class="queue-heading">
          <h2 id="following-heading">正在跟进</h2>
        </div>
        <TicketQueueTable :rows="following" customer compact />
      </section>
    </template>
  </main>
</template>

<style scoped>
.home {
  max-width: 1180px;
}
.help-hero {
  padding: clamp(19px, 4vw, 43px);
  background:
    radial-gradient(ellipse at 99% 12%, #dbe4ff, transparent 62%),
    linear-gradient(118deg, #fafbff, #eeefff 53%, #eae1fa);
  border: 1px solid #d9def4;
  border-radius: 20px;
}
.hero-copy {
  max-width: 780px;
}
.customer-hero {
  text-align: center;
  padding-block: clamp(28px, 5vw, 68px);
}
.customer-hero .hero-copy,
.customer-hero .help-composer {
  max-width: 670px;
  margin-inline: auto;
}
.hero-copy h1 {
  margin: 0;
  font-size: clamp(26px, 4.4vw, 46px);
  font-weight: 760;
  letter-spacing: -0.03em;
}
.hero-copy p {
  margin: 9px 0 0;
  color: var(--desk-muted);
}
.help-composer {
  max-width: 790px;
  margin-top: 23px;
  padding: 9px 13px 13px;
  border: 1px solid #d9dff5;
  border-radius: 16px;
  background: var(--desk-paper);
  box-shadow: var(--desk-shadow);
}
.help-composer :deep(textarea) {
  box-shadow: none;
  resize: vertical;
}
.help-composer:focus-within {
  border-color: var(--desk-accent);
}
.intake-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}
.help-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 13px;
  justify-content: center;
}
.help-shortcuts button {
  padding: 5px 11px;
  color: var(--desk-muted);
  background: #fafaff;
  border: 1px solid var(--desk-border);
  border-radius: 18px;
}
.help-shortcuts button:hover:not(:disabled) {
  background: var(--desk-accent-soft);
  color: var(--desk-accent-text);
}
.help-shortcuts button:disabled {
  opacity: 0.55;
}
.overview-metrics {
  margin-top: 15px;
}
.overview-metrics a {
  display: grid;
  gap: 5px;
  padding: 13px 15px;
  background: var(--desk-paper);
  border: 1px solid var(--desk-border);
  border-radius: 10px;
  text-decoration: none;
}
.overview-metrics a:hover {
  border-color: var(--desk-accent);
}
.overview-metrics strong {
  margin: 0;
  color: var(--desk-text);
}
.home-queue {
  margin-top: 20px;
  border: 1px solid var(--desk-border);
  border-radius: 12px;
  background: var(--desk-paper);
  overflow: hidden;
}
.home-columns {
  display: grid;
  gap: 16px;
  align-items: start;
}
.home-columns.with-acceptance {
  grid-template-columns: minmax(0, 1.7fr) minmax(240px, 1fr);
}
.acceptance-body {
  display: grid;
  gap: 7px;
  padding: 0 15px 15px;
}
.acceptance-body p {
  margin: 0;
}
.acceptance-body a {
  justify-self: start;
}
.acceptance-label {
  color: var(--desk-accent-text);
  background: var(--desk-accent-soft);
  padding: 3px 7px;
  border-radius: 8px;
  font-size: 12px;
}
.queue-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 15px;
}
.queue-heading h2 {
  margin: 0;
  font-size: 17px;
}
.queue-heading a {
  font-size: 13px;
}
.queue-empty {
  padding: 0 15px 13px;
  margin: 0;
  color: var(--desk-muted);
}
.overview-loading {
  margin-top: 22px;
}
@media (max-width: 980px) {
  .home-columns.with-acceptance {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
