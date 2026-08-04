import {AI_AXIOS} from './index.js';
import {GATEWAY_HOST} from '../const/index.js';
import {parseSseBlock} from './sse.js';

const AI_BASE = '/api/v1/ai';
export const AI_EVENT_TYPES = [
    'run_started', 'intent_detected', 'agent_selected',
    'agent_started', 'agent_completed', 'workflow_node_started',
    'workflow_node_completed', 'workflow_waiting_confirmation', 'retrieval_started',
    'retrieval_completed', 'tool_started', 'tool_completed',
    'answer_delta', 'citation',
    'run_completed', 'run_failed'
];
const body = response => response.data;

export const aiApi = {
    createConversation: title => AI_AXIOS.post(`${AI_BASE}/conversations`, title ? {title} : {}).then(body),
    conversations: () => AI_AXIOS.get(`${AI_BASE}/conversations`).then(body),
    messages: (id, limit = 100) => AI_AXIOS.get(`${AI_BASE}/conversations/${id}/messages`, {params: {limit}}).then(body),
    sendMessage: (id, message, requestId) => AI_AXIOS.post(`${AI_BASE}/conversations/${id}/messages`, {message, requestId}).then(body),
    deleteConversation: id => AI_AXIOS.delete(`${AI_BASE}/conversations/${id}`),
    recommendCourses: (goal, limit = 5) => AI_AXIOS.post(`${AI_BASE}/course-recommendations`, {goal, limit}).then(body),
    createLearningPlanDraft: (payload, requestId) => AI_AXIOS.post(
        `${AI_BASE}/learning-plan-drafts`, {...payload, requestId}
    ).then(body),
    plans: () => AI_AXIOS.get(`${AI_BASE}/plans`).then(body),
    learningPlanDrafts: () => AI_AXIOS.get(`${AI_BASE}/learning-plan-drafts`).then(body),
    learningPlanDraftVersions: id => AI_AXIOS.get(
        `${AI_BASE}/learning-plan-drafts/${id}/versions`
    ).then(body),
    adjustLearningPlanDraft: (id, adjustment, requestId) => AI_AXIOS.post(
        `${AI_BASE}/learning-plan-drafts/${id}/adjustments`,
        {adjustment, requestId}
    ).then(body),
    confirmLearningPlanDraft: id => AI_AXIOS.post(
        `${AI_BASE}/learning-plan-drafts/${id}/confirm`
    ).then(body),
    cancelLearningPlanDraft: id => AI_AXIOS.post(
        `${AI_BASE}/learning-plan-drafts/${id}/cancel`
    ),
    updatePlanProgress: (id, progressPercent, note) => AI_AXIOS.patch(
        `${AI_BASE}/plans/${id}/progress`,
        {progressPercent, note}
    ).then(body),
    knowledgeStatus: () => AI_AXIOS.get(`${AI_BASE}/admin/knowledge/status`).then(body),
    knowledgeSources: params => AI_AXIOS.get(`${AI_BASE}/admin/knowledge/sources`, {params}).then(body),
    retryKnowledgeSource: (sourceType, sourceId) => AI_AXIOS.post(
        `${AI_BASE}/admin/knowledge/sources/${encodeURIComponent(sourceType)}/${encodeURIComponent(sourceId)}/retry`
    ).then(body),
    evaluationSummary: () => AI_AXIOS.get(`${AI_BASE}/admin/evaluation/summary`).then(body),
    evaluationResults: (limit = 100) => AI_AXIOS.get(`${AI_BASE}/admin/evaluation/results`, {params: {limit}}).then(body),
    runEvaluation: mode => AI_AXIOS.post(
        `${AI_BASE}/admin/evaluations/run`, null, {params: {mode}}
    ).then(body),
    evaluationReport: id => AI_AXIOS.get(
        `${AI_BASE}/admin/evaluations/${id}`
    ).then(body),
    runTimeline: id => AI_AXIOS.get(`${AI_BASE}/runs/${id}/timeline`).then(body),
    retrievalTrace: id => AI_AXIOS.get(`${AI_BASE}/runs/${id}/retrieval-trace`).then(body),
    toolCalls: params => AI_AXIOS.get(`${AI_BASE}/admin/tools/calls`, {params}).then(body)
};

export function openConversationStream(conversationId, handlers = {}) {
    let controller = null;
    let closed = false;
    let lastEventId = 0;
    let retryMillis = 500;
    const stream = {
        close: () => {
            closed = true;
            controller?.abort();
        }
    };

    const connect = async () => {
        controller = new AbortController();
        try {
            const token = sessionStorage.getItem('token') || '';
            const headers = {Accept: 'text/event-stream', token};
            if (lastEventId) headers['Last-Event-ID'] = String(lastEventId);
            const response = await fetch(
                `${GATEWAY_HOST}${AI_BASE}/conversations/${conversationId}/stream`,
                {headers, signal: controller.signal}
            );
            if (!response.ok || !response.body) {
                throw new Error(`SSE connection failed with HTTP ${response.status}`);
            }
            handlers.open?.();
            retryMillis = 500;
            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let pending = '';
            while (!closed) {
                const {done, value} = await reader.read();
                if (done) break;
                pending += decoder.decode(value, {stream: true});
                const blocks = pending.split(/\r?\n\r?\n/);
                pending = blocks.pop() || '';
                blocks.forEach(block => {
                    const event = parseSseBlock(block);
                    if (!event || !AI_EVENT_TYPES.includes(event.type)) return;
                    if (event.id) lastEventId = Math.max(lastEventId, event.id);
                    handlers.event?.(event.type, event.data);
                });
            }
        } catch (error) {
            if (error.name !== 'AbortError' && !closed) handlers.error?.(error);
        }
        if (!closed) {
            window.setTimeout(connect, retryMillis);
            retryMillis = Math.min(retryMillis * 2, 5000);
        }
    };
    void connect();
    return stream;
}
