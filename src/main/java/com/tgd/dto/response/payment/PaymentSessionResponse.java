package com.tgd.dto.response.payment;

public class PaymentSessionResponse {
	private String url;

	public PaymentSessionResponse(String url) {
		super();
		this.url = url;
	}

	public PaymentSessionResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
