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
    title: '课程检索与问答',
    tag: 'RAG',
    text: '根据已上架课程、章节、知识库内容回答，并返回可追溯依据。',
    prompt: '我想学习睡眠改善和低强度运动，帮我检索平台里适合的课程，并说明依据。'
  },
  {
    icon: 'Aim',
    title: '目标选课推荐',
    tag: '推荐',
    text: '围绕学习目标生成课程组合，标记已拥有、购物车状态和推荐理由。',
    prompt: '我有两个月时间准备 Java 后端面试，请推荐平台课程并按优先级排序。'
  },
  {
    icon: 'Notebook',
    title: '学习计划',
    tag: '计划',
    text: '把目标拆成阶段任务，后续可以在学习计划页维护完成进度。',
    prompt: '帮我制定一个 14 天的摄影入门学习计划，每天安排一个可完成的小任务。'
  },
  {
    icon: 'User',
    title: '个人学习查询',
    tag: '个人',
    text: '查询我的资料、最近订单、购物车，并结合当前状态给建议。',
    prompt: '查询我的最近订单和购物车，帮我判断接下来应该先学哪门课。'
  },
  {
    icon: 'ShoppingCart',
    title: '购物车确认操作',
    tag: '审批',
    text: '加入或移除购物车会生成确认任务，批准后才真正执行。',
    prompt: '帮我把最适合睡前放松的课程加入购物车，执行前请让我确认。'
  }
];

const adminCapabilities = [
  {
    icon: 'DataAnalysis',
    title: 'RAG 评测',
    tag: '运营',
    text: '查看评测结果、命中情况与回答质量，便于定位知识库效果。',
    path: '/ai/admin/evaluation'
  },
  {
    icon: 'Files',
    title: '知识库状态',
    tag: '知识',
    text: '查看课程知识源索引状态，失败来源可以重试。',
    path: '/ai/admin/evaluation'
  },
  {
    icon: 'Operation',
    title: '工具调用审计',
    tag: '审计',
    text: '跟踪 AI 调用业务工具的输入、输出、状态与 traceId。',
    path: '/ai/admin/evaluation'
  },
  {
    icon: 'Refresh',
    title: '重建索引确认',
    tag: '高风险',
    text: '知识库重建属于管理员操作，会进入待确认流程。',
    prompt: '请发起重建知识库索引操作，并说明影响范围。'
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
    <AiPageHeader title="AI 工作台" description="按任务入口使用 AI，普通对话、业务工具、确认流程和运营能力分开呈现"/>

    <section class="summary-band">
      <div>
        <span class="eyebrow">当前账号可用</span>
        <h3>从一个问题开始，也可以直接进入具体任务</h3>
      </div>
      <div class="summary-actions">
        <el-button type="primary" icon="ChatLineRound" @click="openChat('帮我介绍一下当前账号可以使用哪些 AI 能力。')">
          开始对话
        </el-button>
        <el-button icon="CircleCheck" @click="openPath('/ai/approvals')">待确认操作</el-button>
        <el-button icon="Notebook" @click="openPath('/ai/plans')">学习计划</el-button>
      </div>
    </section>

    <section class="capability-section">
      <div class="section-head">
        <h3>用户侧能力</h3>
        <span>面向选课、学习、个人状态和安全确认</span>
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
        <span>面向知识库、评测、工具调用与高风险操作</span>
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.admin-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
