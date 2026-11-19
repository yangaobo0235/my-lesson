<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElOption,
  ElSelect,
  ElSkeleton,
} from 'element-plus'
import { listTickets } from '../../api/ticket/ticketApi'
import { statusNames } from '../../api/ticket/types'
import PageControls from '../../components/PageControls.vue'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import TicketQueueTable from './TicketQueueTable.vue'
import { readTicketQueue, ticketQueueQuery } from './ticketQueueRoute'

const props = defineProps<{ assigned?: boolean }>()
const auth = useAuthStore()
const route = useRoute()
const initial = readTicketQueue(route.query, auth.has('TECHNICIAN'))
const filters = reactive(initial.filters)
const applied = shallowRef({ ...filters })
const result = ref<Awaited<ReturnType<typeof listTickets>> | null>(null)
const detailQuery = computed(() => ({
  ...ticketQueueQuery(applied.value, result.value?.pageNum ?? 1),
  from: props.assigned ? 'assigned' : undefined,
}))
const { busy, error, run, signal } = useOperation()

/** 筛选只有查询成功后才成为分页依据，失败仍保留上一次成功结果。 */
function load(pageNum: number, criteria = applied.value) {
  void run(async () => {
    const response = await listTickets(
      {
        pageNum,
        status: criteria.status || undefined,
        triage: criteria.triage || undefined,
        overdue: criteria.overdue || undefined,
        assignedToMe: props.assigned || criteria.assignedToMe || undefined,
      },
      signal,
    )
    result.value = response
    applied.value = criteria
  })
}

onMounted(() => load(initial.page))
</script>

<template>
  <main>
    <div class="page-heading">
      <div>
        <h1>
          {{
            props.assigned
              ? '我的处理'
              : auth.has('CUSTOMER_USER')
                ? '我的工单'
                : '工单中心'
          }}
        </h1>
        <p class="subtle">
          {{
            auth.has('CUSTOMER_USER')
              ? '查看受理进展、补充信息和确认处理结果。'
              : '查看待受理、处理和客户反馈，按状态或期限筛选。'
          }}
        </p>
      </div>
      <RouterLink
        v-if="auth.has('CUSTOMER_USER')"
        :to="{ name: 'report' }"
        class="button-link"
        >直接报修</RouterLink
      >
    </div>
    <form class="toolbar query-bar" @submit.prevent="load(1, { ...filters })">
      <ElSelect
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        aria-label="工单状态"
      >
        <ElOption
          v-for="(label, value) in statusNames"
          :key="value"
          :label="label"
          :value="value"
        />
      </ElSelect>
      <ElCheckbox v-model="filters.triage">待分类</ElCheckbox>
      <ElCheckbox v-model="filters.overdue">受理超期</ElCheckbox>
      <ElCheckbox
        v-if="!props.assigned && auth.has('TECHNICIAN')"
        v-model="filters.assignedToMe"
        >分派给我</ElCheckbox
      >
      <ElButton native-type="submit" type="primary" :loading="busy"
        >查询</ElButton
      >
    </form>
    <ElAlert v-if="error" :title="error" type="error" :closable="false">
      <span v-if="result">下方显示上一次成功查询的结果。</span>
      <ElButton v-else text size="small" @click="load(1, { ...filters })"
        >重试查询</ElButton
      >
    </ElAlert>
    <ElSkeleton v-if="busy && !result" :rows="4" animated />
    <div v-if="result" class="card ticket-results">
      <TicketQueueTable
        v-if="result.records.length"
        :rows="result.records"
        :customer="auth.has('CUSTOMER_USER')"
        :detail-query="detailQuery"
      />
      <p v-else class="empty-result">暂无符合条件的工单。</p>
    </div>
    <PageControls v-if="result" :page="result" :busy="busy" @change="load" />
  </main>
</template>

<style scoped>
.ticket-results {
  padding: 0;
}
.empty-result {
  padding: 16px;
  margin: 0;
  color: var(--desk-muted);
}
</style>
