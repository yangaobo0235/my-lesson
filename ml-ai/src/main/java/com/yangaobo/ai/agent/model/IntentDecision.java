package com.yangaobo.ai.agent.model;

public record IntentDecision(
        UserIntent intent,
        double confidence,
        String reason
) {

    public IntentDecision normalized() {
        UserIntent safeIntent = intent == null
                ? UserIntent.OUT_OF_SCOPE
                : intent;
        double safeConfidence = Math.max(0.0, Math.min(1.0, confidence));
        String safeReason = reason == null || reason.isBlank()
                ? "未提供分类理由"
                : reason.trim();
        return new IntentDecision(
                safeIntent,
                safeConfidence,
                safeReason);
    }
}
