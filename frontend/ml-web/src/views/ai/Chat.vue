<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ElMessage} from 'element-plus';
import AiPageHeader from './AiPageHeader.vue';
import {aiApi, openConversationStream} from '../../api/ai.js';

const route = useRoute();
const router = useRouter();
const conversations = ref([]);
const conversationId = ref('');
const messages = ref([]);
const input = ref('');
const sending = ref(false);
const streamState = ref('未连接');
const timeline = ref([]);
const liveAnswer = ref('');
const liveCitations = ref([]);
const traceId = ref('');
const runId = ref('');
const currentProfile = ref(null);
const toolStates = ref([]);
const evidenceNotice = ref('');
const retrievalTraces = ref([]);
const lastMessage = ref('');
const recommendationGoal = ref('');
const recommendation = ref(null);
const recommendationLoading = ref(false);
const messagePanel = ref();
let stream;

const currentConversation = computed(() =>
    conversations.value.find(item => item.id === conversationId.value));

function eventLabel(type, data) {
  const toolLabel = `工具：${data.toolName || '-'}`;
  const labels = {
    run_started: '开始处理',
    intent_detected: `识别意图：${data.intent || '-'}`,
    agent_selected: `选择 Agent：${data.displayName || data.agentName || '-'}`,
    agent_started: `${data.displayName || data.agentName || 'Agent'} 开始处理`,
    agent_completed: `${data.displayName || data.agentName || 'Agent'} 处理完成`,
    workflow_node_started: `工作流节点开始：${data.displayName || data.nodeName || '-'}`,
    workflow_node_completed: `${data.displayName || data.nodeName || '工作流节点'}${data.success === false ? '失败' : '完成'}`,
    workflow_waiting_confirmation: `学习计划待确认：${data.goal || data.draftId || '-'}`,
    retrieval_started: '开始检索',
    retrieval_completed: '检索完成',
    tool_started: `调用${toolLabel}`,
    tool_completed: `${toolLabel}完成`,
    run_completed: '回答完成',
    run_failed: `处理失败：${data.message || '请稍后重试'}`
  };
  return labels[type] || type;
}

function closeStream() {
  stream?.close();
  stream = null;
}

function connectStream() {
  closeStream();
  if (!conversationId.value) return;
  streamState.value = '连接中';
  stream = openConversationStream(conversationId.value, {
    open: () => streamState.value = '已连接',
    error: () => streamState.value = '连接异常',
    event: handleEvent
  });
}

function handleEvent(type, event) {
  const data = event.data || {};
  traceId.value = event.traceId || traceId.value;
  runId.value = event.runId || runId.value;
  if (type === 'agent_selected') {
    currentProfile.value = {
      name: data.profileName || data.agentName,
      version: data.profileVersion,
      displayName: data.displayName,
      conservative: data.conservative
    };
  }
  if (type === 'tool_started') {
    toolStates.value.push({
      key: `${data.toolName}-${event.timestamp}`,
      name: data.toolName,
      status: 'STARTED'
    });
  }
  if (type === 'tool_completed') {
    const pending = [...toolStates.value].reverse().find(
        item => item.name === data.toolName && item.status === 'STARTED');
    if (pending) pending.status = data.success === false ? 'FAILED' : (data.status || 'SUCCEEDED');
  }
  if (type === 'retrieval_completed') {
    evidenceNotice.value = Number(data.hitCount || 0) === 0 ? '资料不足，回答将拒答或仅使用已验证业务数据' : '';
  }
  if (type === 'workflow_waiting_confirmation' && data.terminationReason) {
    evidenceNotice.value = data.terminationReason === 'INSUFFICIENT_DATA'
        ? '候选课程资料不足，草案未进入确认'
        : `计划生成已降级：${data.terminationReason}`;
  }
  if (type === 'answer_delta') {
    liveAnswer.value += data.delta || '';
    scrollBottom();
    return;
  }
  if (type === 'citation') {
    liveCitations.value.push(data.citation);
    return;
  }
  timeline.value.push({type, label: eventLabel(type, data), timestamp: event.timestamp});
  if (type === 'run_completed') {
    sending.value = false;
    loadRunDetails();
    loadMessages().finally(() => {
      liveAnswer.value = '';
      liveCitations.value = [];
    });
  }
  if (type === 'run_failed') sending.value = false;
}

async function loadRunDetails() {
  if (!runId.value) return;
  try {
    const [savedTimeline, traces] = await Promise.all([
      aiApi.runTimeline(runId.value),
      aiApi.retrievalTrace(runId.value)
    ]);
    retrievalTraces.value = traces;
    if (savedTimeline.profileName) {
      currentProfile.value = {
        name: savedTimeline.profileName,
        version: savedTimeline.profileVersion,
        displayName: savedTimeline.profileName,
        conservative: savedTimeline.conservativeMode
      };
    }
  } catch {
    // Live SSE information remains available when persistence is delayed.
  }
}

async function loadConversations() {
  conversations.value = await aiApi.conversations();
  const requested = route.query.conversationId;
  if (requested && conversations.value.some(item => item.id === requested)) {
    await selectConversation(requested);
  } else if (!conversationId.value && conversations.value.length) {
    await selectConversation(conversations.value[0].id);
  }
}

async function createConversation() {
  const created = await aiApi.createConversation();
  conversations.value.unshift(created);
  await selectConversation(created.id);
}

async function selectConversation(id) {
  conversationId.value = id;
  await router.replace({path: '/ai/chat', query: {conversationId: id}});
  await loadMessages();
  connectStream();
}

async function loadMessages() {
  if (!conversationId.value) return;
  messages.value = await aiApi.messages(conversationId.value);
  await scrollBottom();
}

async function send(message = input.value) {
  const content = message.trim();
  if (!content || sending.value) return;
  if (!conversationId.value) await createConversation();
  if (!stream || stream.readyState === EventSource.CLOSED) connectStream();
  liveAnswer.value = '';
  liveCitations.value = [];
  timeline.value = [];
  toolStates.value = [];
  evidenceNotice.value = '';
  retrievalTraces.value = [];
  currentProfile.value = null;
  runId.value = '';
  lastMessage.value = content;
  input.value = '';
  sending.value = true;
  messages.value.push({id: crypto.randomUUID(), role: 'USER', content});
  try {
    const result = await aiApi.sendMessage(conversationId.value, content, crypto.randomUUID());
    traceId.value = result.traceId || '';
    runId.value = result.runId || '';
  } catch (error) {
    sending.value = false;
    ElMessage.error(error.response?.data?.message || '消息提交失败');
  }
  await scrollBottom();
}

async function recommendCourses() {
  const goal = (recommendationGoal.value || input.value || lastMessage.value).trim();
  if (!goal) {
    ElMessage.warning('先输入学习目标');
    return;
  }
  recommendationLoading.value = true;
  try {
    recommendation.value = await aiApi.recommendCourses(goal, 5);
    recommendationGoal.value = goal;
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课程推荐生成失败');
  } finally {
    recommendationLoading.value = false;
  }
}

function fillPrompt(prompt) {
  if (typeof prompt === 'string' && prompt.trim()) {
    input.value = prompt.trim();
  }
}

async function scrollBottom() {
  await nextTick();
  if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight;
}

watch(() => route.query.prompt, prompt => fillPrompt(prompt));

onMounted(async () => {
  const initialPrompt = route.query.prompt;
  try {
    await loadConversations();
    fillPrompt(initialPrompt);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载会话失败');
  }
});
onBeforeUnmount(closeStream);
</script>

<template>
  <div>
    <AiPageHeader title="AI 对话" description="课程问答、推荐、个人查询、购物车操作与学习计划统一在这里处理"/>
    <div class="chat-layout">
      <el-card class="conversation-panel">
        <template #header><div class="panel-title"><strong>会话</strong><el-button size="small" type="primary" @click="createConversation">新建</el-button></div></template>
        <div v-if="!conversations.length" class="empty-hint">暂无会话</div>
        <button v-for="item in conversations" :key="item.id" class="conversation-item"
                :class="{active: item.id === conversationId}" @click="selectConversation(item.id)">
          <strong>{{ item.title || '新对话' }}</strong>
          <small>{{ new Date(item.updatedAt).toLocaleString() }}</small>
        </button>
      </el-card>

      <el-card class="message-card">
        <template #header><div class="panel-title">
          <strong>{{ currentConversation?.title || '请选择会话' }}</strong>
          <div class="header-tags">
            <el-tag v-if="currentProfile" type="primary">
              {{ currentProfile.displayName || currentProfile.name }} · {{ currentProfile.version || 'v1' }}
            </el-tag>
            <el-tag v-if="currentProfile?.conservative" type="warning">保守路由</el-tag>
            <el-tag :type="streamState === '已连接' ? 'success' : 'info'">{{ streamState }}</el-tag>
          </div>
        </div></template>
        <el-alert v-if="evidenceNotice" :title="evidenceNotice" type="warning"
                  show-icon :closable="false" class="run-notice"/>
        <div ref="messagePanel" class="message-panel">
          <div v-for="message in messages" :key="message.id" class="message" :class="message.role.toLowerCase()">
            <div class="message-role">{{ message.role === 'USER' ? '你' : 'MyLesson AI' }}</div>
            <div class="message-content">{{ message.content }}</div>
            <div v-if="message.citations?.length" class="citation-list">
              <a v-for="citation in message.citations" :key="citation.sourceKey || citation.sourceUrl"
                 :href="citation.sourceUrl" target="_blank">{{ citation.title || citation.sourceKey }}</a>
            </div>
          </div>
          <div v-if="liveAnswer || sending" class="message assistant">
            <div class="message-role">MyLesson AI</div>
            <div class="message-content">{{ liveAnswer || '正在思考...' }}</div>
            <div class="citation-list">
              <a v-for="citation in liveCitations" :key="citation.sourceKey || citation.sourceUrl"
                 :href="citation.sourceUrl" target="_blank">{{ citation.title || citation.sourceKey }}</a>
            </div>
          </div>
        </div>
        <div class="composer">
          <el-input v-model="input" type="textarea" :rows="3" maxlength="2000"
                    placeholder="例如：帮我找适合睡前放松的课程，并说明推荐依据"
                    @keydown.ctrl.enter.prevent="send()"/>
          <div class="composer-actions">
            <span>Ctrl + Enter 发送</span>
            <el-button v-if="lastMessage && !sending" @click="send(lastMessage)">重试上一条</el-button>
            <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
          </div>
        </div>
      </el-card>

      <div class="side-column">
      <el-card class="recommend-card">
        <template #header><strong>AI 选课</strong></template>
        <el-input v-model="recommendationGoal" type="textarea" :rows="3" maxlength="200"
                  placeholder="例如：两个月准备 Java 后端面试"/>
        <el-button class="recommend-button" type="primary" :loading="recommendationLoading" @click="recommendCourses">
          生成推荐
        </el-button>
        <div v-if="recommendation" class="recommendation">
          <p class="recommend-summary">{{ recommendation.summary }}</p>
          <div v-for="course in recommendation.recommendedCourses" :key="course.courseId" class="course-card">
            <div class="course-head">
              <strong>{{ course.priority }}. {{ course.title }}</strong>
              <el-tag v-if="course.owned" type="success">已拥有</el-tag>
              <el-tag v-else-if="course.inCart" type="warning">购物车</el-tag>
            </div>
            <small>{{ course.category || '-' }} · {{ course.estimatedHours }} 小时</small>
            <p>{{ course.reason }}</p>
            <div class="citation-list">
              <a v-for="citation in course.citations" :key="citation.sourceType + citation.sourceId">
                {{ citation.title }}
              </a>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="timeline-card">
        <template #header><strong>执行时间线</strong></template>
        <el-timeline>
          <el-timeline-item v-for="item in timeline" :key="item.timestamp + item.type" :timestamp="item.timestamp">
            {{ item.label }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!timeline.length" description="发送消息后显示执行过程"/>
        <el-divider v-if="toolStates.length"/>
        <div v-if="toolStates.length" class="tool-status-list">
          <div v-for="tool in toolStates" :key="tool.key" class="tool-status">
            <span>{{ tool.name }}</span>
            <el-tag size="small" :type="tool.status === 'SUCCEEDED' ? 'success' : tool.status === 'FAILED' ? 'danger' : 'warning'">
              {{ tool.status }}
            </el-tag>
          </div>
        </div>
        <el-divider v-if="retrievalTraces.length"/>
        <div v-for="trace in retrievalTraces" :key="trace.id" class="retrieval-summary">
          <strong>RAG</strong>
          <span>向量 {{ trace.vectorCandidateCount }} · 关键词 {{ trace.keywordCandidateCount }} · 融合 {{ trace.fusedCandidateCount }}</span>
          <el-tag v-if="trace.rerankFallback" size="small" type="warning">Rerank 降级</el-tag>
          <el-tag v-if="trace.noAnswerReason" size="small" type="danger">{{ trace.noAnswerReason }}</el-tag>
        </div>
        <el-divider/>
        <div class="trace">
          <strong>runId</strong><code>{{ runId || '-' }}</code>
          <strong>traceId</strong><code>{{ traceId || '-' }}</code>
        </div>
      </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-layout { display:grid; grid-template-columns:220px minmax(420px,1fr) 280px; gap:16px; }
.side-column { display:grid; gap:16px; align-content:start; }
.panel-title { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.header-tags { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:6px; }
.run-notice { margin-bottom:10px; }
.conversation-item { width:100%; padding:12px; margin-bottom:8px; text-align:left; border:0; border-radius:8px; background:var(--el-fill-color-light); cursor:pointer; }
.conversation-item.active { color:var(--el-color-primary); background:var(--el-color-primary-light-9); }
.conversation-item strong,.conversation-item small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.conversation-item small { margin-top:5px; color:var(--el-text-color-secondary); }
.message-panel { height:calc(100vh - 390px); min-height:360px; padding:4px 10px; overflow-y:auto; }
.message { max-width:82%; margin:14px 0; } .message.user { margin-left:auto; }
.message-role { margin-bottom:5px; color:var(--el-text-color-secondary); font-size:12px; }
.message-content { padding:12px 14px; line-height:1.7; white-space:pre-wrap; border-radius:12px; background:var(--el-fill-color-light); }
.message.user .message-content { color:white; background:var(--el-color-primary); }
.citation-list { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
.citation-list a { color:var(--el-color-primary); font-size:12px; }
.composer { border-top:1px solid var(--el-border-color-lighter); padding-top:14px; }
.composer-actions { display:flex; align-items:center; justify-content:flex-end; gap:10px; margin-top:10px; color:var(--el-text-color-secondary); font-size:12px; }
.recommend-button { width:100%; margin-top:10px; }
.recommendation { display:grid; gap:10px; margin-top:12px; }
.recommend-summary { margin:0; color:var(--el-text-color-secondary); line-height:1.6; }
.course-card { padding:10px; border:1px solid var(--el-border-color-lighter); border-radius:8px; }
.course-head { display:flex; align-items:flex-start; justify-content:space-between; gap:8px; }
.course-card small { display:block; margin-top:4px; color:var(--el-text-color-secondary); }
.course-card p { margin:8px 0; line-height:1.6; }
.trace { display:grid; gap:8px; } .trace code { overflow-wrap:anywhere; }
.tool-status-list { display:grid; gap:8px; }
.tool-status { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.tool-status small { color:var(--el-text-color-secondary); }
.retrieval-summary { display:grid; gap:6px; color:var(--el-text-color-secondary); font-size:12px; }
@media (max-width:1250px) { .chat-layout { grid-template-columns:200px 1fr; } .side-column { grid-column:1 / -1; } }
</style>
