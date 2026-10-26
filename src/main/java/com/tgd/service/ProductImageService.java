package com.tgd.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tgd.dao.mappers.OrphanedFileMapper;
import com.tgd.dto.mappers.ProductImageMapperDTO;
import com.tgd.dto.response.DeletedImageResponse;
import com.tgd.entity.OrphanedFile;
import com.tgd.entity.ProductImage;
import com.tgd.enums.OrphanedFileStatus;
import com.tgd.repository.ProductImageRepository;
import com.tgd.repository.ProductRepository;

@Service
public class ProductImageService {
	private final ProductImageRepository productImageRepository;
	private final CloudinaryService cloudinaryService;
	private final OrphanedFileMapper orphanedFileMapper; // Added for Outbox Pattern
	private final ProductImageTxService productImageTxService;
	private final ProductRepository productRepository;

	@Transactional
	public int softDeleteProductImage(Long productImageId) {
		ProductImage productImage = getProductImageById(productImageId);
	    if (Boolean.TRUE.equals(productImage.getIsPrimary())) {
	        throw new IllegalStateException(
	            "This image is the product's cover. Set another image as the cover first, then delete this one.");
	    }
	    return productImageRepository.softDeleteProductImage(productImageId);
	}

	public int softDeleteImagesByProductId(Long productId) {
		return productImageRepository.softDeleteImagesByProductId(productId);
	}

	@Transactional
	public int hardDeleteProductImage(Long productImageId) {
		ProductImage softDeletedImage = getSoftDeletedProductImageById(productImageId);
		if (softDeletedImage.getPublicId() != null) {
			pushToOutboxQueue(softDeletedImage.getPublicId());
		}

		return productImageRepository.hardDeleteProductImage(productImageId);
	}

	@Transactional
	public int hardDeleteImagesByProductId(Long productId) {
		List<ProductImage> softDeletedImages = getSoftDeletedImagesByProductId(productId);

		for (ProductImage img : softDeletedImages) {
			if (img.getPublicId() != null) {
				pushToOutboxQueue(img.getPublicId());
			}
		}

		return productImageRepository.hardDeleteImagesByProductId(productId);
	}

	private ProductImage getSoftDeletedProductImageById(Long productImageId) {
		ProductImage productImage = productImageRepository.getSoftDeletedProductImageById(productImageId)
				.orElseThrow(() -> new IllegalArgumentException(
						"Not found in garbage collection the product image with id: " + productImageId));

		return productImage;
	}

	private List<ProductImage> getSoftDeletedImagesByProductId(Long productId) {
		List<ProductImage> productImages = productImageRepository.getSoftDeletedImagesByProductId(productId);

		return productImages;
	}

	public ProductImage getProductImageById(Long productImageId) {
		ProductImage productImage = productImageRepository.getProductImageById(productImageId).orElseThrow(
				() -> new IllegalArgumentException("Not found the product image with id: " + productImageId));

		return productImage;
	}

	// Main entry point - NO @Transactional here (avoids holding DB connection
	// during HTTP upload)
	public Set<ProductImage> createProductImage(Set<MultipartFile> productImages, Long productId) {
		if (productImages == null || productImages.isEmpty()) {
			return Collections.emptySet();
		}

		productRepository.getProductById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Not found active product with id: " + productId));

		Set<ProductImage> uploadedImages = new HashSet<>();

		// STEP 1: Upload to Cloudinary (HTTP Network I/O)
		for (MultipartFile file : productImages) {
			if (file != null && !file.isEmpty()) {
				try {
					ProductImage image = uploadToCloudinary(file);
					uploadedImages.add(image);
				} catch (IOException e) {
					// Network upload failed mid-way -> immediate cleanup of uploaded files
					rollbackCloudinaryUploads(uploadedImages);
					throw new RuntimeException("Image upload failed", e);
				}
			}
		}

		// STEP 2: Persist to DB atomically inside a Spring Proxy transaction
		try {
			return productImageTxService.saveAllImagesToDb(uploadedImages, productId);
		} catch (Exception e) {
			// DB insert failed -> DB automatically rolled back ALL inserted rows.
			// Queue ALL Cloudinary public_ids for background deletion.
			for (ProductImage img : uploadedImages) {
				if (img.getPublicId() != null) {
					pushToOutboxQueue(img.getPublicId());
				}
			}
			throw new RuntimeException("Failed to save product images to database", e);
		}
	}
	
	@Transactional
	public void setCoverImage(Long productId, Long imageId) {
	    // 1. Verify image exists and belongs to active product
	    ProductImage image = getProductImageById(imageId);
	    if (!image.getProductId().equals(productId)) {
	        throw new IllegalArgumentException("Image " + imageId + " does not belong to product " + productId);
	    }

	    // 2. Clear current primary flag for product
	    productImageRepository.clearPrimaryImage(productId);

	    // 3. Set new primary image
	    int updatedRows = productImageRepository.setPrimaryImage(imageId, productId);
	    if (updatedRows == 0) {
	        throw new IllegalStateException("Failed to set image as primary cover.");
	    }
	}

	public ProductImage uploadToCloudinary(MultipartFile rawProductImage) throws IOException {
		Map uploadResult = cloudinaryService.uploadFile(rawProductImage, "products");
		return ProductImageMapperDTO.toProductImage(uploadResult);
	}
	
	@Transactional
	public int recoverProductImage(Long productImageId) {
	    ProductImage softDeletedImage = getSoftDeletedProductImageById(productImageId);
	    productRepository.getProductById(softDeletedImage.getProductId())
	            .orElseThrow(() -> new IllegalArgumentException(
	                    "Cannot recover image. Parent product ID " + softDeletedImage.getProductId() + " is soft-deleted or does not exist. Recover product first."));

	    return productImageRepository.recoverProductImage(productImageId);
	}

	public int recoverImagesByProductId(Long productId) {
	    return productImageRepository.recoverImagesByProductId(productId);
	}
	
	public List<DeletedImageResponse> getAllSoftDeletedImages(){
		return productImageRepository.getAllSoftDeletedImages();
	}

	private void rollbackCloudinaryUploads(Set<ProductImage> images) {
		for (ProductImage img : images) {
			if (img.getPublicId() != null) {
				try {
					cloudinaryService.deleteFile(img.getPublicId());
				} catch (IOException e) {
					pushToOutboxQueue(img.getPublicId());
				}
			}
		}
	}

	private void pushToOutboxQueue(String publicId) {
		OrphanedFile file = new OrphanedFile();
		file.setPublicId(publicId);
		file.setStatus(OrphanedFileStatus.PENDING.name());
		file.setRetryCount(0);
		orphanedFileMapper.insert(file);
	}

	public ProductImageService(ProductImageRepository productImageRepository, CloudinaryService cloudinaryService,
			OrphanedFileMapper orphanedFileMapper, ProductImageTxService productImageTxService,
			ProductRepository productRepository) {
		super();
		this.productImageRepository = productImageRepository;
		this.cloudinaryService = cloudinaryService;
		this.orphanedFileMapper = orphanedFileMapper;
		this.productImageTxService = productImageTxService;
		this.productRepository = productRepository;
	}

}
