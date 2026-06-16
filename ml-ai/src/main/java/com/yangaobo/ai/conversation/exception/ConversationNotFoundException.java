package com.yangaobo.ai.conversation.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation does not exist or does not belong to current user");
    }
}
