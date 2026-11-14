<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import {
  bookingHistory,
  ticketSlots,
  bookOnsite,
  bookingReceipt,
  queryBooking,
  cancelBooking,
  finishBooking,
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
const pending = ref<{ slotId: string; clientRequestId: string } | null>(null)
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
  void run(async () => {
    if (result)
      await finishBooking(props.ticket.id, value.id, result, await auth.token())
    else await cancelBooking(props.ticket.id, value.id, await auth.token())
    await load()
    emit('changed')
  })
}
function eligibility() {
  void run(async () => {
    await setOnsiteEligibility(
      props.ticket.id,
      props.ticket.rowVersion,
      newRequestId(),
      !props.ticket.onsiteEligible,
      reason.value,
      await auth.token(),
    )
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
        aria-label="现场资格核对原因"
        placeholder="填写区域、地点和现场能力核对结果"
        maxlength="500"
      />
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
    <ul>
      <li v-for="b in bookings" :key="b.id">
        {{ b.status }} {{ b.resultCode }} {{ b.finishResult }} ·
        {{ b.visitAddress }}
        <ElButton
          v-if="auth.has('SERVICE_AGENT') || auth.has('TECHNICIAN')"
          :disabled="busy"
          @click="verifyBooking(b)"
          >核对原预约</ElButton
        >
        <template v-if="b.status === 'CONFIRMED'">
          <ElButton :disabled="busy" @click="end(b)">到访前取消</ElButton>
          <template
            v-if="
              auth.has('TECHNICIAN') && ticket.assigneeId === auth.user?.ownerId
            "
          >
            <ElButton :disabled="busy" @click="end(b, 'VISITED')"
              >登记已到访</ElButton
            >
            <ElButton :disabled="busy" @click="end(b, 'NOT_PERFORMED')"
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
