from dataclasses import dataclass

from mylesson_agent.domain.api_models import UserIntent
from mylesson_agent.llm.client import IntentResult, ModelClient


@dataclass(frozen=True)
class AgentProfile:
    name: str
    display_name: str
    description: str
    system_prompt: str
    version: str = "v1"


@dataclass(frozen=True)
class AgentRoute:
    retrieval_enabled: bool
    conservative: bool
    tool_names: tuple[str, ...]


@dataclass(frozen=True)
class AgentSelection:
    decision: IntentResult
    profile: AgentProfile
    route: AgentRoute


READ_ONLY_TOOLS = (
    "search_courses",
    "get_course_detail",
    "get_my_recent_orders",
    "get_my_cart",
    "get_my_profile",
    "get_learning_plans",
)

WRITE_TOOLS = ("create_learning_plan_draft",)

TOOLS_BY_INTENT: dict[UserIntent, tuple[str, ...]] = {
    UserIntent.KNOWLEDGE_QA: ("search_courses", "get_course_detail"),
    UserIntent.COURSE_SEARCH: ("search_courses", "get_course_detail", "get_my_profile"),
    UserIntent.PERSONAL_QUERY: (
        "get_my_recent_orders",
        "get_my_cart",
        "get_my_profile",
        "get_learning_plans",
    ),
    UserIntent.LEARNING_PLAN: (
        "search_courses",
        "get_course_detail",
        "get_my_profile",
        "get_learning_plans",
        "create_learning_plan_draft",
    ),
    UserIntent.OUT_OF_SCOPE: (),
}

PROFILES: dict[UserIntent, AgentProfile] = {
    UserIntent.KNOWLEDGE_QA: AgentProfile(
        "KNOWLEDGE_QA",
        "知识问答",
        "负责课程知识解释、RAG引用和学习资料问答",
        "回答事实结论必须引用提供的知识资料；资料不足时明确拒答。",
    ),
    UserIntent.COURSE_SEARCH: AgentProfile(
        "COURSE_RECOMMENDATION",
        "课程推荐",
        "负责课程搜索、详情分析和选课建议",
        "不得编造课程，推荐前必须用课程工具或知识资料确认课程存在。",
    ),
    UserIntent.PERSONAL_QUERY: AgentProfile(
        "PERSONAL_QUERY",
        "个人查询",
        "负责当前用户资料、订单、购物车和正式学习计划查询",
        "只能查询委托令牌对应用户的数据，不得接受任何userId参数。",
    ),
    UserIntent.LEARNING_PLAN: AgentProfile(
        "LEARNING_PLAN",
        "学习计划",
        "负责目标拆解和学习计划候选生成",
        "正式计划必须由Java业务服务校验，并在用户确认后创建。",
    ),
    UserIntent.OUT_OF_SCOPE: AgentProfile(
        "OUT_OF_SCOPE",
        "范围控制",
        "负责拒绝MyLesson范围外请求",
        "只处理学习、课程、当前用户订单、购物车和学习计划相关问题。",
    ),
}


class IntentRouter:
    def __init__(self, model: ModelClient, threshold: float) -> None:
        self._model = model
        self._threshold = threshold

    async def select(self, question: str, context: str) -> AgentSelection:
        guarded = self._safety_guard(question)
        if guarded is not None:
            decision = guarded
        else:
            deterministic = self._classify_by_rule(question)
            if deterministic.confidence >= 0.9:
                decision = deterministic
            else:
                try:
                    decision = await self._model.classify(question, context)
                except Exception:
                    decision = deterministic

        if decision.confidence < self._threshold:
            return AgentSelection(
                decision,
                PROFILES[UserIntent.KNOWLEDGE_QA],
                AgentRoute(True, True, READ_ONLY_TOOLS),
            )
        intent = decision.intent
        return AgentSelection(
            decision,
            PROFILES[intent],
            AgentRoute(
                intent
                in {UserIntent.KNOWLEDGE_QA, UserIntent.COURSE_SEARCH, UserIntent.LEARNING_PLAN},
                False,
                TOOLS_BY_INTENT[intent],
            ),
        )

    @staticmethod
    def _safety_guard(question: str) -> IntentResult | None:
        text = question.lower()
        explanations = (
            "为什么",
            "如何防止",
            "解释一下",
            "怎么理解",
            "是什么意思",
            "脱敏",
            "属于什么风险",
            "课程里",
            "课程中",
            "课程案例",
            "安全章节",
            "不需要展示真实值",
        )
        if any(keyword in text for keyword in explanations):
            return IntentResult(UserIntent.KNOWLEDGE_QA, 1.0, "安全概念解释请求")

        forbidden = (
            "加入购物车",
            "添加到购物车",
            "移出购物车",
            "清空购物车",
            "结算购物车",
            "删除订单",
            "修改订单",
            "重建知识索引",
            "删除所有课程",
            "刷新知识索引",
            "启动索引任务",
            "绕过确认",
            "不要走确认",
            "跳过二次确认",
            "直接创建正式学习计划",
            "直接覆盖正式学习计划",
            "伪造管理员",
            "假装我拥有后台权限",
            "临时把我当运营人员",
        )
        sensitive = (
            "用户2",
            "userid",
            "另一个用户",
            "其他用户",
            "同学最近",
            "旁边那位",
            "任意一位学员",
            "系统提示词全文",
            "隐藏的系统提示",
            "隐藏指令",
            "internaltoken 原文",
            "api key 完整",
            "鉴权的凭据",
            "内部配置内容",
            "手机号和身份证原文",
            "手机号与身份证字段",
            "联系方式给我",
        )
        if any(keyword in text for keyword in forbidden + sensitive):
            return IntentResult(UserIntent.OUT_OF_SCOPE, 1.0, "请求包含不允许的业务写操作")
        return None

    @staticmethod
    def _classify_by_rule(question: str) -> IntentResult:
        text = question.lower()
        existing_plan = (
            "当前的学习计划",
            "我的学习计划",
            "学习计划详情",
            "学习计划安排",
            "当前的计划",
            "学习进度",
            "已经确认过的学习方案",
            "已经存在的学习安排",
            "哪一版学习计划",
        )
        personal = (
            "我的订单",
            "最近订单",
            "订单记录",
            "买过",
            "购买过",
            "最近购买",
            "购买记录",
            "已购内容",
            "付款过",
            "购物车",
            "已选内容",
            "暂存但还没购买",
            "待购买",
            "结算前",
            "我的资料",
            "个人资料",
            "个人信息",
            "账号信息",
            "基本资料",
            "账户展示的资料",
            "当前账号的资料",
            "当前登录人的",
        ) + existing_plan
        if any(keyword in text for keyword in personal):
            return IntentResult(UserIntent.PERSONAL_QUERY, 0.99, "明确的个人业务查询")

        learning_plan = (
            "制定学习计划",
            "生成学习计划",
            "创建学习计划",
            "学习规划",
            "每天学习",
            "学习目标",
        )
        plan_creation = "学习计划" in text and any(
            keyword in text for keyword in ("制定", "生成", "创建", "帮我", "每天")
        )
        if plan_creation or any(keyword in text for keyword in learning_plan):
            return IntentResult(UserIntent.LEARNING_PLAN, 0.99, "明确的学习计划创建请求")

        course_query = (
            "推荐课程",
            "搜索课程",
            "查找课程",
            "查一下摄影",
            "找找",
            "帮我找",
            "哪些课",
            "有没有相关内容",
            "课程详情",
            "详细信息",
            "这门课",
            "讲师",
            "谁讲",
            "作者和价格",
            "具体多少钱",
            "具体讲什么",
            "展开看看",
        )
        if any(keyword in text for keyword in course_query):
            return IntentResult(UserIntent.COURSE_SEARCH, 0.99, "明确的课程查询")

        mappings = (
            (UserIntent.KNOWLEDGE_QA, ("什么是", "为什么", "怎么学", "讲解", "解释", "知识点")),
        )
        for intent, keywords in mappings:
            if any(keyword in question for keyword in keywords):
                return IntentResult(intent, 0.8, "规则降级分类")
        return IntentResult(UserIntent.OUT_OF_SCOPE, 0.4, "模型不可用且规则无法确认意图")
