package com.tgd.service;

import org.springframework.stereotype.Service;

import com.tgd.dto.response.DashboardResponse;
import com.tgd.enums.TimeLabel;
import com.tgd.repository.AdminDashboardRepository;

import java.util.List;

@Service
public class AdminDashboardService {
	private final AdminDashboardRepository dashboardRepository;

	public DashboardResponse getDashboardData(TimeLabel period) {
		if (period == null) {
			period = TimeLabel.DAILY;
		}

		DashboardResponse response = new DashboardResponse();

		// 1. KPI Cards
		response.setTotalOrders(dashboardRepository.countTotalOrders());
		response.setTotalCustomers(dashboardRepository.countTotalCustomers());
		response.setTotalInventoryCount(dashboardRepository.sumTotalInventoryCount());
		response.setTotalRevenue(dashboardRepository.sumTotalRevenue());

		// 2. Charts & Analytics
		response.setRevenueOverTime(dashboardRepository.getRevenueOverTime(period));

		List<DashboardResponse.CategoryRevenue> categoryRevenues = dashboardRepository.getRevenueByCategory();
		response.setRevenueByCategory(categoryRevenues);

		//Top 3
		List<DashboardResponse.CategoryRevenue> topCategories = categoryRevenues.stream().limit(3).toList();
		response.setTopPerformingCategories(topCategories);

		response.setBestSellingBooks(dashboardRepository.getBestSellingBooks(5));

		// 3. Alerts (< 5 units)
		response.setLowStockProducts(dashboardRepository.getLowStockProducts(5));

		return response;
	}

	public AdminDashboardService(AdminDashboardRepository dashboardRepository) {
		super();
		this.dashboardRepository = dashboardRepository;
	}
}