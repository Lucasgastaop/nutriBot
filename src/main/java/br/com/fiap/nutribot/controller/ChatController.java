package br.com.fiap.nutribot.controller;

import br.com.fiap.nutribot.dto.ChatRequest;
import br.com.fiap.nutribot.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        String answer = chatClient.prompt()
                .user(request.message())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .call()
                .content();
        return new ChatResponse(answer == null ? "" : answer);
    }
}
