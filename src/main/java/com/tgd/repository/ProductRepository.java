package com.tgd.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.tgd.dao.mappers.ProductMapper;
import com.tgd.dto.request.ProductSearchSortFilterRequest;
import com.tgd.dto.request.ProductStatUpdate;
import com.tgd.dto.response.DeletedProductResponse;
import com.tgd.entity.Product;

@Repository
public class ProductRepository {
	private final ProductMapper productMapper;

	public Optional<Product> getProductById(Long id) {
		Map<String, Object> param = new HashMap<>();
		param.put("id", id);

		return productMapper.getProductById(param);
	}

	public List<Product> getAllProducts(int offset, int size) {
        Map<String, Object> param = new HashMap<>();
        param.put("offset", offset);
        param.put("size", size);
        return productMapper.getAllProducts(param);
    }
	
	public Long countAllProducts() {
		return productMapper.countAllProducts();
	}

	public Optional<Product> getSoftDeletedProductById(Long id) {
		Map<String, Object> param = new HashMap<>();
		param.put("id", id);

		return productMapper.getSoftDeletedProductById(param);
	}

	public Number createProduct(Product product) {
		Map<String, Object> param = new HashMap<>();
		param.put("name", product.getName());
		param.put("importPrice", product.getImportPrice());
		param.put("sellingPrice", product.getSellingPrice());
		param.put("quantity", product.getQuantity());
		param.put("description", product.getDescription());
		param.put("categoryId", product.getCategoryId());
		productMapper.createProduct(param);

		return (Number) param.get("id");
	}

	public int updateProduct(Product product) {
		Map<String, Object> param = new HashMap<>();
		param.put("id", product.getId());
		param.put("name", product.getName());
		param.put("importPrice", product.getImportPrice());
		param.put("sellingPrice", product.getSellingPrice());
		param.put("quantity", product.getQuantity());
		param.put("description", product.getDescription());
		param.put("categoryId", product.getCategoryId());

		return productMapper.updateProduct(param);
	}

	public int softDeleteProduct(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productMapper.softDeleteProduct(param);
	}

	public int hardDeleteProduct(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productMapper.hardDeleteProduct(param);
	}

	public int recoverProduct(Long productId) {
		Map<String, Object> param = new HashMap<>();
		param.put("productId", productId);

		return productMapper.recoverProduct(param);
	}

	public List<Product> searchSortFilterProducts(ProductSearchSortFilterRequest criteria) {
		Map<String, Object> param = new HashMap<>();
        param.put("name", criteria.getName());
        param.put("categoryId", criteria.getCategoryId());
        param.put("minPrice", criteria.getMinPrice());
        param.put("maxPrice", criteria.getMaxPrice());
        param.put("minRating", criteria.getMinRating());
        param.put("maxRating", criteria.getMaxRating());
        param.put("createdFrom", criteria.getCreatedFrom());
        param.put("createdTo", criteria.getCreatedTo());
        param.put("sortBy", criteria.getSortBy());
        param.put("sortDirection", criteria.getSortDirection());
        param.put("offset", criteria.getOffset());
        param.put("size", criteria.getSize());

		return productMapper.searchSortFilterProducts(param);
	}
	
	public Long countSearchSortFilterProducts(ProductSearchSortFilterRequest criteria) {
		Map<String, Object> param = new HashMap<>();
        param.put("name", criteria.getName());
        param.put("categoryId", criteria.getCategoryId());
        param.put("minPrice", criteria.getMinPrice());
        param.put("maxPrice", criteria.getMaxPrice());
        param.put("minRating", criteria.getMinRating());
        param.put("maxRating", criteria.getMaxRating());
        param.put("createdFrom", criteria.getCreatedFrom());
        param.put("createdTo", criteria.getCreatedTo());

		return productMapper.countSearchSortFilterProducts(param);
	}

	public int decreaseStock(Long productId, Integer quantity) {
		Map<String, Object> params = new HashMap<>();
		params.put("productId", productId);
		params.put("quantity", quantity);

		return productMapper.decreaseStock(params);
	}

	public void updateBatchStats(List<ProductStatUpdate> statsList) {
		if (statsList == null || statsList.isEmpty()) {
			return;
		}

		Map<String, Object> param = new HashMap<>();
		param.put("statsList", statsList);

		productMapper.updateBatchStats(param);
	}
	
	public List<DeletedProductResponse> getAllSoftDeletedProducts() {
	    return productMapper.getAllSoftDeletedProducts();
	}

	public ProductRepository(ProductMapper productMapper) {
		super();
		this.productMapper = productMapper;
	}

}
