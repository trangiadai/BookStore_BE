package com.tgd.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tgd.dto.request.ProductRequest;
import com.tgd.dto.request.ProductSearchSortFilterRequest;
import com.tgd.dto.response.DeletedProductResponse;
import com.tgd.dto.response.ProductResponse;
import com.tgd.service.ProductService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/products")
public class ProductController {
	private final ProductService productService;

	@GetMapping("/{id}")
	public ProductResponse getProductById(
			@PathVariable("id") @Positive(message = "Product ID must be greater than 0") Long id) {
		return productService.getProductById(id);
	}

	@GetMapping
	public Page<ProductResponse> getAllProducts(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		
		return productService.getAllProducts(page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse createProduct(@Valid @RequestBody ProductRequest productRequest) {
		return productService.createProduct(productRequest);
	}

	@PutMapping("/{id}")
	public ProductResponse updateProduct(
			@PathVariable("id") @Positive(message = "Product ID must be greater than 0") Long id,
			@Valid @RequestBody ProductRequest productRequest) {
		return productService.updateProduct(id, productRequest);
	}

	@DeleteMapping("/{id}")
	public int softDeleteProduct(@PathVariable @Positive(message = "Product ID must be greater than 0") Long id) {
		return productService.softDeleteProduct(id);
	}

	@DeleteMapping("/{id}/hard")
	public int hardDeleteProduct(@PathVariable @Positive(message = "Product ID must be greater than 0") Long id) {
		return productService.hardDeleteProduct(id);
	}

	@PatchMapping("/recover/{id}")
	public int recoverProduct(@PathVariable @Positive(message = "Product ID must be positive") Long id) {
		return productService.recoverProduct(id);
	}

	@GetMapping("/search")
	public Page<ProductResponse> searchSortFilterProducts(@Valid ProductSearchSortFilterRequest criteria) {
		return productService.searchSortFilterProducts(criteria);
	}
	
	@GetMapping("/trash")
	public List<DeletedProductResponse> getAllSoftDeletedProducts() {
	    return productService.getAllSoftDeletedProducts();
	}

	public ProductController(ProductService productService) {
		super();
		this.productService = productService;
	}

}
