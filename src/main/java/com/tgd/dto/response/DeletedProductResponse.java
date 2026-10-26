package com.tgd.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DeletedProductResponse {
	private Long id;
	private String name;
	private String categoryName;
	private BigDecimal sellingPrice;
	private Integer quantity;
	private LocalDateTime deletedAt;
	String coverUrl;

	public DeletedProductResponse(Long id, String name, String categoryName, BigDecimal sellingPrice, Integer quantity,
			LocalDateTime deletedAt, String coverUrl) {
		super();
		this.id = id;
		this.name = name;
		this.categoryName = categoryName;
		this.sellingPrice = sellingPrice;
		this.quantity = quantity;
		this.deletedAt = deletedAt;
		this.coverUrl = coverUrl;
	}

	public DeletedProductResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String categoryName) {
		this.categoryName = categoryName;
	}

	public BigDecimal getSellingPrice() {
		return sellingPrice;
	}

	public void setSellingPrice(BigDecimal sellingPrice) {
		this.sellingPrice = sellingPrice;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public String getCoverUrl() {
		return coverUrl;
	}

	public void setCoverUrl(String coverUrl) {
		this.coverUrl = coverUrl;
	}
}
