package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class PromotionClientFallBack implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
                        log.error("调用优惠券服务异常：", cause);
        return new PromotionClient() {
            @Override
            public java.util.List<com.tianji.api.dto.promotion.CouponDiscountDTO> findDiscountSolution(java.util.List<com.tianji.api.dto.promotion.OrderCourseDTO> courses) {

                return java.util.Collections.emptyList();
            }
        };
    }
}
