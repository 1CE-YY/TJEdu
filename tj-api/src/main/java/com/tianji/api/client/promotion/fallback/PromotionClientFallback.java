package com.tianji.api.client.promotion.fallback;


import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;

@Slf4j
@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallback.class)
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        log.error("调用促销服务失败: {}", cause.getMessage());
        return new PromotionClient() {
            @Override
            public List<String> queryDiscountRules(List<Long> userCouponIds) {
                return null;
            }

            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
                return null;
            }
        };
    }
}
