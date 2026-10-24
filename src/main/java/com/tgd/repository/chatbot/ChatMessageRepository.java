package com.tgd.repository.chatbot;

import com.tgd.document.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
	List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);
	
	List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);
}