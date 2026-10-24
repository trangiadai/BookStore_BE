package com.tgd.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ReviewCreateRequest {
	@NotNull(message = "Order item ID is required")
	@Positive(message = "Order item ID must be positive")
	private Long orderItemId;

	@NotNull(message = "Product ID is required")
	@Positive(message = "Product ID must be positive")
	private Long productId;

	@NotNull(message = "Rating is required")
	@Min(value = 1, message = "Rating must be at least 1")
	@Max(value = 5, message = "Rating cannot exceed 5")
	private Integer rating;

	@Size(max = 1000, message = "Comment must not exceed 1000 characters")
	private String comment;

	public ReviewCreateRequest(Long orderItemId, Long productId, Integer rating, String comment) {
		super();
		this.orderItemId = orderItemId;
		this.productId = productId;
		this.rating = rating;
		this.comment = comment;
	}

	public ReviewCreateRequest() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Long getOrderItemId() {
		return orderItemId;
	}

	public void setOrderItemId(Long orderItemId) {
		this.orderItemId = orderItemId;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public Integer getRating() {
		return rating;
	}

	public void setRating(Integer rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}
}
