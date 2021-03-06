package com.spring.usinsa.serviceImpl.product;

import com.spring.usinsa.exception.ApiErrorCode;
import com.spring.usinsa.exception.ApiException;
import com.spring.usinsa.model.product.ProductLike;
import com.spring.usinsa.repository.ProductLikeRepository;
import com.spring.usinsa.service.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductLikeServiceImpl implements ProductLikeService {

    final ProductLikeRepository productLikeRepository;

    @Override
    public ProductLike save(Long userId, Long productId) {

        return productLikeRepository.save(ProductLike.builder()
                .userId(userId)
                .productId(productId)
                .build());
    }

    @Override
    public void deleteProductLikeByUserIdAndProductId(Long userId, Long productId) {
        if(productLikeRepository.deleteByUserIdAndProductId(userId, productId) <= 0)
            throw new ApiException(ApiErrorCode.OAUTH_ERROR);
    }

    @Override
    public Boolean existsProductLikeByUserIdAndProductId(Long userId, Long productId) {
        return productLikeRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public int countByProductId(Long productId) {
        return productLikeRepository.countByProductId(productId);
    }

    @Override
    public Page<ProductLike> countProductLikeByUserId(Long userId, Pageable pageable) {
        Page<ProductLike> productLikes = productLikeRepository.findByUserId(userId, pageable);
//        productLikes.map(m -> new ProductLikeDto());

        return productLikes;
    }
}
