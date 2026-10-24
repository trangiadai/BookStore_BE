package com.tgd.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {
	// KPI Cards
	private long totalOrders;
	private long totalCustomers;
	private long totalInventoryCount;
	private BigDecimal totalRevenue;

	// Charts & Analytics
	private List<RevenueDataPoint> revenueOverTime;
	private List<CategoryRevenue> revenueByCategory;
	private List<BestSellingBook> bestSellingBooks;
	private List<CategoryRevenue> topPerformingCategories;

	// Alerts
	private List<LowStockProduct> lowStockProducts;

	public static class RevenueDataPoint {
		private String timeLabel; // e.g., "2026-10-23" (daily), "2026-10" (monthly), "2026" (yearly)
		private BigDecimal revenue;

		public RevenueDataPoint(String timeLabel, BigDecimal revenue) {
			super();
			this.timeLabel = timeLabel;
			this.revenue = revenue;
		}

		public RevenueDataPoint() {
			super();
			// TODO Auto-generated constructor stub
		}

		public String getTimeLabel() {
			return timeLabel;
		}

		public void setTimeLabel(String timeLabel) {
			this.timeLabel = timeLabel;
		}

		public BigDecimal getRevenue() {
			return revenue;
		}

		public void setRevenue(BigDecimal revenue) {
			this.revenue = revenue;
		}
	}

	public static class CategoryRevenue {
		private Long categoryId;
		private String categoryName;
		private BigDecimal revenue;
		private long unitsSold;

		public CategoryRevenue(Long categoryId, String categoryName, BigDecimal revenue, long unitsSold) {
			super();
			this.categoryId = categoryId;
			this.categoryName = categoryName;
			this.revenue = revenue;
			this.unitsSold = unitsSold;
		}

		public CategoryRevenue() {
			super();
			// TODO Auto-generated constructor stub
		}

		public Long getCategoryId() {
			return categoryId;
		}

		public void setCategoryId(Long categoryId) {
			this.categoryId = categoryId;
		}

		public String getCategoryName() {
			return categoryName;
		}

		public void setCategoryName(String categoryName) {
			this.categoryName = categoryName;
		}

		public BigDecimal getRevenue() {
			return revenue;
		}

		public void setRevenue(BigDecimal revenue) {
			this.revenue = revenue;
		}

		public long getUnitsSold() {
			return unitsSold;
		}

		public void setUnitsSold(long unitsSold) {
			this.unitsSold = unitsSold;
		}
	}

	public static class BestSellingBook {
		private Long productId;
		private String productName;
		private long totalUnitsSold;
		private BigDecimal totalRevenue;

		public BestSellingBook(Long productId, String productName, long totalUnitsSold, BigDecimal totalRevenue) {
			super();
			this.productId = productId;
			this.productName = productName;
			this.totalUnitsSold = totalUnitsSold;
			this.totalRevenue = totalRevenue;
		}

		public BestSellingBook() {
			super();
			// TODO Auto-generated constructor stub
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

		public long getTotalUnitsSold() {
			return totalUnitsSold;
		}

		public void setTotalUnitsSold(long totalUnitsSold) {
			this.totalUnitsSold = totalUnitsSold;
		}

		public BigDecimal getTotalRevenue() {
			return totalRevenue;
		}

		public void setTotalRevenue(BigDecimal totalRevenue) {
			this.totalRevenue = totalRevenue;
		}
	}

	public static class LowStockProduct {
		private Long productId;
		private String productName;
		private int stockQuantity;

		public LowStockProduct(Long productId, String productName, int stockQuantity) {
			super();
			this.productId = productId;
			this.productName = productName;
			this.stockQuantity = stockQuantity;
		}

		public LowStockProduct() {
			super();
			// TODO Auto-generated constructor stub
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

		public int getStockQuantity() {
			return stockQuantity;
		}

		public void setStockQuantity(int stockQuantity) {
			this.stockQuantity = stockQuantity;
		}
	}

	public DashboardResponse(long totalOrders, long totalCustomers, long totalInventoryCount, BigDecimal totalRevenue,
			List<RevenueDataPoint> revenueOverTime, List<CategoryRevenue> revenueByCategory,
			List<BestSellingBook> bestSellingBooks, List<CategoryRevenue> topPerformingCategories,
			List<LowStockProduct> lowStockProducts) {
		super();
		this.totalOrders = totalOrders;
		this.totalCustomers = totalCustomers;
		this.totalInventoryCount = totalInventoryCount;
		this.totalRevenue = totalRevenue;
		this.revenueOverTime = revenueOverTime;
		this.revenueByCategory = revenueByCategory;
		this.bestSellingBooks = bestSellingBooks;
		this.topPerformingCategories = topPerformingCategories;
		this.lowStockProducts = lowStockProducts;
	}

	public DashboardResponse() {
		super();
		// TODO Auto-generated constructor stub
	}

	public long getTotalOrders() {
		return totalOrders;
	}

	public void setTotalOrders(long totalOrders) {
		this.totalOrders = totalOrders;
	}

	public long getTotalCustomers() {
		return totalCustomers;
	}

	public void setTotalCustomers(long totalCustomers) {
		this.totalCustomers = totalCustomers;
	}

	public long getTotalInventoryCount() {
		return totalInventoryCount;
	}

	public void setTotalInventoryCount(long totalInventoryCount) {
		this.totalInventoryCount = totalInventoryCount;
	}

	public BigDecimal getTotalRevenue() {
		return totalRevenue;
	}

	public void setTotalRevenue(BigDecimal totalRevenue) {
		this.totalRevenue = totalRevenue;
	}

	public List<RevenueDataPoint> getRevenueOverTime() {
		return revenueOverTime;
	}

	public void setRevenueOverTime(List<RevenueDataPoint> revenueOverTime) {
		this.revenueOverTime = revenueOverTime;
	}

	public List<CategoryRevenue> getRevenueByCategory() {
		return revenueByCategory;
	}

	public void setRevenueByCategory(List<CategoryRevenue> revenueByCategory) {
		this.revenueByCategory = revenueByCategory;
	}

	public List<BestSellingBook> getBestSellingBooks() {
		return bestSellingBooks;
	}

	public void setBestSellingBooks(List<BestSellingBook> bestSellingBooks) {
		this.bestSellingBooks = bestSellingBooks;
	}

	public List<CategoryRevenue> getTopPerformingCategories() {
		return topPerformingCategories;
	}

	public void setTopPerformingCategories(List<CategoryRevenue> topPerformingCategories) {
		this.topPerformingCategories = topPerformingCategories;
	}

	public List<LowStockProduct> getLowStockProducts() {
		return lowStockProducts;
	}

	public void setLowStockProducts(List<LowStockProduct> lowStockProducts) {
		this.lowStockProducts = lowStockProducts;
	}
}