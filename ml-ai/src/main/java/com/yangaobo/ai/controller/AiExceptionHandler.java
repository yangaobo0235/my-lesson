package com.yangaobo.ai.controller;

import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.exception.DownstreamServiceException;
import com.yangaobo.ai.conversation.exception.ConversationBusyException;
import com.yangaobo.ai.conversation.exception.ConversationNotFoundException;
import com.yangaobo.ai.knowledge.exception.AdminAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiExceptionHandler {

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<AiErrorResponse> downstreamUnavailable(
            DownstreamServiceException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new AiErrorResponse(
                        "DOWNSTREAM_UNAVAILABLE",
                        exception.getMessage()));
    }

    @ExceptionHandler(AdminAccessDeniedException.class)
    public ResponseEntity<AiErrorResponse> adminAccessDenied(
            AdminAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AiErrorResponse(
                        "ADMIN_REQUIRED",
                        exception.getMessage()));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<AiErrorResponse> conversationNotFound(
            ConversationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AiErrorResponse(
                        "AI_CONVERSATION_NOT_FOUND",
                        exception.getMessage()));
    }

    @ExceptionHandler(ConversationBusyException.class)
    public ResponseEntity<AiErrorResponse> conversationBusy(
            ConversationBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AiErrorResponse(
                        "AI_CONVERSATION_BUSY",
                        exception.getMessage()));
    }

    @ExceptionHandler(BusinessOperationException.class)
    public ResponseEntity<AiErrorResponse> businessOperation(
            BusinessOperationException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new AiErrorResponse(
                        exception.getCode(),
                        exception.getMessage()));
    }

    public record AiErrorResponse(String code, String message) {
    }
}
