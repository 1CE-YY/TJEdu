package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionClientFallBack;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallBack.class)
public interface PromotionClient {

    @PostMapping("/user-coupons/available")
    List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> courses);

}
