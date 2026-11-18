<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import { listTickets, getOverview } from '../../api/ticket/ticketApi'
import {
  statusNames,
  type Ticket,
  type TicketStatus,
  type Overview,
} from '../../api/ticket/types'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
const rows = ref<Ticket[]>([]),
  summary = ref<Overview | null>(null)
const status = ref<TicketStatus | ''>(''),
  triage = ref(false),
  overdue = ref(false),
  assignedToMe = ref(false),
  pageNum = ref(1),
  total = ref('0')
const { busy, error, run, signal } = useOperation()
const auth = useAuthStore()
function load() {
  void run(async () => {
    const [page, counts] = await Promise.all([
      listTickets(
        {
          pageNum: pageNum.value,
          status: status.value || undefined,
          triage: triage.value || undefined,
          overdue: overdue.value || undefined,
          assignedToMe: assignedToMe.value || undefined,
        },
        signal,
      ),
      getOverview(signal),
    ])
    rows.value = page.records
    total.value = page.total
    summary.value = counts
  })
}
function search() {
  pageNum.value = 1
  load()
}
function next(delta: number) {
  pageNum.value += delta
  load()
}
onMounted(load)
</script>
<template>
  <main>
    <div class="page-heading">
      <div>
        <p class="eyebrow">人工服务工作台</p>
        <h1>{{ auth.has('CUSTOMER_USER') ? '我的工单' : '工单队列' }}</h1>
      </div>
      <RouterLink
        v-if="auth.has('CUSTOMER_USER')"
        to="/support"
        class="button-link"
        >提交新问题</RouterLink
      >
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="summary" class="metrics">
      <article>
        <strong>{{ summary.submitted }}</strong
        ><span>待受理</span>
      </article>
      <article>
        <strong>{{ summary.processing }}</strong
        ><span>处理中</span>
      </article>
      <article>
        <strong>{{ summary.waitingCustomer }}</strong
        ><span>待补充</span>
      </article>
      <article>
        <strong>{{ summary.waitingExternal }}</strong
        ><span>等待外部反馈</span>
      </article>
      <article>
        <strong>{{ summary.resolved }}</strong
        ><span>待确认</span>
      </article>
      <article>
        <strong>{{ summary.customerConfirmed }}</strong
        ><span>客户确认结单</span>
      </article>
      <article>
        <strong>{{ summary.confirmationTimeout }}</strong
        ><span>超时结单</span>
      </article>
    </div>
    <form class="toolbar" @submit.prevent="search">
      <ElSelect
        v-model="status"
        placeholder="全部状态"
        clearable
        aria-label="工单状态"
        ><ElOption
          v-for="(label, value) in statusNames"
          :key="value"
          :label="label"
          :value="value" /></ElSelect
      ><ElCheckbox v-model="triage">待分类</ElCheckbox
      ><ElCheckbox v-model="overdue">受理超期</ElCheckbox
      ><ElCheckbox v-if="auth.has('TECHNICIAN')" v-model="assignedToMe"
        >分派给我</ElCheckbox
      ><ElButton native-type="submit" :loading="busy">查询</ElButton>
    </form>
    <div class="card table-scroll">
      <table>
        <thead>
          <tr>
            <th>问题</th>
            <th>状态</th>
            <th>优先级</th>
            <th>最近更新</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <RouterLink :to="`/tickets/${row.id}`">{{ row.title }}</RouterLink
              ><small class="subtle"
                >{{ row.ticketNo }} ·
                {{ row.serviceItemId ? '已分类' : '待分类' }}</small
              >
            </td>
            <td>
              <ElTag>{{ statusNames[row.status] }}</ElTag>
            </td>
            <td>{{ row.priority === 'HIGH' ? '高' : '普通' }}</td>
            <td>
              {{
                new Date(row.updatedAt).toLocaleString('zh-CN', {
                  timeZone: 'Asia/Shanghai',
                })
              }}
            </td>
          </tr>
          <tr v-if="!rows.length && !busy">
            <td colspan="4">暂无符合条件的工单</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="toolbar">
      <span>共 {{ total }} 条 · 第 {{ pageNum }} 页</span
      ><ElButton :disabled="busy || pageNum === 1" @click="next(-1)"
        >上一页</ElButton
      ><ElButton
        :disabled="busy || BigInt(pageNum * 20) >= BigInt(total)"
        @click="next(1)"
        >下一页</ElButton
      >
    </div>
  </main>
</template>
