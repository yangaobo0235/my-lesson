package com.yangaobo.ai.conversation.model;

public enum ConversationEventType {
    RUN_STARTED("run_started"),
    INTENT_DETECTED("intent_detected"),
    RETRIEVAL_STARTED("retrieval_started"),
    RETRIEVAL_COMPLETED("retrieval_completed"),
    TOOL_STARTED("tool_started"),
    TOOL_COMPLETED("tool_completed"),
    ANSWER_DELTA("answer_delta"),
    CITATION("citation"),
    APPROVAL_REQUIRED("approval_required"),
    RUN_COMPLETED("run_completed"),
    RUN_FAILED("run_failed");

    private final String eventName;

    ConversationEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
