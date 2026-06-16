package com.yangaobo.ai.conversation.controller;

import com.yangaobo.ai.conversation.dto.CreateConversationRequest;
import com.yangaobo.ai.conversation.dto.MessageSubmissionResponse;
import com.yangaobo.ai.conversation.dto.SendMessageRequest;
import com.yangaobo.ai.conversation.model.Conversation;
import com.yangaobo.ai.conversation.model.ConversationMessage;
import com.yangaobo.ai.conversation.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<Conversation> create(
            @Valid
            @RequestBody(required = false)
            CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(conversationService.create(title));
    }

    @GetMapping
    public List<Conversation> list() {
        return conversationService.list();
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessage> messages(
            @PathVariable UUID conversationId,
            @RequestParam(required = false)
            @Min(1) @Max(200) Integer limit) {
        return conversationService.messages(conversationId, limit);
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageSubmissionResponse> send(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(conversationService.submit(conversationId, request));
    }

    @GetMapping(
            value = "/{conversationId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID conversationId) {
        return conversationService.openStream(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID conversationId) {
        conversationService.delete(conversationId);
        return ResponseEntity.noContent().build();
    }
}
