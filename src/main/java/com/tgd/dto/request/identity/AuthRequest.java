package com.tgd.dto.request.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {
	@NotBlank(message = "Email cannot be blank")
	@Email(message = "Invalid email format")
	@Size(max = 150, min = 5, message = "The maximun length of email is 150 characters, and the minimun is 5 characters")
	private String email;

	@NotBlank(message = "Password cannot be blank")
	@Size(max = 50, message = "The maximun length of password is 50 characters")
	private String password;

	public AuthRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	public AuthRequest(String email, String password) {
		super();
		this.email = email;
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
