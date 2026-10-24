package com.tgd.service;

import com.tgd.dao.mappers.OrphanedFileMapper;
import com.tgd.entity.OrphanedFile;
import com.tgd.enums.OrphanedFileStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminCloudinaryCleanupService {
	private final OrphanedFileMapper orphanedFileMapper;
	private final CloudinaryService cloudinaryService;
	private static final Logger log = LoggerFactory.getLogger(AdminCloudinaryCleanupService.class);

	
	// List all records stuck in FAILED_PERMANENTLY for the Admin UI.
	public List<OrphanedFile> getFailedPermanentlyFiles() {
		return orphanedFileMapper.findFailedPermanentlyFiles();
	}

	//Manual Retry for a single record triggered by Admin UI button.
	public boolean retrySingleFile(Long id) {
		OrphanedFile file = orphanedFileMapper.findById(id);
		if (file == null) {
			throw new IllegalArgumentException("Orphaned record not found with id: " + id);
		}

		try {
			cloudinaryService.deleteFile(file.getPublicId());
			orphanedFileMapper.deleteById(id);
			log.info("Admin successfully manually deleted Cloudinary file: {}", file.getPublicId());
			return true;

		} catch (Exception e) {
			file.setRetryCount(file.getRetryCount() + 1);
			file.setLastError("Manual Retry Failed: " + e.getMessage());
			orphanedFileMapper.updateStatusAndRetry(file);
			log.error("Admin manual retry failed for publicId {}: {}", file.getPublicId(), e.getMessage());
			return false;
		}
	}

	/**
	 * Force resolve a record (marks as MANUALLY_RESOLVED without contacting
	 * Cloudinary). Useful if the admin deleted the image directly via the
	 * Cloudinary Console.
	 */
	public void markAsResolved(Long id) {
		OrphanedFile file = orphanedFileMapper.findById(id);
		if (file != null) {
			file.setStatus(OrphanedFileStatus.MANUALLY_RESOLVED.name());
			orphanedFileMapper.updateStatusAndRetry(file);
			log.info("Record ID {} manually marked as RESOLVED by admin.", id);
		}
	}

	public AdminCloudinaryCleanupService(OrphanedFileMapper orphanedFileMapper, CloudinaryService cloudinaryService) {
		super();
		this.orphanedFileMapper = orphanedFileMapper;
		this.cloudinaryService = cloudinaryService;
	}
}