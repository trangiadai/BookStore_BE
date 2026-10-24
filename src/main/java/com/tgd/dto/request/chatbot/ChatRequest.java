package com.tgd.dto.request.chatbot;

import jakarta.validation.constraints.Size;

public class ChatRequest {
	@Size(max = 1000, message = "The maximum number of words per prompt is 1000")
	private String prompt;

	public ChatRequest(String prompt) {
		super();
		this.prompt = prompt;
	}

	public ChatRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
}
