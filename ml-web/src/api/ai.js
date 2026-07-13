import {GATEWAY_AXIOS} from './index.js';
import {GATEWAY_HOST} from '../const/index.js';

const AI_BASE = '/api/v1/ai';
export const AI_EVENT_TYPES = [
    'run_started', 'intent_detected', 'agent_selected',
    'agent_started', 'agent_completed', 'workflow_node_started',
    'workflow_node_completed', 'workflow_waiting_approval', 'retrieval_started',
    'retrieval_completed', 'tool_started', 'tool_completed',
    'answer_delta', 'citation', 'approval_required',
    'run_completed', 'run_failed'
];
const body = response => response.data;

export const aiApi = {
    createConversation: title => GATEWAY_AXIOS.post(`${AI_BASE}/conversations`, title ? {title} : {}).then(body),
    conversations: () => GATEWAY_AXIOS.get(`${AI_BASE}/conversations`).then(body),
    messages: (id, limit = 100) => GATEWAY_AXIOS.get(`${AI_BASE}/conversations/${id}/messages`, {params: {limit}}).then(body),
    sendMessage: (id, message, requestId) => GATEWAY_AXIOS.post(`${AI_BASE}/conversations/${id}/messages`, {message, requestId}).then(body),
    deleteConversation: id => GATEWAY_AXIOS.delete(`${AI_BASE}/conversations/${id}`),
    recommendCourses: (goal, limit = 5) => GATEWAY_AXIOS.post(`${AI_BASE}/course-recommendations`, {goal, limit}).then(body),
    plans: () => GATEWAY_AXIOS.get(`${AI_BASE}/plans`).then(body),
    updatePlanProgress: (id, progressPercent, note) => GATEWAY_AXIOS.patch(
        `${AI_BASE}/plans/${id}/progress`,
        {progressPercent, note}
    ).then(body),
    approvals: () => GATEWAY_AXIOS.get(`${AI_BASE}/approvals`).then(body),
    approve: id => GATEWAY_AXIOS.post(`${AI_BASE}/approvals/${id}/approve`).then(body),
    reject: id => GATEWAY_AXIOS.post(`${AI_BASE}/approvals/${id}/reject`).then(body),
    knowledgeStatus: () => GATEWAY_AXIOS.get(`${AI_BASE}/admin/knowledge/status`).then(body),
    knowledgeSources: params => GATEWAY_AXIOS.get(`${AI_BASE}/admin/knowledge/sources`, {params}).then(body),
    retryKnowledgeSource: (sourceType, sourceId) => GATEWAY_AXIOS.post(
        `${AI_BASE}/admin/knowledge/sources/${encodeURIComponent(sourceType)}/${encodeURIComponent(sourceId)}/retry`
    ).then(body),
    evaluationSummary: () => GATEWAY_AXIOS.get(`${AI_BASE}/admin/evaluation/summary`).then(body),
    evaluationResults: (limit = 100) => GATEWAY_AXIOS.get(`${AI_BASE}/admin/evaluation/results`, {params: {limit}}).then(body),
    toolCalls: params => GATEWAY_AXIOS.get(`${AI_BASE}/admin/tools/calls`, {params}).then(body)
};

export function openConversationStream(conversationId, handlers = {}) {
    const token = sessionStorage.getItem('token') || '';
    const controller = new AbortController();
    const stream = {close: () => controller.abort()};

    fetch(`${GATEWAY_HOST}${AI_BASE}/conversations/${conversationId}/stream`, {
        headers: {
            Accept: 'text/event-stream',
            token
        },
        signal: controller.signal
    }).then(async response => {
        if (!response.ok || !response.body) {
            throw new Error(`SSE connection failed with HTTP ${response.status}`);
        }
        handlers.open?.();
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let pending = '';
        while (true) {
            const {done, value} = await reader.read();
            if (done) break;
            pending += decoder.decode(value, {stream: true});
            const blocks = pending.split(/\r?\n\r?\n/);
            pending = blocks.pop() || '';
            blocks.forEach(block => {
                let type = 'message';
                const data = [];
                block.split(/\r?\n/).forEach(line => {
                    if (line.startsWith('event:')) type = line.slice(6).trim();
                    if (line.startsWith('data:')) data.push(line.slice(5).trim());
                });
                if (!AI_EVENT_TYPES.includes(type) || !data.length) return;
                handlers.event?.(type, JSON.parse(data.join('\n')));
            });
        }
    }).catch(error => {
        if (error.name !== 'AbortError') {
            handlers.error?.(error);
        }
    });
    return stream;
}
