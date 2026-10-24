package com.tgd.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "chat_sessions")
public class ChatSession {
	@Id
	private String id;

	@Indexed(unique = true)
	private Long accountId;

	private Instant createdAt = Instant.now();
	private Instant updatedAt = Instant.now();

	public ChatSession(String id, Long accountId, Instant createdAt, Instant updatedAt) {
		super();
		this.id = id;
		this.accountId = accountId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public ChatSession() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long accountId) {
		this.accountId = accountId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}