<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import {
  bookingHistory,
  ticketSlots,
  bookOnsite,
  bookingReceipt,
  queryBooking,
  changeBooking,
  unifiedReceipt,
  type BookingAction,
  setOnsiteEligibility,
  type OnsiteBooking,
  type OnsiteSlot,
} from '../../api/onsite/onsiteApi'
import { newRequestId } from '../../api/http'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import type { Ticket } from '../../api/ticket/types'

/** 工单内现场分支；原请求未知时保留同一键供核对。 @author yangaobo */
const props = defineProps<{ ticket: Ticket }>()
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const { run, busy, error, signal } = useOperation()
const slots = ref<OnsiteSlot[]>([]),
  bookings = ref<OnsiteBooking[]>([])
const slotPage = ref(1),
  historyPage = ref(1),
  slotTotal = ref('0'),
  historyTotal = ref('0')
const selected = ref(''),
  reason = ref('')
const pending = ref<{
  slotId: string
  clientRequestId: string
  rowVersion: string
} | null>(null)
const actionReason = ref(''),
  performedBy = ref(''),
  technicianId = ref(''),
  notice = ref('')
const pendingAction = ref<{ bookingId: string; action: BookingAction } | null>(
  null,
)
const pendingEligibility = ref<{
  rowVersion: string
  clientActionId: string
  onsiteEligible: boolean
  reason: string
} | null>(null)
watch(
  () => props.ticket.rowVersion,
  () => {
    void run(load)
  },
)
function verifyBooking(value: OnsiteBooking) {
  void run(async () => {
    const latest = await queryBooking(value.id, await auth.token(), signal)
    bookings.value = bookings.value.map((row) =>
      row.id === latest.id ? latest : row,
    )
  })
}
function nextPage(kind: 'slot' | 'history', delta: number) {
  if (kind === 'slot') slotPage.value += delta
  else historyPage.value += delta
  void run(load)
}
async function load() {
  const history = await bookingHistory(
    props.ticket.id,
    historyPage.value,
    signal,
  )
  bookings.value = history.records
  historyTotal.value = history.total
  if (
    props.ticket.onsiteEligible &&
    ['PROCESSING', 'WAITING_CUSTOMER'].includes(props.ticket.status)
  ) {
    const available = await ticketSlots(props.ticket.id, slotPage.value, signal)
    slots.value = available.records
    slotTotal.value = available.total
  } else slots.value = []
}
function reserve() {
  void run(async () => {
    if (!pending.value)
      pending.value = {
        slotId: selected.value,
        rowVersion: props.ticket.rowVersion,
        clientRequestId: newRequestId(),
      }
    await bookOnsite(props.ticket.id, pending.value, await auth.token())
    pending.value = null
    await load()
    emit('changed')
  })
}
function check() {
  void run(async () => {
    if (!pending.value) return
    await bookingReceipt(props.ticket.id, pending.value.clientRequestId, signal)
    pending.value = null
    await load()
    emit('changed')
  })
}
function end(value: OnsiteBooking, result?: 'VISITED' | 'NOT_PERFORMED') {
  if (pendingAction.value) return
  const base = { clientActionId: newRequestId(), rowVersion: value.rowVersion }
  pendingAction.value = {
    bookingId: value.id,
    action: result
      ? {
          name: 'finish',
          body: {
            ...base,
            finishResult: result,
            performedBy: performedBy.value,
            finishNote: actionReason.value,
          },
        }
      : { name: 'cancel', body: { ...base, reason: actionReason.value } },
  }
  submitAction()
}
function assign(value: OnsiteBooking) {
  if (pendingAction.value) return
  pendingAction.value = {
    bookingId: value.id,
    action: {
      name: 'assign',
      body: {
        clientActionId: newRequestId(),
        rowVersion: value.rowVersion,
        technicianId: technicianId.value,
        reason: actionReason.value,
      },
    },
  }
  submitAction()
}
function submitAction() {
  void run(async () => {
    if (!pendingAction.value) return
    const receipt = await changeBooking(
      props.ticket.id,
      pendingAction.value.bookingId,
      pendingAction.value.action,
      await auth.token(),
    )
    notice.value = `原动作已提交：${receipt.actionType}，预约版本 ${receipt.bookingRowVersion}。下方展示当前预约状态。`
    pendingAction.value = null
    await load()
    emit('changed')
  })
}
function recoverAction() {
  void run(async () => {
    if (!pendingAction.value) return
    await unifiedReceipt(pendingAction.value.action.body.clientActionId, signal)
    pendingAction.value = null
    await load()
    emit('changed')
  })
}
function eligibility() {
  void run(async () => {
    pendingEligibility.value ??= {
      rowVersion: props.ticket.rowVersion,
      clientActionId: newRequestId(),
      onsiteEligible: !props.ticket.onsiteEligible,
      reason: reason.value,
    }
    const input = pendingEligibility.value
    await setOnsiteEligibility(
      props.ticket.id,
      input.rowVersion,
      input.clientActionId,
      input.onsiteEligible,
      input.reason,
      await auth.token(),
    )
    pendingEligibility.value = null
    emit('changed')
  })
}
function recoverEligibility() {
  void run(async () => {
    if (!pendingEligibility.value) return
    await unifiedReceipt(pendingEligibility.value.clientActionId, signal)
    pendingEligibility.value = null
    emit('changed')
  })
}
onMounted(() => {
  void run(load)
})
</script>

<template>
  <section class="panel">
    <h2>现场服务</h2>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <p>
      现场资格：{{
        ticket.onsiteEligible ? '已人工核对' : '尚未开放'
      }}。预约固定工单当时地址，窗口结束后由处理人登记实际履约。
    </p>
    <div
      v-if="
        auth.has('SERVICE_AGENT') ||
        (auth.has('TECHNICIAN') && ticket.assigneeId === auth.user?.ownerId)
      "
    >
      <ElInput
        v-model="reason"
        :disabled="!!pendingEligibility"
        aria-label="现场资格核对原因"
        placeholder="填写区域、地点和现场能力核对结果"
        maxlength="500"
      />
      <ElButton
        v-if="pendingEligibility"
        :disabled="busy"
        @click="recoverEligibility"
        >查询原资格动作</ElButton
      >
      <ElButton :disabled="!reason || busy" @click="eligibility">{{
        ticket.onsiteEligible ? '撤回现场资格' : '开放现场资格'
      }}</ElButton>
    </div>
    <div v-if="auth.has('CUSTOMER_USER') && ticket.onsiteEligible">
      <ElSelect
        v-model="selected"
        aria-label="选择区域时段"
        :disabled="!!pending"
      >
        <ElOption
          v-for="s in slots"
          :key="s.id"
          :value="s.id"
          :label="`${new Date(s.startsAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })} · 剩余 ${s.capacity - s.reservedCount}`"
        />
      </ElSelect>
      <ElButton :disabled="!selected || busy" @click="reserve">{{
        pending ? '沿原键重试' : '申请预约'
      }}</ElButton>
      <ElButton v-if="pending" :disabled="busy" @click="check"
        >核对原申请</ElButton
      >
      <ElButton :disabled="slotPage === 1 || busy" @click="nextPage('slot', -1)"
        >上一页时段</ElButton
      >
      <ElButton
        :disabled="BigInt(slotPage * 10) >= BigInt(slotTotal) || busy"
        @click="nextPage('slot', 1)"
        >下一页时段</ElButton
      >
    </div>
    <ElInput
      v-model="actionReason"
      :disabled="!!pendingAction"
      aria-label="预约动作说明"
      placeholder="填写取消、更换人员或实际履约的具体原因"
      maxlength="500"
    />
    <template v-if="auth.has('SERVICE_AGENT') || auth.has('TECHNICIAN')">
      <ElInput
        v-model="technicianId"
        :disabled="!!pendingAction"
        aria-label="现场负责人账号标识"
        placeholder="新的现场负责人账号标识"
      />
      <ElInput
        v-model="performedBy"
        :disabled="!!pendingAction"
        aria-label="实际执行人账号标识"
        placeholder="实际执行人账号标识（履约登记必填）"
      />
    </template>
    <template v-if="pendingAction">
      <ElButton :disabled="busy" @click="submitAction">沿原动作键重试</ElButton>
      <ElButton :disabled="busy" @click="recoverAction"
        >查询原动作回执</ElButton
      >
    </template>
    <ul>
      <li v-for="b in bookings" :key="b.id">
        {{ b.status }} {{ b.resultCode }} {{ b.finishResult }} ·
        {{ b.visitAddress }} · 预约版本 {{ b.rowVersion }}
        <p v-if="b.assignedTechnicianId">
          现场负责人：{{ b.assignedTechnicianId }}
        </p>
        <p v-if="b.finishNote">
          执行人：{{ b.performedBy }}；{{ b.finishNote }}
        </p>
        <p v-if="b.cancelReason">取消原因：{{ b.cancelReason }}</p>
        <ElButton
          v-if="auth.has('SERVICE_AGENT') || auth.has('TECHNICIAN')"
          :disabled="busy"
          @click="verifyBooking(b)"
          >核对原预约</ElButton
        >
        <template v-if="b.status === 'CONFIRMED'">
          <ElButton
            v-if="
              auth.has('SERVICE_AGENT') ||
              (auth.has('TECHNICIAN') &&
                ticket.assigneeId === auth.user?.ownerId)
            "
            :disabled="
              busy || !!pendingAction || !actionReason || !technicianId
            "
            @click="assign(b)"
            >调整现场负责人</ElButton
          >
          <ElButton
            :disabled="busy || !!pendingAction || !actionReason"
            @click="end(b)"
            >到访前取消</ElButton
          >
          <template
            v-if="
              auth.has('TECHNICIAN') && ticket.assigneeId === auth.user?.ownerId
            "
          >
            <ElButton
              :disabled="
                busy || !!pendingAction || !actionReason || !performedBy
              "
              @click="end(b, 'VISITED')"
              >登记已到访</ElButton
            >
            <ElButton
              :disabled="
                busy || !!pendingAction || !actionReason || !performedBy
              "
              @click="end(b, 'NOT_PERFORMED')"
              >登记未履约</ElButton
            >
          </template>
        </template>
      </li>
    </ul>
    <ElButton
      :disabled="historyPage === 1 || busy"
      @click="nextPage('history', -1)"
      >上一页预约</ElButton
    >
    <ElButton
      :disabled="BigInt(historyPage * 10) >= BigInt(historyTotal) || busy"
      @click="nextPage('history', 1)"
      >下一页预约</ElButton
    >
  </section>
</template>
