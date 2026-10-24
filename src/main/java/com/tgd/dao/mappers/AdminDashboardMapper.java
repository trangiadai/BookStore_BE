package com.tgd.dao.mappers;

import org.apache.ibatis.annotations.Mapper;

import com.tgd.dto.response.DashboardResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDashboardMapper {
	Long countTotalOrders();

	Long countTotalCustomers();

	Long sumTotalInventoryCount();

	BigDecimal sumTotalRevenue();

	List<DashboardResponse.RevenueDataPoint> getRevenueOverTime(Map<String, Object> param);

	List<DashboardResponse.CategoryRevenue> getRevenueByCategory();

	List<DashboardResponse.BestSellingBook> getBestSellingBooks(Map<String, Object> param);

	List<DashboardResponse.LowStockProduct> getLowStockProducts(Map<String, Object> param);
}