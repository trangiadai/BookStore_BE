package com.tgd.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "chat_messages")
public class ChatMessage {
	@Id
	private String id;

	@Indexed
	private String sessionId;

	private String role;
	private String content;

	private Instant createdAt = Instant.now();

	public ChatMessage(String id, String sessionId, String role, String content, Instant createdAt) {
		super();
		this.id = id;
		this.sessionId = sessionId;
		this.role = role;
		this.content = content;
		this.createdAt = createdAt;
	}

	public ChatMessage() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}