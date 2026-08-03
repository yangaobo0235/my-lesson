package com.yangaobo.ai.conversation.model;

public enum ConversationEventType {
    RUN_STARTED("run_started"),
    INTENT_DETECTED("intent_detected"),
    AGENT_SELECTED("agent_selected"),
    AGENT_STARTED("agent_started"),
    AGENT_COMPLETED("agent_completed"),
    WORKFLOW_NODE_STARTED("workflow_node_started"),
    WORKFLOW_NODE_COMPLETED("workflow_node_completed"),
    WORKFLOW_WAITING_CONFIRMATION("workflow_waiting_confirmation"),
    RETRIEVAL_STARTED("retrieval_started"),
    RETRIEVAL_COMPLETED("retrieval_completed"),
    TOOL_STARTED("tool_started"),
    TOOL_COMPLETED("tool_completed"),
    ANSWER_DELTA("answer_delta"),
    CITATION("citation"),
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
