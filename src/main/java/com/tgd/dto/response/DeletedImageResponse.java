package com.tgd.dto.response;

import java.time.LocalDateTime;

public class DeletedImageResponse {
	private Long id;
	private String url;
	private Long productId;
	private String productName;
	private boolean productDeleted;
	private LocalDateTime deletedAt;

	public DeletedImageResponse(Long id, String url, Long productId, String productName, boolean productDeleted,
			LocalDateTime deletedAt) {
		super();
		this.id = id;
		this.url = url;
		this.productId = productId;
		this.productName = productName;
		this.productDeleted = productDeleted;
		this.deletedAt = deletedAt;
	}

	public DeletedImageResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public boolean isProductDeleted() {
		return productDeleted;
	}

	public void setProductDeleted(boolean productDeleted) {
		this.productDeleted = productDeleted;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}
}
