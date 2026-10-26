package com.tgd.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.tgd.dao.mappers.ProductImageMapper;
import com.tgd.dto.response.DeletedImageResponse;
import com.tgd.entity.ProductImage;

@Repository
public class ProductImageRepository {
	private final ProductImageMapper productImageMapper;

	public Optional<ProductImage> getProductImageById(Long productImageId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productImageId", productImageId);

		return productImageMapper.getProductImageById(param);
	}

	public Optional<ProductImage> getSoftDeletedProductImageById(Long productImageId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productImageId", productImageId);

		return productImageMapper.getSoftDeletedProductImageById(param);
	}

	public List<ProductImage> getSoftDeletedImagesByProductId(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productImageMapper.getSoftDeletedImagesByProductId(param);
	}

	public Number createProductImage(ProductImage productImage) {
		Map<String, Object> param = new HashMap<>();
		param.put("url", productImage.getUrl());
		param.put("publicId", productImage.getPublicId());
		param.put("productId", productImage.getProductId());
		param.put("isPrimary", Boolean.TRUE.equals(productImage.getIsPrimary()) ? 1 : 0);
	    param.put("displayOrder", productImage.getDisplayOrder());

		productImageMapper.createProductImage(param);
		return (Number) param.get("id");
	}
	
	public boolean hasPrimaryImage(Long productId) {
	    Map<String, Object> param = new HashMap<>();
	    param.put("productId", productId);
	    return productImageMapper.hasPrimaryImage(param);
	}

	public int clearPrimaryImage(Long productId) {
	    Map<String, Object> param = new HashMap<>();
	    param.put("productId", productId);
	    return productImageMapper.clearPrimaryImage(param);
	}

	public int setPrimaryImage(Long imageId, Long productId) {
	    Map<String, Object> param = new HashMap<>();
	    param.put("imageId", imageId);
	    param.put("productId", productId);
	    return productImageMapper.setPrimaryImage(param);
	}
	

	public int softDeleteProductImage(Long productImageId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productImageId", productImageId);

		return productImageMapper.softDeleteProductImage(param);
	}
	
	public int promoteFirstRemainingImageToPrimary(Long productId) {
	    Map<String, Object> param = new HashMap<>();
	    param.put("productId", productId);
	    return productImageMapper.promoteFirstRemainingImageToPrimary(param);
	}

	public int softDeleteImagesByProductId(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productImageMapper.softDeleteImagesByProductId(param);
	}

	public int hardDeleteProductImage(Long productImageId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productImageId", productImageId);

		return productImageMapper.hardDeleteProductImage(param);
	}

	public int hardDeleteImagesByProductId(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productImageMapper.hardDeleteImagesByProductId(param);
	}

	public int recoverProductImage(Long productImageId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productImageId", productImageId);

		return productImageMapper.recoverProductImage(param);
	}

	public int recoverImagesByProductId(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productImageMapper.recoverImagesByProductId(param);
	}
	
	public List<DeletedImageResponse> getAllSoftDeletedImages(){
		return productImageMapper.getAllSoftDeletedImages();
	}

	public ProductImageRepository(ProductImageMapper productImageMapper) {
		super();
		this.productImageMapper = productImageMapper;
	}

}
