package com.healthassist.controller;

import com.healthassist.model.ChatRequest;
import com.healthassist.model.ChatResponse;
import com.healthassist.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sync")
    public ChatResponse chatSync(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.getMessage(), request.getSessionId());
    }

    @PostMapping(value = "/async", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatAsync(@Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request.getMessage(), request.getSessionId())
                .map(token -> ServerSentEvent.<String>builder()
                        .data(token)
                        .build());
    }
}
