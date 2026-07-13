package com.yangaobo.ai.agent.service;

import com.yangaobo.ai.agent.model.AgentProfile;
import com.yangaobo.ai.agent.model.AgentRoute;
import com.yangaobo.ai.agent.model.AgentSelection;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AgentOrchestrator {

    private final IntentRoutingPolicy routingPolicy;
    private final Map<UserIntent, AgentProfile> profiles;

    public AgentOrchestrator(IntentRoutingPolicy routingPolicy) {
        this.routingPolicy = routingPolicy;
        this.profiles = profiles();
    }

    public AgentSelection select(IntentDecision decision) {
        IntentDecision normalized = decision.normalized();
        AgentRoute route = routingPolicy.route(normalized);
        AgentProfile profile = route.conservative()
                ? profiles.get(UserIntent.KNOWLEDGE_QA)
                : profiles.getOrDefault(
                        normalized.intent(),
                        profiles.get(UserIntent.OUT_OF_SCOPE));
        return new AgentSelection(profile, route);
    }

    private Map<UserIntent, AgentProfile> profiles() {
        Map<UserIntent, AgentProfile> result =
                new EnumMap<>(UserIntent.class);
        result.put(
                UserIntent.KNOWLEDGE_QA,
                new AgentProfile(
                        "knowledge_qa_agent",
                        "知识问答 Agent",
                        "负责课程知识解释、RAG 引用和学习资料问答",
                        """
                        你是知识问答 Agent。优先解释课程知识点、学习方法和平台资料。
                        回答事实结论时必须结合知识资料编号；资料不足时明确说明不足。
                        如果用户需要业务数据，只能调用允许的只读工具补充事实。
                        """));
        result.put(
                UserIntent.COURSE_SEARCH,
                new AgentProfile(
                        "course_recommendation_agent",
                        "课程推荐 Agent",
                        "负责课程搜索、课程详情分析和选课建议",
                        """
                        你是课程推荐 Agent。围绕用户目标搜索课程、比较课程适配度，
                        给出优先级、推荐理由和下一步学习建议。
                        不得编造课程，推荐前必须使用课程工具或知识资料确认课程存在。
                        """));
        result.put(
                UserIntent.PERSONAL_QUERY,
                new AgentProfile(
                        "personal_query_agent",
                        "个人查询 Agent",
                        "负责当前用户资料、订单、购物车和已有学习计划查询",
                        """
                        你是个人查询 Agent。只查询当前登录用户自己的资料、订单、购物车和学习计划。
                        不得要求或猜测 userId，不得返回其他用户数据。
                        结合查询结果给出简短、可执行的学习建议。
                        """));
        result.put(
                UserIntent.CART_ACTION,
                new AgentProfile(
                        "cart_action_agent",
                        "购物车操作 Agent",
                        "负责加入或移除购物车，并确保写操作进入确认流程",
                        """
                        你是购物车操作 Agent。执行加入或移除购物车前，必须确认目标课程。
                        写操作只允许通过工具创建审批任务，不得宣称已直接完成。
                        如果出现待确认操作，立即停止继续写入并提示用户确认。
                        """));
        result.put(
                UserIntent.LEARNING_PLAN,
                new AgentProfile(
                        "learning_plan_agent",
                        "学习计划 Agent",
                        "负责学习目标拆解、计划草案、计划查询和进度建议",
                        """
                        你是学习计划 Agent。先理解目标、每日可用时间和课程候选，
                        再生成或查询学习计划。创建正式计划前必须走审批确认。
                        对计划建议要具体到阶段、课程顺序和每日动作。
                        """));
        result.put(
                UserIntent.ADMIN_OPERATION,
                new AgentProfile(
                        "admin_operation_agent",
                        "运营管理 Agent",
                        "负责知识库重建等管理员操作",
                        """
                        你是运营管理 Agent。只处理管理员允许的知识库和评测运维操作。
                        高风险操作必须走审批流程，并说明影响范围和恢复方式。
                        """));
        result.put(
                UserIntent.OUT_OF_SCOPE,
                new AgentProfile(
                        "safety_agent",
                        "安全路由 Agent",
                        "负责拒绝 MyLesson 范围外请求",
                        """
                        你是安全路由 Agent。只处理 MyLesson 学习、课程、订单、购物车和学习计划相关问题。
                        对范围外请求要简短拒绝，并引导用户回到平台能力范围。
                        """));
        return Map.copyOf(result);
    }
}
