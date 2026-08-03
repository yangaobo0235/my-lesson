<script setup>
import {computed} from 'vue';
import {useRouter} from 'vue-router';
import AiPageHeader from './AiPageHeader.vue';
import {getLoginRoles, isAiAdmin} from '../../util/auth.js';

const router = useRouter();
const roles = getLoginRoles();
const aiAdmin = computed(() => isAiAdmin(roles));

const userCapabilities = [
  {
    icon: 'Search',
    title: '知识问答',
    tag: 'RAG',
    text: '通过课程与课程分集的混合检索回答问题，证据不足时明确拒答。',
    prompt: '我想学习睡眠改善和低强度运动，帮我检索平台里适合的课程，并说明依据。'
  },
  {
    icon: 'Aim',
    title: '课程推荐',
    tag: '推荐',
    text: '按学习目标检索课程，结合当前用户状态输出优先级、理由与课程引用。',
    prompt: '我有两个月时间准备 Java 后端面试，请推荐平台课程并按优先级排序。'
  },
  {
    icon: 'Notebook',
    title: '学习计划',
    tag: 'Graph',
    text: '通过 Graph、Java 硬规则和 Designer/Reviewer 生成版本化待确认草案。',
    prompt: '帮我制定一个 14 天的摄影入门学习计划，每天安排一个可完成的小任务。'
  },
  {
    icon: 'User',
    title: '个人学习查询',
    tag: '个人',
    text: '只读查询当前用户的资料、最近订单、购物车和已有学习计划。',
    prompt: '查询我的最近订单和购物车，帮我判断接下来应该先学哪门课。'
  }
];

const adminCapabilities = [
  {
    icon: 'DataAnalysis',
    title: 'RAG 评测',
    tag: '运营',
    text: '运行 240 条 RAG、Tool、安全与拒答用例，并查看失败明细。',
    path: '/ai/admin/evaluation'
  },
  {
    icon: 'Files',
    title: '知识库状态',
    tag: '知识',
    text: '查看课程与课程分集的增量索引状态，失败来源可以重试。',
    path: '/ai/admin/evaluation'
  }
];

const quickPrompts = [
  '我睡眠不好又想开始运动，平台有哪些课程适合？',
  '帮我找 15 分钟以内、适合碎片时间学习的课程。',
  '查询我的购物车，并建议哪些课程可以先保留。',
  '根据我最近订单生成一份 7 天学习安排。'
];

function openChat(prompt) {
  router.push({path: '/ai/chat', query: {prompt}});
}

function openPath(path) {
  router.push(path);
}
</script>

<template>
  <div class="ai-overview">
    <AiPageHeader title="AI 工作台" description="知识问答、课程推荐、个人查询和学习计划四个场景"/>

    <section class="summary-band">
      <div>
        <span class="eyebrow">当前账号可用</span>
        <h3>从一个问题开始，也可以直接进入具体任务</h3>
      </div>
      <div class="summary-actions">
        <el-button type="primary" icon="ChatLineRound" @click="openChat('帮我介绍一下当前账号可以使用哪些 AI 能力。')">
          开始对话
        </el-button>
        <el-button icon="Notebook" @click="openPath('/ai/plans')">学习计划</el-button>
      </div>
    </section>

    <section class="capability-section">
      <div class="section-head">
        <h3>用户侧能力</h3>
        <span>轻量模型路由，主模型按场景调用最小只读工具集</span>
      </div>
      <div class="capability-grid">
        <article v-for="item in userCapabilities" :key="item.title" class="capability-card">
          <div class="card-head">
            <el-icon><component :is="item.icon"/></el-icon>
            <el-tag size="small" effect="plain">{{ item.tag }}</el-tag>
          </div>
          <h4>{{ item.title }}</h4>
          <p>{{ item.text }}</p>
          <button class="prompt-button" @click="openChat(item.prompt)">
            {{ item.prompt }}
          </button>
        </article>
      </div>
    </section>

    <section v-if="aiAdmin" class="capability-section">
      <div class="section-head">
        <h3>运营侧能力</h3>
        <span>课程知识同步与 240 条可执行评测</span>
      </div>
      <div class="capability-grid admin-grid">
        <article v-for="item in adminCapabilities" :key="item.title" class="capability-card">
          <div class="card-head">
            <el-icon><component :is="item.icon"/></el-icon>
            <el-tag size="small" effect="plain" type="warning">{{ item.tag }}</el-tag>
          </div>
          <h4>{{ item.title }}</h4>
          <p>{{ item.text }}</p>
          <el-button v-if="item.path" text type="primary" @click="openPath(item.path)">进入页面</el-button>
          <button v-else class="prompt-button" @click="openChat(item.prompt)">
            {{ item.prompt }}
          </button>
        </article>
      </div>
    </section>

    <section class="prompt-section">
      <div class="section-head">
        <h3>常用提问</h3>
        <span>点击后会带入 AI 对话输入框</span>
      </div>
      <div class="prompt-list">
        <button v-for="prompt in quickPrompts" :key="prompt" @click="openChat(prompt)">
          {{ prompt }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.ai-overview {
  display: grid;
  gap: 18px;
}

.summary-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--ml-border);
  border-radius: 8px;
  background: var(--ml-surface);
  box-shadow: var(--ml-shadow);
}

.eyebrow {
  display: block;
  margin-bottom: 6px;
  color: var(--ml-primary);
  font-size: 12px;
  font-weight: 700;
}

h3, h4, p {
  margin: 0;
}

.summary-band h3 {
  color: var(--ml-text);
  font-size: 18px;
}

.summary-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.capability-section,
.prompt-section {
  display: grid;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  color: var(--ml-text);
  font-size: 17px;
}

.section-head span {
  color: var(--ml-muted);
  font-size: 12px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.admin-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.capability-card {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  gap: 10px;
  min-height: 210px;
  padding: 14px;
  border: 1px solid var(--ml-border);
  border-radius: 8px;
  background: var(--ml-surface);
  box-shadow: var(--ml-shadow);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-head .el-icon {
  color: var(--ml-primary);
  font-size: 20px;
}

.capability-card h4 {
  color: var(--ml-text);
  font-size: 15px;
}

.capability-card p {
  color: var(--ml-muted);
  line-height: 1.6;
}

.prompt-button,
.prompt-list button {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--ml-border);
  border-radius: 6px;
  background: var(--ml-surface-soft);
  color: var(--ml-text);
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
}

.prompt-button:hover,
.prompt-list button:hover {
  border-color: var(--ml-primary);
  color: var(--ml-primary);
}

.prompt-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 1400px) {
  .capability-grid,
  .admin-grid,
  .prompt-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .summary-band,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-actions {
    justify-content: flex-start;
  }

  .capability-grid,
  .admin-grid,
  .prompt-list {
    grid-template-columns: 1fr;
  }
}
</style>
