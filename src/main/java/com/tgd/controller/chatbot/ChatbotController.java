package com.tgd.controller.chatbot;

import com.tgd.document.ChatMessage;
import com.tgd.dto.request.chatbot.ChatRequest;
import com.tgd.dto.response.chatbot.ChatResponse;
import com.tgd.service.ChatbotService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chatbot")
public class ChatbotController {
	private final ChatbotService chatbotService;

	@PostMapping
	public ResponseEntity<ChatResponse> chatWithBot(@RequestBody ChatRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		String reply = chatbotService.processMessage(jwt, request.getPrompt());
		return ResponseEntity.ok(new ChatResponse(reply));
	}

	@GetMapping("/history")
	public ResponseEntity<List<ChatMessage>> getChatHistory(@AuthenticationPrincipal Jwt jwt) {
		List<ChatMessage> history = chatbotService.getChatHistory(jwt);
		
		return ResponseEntity.ok(history);
	}

	public ChatbotController(ChatbotService chatbotService) {
		super();
		this.chatbotService = chatbotService;
	}

}