package com.tgd.dao.mappers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.tgd.dto.response.DeletedProductResponse;
import com.tgd.entity.Product;

@Mapper
public interface ProductMapper {
	List<Product> getAllProducts(Map<String, Object> param);

	void createProduct(Map<String, Object> param);
	
	Long countAllProducts();

	int updateProduct(Map<String, Object> param);

	Optional<Product> getProductById(Map<String, Object> param);

	int softDeleteProduct(Map<String, Object> param);

	int hardDeleteProduct(Map<String, Object> param);

	Optional<Product> getSoftDeletedProductById(Map<String, Object> param);

	int recoverProduct(Map<String, Object> param);

	List<Product> searchSortFilterProducts(Map<String, Object> param);
	
	Long countSearchSortFilterProducts(Map<String, Object> param);
	
	int decreaseStock(Map<String, Object> params);
	
	void updateBatchStats(Map<String, Object> param);
	
	List<DeletedProductResponse> getAllSoftDeletedProducts();
}
