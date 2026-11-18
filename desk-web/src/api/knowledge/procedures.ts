import { array, choices, integer, nullable, record, text } from '../validation'

/** 审核规程的有限条件代码，标签供人工核对；判定由后端负责。 @author yangaobo */
export const conditionOptions = [
  { value: 'SAFETY_HAZARD_REPORTED', label: '报告冒烟、异味等人身或设备风险' },
  { value: 'DATA_LOSS_RISK_REPORTED', label: '报告数据丢失风险' },
  { value: 'ADMIN_PRIVILEGE_REQUIRED', label: '需要管理员权限' },
  { value: 'OUT_OF_SERVICE_SCOPE', label: '超出支持范围' },
  { value: 'NO_PROGRESS_LIMIT', label: '多次检查无进展' },
  { value: 'EVIDENCE_CONFLICT', label: '证据冲突' },
] as const
export const fieldOptions = [
  { value: 'os_name', label: '操作系统' },
  { value: 'os_version', label: '系统版本' },
  { value: 'software_name', label: '软件名称' },
  { value: 'software_version', label: '软件版本' },
  { value: 'connection_type', label: '连接方式' },
  { value: 'affected_scope', label: '影响范围' },
  { value: 'error_code', label: '错误码' },
] as const
const field = choices(...fieldOptions.map((item) => item.value))
const condition = choices(...conditionOptions.map((item) => item.value))

/** 固定原件定位，不接受路径或URL。 @author yangaobo */
export function procedureLocator(value: unknown) {
  const v = record(value)
  const kind = choices('PDF', 'MARKDOWN')(v.kind)
  return kind === 'PDF'
    ? { kind, pageNumber: integer(v.pageNumber) }
    : {
        kind,
        anchor: text(v.anchor),
        lineStart: integer(v.lineStart),
        lineEnd: integer(v.lineEnd),
      }
}
/** 完整读取定义；客户端展示校验不能替代服务端规程审核。 @author yangaobo */
export function procedureCheck(value: unknown) {
  const v = record(value),
    answer = record(v.answerSchema)
  return {
    checkKey: text(v.checkKey),
    roles: array(choices('CUSTOMER', 'TECHNICIAN'))(v.roles),
    instruction: text(v.instruction),
    answerSchema: {
      schemaVersion: integer(answer.schemaVersion),
      kind: choices('TEXT', 'BOOLEAN', 'CHOICE', 'NUMBER')(answer.kind),
      options: array(text)(answer.options),
      maxLength: integer(answer.maxLength),
      minimum: answer.minimum == null ? null : finiteNumber(answer.minimum),
      maximum: answer.maximum == null ? null : finiteNumber(answer.maximum),
    },
    requiredFields: array(field)(v.requiredFields),
    prerequisiteCheckKeys: array(text)(v.prerequisiteCheckKeys),
    prerequisiteAnswers:
      v.prerequisiteAnswers === undefined
        ? []
        : array((value) => {
            const condition = record(value)
            return {
              checkKey: text(condition.checkKey),
              allowedValues: array(text)(condition.allowedValues),
            }
          })(v.prerequisiteAnswers),
    riskLevel: choices('LOW', 'HANDOVER_REQUIRED')(v.riskLevel),
    stopConditions: array(condition)(v.stopConditions),
    sourceLocator: procedureLocator(v.sourceLocator),
  }
}
/** 数值答案范围必须是有限JSON数字。 @author yangaobo */
function finiteNumber(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value))
    throw new Error('规程数值范围不合法')
  return value
}
/** 修订内规程，没有定义时必须返回null。 @author yangaobo */
export function procedure(value: unknown) {
  const v = record(value)
  return {
    schemaVersion: integer(v.schemaVersion),
    applicability: text(v.applicability),
    requiredFields: array(field)(v.requiredFields),
    checks: array(procedureCheck)(v.checks),
    stopConditions: array(condition)(v.stopConditions),
    handoverConditions: array(condition)(v.handoverConditions),
  }
}
export const nullableProcedure = nullable(procedure)
export type ProcedureInput = ReturnType<typeof procedure>
export type ProcedureCheckInput = ReturnType<typeof procedureCheck>
