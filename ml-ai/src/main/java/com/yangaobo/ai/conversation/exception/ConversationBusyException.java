package com.yangaobo.ai.conversation.exception;

public class ConversationBusyException extends RuntimeException {

    public ConversationBusyException() {
        super("Conversation is processing another request");
    }
}
