<script setup lang="ts">
import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus'
import {
  conditionOptions,
  fieldOptions,
  procedure,
  type ProcedureInput,
  type ProcedureCheckInput,
} from '../../api/knowledge/procedures'
import { newRequestId } from '../../api/http'

/** 人工编写修订内检查，所有编辑输出独立副本，上传后只读。 @author yangaobo */
const props = defineProps<{
  modelValue: ProcedureInput | null
  format: 'PDF' | 'MARKDOWN'
  disabled: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: ProcedureInput | null]
}>()
function change(patch: Partial<ProcedureInput>) {
  if (!props.modelValue || props.disabled) return
  emit(
    'update:modelValue',
    procedure(JSON.parse(JSON.stringify({ ...props.modelValue, ...patch }))),
  )
}
function edit(key: string, patch: Partial<ProcedureCheckInput>) {
  change({
    checks:
      props.modelValue?.checks.map((check) =>
        check.checkKey === key
          ? {
              ...check,
              ...patch,
              prerequisiteAnswers: (
                patch.prerequisiteAnswers ?? check.prerequisiteAnswers
              ).filter((condition) =>
                (
                  patch.prerequisiteCheckKeys ?? check.prerequisiteCheckKeys
                ).includes(condition.checkKey),
              ),
            }
          : { ...check },
      ) ?? [],
  })
}
/** 只能选择前置选择题的既有选项，空选择表示不附加答案值条件。 @author yangaobo */
function requireAnswers(
  check: ProcedureCheckInput,
  priorKey: string,
  values: string[],
) {
  edit(check.checkKey, {
    prerequisiteAnswers: [
      ...check.prerequisiteAnswers.filter(
        (condition) => condition.checkKey !== priorKey,
      ),
      ...(values.length ? [{ checkKey: priorKey, allowedValues: values }] : []),
    ],
  })
}
function enable() {
  emit('update:modelValue', {
    schemaVersion: 1,
    applicability: '',
    requiredFields: [],
    checks: [],
    stopConditions: [],
    handoverConditions: [],
  })
}
function add() {
  const item: ProcedureCheckInput = {
    checkKey: `check_${newRequestId()}`,
    roles: ['CUSTOMER', 'TECHNICIAN'],
    instruction: '',
    answerSchema: {
      schemaVersion: 1,
      kind: 'TEXT',
      options: [],
      maxLength: 2000,
      minimum: null,
      maximum: null,
    },
    requiredFields: [],
    prerequisiteCheckKeys: [],
    prerequisiteAnswers: [],
    riskLevel: 'LOW',
    stopConditions: [],
    sourceLocator:
      props.format === 'PDF'
        ? { kind: 'PDF', pageNumber: 1 }
        : { kind: 'MARKDOWN', anchor: 'document', lineStart: 1, lineEnd: 1 },
  }
  change({ checks: [...(props.modelValue?.checks ?? []), item] })
}
function remove(key: string) {
  change({
    checks:
      props.modelValue?.checks
        .filter((check) => check.checkKey !== key)
        .map((check) => ({
          ...check,
          prerequisiteCheckKeys: check.prerequisiteCheckKeys.filter(
            (value) => value !== key,
          ),
          prerequisiteAnswers: check.prerequisiteAnswers.filter(
            (condition) => condition.checkKey !== key,
          ),
        })) ?? [],
  })
}
</script>

<template>
  <section class="procedure-editor">
    <h3>人工审核规程（可选）</h3>
    <p>只填写有原文依据的检查。上传后定义固定，校验通过后才能随资料发布。</p>
    <ElButton v-if="!modelValue" :disabled="disabled" @click="enable"
      >添加审核规程</ElButton
    >
    <template v-else>
      <label
        >适用条件<ElInput
          :model-value="modelValue.applicability"
          :disabled="disabled"
          type="textarea"
          @update:model-value="change({ applicability: $event })"
      /></label>
      <label
        >开始前必须确认<ElSelect
          :model-value="modelValue.requiredFields"
          multiple
          :disabled="disabled"
          @update:model-value="change({ requiredFields: $event })"
          ><ElOption
            v-for="field in fieldOptions"
            :key="field.value"
            :label="field.label"
            :value="field.value" /></ElSelect
      ></label>
      <label
        >停止条件<ElSelect
          :model-value="modelValue.stopConditions"
          multiple
          :disabled="disabled"
          @update:model-value="change({ stopConditions: $event })"
          ><ElOption
            v-for="condition in conditionOptions"
            :key="condition.value"
            :label="condition.label"
            :value="condition.value" /></ElSelect
      ></label>
      <label
        >转人工条件<ElSelect
          :model-value="modelValue.handoverConditions"
          multiple
          :disabled="disabled"
          @update:model-value="change({ handoverConditions: $event })"
          ><ElOption
            v-for="condition in conditionOptions"
            :key="condition.value"
            :label="condition.label"
            :value="condition.value" /></ElSelect
      ></label>
      <article
        v-for="(check, index) in modelValue.checks"
        :key="check.checkKey"
      >
        <h4>检查 {{ index + 1 }}</h4>
        <label
          >操作说明<ElInput
            :model-value="check.instruction"
            type="textarea"
            :disabled="disabled"
            @update:model-value="edit(check.checkKey, { instruction: $event })"
        /></label>
        <label
          >允许执行者<ElSelect
            :model-value="check.roles"
            multiple
            :disabled="disabled"
            @update:model-value="edit(check.checkKey, { roles: $event })"
            ><ElOption label="客户" value="CUSTOMER" /><ElOption
              label="技术人员"
              value="TECHNICIAN" /></ElSelect
        ></label>
        <label
          >风险处理<ElSelect
            :model-value="check.riskLevel"
            :disabled="disabled"
            @update:model-value="edit(check.checkKey, { riskLevel: $event })"
            ><ElOption label="低风险检查" value="LOW" /><ElOption
              label="必须交接处理"
              value="HANDOVER_REQUIRED" /></ElSelect
        ></label>
        <label
          >答案类型<ElSelect
            :model-value="check.answerSchema.kind"
            :disabled="disabled"
            @update:model-value="
              edit(check.checkKey, {
                answerSchema: {
                  ...check.answerSchema,
                  kind: $event,
                  options: [],
                  minimum: null,
                  maximum: null,
                },
              })
            "
            ><ElOption label="文字说明" value="TEXT" /><ElOption
              label="是或否"
              value="BOOLEAN" /><ElOption
              label="选项"
              value="CHOICE" /><ElOption
              label="数值"
              value="NUMBER" /></ElSelect
        ></label>
        <label v-if="check.answerSchema.kind === 'CHOICE'"
          >可选答案（每行一项）<ElInput
            :model-value="check.answerSchema.options.join('\n')"
            type="textarea"
            :disabled="disabled"
            @update:model-value="
              edit(check.checkKey, {
                answerSchema: {
                  ...check.answerSchema,
                  options: $event.split('\n'),
                },
              })
            "
        /></label>
        <label
          >此项需要确认的环境<ElSelect
            :model-value="check.requiredFields"
            multiple
            :disabled="disabled"
            @update:model-value="
              edit(check.checkKey, { requiredFields: $event })
            "
            ><ElOption
              v-for="field in fieldOptions"
              :key="field.value"
              :label="field.label"
              :value="field.value" /></ElSelect
        ></label>
        <label
          >必须先完成的检查<ElSelect
            :model-value="check.prerequisiteCheckKeys"
            multiple
            :disabled="disabled"
            @update:model-value="
              edit(check.checkKey, { prerequisiteCheckKeys: $event })
            "
            ><ElOption
              v-for="prior in modelValue.checks.slice(0, index)"
              :key="prior.checkKey"
              :label="prior.instruction || '尚未填写操作说明'"
              :value="prior.checkKey" /></ElSelect
        ></label>
        <label
          v-for="prior in modelValue.checks
            .slice(0, index)
            .filter(
              (item) =>
                check.prerequisiteCheckKeys.includes(item.checkKey) &&
                item.answerSchema.kind === 'CHOICE',
            )"
          :key="prior.checkKey"
          >前置答案要求：{{ prior.instruction || prior.checkKey }}
          <ElSelect
            :model-value="
              check.prerequisiteAnswers.find(
                (condition) => condition.checkKey === prior.checkKey,
              )?.allowedValues ?? []
            "
            multiple
            clearable
            placeholder="不限制答案；有条件操作必须选择允许值"
            :disabled="disabled"
            @update:model-value="requireAnswers(check, prior.checkKey, $event)"
            ><ElOption
              v-for="option in prior.answerSchema.options"
              :key="option"
              :label="option"
              :value="option"
          /></ElSelect>
        </label>
        <label
          >此项停止条件<ElSelect
            :model-value="check.stopConditions"
            multiple
            :disabled="disabled"
            @update:model-value="
              edit(check.checkKey, { stopConditions: $event })
            "
            ><ElOption
              v-for="condition in conditionOptions"
              :key="condition.value"
              :label="condition.label"
              :value="condition.value" /></ElSelect
        ></label>
        <template v-if="check.sourceLocator.kind === 'PDF'">
          <label
            >原件物理页<ElInputNumber
              :model-value="check.sourceLocator.pageNumber"
              :min="1"
              :disabled="disabled"
              @update:model-value="
                edit(check.checkKey, {
                  sourceLocator: { kind: 'PDF', pageNumber: $event ?? 1 },
                })
              "
          /></label>
        </template>
        <template v-else>
          <label
            >标题锚点<ElInput
              :model-value="check.sourceLocator.anchor"
              :disabled="disabled"
              @update:model-value="
                edit(check.checkKey, {
                  sourceLocator: { ...check.sourceLocator, anchor: $event },
                })
              "
          /></label>
          <label
            >原件起始行<ElInputNumber
              :model-value="check.sourceLocator.lineStart"
              :min="1"
              :disabled="disabled"
              @update:model-value="
                edit(check.checkKey, {
                  sourceLocator: {
                    ...check.sourceLocator,
                    lineStart: $event ?? 1,
                  },
                })
              "
          /></label>
          <label
            >原件结束行<ElInputNumber
              :model-value="check.sourceLocator.lineEnd"
              :min="1"
              :disabled="disabled"
              @update:model-value="
                edit(check.checkKey, {
                  sourceLocator: {
                    ...check.sourceLocator,
                    lineEnd: $event ?? 1,
                  },
                })
              "
          /></label>
        </template>
        <ElButton :disabled="disabled" @click="remove(check.checkKey)"
          >移除此项</ElButton
        >
      </article>
      <ElButton
        :disabled="disabled || modelValue.checks.length >= 20"
        @click="add"
        >添加检查</ElButton
      >
      <ElButton :disabled="disabled" @click="emit('update:modelValue', null)"
        >移除规程</ElButton
      >
    </template>
  </section>
</template>

<style scoped>
.procedure-editor {
  display: grid;
  gap: 12px;
}
article {
  padding: 12px;
  border: 1px solid var(--el-border-color);
  display: grid;
  gap: 8px;
}
label {
  display: grid;
  gap: 4px;
}
</style>
