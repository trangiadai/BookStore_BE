package com.tgd.dto.response.chatbot;

/**
 * A store book mentioned in a chat message: the chat window turns its title
 * into a link to /books/{id}.
 */
public class BookRef {
	private Long id;
	private String label; // the title text to look for in the message (full title, or the short one
							// before ':' or '(')

	public BookRef(Long id, String label) {
		super();
		this.id = id;
		this.label = label;
	}

	public BookRef() {
		super();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}