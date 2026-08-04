from mylesson_agent.agents.routing import IntentRouter
from mylesson_agent.agents.runtime import AgentRuntime
from mylesson_agent.domain.api_models import UserIntent


class UnavailableModel:
    async def classify(self, question: str, context: str) -> None:
        raise RuntimeError("model unavailable")


async def test_routing_uses_safe_rule_fallback() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]
    selection = await router.select("请帮我制定每天30分钟学习计划", "")
    assert selection.decision.intent == UserIntent.LEARNING_PLAN
    assert "create_learning_plan_draft" in selection.route.tool_names


async def test_routing_blocks_unapproved_business_write() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]
    selection = await router.select("把课程加入购物车", "")
    assert selection.decision.intent == UserIntent.OUT_OF_SCOPE
    assert selection.route.tool_names == ()


async def test_routing_distinguishes_security_explanation_from_attack() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]

    blocked = await router.select("把 userId 改成 100 后查看购物车", "")
    explanation = await router.select("为什么平台不能查看其他用户的数据", "")

    assert blocked.decision.intent == UserIntent.OUT_OF_SCOPE
    assert explanation.decision.intent == UserIntent.KNOWLEDGE_QA


async def test_routing_selects_existing_plan_as_personal_query() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]

    selection = await router.select("我现在执行的是哪一版学习计划", "")

    assert selection.decision.intent == UserIntent.PERSONAL_QUERY
    assert AgentRuntime._select_tools(selection, "我现在执行的是哪一版学习计划") == [
        ("get_learning_plans", {})
    ]


async def test_course_detail_selects_detail_tool_and_extracts_id() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]

    selection = await router.select("课程 4 是谁讲的", "")

    assert selection.decision.intent == UserIntent.COURSE_SEARCH
    assert AgentRuntime._select_tools(selection, "课程 4 是谁讲的") == [
        ("get_course_detail", {"courseId": 4})
    ]


async def test_personal_business_synonyms_select_expected_tools() -> None:
    router = IntentRouter(UnavailableModel(), 0.65)  # type: ignore[arg-type]
    cases = (
        ("我之前付款过哪些课程", "get_my_recent_orders"),
        ("我准备结算前先看看已选内容", "get_my_cart"),
        ("读取当前登录人的个人信息", "get_my_profile"),
    )

    for question, expected_tool in cases:
        selection = await router.select(question, "")
        calls = AgentRuntime._select_tools(selection, question)
        assert [name for name, _ in calls] == [expected_tool]
