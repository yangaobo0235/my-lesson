from scripts.run_external_quality_evaluation import (
    failure_samples,
    quality_gates,
    tool_confusion_matrix,
)


def result(expected: str, actual: str, passed: bool) -> dict[str, object]:
    return {
        "caseId": f"{expected}-{actual}",
        "caseType": "TOOL",
        "dimension": "routing",
        "question": "question",
        "passed": passed,
        "metrics": {
            "expectedIntent": expected,
            "actualIntent": actual,
        },
    }


def test_tool_report_contains_confusion_matrix_and_failures() -> None:
    results = [
        result("COURSE_SEARCH", "COURSE_SEARCH", True),
        result("KNOWLEDGE_QA", "COURSE_SEARCH", False),
    ]

    matrix = tool_confusion_matrix(results)

    assert matrix["COURSE_SEARCH"]["COURSE_SEARCH"] == 1
    assert matrix["KNOWLEDGE_QA"]["COURSE_SEARCH"] == 1
    assert failure_samples(results)[0]["caseId"] == "KNOWLEDGE_QA-COURSE_SEARCH"


def test_tool_quality_gate_has_explicit_thresholds() -> None:
    summary = {
        "categories": {
            "TOOL": {
                "passRate": 0.78,
                "intentMatchRate": 0.81,
                "routeMatchRate": 0.80,
                "argumentsValidRate": 0.99,
                "errors": 0,
            }
        }
    }

    gate = quality_gates(summary)

    assert gate["passed"] is True
    assert gate["thresholds"]["intentMatchRate"] == 0.80
