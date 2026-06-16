<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue';
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
const liveApproval = ref(null);
const traceId = ref('');
const lastMessage = ref('');
const messagePanel = ref();
let stream;

const currentConversation = computed(() =>
    conversations.value.find(item => item.id === conversationId.value));

function eventLabel(type, data) {
  const labels = {
    run_started: '开始处理',
    intent_detected: `识别意图：${data.intent || '-'}`,
    retrieval_started: '开始检索',
    retrieval_completed: '检索完成',
    tool_started: `调用工具：${data.toolName || '-'}`,
    tool_completed: `工具完成：${data.toolName || '-'}`,
    approval_required: `等待确认：${data.actionType || '-'}`,
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
  if (type === 'answer_delta') {
    liveAnswer.value += data.delta || '';
    scrollBottom();
    return;
  }
  if (type === 'citation') {
    liveCitations.value.push(data.citation);
    return;
  }
  if (type === 'approval_required') liveApproval.value = data;
  timeline.value.push({type, label: eventLabel(type, data), timestamp: event.timestamp});
  if (type === 'run_completed') {
    sending.value = false;
    loadMessages();
  }
  if (type === 'run_failed') sending.value = false;
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
  liveApproval.value = null;
  timeline.value = [];
  lastMessage.value = content;
  input.value = '';
  sending.value = true;
  messages.value.push({id: crypto.randomUUID(), role: 'USER', content});
  try {
    const result = await aiApi.sendMessage(conversationId.value, content, crypto.randomUUID());
    traceId.value = result.traceId || '';
  } catch (error) {
    sending.value = false;
    ElMessage.error(error.response?.data?.message || '消息提交失败');
  }
  await scrollBottom();
}

async function decide(approved) {
  if (!liveApproval.value?.approvalId) return;
  await (approved ? aiApi.approve(liveApproval.value.approvalId) : aiApi.reject(liveApproval.value.approvalId));
  ElMessage.success(approved ? '已批准' : '已拒绝');
  liveApproval.value = null;
}

async function scrollBottom() {
  await nextTick();
  if (messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight;
}

onMounted(() => loadConversations().catch(error =>
    ElMessage.error(error.response?.data?.message || '加载会话失败')));
onBeforeUnmount(closeStream);
</script>

<template>
  <div>
    <AiPageHeader title="AI 对话" description="流式回答、引用、工具时间线与人工确认集中展示"/>
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
          <el-tag :type="streamState === '已连接' ? 'success' : 'info'">{{ streamState }}</el-tag>
        </div></template>
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
        <div v-if="liveApproval" class="approval-box">
          <div><strong>需要你的确认</strong><p>{{ liveApproval.reason || liveApproval.actionType }}</p></div>
          <el-button type="success" @click="decide(true)">批准</el-button>
          <el-button type="danger" plain @click="decide(false)">拒绝</el-button>
        </div>
        <div class="composer">
          <el-input v-model="input" type="textarea" :rows="3" maxlength="2000"
                    placeholder="输入课程咨询、个人查询或学习计划目标"
                    @keydown.ctrl.enter.prevent="send()"/>
          <div class="composer-actions">
            <span>Ctrl + Enter 发送</span>
            <el-button v-if="lastMessage && !sending" @click="send(lastMessage)">重试上一条</el-button>
            <el-button type="primary" :loading="sending" @click="send()">发送</el-button>
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
        <el-divider/>
        <div class="trace"><strong>traceId</strong><code>{{ traceId || '-' }}</code></div>
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-layout { display:grid; grid-template-columns:220px minmax(420px,1fr) 280px; gap:16px; }
.panel-title { display:flex; align-items:center; justify-content:space-between; gap:10px; }
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
.approval-box { display:flex; align-items:center; gap:10px; padding:12px; margin:8px 0; border:1px solid var(--el-color-warning); border-radius:10px; }
.approval-box div { flex:1; } .approval-box p { margin:5px 0 0; color:var(--el-text-color-secondary); }
.composer { border-top:1px solid var(--el-border-color-lighter); padding-top:14px; }
.composer-actions { display:flex; align-items:center; justify-content:flex-end; gap:10px; margin-top:10px; color:var(--el-text-color-secondary); font-size:12px; }
.trace { display:grid; gap:8px; } .trace code { overflow-wrap:anywhere; }
@media (max-width:1250px) { .chat-layout { grid-template-columns:200px 1fr; } .timeline-card { grid-column:1 / -1; } }
</style>
