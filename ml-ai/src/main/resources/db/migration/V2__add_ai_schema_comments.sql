-- Database comments for AI domain objects.
COMMENT ON TABLE ai_conversation IS 'AI 对话会话表';
COMMENT ON COLUMN ai_conversation.id IS '会话主键';
COMMENT ON COLUMN ai_conversation.user_id IS '用户 ID';
COMMENT ON COLUMN ai_conversation.title IS '会话标题';
COMMENT ON COLUMN ai_conversation.status IS '会话状态，例如 ACTIVE、ARCHIVED、DELETED';
COMMENT ON COLUMN ai_conversation.created_at IS '创建时间';
COMMENT ON COLUMN ai_conversation.updated_at IS '最后更新时间';

COMMENT ON TABLE ai_message IS 'AI 会话消息表';
COMMENT ON COLUMN ai_message.id IS '消息主键';
COMMENT ON COLUMN ai_message.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN ai_message.role IS '消息角色，例如 system、user、assistant、tool';
COMMENT ON COLUMN ai_message.content IS '消息正文';
COMMENT ON COLUMN ai_message.token_count IS '消息 Token 数量';
COMMENT ON COLUMN ai_message.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN ai_message.created_at IS '创建时间';

COMMENT ON TABLE ai_agent_run IS 'AI Agent 运行记录表';
COMMENT ON COLUMN ai_agent_run.id IS '运行记录主键';
COMMENT ON COLUMN ai_agent_run.conversation_id IS '关联会话 ID';
COMMENT ON COLUMN ai_agent_run.user_id IS '发起运行的用户 ID';
COMMENT ON COLUMN ai_agent_run.agent_name IS 'Agent 名称';
COMMENT ON COLUMN ai_agent_run.intent IS '识别出的用户意图';
COMMENT ON COLUMN ai_agent_run.status IS '运行状态，例如 RUNNING、SUCCEEDED、FAILED';
COMMENT ON COLUMN ai_agent_run.model_name IS '使用的模型名称';
COMMENT ON COLUMN ai_agent_run.prompt_tokens IS '输入 Token 数量';
COMMENT ON COLUMN ai_agent_run.completion_tokens IS '输出 Token 数量';
COMMENT ON COLUMN ai_agent_run.latency_ms IS '运行耗时，单位毫秒';
COMMENT ON COLUMN ai_agent_run.error_message IS '失败时的错误信息';
COMMENT ON COLUMN ai_agent_run.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN ai_agent_run.created_at IS '开始创建时间';
COMMENT ON COLUMN ai_agent_run.finished_at IS '运行完成时间';

COMMENT ON TABLE ai_tool_call IS 'AI Agent 工具调用记录表';
COMMENT ON COLUMN ai_tool_call.id IS '工具调用主键';
COMMENT ON COLUMN ai_tool_call.run_id IS '所属 Agent 运行记录 ID';
COMMENT ON COLUMN ai_tool_call.tool_name IS '工具名称';
COMMENT ON COLUMN ai_tool_call.request_json IS '工具调用请求参数';
COMMENT ON COLUMN ai_tool_call.response_json IS '工具调用响应结果';
COMMENT ON COLUMN ai_tool_call.success IS '是否调用成功';
COMMENT ON COLUMN ai_tool_call.latency_ms IS '调用耗时，单位毫秒';
COMMENT ON COLUMN ai_tool_call.created_at IS '创建时间';

COMMENT ON TABLE ai_knowledge_source IS 'AI 知识库来源表';
COMMENT ON COLUMN ai_knowledge_source.id IS '知识来源主键';
COMMENT ON COLUMN ai_knowledge_source.source_type IS '来源类型，例如 COURSE、DOCUMENT、URL';
COMMENT ON COLUMN ai_knowledge_source.source_id IS '来源业务 ID';
COMMENT ON COLUMN ai_knowledge_source.title IS '知识来源标题';
COMMENT ON COLUMN ai_knowledge_source.source_url IS '知识来源地址';
COMMENT ON COLUMN ai_knowledge_source.content_hash IS '内容哈希，用于判断内容是否变化';
COMMENT ON COLUMN ai_knowledge_source.version IS '内容版本号';
COMMENT ON COLUMN ai_knowledge_source.status IS '数据状态，例如 ACTIVE、DISABLED、DELETED';
COMMENT ON COLUMN ai_knowledge_source.indexed_at IS '最后完成向量索引的时间';
COMMENT ON COLUMN ai_knowledge_source.updated_at IS '最后更新时间';

COMMENT ON TABLE vector_store IS 'Spring AI PGVector 向量存储表';
COMMENT ON COLUMN vector_store.id IS '向量记录主键';
COMMENT ON COLUMN vector_store.content IS '用于检索的文本内容';
COMMENT ON COLUMN vector_store.metadata IS '文本来源及业务元数据';
COMMENT ON COLUMN vector_store.embedding IS '文本嵌入向量，固定为 1024 维';

COMMENT ON TABLE ai_approval_task IS 'AI 高风险操作人工审批任务表';
COMMENT ON COLUMN ai_approval_task.id IS '审批任务主键';
COMMENT ON COLUMN ai_approval_task.run_id IS '所属 Agent 运行记录 ID';
COMMENT ON COLUMN ai_approval_task.user_id IS '审批任务所属用户 ID';
COMMENT ON COLUMN ai_approval_task.action_type IS '待审批操作类型';
COMMENT ON COLUMN ai_approval_task.action_payload IS '待审批操作参数';
COMMENT ON COLUMN ai_approval_task.reason IS '发起审批的原因';
COMMENT ON COLUMN ai_approval_task.status IS '审批状态，例如 PENDING、APPROVED、REJECTED、EXPIRED';
COMMENT ON COLUMN ai_approval_task.expires_at IS '审批任务过期时间';
COMMENT ON COLUMN ai_approval_task.decided_at IS '审批决定时间';
COMMENT ON COLUMN ai_approval_task.created_at IS '创建时间';

COMMENT ON TABLE ai_inbox_event IS 'AI 服务消息收件箱事件表';
COMMENT ON COLUMN ai_inbox_event.event_id IS '事件唯一 ID，用于幂等处理';
COMMENT ON COLUMN ai_inbox_event.event_type IS '事件类型';
COMMENT ON COLUMN ai_inbox_event.payload IS '事件消息内容';
COMMENT ON COLUMN ai_inbox_event.status IS '处理状态，例如 PENDING、PROCESSED、FAILED';
COMMENT ON COLUMN ai_inbox_event.retry_count IS '失败重试次数';
COMMENT ON COLUMN ai_inbox_event.created_at IS '事件接收时间';
COMMENT ON COLUMN ai_inbox_event.processed_at IS '事件处理完成时间';

COMMENT ON TABLE ai_eval_case IS 'AI 能力评测用例表';
COMMENT ON COLUMN ai_eval_case.id IS '评测用例主键';
COMMENT ON COLUMN ai_eval_case.case_type IS '评测类型';
COMMENT ON COLUMN ai_eval_case.question IS '评测问题或输入内容';
COMMENT ON COLUMN ai_eval_case.expected_json IS '期望结果及断言配置';
COMMENT ON COLUMN ai_eval_case.enabled IS '是否启用该评测用例';
COMMENT ON COLUMN ai_eval_case.created_at IS '创建时间';

COMMENT ON TABLE ai_eval_result IS 'AI 能力评测结果表';
COMMENT ON COLUMN ai_eval_result.id IS '评测结果主键';
COMMENT ON COLUMN ai_eval_result.case_id IS '关联评测用例 ID';
COMMENT ON COLUMN ai_eval_result.model_name IS '参与评测的模型名称';
COMMENT ON COLUMN ai_eval_result.answer IS '模型实际回答';
COMMENT ON COLUMN ai_eval_result.metrics IS '准确率、相关性和耗时等评测指标';
COMMENT ON COLUMN ai_eval_result.passed IS '是否通过评测';
COMMENT ON COLUMN ai_eval_result.created_at IS '评测时间';

COMMENT ON TABLE ai_learning_plan IS 'AI 个性化学习计划表';
COMMENT ON COLUMN ai_learning_plan.id IS '学习计划主键';
COMMENT ON COLUMN ai_learning_plan.user_id IS '用户 ID';
COMMENT ON COLUMN ai_learning_plan.goal IS '用户学习目标';
COMMENT ON COLUMN ai_learning_plan.status IS '计划状态，例如 DRAFT、ACTIVE、COMPLETED、CANCELLED';
COMMENT ON COLUMN ai_learning_plan.plan_json IS '结构化学习计划内容';
COMMENT ON COLUMN ai_learning_plan.created_at IS '创建时间';
COMMENT ON COLUMN ai_learning_plan.updated_at IS '最后更新时间';
