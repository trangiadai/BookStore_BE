package com.tgd.repository.chatbot;

import com.tgd.document.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
	Optional<ChatSession> findByAccountId(Long accountId);
}