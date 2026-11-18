<script setup lang="ts">
import { computed, onMounted, onScopeDispose, reactive, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElInput,
  ElOption,
  ElSelect,
  ElMessageBox,
} from 'element-plus'
import {
  blankRule,
  listSeries,
  getSeries,
  createSeries,
  listDocuments,
  getDocument,
  uploadDocument,
  uploadReceipt,
  updateDocument,
  changePublication,
  validateProcedure,
  changeDocumentSafety,
  type DocumentSafetyAction,
  type DocumentSafetyInput,
  deleteDocument,
  createIndex,
  getIndex,
  cancelIndex,
  replayIndex,
  type DocumentSeries,
  type KnowledgeDocument,
  type IndexJob,
  type MetadataInput,
} from '../../api/knowledge/knowledgeApi'
import { ApiError, newRequestId } from '../../api/http'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
import RuleEditor from './RuleEditor.vue'
import ProcedureEditor from './ProcedureEditor.vue'
import type { ProcedureInput } from '../../api/knowledge/procedures'

/** 原件、索引事务、AI执行和发布分别显示，超时沿原键查证。 @author yangaobo */
const auth = useAuthStore(),
  { run, busy, error, signal } = useOperation()
const seriesRows = ref<DocumentSeries[]>([]),
  docs = ref<KnowledgeDocument[]>([]),
  seriesId = ref<string | null>(null)
const seriesPage = ref(1),
  seriesTotal = ref('0'),
  pageNum = ref(1),
  total = ref('0')
const selected = ref<KnowledgeDocument | null>(null),
  job = ref<IndexJob | null>(null)
const selectedSeries = ref<DocumentSeries | null>(null)
const seriesForm = reactive<Omit<DocumentSeries, 'id'>>({
  sourceType: 'OFFICIAL_MANUAL',
  sourceNamespace: '',
  sourceCode: '',
  sourceOrganization: '',
  languageCode: 'zh-CN',
  audience: 'CUSTOMER',
})
const metadata = ref<MetadataInput>({
  title: '',
  sourceUrl: null,
  sourceReviewNote: null,
  revisionLabel: null,
  issuedOn: null,
  applicabilityNote: null,
  rules: [blankRule()],
})
const format = ref<'PDF' | 'MARKDOWN'>('MARKDOWN'),
  file = ref<File | null>(null),
  markdown = ref(''),
  useEditor = ref(true)
const uploadPending = ref<{ form: FormData; key: string } | null>(null),
  indexPending = ref<{
    document: KnowledgeDocument
    key: string
    parserTier: 'flash' | 'basic'
  } | null>(null)
const parserTier = ref<'flash' | 'basic'>('flash')
const predecessor = ref('')
const procedureDefinition = ref<ProcedureInput | null>(null)
const procedurePending = ref<{
  id: string
  rowVersion: string
  clientActionId: string
} | null>(null)
const safetyPending = ref<{
  action: DocumentSafetyAction
  body: DocumentSafetyInput
} | null>(null)
const safetyReason = ref('')
let timer: ReturnType<typeof setTimeout> | undefined,
  pollingUntil = 0,
  selectionVersion = 0
const frozen = computed(
  () =>
    busy.value ||
    !!uploadPending.value ||
    !!procedurePending.value ||
    (selected.value !== null && selected.value.publicationStatus !== 'DRAFT'),
)
const canIndex = computed(
  () =>
    selected.value?.publicationStatus === 'DRAFT' &&
    (!job.value ||
      job.value.submitState === 'ABORTED' ||
      (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(
        job.value.aiStatus ?? '',
      ) &&
        ['NOT_REQUIRED', 'NOT_SUBMITTED', 'SETTLED'].includes(
          job.value.parserState ?? '',
        ) &&
        job.value.cancelState !== 'PREPARING')),
)
function stopPolling() {
  if (timer) clearTimeout(timer)
  timer = undefined
}
async function loadList() {
  const value = await listDocuments(
    seriesId.value,
    pageNum.value,
    await auth.token(),
    signal,
  )
  docs.value = value.records
  total.value = value.total
}
async function readSelected() {
  if (selected.value)
    selected.value = await getDocument(
      selected.value.id,
      await auth.token(),
      signal,
    )
}
async function poll(version: number) {
  if (
    !selected.value?.currentJobId ||
    version !== selectionVersion ||
    signal.aborted
  )
    return
  try {
    const value = await getIndex(
      { id: selected.value.currentJobId },
      await auth.token(),
      signal,
    )
    if (version !== selectionVersion || signal.aborted) return
    job.value = value
    const settled =
      value.submitState === 'ABORTED' ||
      (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(value.aiStatus ?? '') &&
        ['NOT_REQUIRED', 'NOT_SUBMITTED', 'SETTLED'].includes(
          value.parserState ?? '',
        ) &&
        value.cancelState !== 'PREPARING')
    if (!settled && Date.now() < pollingUntil)
      timer = setTimeout(() => {
        void poll(version)
      }, 3000)
    else await readSelected()
  } catch (failure) {
    if (!signal.aborted && version === selectionVersion)
      error.value =
        failure instanceof Error
          ? failure.message
          : '索引状态查询失败，请沿原任务重新核对'
  }
}
function select(value: KnowledgeDocument) {
  if (procedurePending.value) return
  stopPolling()
  selectionVersion++
  const version = selectionVersion
  void run(async () => {
    const document = await getDocument(value.id, await auth.token(), signal)
    const source = await getSeries(
      document.seriesId,
      await auth.token(),
      signal,
    )
    if (version !== selectionVersion) return
    selectedSeries.value = source
    selected.value = document
    procedureDefinition.value = document.procedureDefinition
    job.value = null
    metadata.value = {
      title: document.title,
      sourceUrl: document.sourceUrl,
      sourceReviewNote: document.sourceReviewNote,
      revisionLabel: document.revisionLabel,
      issuedOn: document.issuedOn,
      applicabilityNote: document.applicabilityNote,
      rules: document.rules.map((rule) => ({ ...rule })),
    }
    pollingUntil = Date.now() + 300000
    if (document.currentJobId) void poll(version)
  })
}
function newRevision() {
  if (procedurePending.value) return
  stopPolling()
  selectionVersion++
  selected.value = null
  procedureDefinition.value = null
  selectedSeries.value = null
  job.value = null
}
function addSeries() {
  void run(async () => {
    const value = await createSeries({ ...seriesForm }, await auth.token())
    seriesRows.value.unshift(value)
    seriesId.value = value.id
    await loadList()
  })
}
function moreSeries() {
  void run(async () => {
    const value = await listSeries(
      seriesPage.value + 1,
      await auth.token(),
      signal,
    )
    seriesRows.value.push(...value.records)
    seriesTotal.value = value.total
    seriesPage.value++
  })
}
function chooseFile(event: Event) {
  if (event.target instanceof HTMLInputElement)
    file.value = event.target.files?.[0] ?? null
}
function upload() {
  void run(async () => {
    if (!uploadPending.value) {
      if (!seriesId.value) throw new Error('请先选择资料系列')
      const key = newRequestId(),
        data = new FormData()
      const original =
        format.value === 'MARKDOWN' && useEditor.value
          ? new File([markdown.value], 'document.md', { type: 'text/markdown' })
          : file.value
      if (!original) throw new Error('请选择PDF或Markdown文件')
      data.append(
        'metadata',
        new Blob(
          [
            JSON.stringify({
              ...metadata.value,
              rules: metadata.value.rules.map((rule) => ({ ...rule })),
              clientUploadId: key,
              seriesId: seriesId.value,
              contentFormat: format.value,
              procedureDefinition: procedureDefinition.value,
            }),
          ],
          { type: 'application/json' },
        ),
      )
      data.append('file', original)
      uploadPending.value = { form: data, key }
    }
    const value = await uploadDocument(
      uploadPending.value.form,
      await auth.token(),
      signal,
    )
    uploadPending.value = null
    selected.value = value
    await loadList()
  })
}
function checkUpload() {
  void run(async () => {
    if (!uploadPending.value) return
    const value = await uploadReceipt(
      uploadPending.value.key,
      await auth.token(),
      signal,
    )
    uploadPending.value = null
    selected.value = value
    await loadList()
  })
}

/** 响应未知沿原动作重试，只有明确业务拒绝才能释放原表单。 @author yangaobo */
function validateSelectedProcedure() {
  void run(async () => {
    if (!procedurePending.value) {
      if (!selected.value) return
      procedurePending.value = {
        id: selected.value.id,
        rowVersion: selected.value.rowVersion,
        clientActionId: newRequestId(),
      }
    }
    const submission = procedurePending.value
    try {
      await validateProcedure(submission, await auth.token())
      procedurePending.value = null
      selected.value = await getDocument(
        submission.id,
        await auth.token(),
        signal,
      )
    } catch (failure) {
      if (failure instanceof ApiError && [1001, 1004].includes(failure.code))
        procedurePending.value = null
      throw failure
    }
  })
}
function save() {
  void run(async () => {
    if (!selected.value) return
    selected.value = await updateDocument(
      selected.value.id,
      selected.value.rowVersion,
      {
        ...metadata.value,
        rules: metadata.value.rules.map((rule) => ({ ...rule })),
      },
      await auth.token(),
    )
    await loadList()
  })
}
function index() {
  void run(async () => {
    if (!selected.value) return
    indexPending.value ??= {
      document: { ...selected.value },
      key: newRequestId(),
      parserTier: parserTier.value,
    }
    job.value = await createIndex(
      indexPending.value.document,
      indexPending.value.key,
      await auth.token(),
      indexPending.value.parserTier,
    )
    indexPending.value = null
    await readSelected()
    pollingUntil = Date.now() + 300000
    void poll(selectionVersion)
  })
}
function checkIndex() {
  void run(async () => {
    if (indexPending.value) {
      job.value = await getIndex(
        { clientRequestId: indexPending.value.key },
        await auth.token(),
        signal,
      )
      indexPending.value = null
    } else if (selected.value?.currentJobId)
      job.value = await getIndex(
        { id: selected.value.currentJobId },
        await auth.token(),
        signal,
      )
    await readSelected()
    pollingUntil = Date.now() + 300000
    void poll(selectionVersion)
  })
}
function cancel() {
  void run(async () => {
    if (!selected.value) return
    job.value = await cancelIndex(selected.value, await auth.token())
    await readSelected()
    pollingUntil = Date.now() + 300000
    void poll(selectionVersion)
  })
}
function replay() {
  void run(async () => {
    if (job.value)
      job.value = await replayIndex(job.value.id, await auth.token())
  })
}
function publication(action: 'publish' | 'replace' | 'disable' | 'restore') {
  void run(async () => {
    if (!selected.value) return
    await ElMessageBox.confirm(
      `确认对“${selected.value.title}”执行${{ publish: '发布', replace: '替换当前修订', disable: '停用', restore: '恢复' }[action]}？`,
      '核对发布范围',
    )
    selected.value = await changePublication(
      action,
      selected.value,
      await auth.token(),
      predecessor.value || undefined,
    )
    await loadList()
  })
}
function remove() {
  void run(async () => {
    if (!selected.value) return
    await ElMessageBox.confirm(
      '仅删除已安全收敛且从未发布的草稿，原件版本和事务事实仍保留。',
      '删除草稿',
    )
    await deleteDocument(selected.value, await auth.token())
    newRevision()
    await loadList()
  })
}
/** 未知结果保留原动作身份；重试只查证同一次动作，不重新生成请求。 */
function safety(action?: DocumentSafetyAction) {
  void run(async () => {
    if (!safetyPending.value) {
      if (!selected.value || !action || !safetyReason.value.trim()) return
      const labels = {
        withdraw: '风险撤回',
        'revoke-access': '撤销访问',
        'restore-access': '恢复访问',
      }
      await ElMessageBox.confirm(
        `确认${labels[action]}“${selected.value.title}”？风险撤回必须另建修订纠错，恢复访问不会恢复已撤回的指导。`,
        '核对资料安全动作',
      )
      safetyPending.value = {
        action,
        body: {
          id: selected.value.id,
          rowVersion: selected.value.rowVersion,
          clientActionId: newRequestId(),
          reason: safetyReason.value.trim(),
        },
      }
    }
    const pending = safetyPending.value
    try {
      await changeDocumentSafety(
        pending.action,
        pending.body,
        await auth.token(),
      )
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [400, 401, 403, 404, 409, 422].includes(failure.status)
      ) {
        safetyPending.value = null
      }
      throw failure
    }
    safetyPending.value = null
    safetyReason.value = ''
    if (selected.value?.id === pending.body.id) {
      selected.value = await getDocument(
        pending.body.id,
        await auth.token(),
        signal,
      )
    }
    await loadList()
  })
}
onMounted(() => {
  void run(async () => {
    const value = await listSeries(1, await auth.token(), signal)
    seriesRows.value = value.records
    seriesTotal.value = value.total
    await loadList()
  })
})
onScopeDispose(stopPolling)

function pageNumReset() {
  pageNum.value = 1
  void run(loadList)
}
function pageNumPrevious() {
  pageNum.value--
  void run(loadList)
}
function pageNumNext() {
  pageNum.value++
  void run(loadList)
}
</script>

<template>
  <main class="page">
    <h1>知识原件与发布</h1>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <details class="panel">
      <summary>登记资料来源系列</summary>
      <form class="form-grid" @submit.prevent="addSeries">
        <label
          >资料类型<ElSelect v-model="seriesForm.sourceType"
            ><ElOption value="OFFICIAL_MANUAL" label="官方手册" /><ElOption
              value="TROUBLESHOOTING_GUIDE"
              label="排障规程" /><ElOption
              value="SANITIZED_CASE"
              label="已审核脱敏案例" /></ElSelect
        ></label>
        <label
          >来源目录代码<ElInput
            v-model="seriesForm.sourceNamespace"
            maxlength="64"
            required /></label
        ><label
          >稳定资料编号<ElInput
            v-model="seriesForm.sourceCode"
            maxlength="120"
            required /></label
        ><label
          >发布组织<ElInput
            v-model="seriesForm.sourceOrganization"
            maxlength="200"
            required
        /></label>
        <label
          >语言<ElInput
            v-model="seriesForm.languageCode"
            maxlength="16"
            required /></label
        ><label
          >受众<ElSelect v-model="seriesForm.audience"
            ><ElOption value="CUSTOMER" label="客户可读" /><ElOption
              value="SERVICE_STAFF"
              label="仅服务人员" /></ElSelect></label
        ><ElButton native-type="submit" :loading="busy">登记系列</ElButton>
      </form>
    </details>
    <section class="panel">
      <label
        >资料系列<ElSelect
          v-model="seriesId"
          clearable
          :disabled="!!uploadPending"
          @change="pageNumReset"
          ><ElOption
            v-for="row in seriesRows"
            :key="row.id"
            :value="row.id"
            :label="`${row.sourceOrganization} · ${row.sourceCode} · ${row.audience}`" /></ElSelect></label
      ><ElButton
        v-if="BigInt(seriesRows.length) < BigInt(seriesTotal)"
        :disabled="busy"
        @click="moreSeries"
        >更多系列</ElButton
      ><ElButton
        :disabled="busy || !!uploadPending || !!indexPending"
        @click="newRevision"
        >上传新修订</ElButton
      >
      <ul>
        <li v-for="row in docs" :key="row.id">
          <ElButton
            link
            :disabled="busy || !!uploadPending || !!indexPending"
            @click="select(row)"
            >{{ row.title }} · 修订 {{ row.revisionNumber }} ·
            {{ row.publicationStatus }}</ElButton
          >
        </li>
      </ul>
      <ElButton :disabled="pageNum === 1 || busy" @click="pageNumPrevious"
        >上一页</ElButton
      ><ElButton
        :disabled="BigInt(pageNum * 10) >= BigInt(total) || busy"
        @click="pageNumNext"
        >下一页</ElButton
      >
    </section>
    <section class="panel">
      <h2>
        {{
          selected
            ? `修订 ${selected.revisionNumber} · ${selected.publicationStatus}`
            : '新原件修订'
        }}
      </h2>
      <label
        >标题<ElInput
          v-model="metadata.title"
          maxlength="255"
          :disabled="frozen" /></label
      ><label
        >来源链接说明<ElInput
          :model-value="metadata.sourceUrl ?? ''"
          :disabled="frozen"
          @update:model-value="metadata.sourceUrl = $event || null" /></label
      ><label
        >来源与使用依据复核记录<ElInput
          :model-value="metadata.sourceReviewNote ?? ''"
          type="textarea"
          maxlength="1000"
          :disabled="frozen"
          @update:model-value="
            metadata.sourceReviewNote = $event || null
          " /></label
      ><label
        >原件修订标识<ElInput
          :model-value="metadata.revisionLabel ?? ''"
          maxlength="100"
          :disabled="frozen"
          @update:model-value="
            metadata.revisionLabel = $event || null
          " /></label
      ><label
        >适用说明<ElInput
          :model-value="metadata.applicabilityNote ?? ''"
          maxlength="500"
          :disabled="frozen"
          @update:model-value="metadata.applicabilityNote = $event || null"
      /></label>
      <RuleEditor v-model="metadata.rules" :disabled="frozen" />
      <ProcedureEditor
        v-model="procedureDefinition"
        :format="selected?.contentFormat ?? format"
        :disabled="frozen || !!selected"
      />
      <template v-if="selected?.procedureDefinition">
        <p>
          规程校验：{{
            selected.procedureSha256 === selected.procedureValidatedSha256
              ? '已通过'
              : '待校验'
          }}
        </p>
        <ElButton
          v-if="selected.publicationStatus === 'DRAFT'"
          :disabled="busy"
          @click="validateSelectedProcedure"
          >{{
            procedurePending ? '沿原动作重试校验' : '核对并校验规程'
          }}</ElButton
        >
      </template>
      <template v-if="!selected"
        ><label
          >原件格式<ElSelect v-model="format" :disabled="!!uploadPending"
            ><ElOption
              value="MARKDOWN"
              label="UTF-8 Markdown，至多1MiB" /><ElOption
              value="PDF"
              label="PDF，至多20MiB" /></ElSelect></label
        ><label v-if="format === 'MARKDOWN'"
          ><input
            v-model="useEditor"
            type="checkbox"
            :disabled="!!uploadPending"
          />使用文本编辑器</label
        ><ElInput
          v-if="format === 'MARKDOWN' && useEditor"
          v-model="markdown"
          type="textarea"
          :rows="10"
          :disabled="!!uploadPending"
          placeholder="填写前置、检查、反馈分支、风险与停止条件。禁止原始HTML和远程图片。"
        /><input
          v-else
          type="file"
          accept=".pdf,.md,.markdown"
          :disabled="!!uploadPending"
          @change="chooseFile"
        /><ElButton :loading="busy" @click="upload">{{
          uploadPending ? '沿原上传键重试' : '校验并上传原件'
        }}</ElButton
        ><ElButton v-if="uploadPending" :disabled="busy" @click="checkUpload"
          >核对原上传回执</ElButton
        ></template
      >
      <template v-else
        ><p v-if="selectedSeries">
          来源：{{ selectedSeries.sourceOrganization }} ·
          {{ selectedSeries.sourceCode }} · {{ selectedSeries.languageCode }} ·
          {{ selectedSeries.audience }}
        </p>
        <p>
          原件 {{ selected.originalFileName }} ·
          {{ selected.fileSizeBytes }} 字节 · 文档版本 {{ selected.rowVersion }}
        </p>
        <a :href="`/api/v1/admin/document/${selected.id}/file`"
          >读取精确原件版本</a
        ><ElButton
          v-if="selected.publicationStatus === 'DRAFT'"
          :disabled="busy"
          @click="save"
          >保存草稿规则与元数据</ElButton
        >
        <div v-if="selected.publicationStatus === 'DRAFT'">
          <label v-if="selected.contentFormat === 'PDF'">
            PDF 解析方式
            <ElSelect v-model="parserTier" :disabled="busy || !!indexPending">
              <ElOption label="快速解析（普通文字资料）" value="flash" />
              <ElOption label="基础解析" value="basic" />
            </ElSelect>
          </label>
          <ElButton
            :disabled="busy || (!canIndex && !indexPending)"
            @click="index"
            >{{ indexPending ? '沿原索引请求重试' : '创建索引尝试' }}</ElButton
          ><ElButton :disabled="busy" @click="checkIndex"
            >核对原任务状态</ElButton
          ><ElButton
            v-if="selected.currentJobId"
            :disabled="busy"
            @click="cancel"
            >请求取消当前索引</ElButton
          ><ElButton :disabled="busy" @click="publication('publish')"
            >核验并发布</ElButton
          ><label
            >替换的当前修订 ID<ElSelect v-model="predecessor"
              ><ElOption
                v-for="row in docs.filter((row) =>
                  ['PUBLISHED', 'DISABLED', 'WITHDRAWN'].includes(
                    row.publicationStatus,
                  ),
                )"
                :key="row.id"
                :value="row.id"
                :label="`${row.title} · 修订 ${row.revisionNumber}`" /></ElSelect></label
          ><ElButton
            :disabled="busy || !predecessor"
            @click="publication('replace')"
            >核验并替换当前修订</ElButton
          ><ElButton :disabled="busy" @click="remove">删除已收敛草稿</ElButton>
        </div>
        <ElButton
          v-if="selected.publicationStatus === 'PUBLISHED'"
          :disabled="busy"
          @click="publication('disable')"
          >停用</ElButton
        ><ElButton
          v-if="
            selected.publicationStatus === 'DISABLED' &&
            selected.accessStatus === 'ALLOWED'
          "
          :disabled="busy"
          @click="publication('restore')"
          >重新核验并恢复</ElButton
        >
        <p>
          访问状态：{{
            selected.accessStatus === 'ALLOWED' ? '允许授权读取' : '已撤销访问'
          }}
        </p>
        <p v-if="selected.accessReason">
          访问变更依据：{{ selected.accessReason }}
        </p>
        <ElAlert
          v-if="selected.withdrawalReason"
          type="warning"
          :closable="false"
          :title="`风险撤回：${selected.withdrawalReason}`"
        />
        <template
          v-if="selected.publicationStatus !== 'DELETED' && !safetyPending"
        >
          <label
            >安全动作依据<ElInput
              v-model="safetyReason"
              type="textarea"
              :maxlength="1000"
              :disabled="busy"
          /></label>
          <ElButton
            v-if="
              ['PUBLISHED', 'DISABLED', 'RETIRED'].includes(
                selected.publicationStatus,
              )
            "
            :disabled="busy || !safetyReason.trim()"
            @click="safety('withdraw')"
            >风险撤回</ElButton
          >
          <ElButton
            v-if="selected.accessStatus === 'ALLOWED'"
            :disabled="busy || !safetyReason.trim()"
            @click="safety('revoke-access')"
            >撤销访问</ElButton
          >
          <ElButton
            v-else
            :disabled="busy || !safetyReason.trim()"
            @click="safety('restore-access')"
            >仅恢复访问</ElButton
          >
        </template>
      </template>
    </section>
    <section v-if="safetyPending" class="panel" aria-live="polite">
      <ElAlert
        type="warning"
        :closable="false"
        title="安全动作尚未核对结果。保留原参数，沿原动作重试。"
      />
      <p>资料 {{ safetyPending.body.id }} · {{ safetyPending.body.reason }}</p>
      <ElButton :disabled="busy" @click="safety()">核对原安全动作</ElButton>
    </section>
    <section v-if="job" class="panel" aria-live="polite">
      <h2>索引任务进展</h2>
      <p>本地提交 {{ job.submitState }} · 本地取消 {{ job.cancelState }}</p>
      <p>
        AI受理：{{
          job.aiAccepted === null
            ? '尚未查询'
            : job.aiAccepted
              ? '已持久受理'
              : '本次未查到，仍可能在途'
        }}
        · 执行 {{ job.aiStatus ?? '未知' }} · 阶段 {{ job.aiStage ?? '尚无' }} ·
        解析 {{ job.parserState ?? '未知' }}
      </p>
      <p>
        双索引当前复核：{{
          job.replicasVerified === null
            ? '未验证'
            : job.replicasVerified
              ? '通过'
              : '尚不满足'
        }}
        {{ job.lastErrorCode }}
      </p>
      <ElButton
        :disabled="
          busy ||
          job.submitState !== 'COMMITTED' ||
          job.cancelState === 'PREPARING'
        "
        @click="replay"
        >审计并重放已提交原指令</ElButton
      >
    </section>
  </main>
</template>
