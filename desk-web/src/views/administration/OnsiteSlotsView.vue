<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElInputNumber } from 'element-plus'
import {
  addSlot,
  changeSlot,
  listSlots,
  querySlot,
  updateSlot,
  type OnsiteSlot,
  type SlotInput,
} from '../../api/onsite/onsiteApi'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'

/** 时段发布前人工核对出勤，发布后容量与窗口冻结。 @author yangaobo */
const auth = useAuthStore()
const { run, busy, error, signal } = useOperation()
const rows = ref<OnsiteSlot[]>([]),
  pageNum = ref(1),
  total = ref('0')
const form = reactive<SlotInput>({
  serviceAreaCode: '',
  startsAt: '',
  bookingOpensAt: '',
  bookingClosesAt: '',
  capacity: 1,
})
const editing = ref<OnsiteSlot | null>(null)
function edit(row: OnsiteSlot) {
  void run(async () => {
    const current = await querySlot(row.id, await auth.token(), signal)
    if (current.status !== 'DRAFT') throw new Error('时段已发布，请刷新')
    editing.value = current
    Object.assign(form, {
      serviceAreaCode: current.serviceAreaCode,
      startsAt: current.startsAt,
      bookingOpensAt: current.bookingOpensAt,
      bookingClosesAt: current.bookingClosesAt,
      capacity: current.capacity,
    })
  })
}
async function load() {
  const value = await listSlots(pageNum.value, await auth.token(), signal)
  rows.value = value.records
  total.value = value.total
}
function create() {
  void run(async () => {
    if (editing.value)
      await updateSlot(
        { ...form, id: editing.value.id, rowVersion: editing.value.rowVersion },
        await auth.token(),
      )
    else await addSlot({ ...form }, await auth.token())
    editing.value = null
    await load()
  })
}
function change(value: OnsiteSlot) {
  void run(async () => {
    await changeSlot(
      value.status === 'DRAFT' ? 'publish' : 'close',
      value,
      await auth.token(),
    )
    await load()
  })
}
onMounted(() => {
  void run(load)
})

function pageNumPrevious() {
  pageNum.value--
  void run(load)
}
function pageNumNext() {
  pageNum.value++
  void run(load)
}
</script>

<template>
  <main class="page">
    <h1>区域上门时段</h1>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p>
      先人工确认出勤。北京时间仅开放 09:00、11:00、14:00、16:00
      开始的两小时窗口。时刻填写 ISO 8601 并带 +08:00。
    </p>
    <form class="panel" @submit.prevent="create">
      <label
        >区域代码<ElInput
          v-model="form.serviceAreaCode"
          required
          maxlength="32"
      /></label>
      <label
        >到访开始<ElInput
          v-model="form.startsAt"
          required
          placeholder="2026-11-20T09:00:00+08:00"
      /></label>
      <label
        >开放申请<ElInput
          v-model="form.bookingOpensAt"
          required
          placeholder="须晚于当前时间，先发布再开放"
      /></label>
      <label
        >申请截止<ElInput
          v-model="form.bookingClosesAt"
          required
          placeholder="不得晚于到访开始"
      /></label>
      <label
        >已核对容量<ElInputNumber v-model="form.capacity" :min="1" :max="1000"
      /></label>
      <ElButton native-type="submit" :loading="busy">{{
        editing ? '保存草稿修改' : '建立草稿时段'
      }}</ElButton>
      <ElButton v-if="editing" :disabled="busy" @click="editing = null"
        >取消编辑</ElButton
      >
    </form>
    <ul class="panel">
      <li v-for="row in rows" :key="row.id">
        {{ row.serviceAreaCode }} ·
        {{
          new Date(row.startsAt).toLocaleString('zh-CN', {
            timeZone: 'Asia/Shanghai',
          })
        }}
        · {{ row.status }} · 占用 {{ row.reservedCount }}/{{ row.capacity }}
        <ElButton
          v-if="row.status === 'DRAFT'"
          :disabled="busy"
          @click="edit(row)"
          >编辑草稿</ElButton
        >
        <ElButton
          v-if="row.status !== 'CLOSED'"
          :disabled="busy"
          @click="change(row)"
          >{{
            row.status === 'DRAFT' ? '确认出勤并发布' : '关停新申请'
          }}</ElButton
        >
      </li>
    </ul>
    <ElButton :disabled="pageNum === 1 || busy" @click="pageNumPrevious"
      >上一页</ElButton
    >
    <ElButton
      :disabled="BigInt(pageNum * 10) >= BigInt(total) || busy"
      @click="pageNumNext"
      >下一页</ElButton
    >
  </main>
</template>
