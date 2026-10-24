package com.tgd.controller.admin;

import com.tgd.dto.response.DashboardResponse;
import com.tgd.enums.TimeLabel;
import com.tgd.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

	private final AdminDashboardService dashboardService;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DashboardResponse> getDashboard(
			@RequestParam(name = "period", defaultValue = "DAILY") TimeLabel period) {
		return ResponseEntity.ok(dashboardService.getDashboardData(period));
	}

	public AdminDashboardController(AdminDashboardService dashboardService) {
		super();
		this.dashboardService = dashboardService;
	}

}