package com.tgd.repository;

import org.springframework.stereotype.Repository;

import com.tgd.dao.mappers.AdminDashboardMapper;
import com.tgd.dto.response.DashboardResponse;
import com.tgd.enums.TimeLabel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminDashboardRepository {
    private final AdminDashboardMapper dashboardMapper;

	public long countTotalOrders() {
		return dashboardMapper.countTotalOrders();
	}

	public long countTotalCustomers() {
		return dashboardMapper.countTotalCustomers();
	}

	public long sumTotalInventoryCount() {
		return dashboardMapper.sumTotalInventoryCount();
	}

	public BigDecimal sumTotalRevenue() {
		return dashboardMapper.sumTotalRevenue();
	}

	public List<DashboardResponse.RevenueDataPoint> getRevenueOverTime(TimeLabel period) {
		Map<String, Object> param = new HashMap<>();
		param.put("period", period.name().toLowerCase());
		return dashboardMapper.getRevenueOverTime(param);
	}

	public List<DashboardResponse.CategoryRevenue> getRevenueByCategory() {
		return dashboardMapper.getRevenueByCategory();
	}

	public List<DashboardResponse.BestSellingBook> getBestSellingBooks(int limit) {
		Map<String, Object> param = new HashMap<>();
		param.put("limit", limit);
		return dashboardMapper.getBestSellingBooks(param);
	}

	public List<DashboardResponse.LowStockProduct> getLowStockProducts(int threshold) {
		Map<String, Object> param = new HashMap<>();
		param.put("threshold", threshold);
		return dashboardMapper.getLowStockProducts(param);
	}

	public AdminDashboardRepository(AdminDashboardMapper dashboardMapper) {
		super();
		this.dashboardMapper = dashboardMapper;
	}
    
    
}