package com.tgd.dto.response.chatbot;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {
	private String reply;
	private List<BookRef> books = new ArrayList<>();

	public ChatResponse(String reply) {
		super();
		this.reply = reply;
	}

	public ChatResponse(String reply, List<BookRef> books) {
		super();
		this.reply = reply;
		this.books = books == null ? new ArrayList<>() : books;
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

	public List<BookRef> getBooks() {
		return books;
	}

	public void setBooks(List<BookRef> books) {
		this.books = books;
	}
}