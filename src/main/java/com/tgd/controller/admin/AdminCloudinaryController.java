package com.tgd.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tgd.entity.OrphanedFile;
import com.tgd.service.AdminCloudinaryCleanupService;

import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/admin/cloudinary-cleanup")
public class AdminCloudinaryController {
	private final AdminCloudinaryCleanupService adminService;

	// List all permanently failed files
	@GetMapping("/failed")
	public ResponseEntity<List<OrphanedFile>> getFailedFiles() {
		return ResponseEntity.ok(adminService.getFailedPermanentlyFiles());
	}

	// Manual Retry Delete Action
	@PostMapping("/{id}/retry")
	public ResponseEntity<String> retryFile(
			@PathVariable @Positive(message = "The id must be greater than 0") Long id) {
		boolean success = adminService.retrySingleFile(id);
		if (success) {
			return ResponseEntity.ok("Successfully deleted file from Cloudinary.");
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Manual retry failed. Check last_error field in database.");
	}

	// Force Resolve (in case admin delete directly via the Cloudinary Console)
	@PutMapping("/{id}/resolve")
	public ResponseEntity<Void> markAsResolved(
			@PathVariable @Positive(message = "The id must be greater than 0") Long id) {
		adminService.markAsResolved(id);
		return ResponseEntity.noContent().build();
	}

	public AdminCloudinaryController(AdminCloudinaryCleanupService adminService) {
		super();
		this.adminService = adminService;
	}
}