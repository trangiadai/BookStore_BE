package com.tgd.dto.response.chatbot;

public class ChatResponse {
	private String reply;

	public ChatResponse(String reply) {
		super();
		this.reply = reply;
	}

	public ChatResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getReply() {
		return reply;
	}

	public void setReply(String reply) {
		this.reply = reply;
	}
}
