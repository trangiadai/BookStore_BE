package com.tgd.dto.request.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
	@NotBlank(message = "Email cannot be blank")
	@Email(message = "Invalid email format")
	@Size(min = 5, max = 150, message = "Email must be at least 5 characters and the maximun characters is 150")
	private String email;

	@NotBlank(message = "Password cannot be blank")
	@Size(min = 8, message = "Password must be at least 8 characters and the maximun characters is 50")
	private String password;

	@NotBlank(message = "Full name cannot be blank")
	@Size(max = 100, message = "The maximum lenght of full name is 100")
	private String fullName;

	@NotBlank(message = "Phone number cannot be blank")
	@Size(max = 20, message = "The maximum lenght of phone number is 20")
	@Pattern(regexp = "^[0-9]+$", message = "Phone number must contain only numbers")
	private String phoneNumber;

	@NotBlank(message = "Address cannot be blank")
	@Size(min = 3, max = 1000, message = "Address must be at least 3 characters and the maximun characters is 1000")
	private String address;

	public RegisterRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	public RegisterRequest(String email, String password, String fullName, String phoneNumber, String address) {
		super();
		this.email = email;
		this.password = password;
		this.fullName = fullName;
		this.phoneNumber = phoneNumber;
		this.address = address;
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

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

}
