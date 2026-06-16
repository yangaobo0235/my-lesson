package com.yangaobo.ai.conversation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ai.conversation")
public class ConversationProperties {

    private int recentMessageLimit = 12;
    private int summaryBatchSize = 200;
    private int messageListLimit = 100;
    private int answerDeltaSize = 48;
    private Duration lockTtl = Duration.ofMinutes(2);
    private Duration streamTimeout = Duration.ofMinutes(30);

    public int getRecentMessageLimit() {
        return recentMessageLimit;
    }

    public void setRecentMessageLimit(int recentMessageLimit) {
        this.recentMessageLimit = recentMessageLimit;
    }

    public int getSummaryBatchSize() {
        return summaryBatchSize;
    }

    public void setSummaryBatchSize(int summaryBatchSize) {
        this.summaryBatchSize = summaryBatchSize;
    }

    public int getMessageListLimit() {
        return messageListLimit;
    }

    public void setMessageListLimit(int messageListLimit) {
        this.messageListLimit = messageListLimit;
    }

    public int getAnswerDeltaSize() {
        return answerDeltaSize;
    }

    public void setAnswerDeltaSize(int answerDeltaSize) {
        this.answerDeltaSize = answerDeltaSize;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getStreamTimeout() {
        return streamTimeout;
    }

    public void setStreamTimeout(Duration streamTimeout) {
        this.streamTimeout = streamTimeout;
    }
}
